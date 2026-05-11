package org.ymz.app.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import dev.langchain4j.invocation.InvocationParameters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ymz.app.ai.monitoring.LlmMonitoringAttributes;
import org.ymz.app.ai.monitoring.LlmMonitoringContext;
import org.ymz.app.ai.title.TitleGenerateAssistant;
import org.ymz.app.ai.title.TitleGenerateResult;
import org.ymz.app.browser.WebPageScreenshotService;
import org.ymz.app.model.dto.app.CreateAppRequest;
import org.ymz.app.model.dto.app.CreateAppResponse;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.enums.UserRole;
import org.ymz.app.model.enums.app.*;
import org.ymz.app.model.enums.oss.BucketType;
import org.ymz.app.oss.RustFSClient;
import org.ymz.app.security.AuthContext;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.ymz.app.model.entity.table.AppTableDef.APP;

/**
 * 应用创建与部署业务。
 *
 * @author ymz
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppOperationService {

    private static final String DEPLOY_HOSTNAME = "localhost";

    private final TitleGenerateAssistant titleGenerateAssistant;
    private final AppService appService;
    private final WebPageScreenshotService webPageScreenshotService;
    private final RustFSClient rustFSClient;

    /*
     * TODO： 要大改！
     */

    @Transactional
    public CreateAppResponse createApp(Long userId, CreateAppRequest request) {
        String prompt = StrUtil.trim(request.getPrompt());
        // 由标题生成 AI 来对用户提示词进行初步筛选
        TitleGenerateResult titleResult = titleGenerateAssistant.chat(
                prompt,
                InvocationParameters.from(
                        LlmMonitoringAttributes.CONTEXT,
                        new LlmMonitoringContext(LlmMonitoringAttributes.SCENARIO_TITLE_GENERATION, null, null)));
        if (titleResult == null || !titleResult.isAccepted() || StrUtil.isBlank(titleResult.getTitle())) {
            String reason = titleResult == null || titleResult.getReason() == null
                    ? "无法识别应用需求"
                    : titleResult.getReason().getDescription();
            throw BusinessException.of(ResultCode.INVALID_PARAM, reason);
        }

        App app = App.builder()
                .userId(userId)
                .name(titleResult.getTitle())
                .initPrompt(prompt)
                .status(AppStatus.CREATING.name())
                .deployStatus(AppDeployStatus.UNDEPLOYED.name())
                .createdAt(LocalDateTime.now())
                .build();
        appService.save(app);

        return CreateAppResponse.builder()
                .appId(app.getId())
                .name(app.getName())
                .status(app.getStatus())
                .build();
    }

    /**
     * 获取预览的静态资源
     * 仅有作者本人以及管理员能看到
     */
    public Path getPreviewFilePath(Long appId, String resourcePath, AuthContext authContext) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND);
        }
        // 仅应用作者本人或管理员可以预览
        if (!authContext.getUserId().equals(app.getUserId()) && !UserRole.ADMIN.equals(authContext.getUserRole())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }

        // 默认目录访问 index.html
        if ("/".equals(resourcePath)) {
            resourcePath = "/index.html";
        }
        Path previewRootPath = Paths.get(
                System.getProperty("user.dir"),
                "tmp",
                "app-previews",
                String.valueOf(appId)).normalize();
        // 构建目标文件路径
        Path targetPath = previewRootPath
                // 去掉开头的 /
                .resolve(resourcePath.substring(1))
                .normalize();
        // 防止路径穿越，即防止用户用 ../../ 访问预览目录外的文件
        if (!targetPath.startsWith(previewRootPath)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM);
        }
        return targetPath;
    }

    /**
     * 部署应用，并自动给应用截取封面图片
     * TODO 此方法存在并发生成 deployKey 的风险，但本科毕业设计演示场景下无需加锁，可作为后续优化点
     */
    public String deployApp(Long userId, Long appId) {
        log.debug("开始部署应用");
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        // 仅作者本人可以部署自己的应用
        if (!userId.equals(app.getUserId())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }
        // 校验 deployeKey
        String deployKey = app.getDeployKey();

        if (StrUtil.isBlank(deployKey)) {
            // 如果为空，则生成一个 16 位的唯一Key
            // TODO 本科毕业设计演示场景下，deployKey重复的概率极低，可作为后续优化点
            deployKey = RandomUtil.randomString(16);
        }

        try {
            Path sourcePath = Paths.get(
                    System.getProperty("user.dir"),
                    "tmp",
                    "app-workspace",
                    String.valueOf(appId)).normalize();
            if (!Files.isDirectory(sourcePath)) {
                throw BusinessException.of(ResultCode.NOT_FOUND, "应用源码目录不存在");
            }

            // 部署前重新安装依赖并执行正式构建，确保 dist 是最新产物。
            log.debug("开始安装依赖和构建依赖");
            for (String command : new String[] { "install", "build" }) {
                String pnpm = System.getProperty("os.name").toLowerCase().contains("win") ? "pnpm.cmd" : "pnpm";
                Process process = new ProcessBuilder(pnpm, command)
                        .directory(sourcePath.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                        .start();
                boolean finished = process.waitFor(10, TimeUnit.MINUTES);
                if (!finished) {
                    process.destroyForcibly();
                    throw new IllegalStateException("部署命令执行超时：pnpm " + command);
                }
                if (process.exitValue() != 0) {
                    throw new IllegalStateException("部署命令执行失败：pnpm " + command);
                }
            }

            Path distPath = sourcePath.resolve("dist").normalize();
            if (!Files.isDirectory(distPath) || !Files.isRegularFile(distPath.resolve("index.html"))) {
                throw BusinessException.of(ResultCode.INVALID_PARAM, "构建产物不存在");
            }

            // 正式部署目录放在 tmp 下，并按 deployKey 覆盖，保持演示流程简单直观。
            log.debug("开始复制文件到部署目录");
            Path deployPath = Paths.get(System.getProperty("user.dir"), "tmp", "app-deploy", deployKey).normalize();
            if (Files.exists(deployPath)) {
                try (Stream<Path> stream = Files.walk(deployPath)) {
                    // 先删除子文件和子目录，再删除父目录，避免目录非空导致删除失败。
                    for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                        Files.delete(item);
                    }
                }
            }
            try (Stream<Path> stream = Files.walk(distPath)) {
                for (Path sourceItem : stream.toList()) {
                    // 保留 dist 内部的相对路径结构，将构建产物复制到部署目录。
                    Path targetItem = deployPath.resolve(distPath.relativize(sourceItem)).normalize();
                    if (Files.isDirectory(sourceItem)) {
                        Files.createDirectories(targetItem);
                    } else {
                        Files.createDirectories(targetItem.getParent());
                        Files.copy(sourceItem, targetItem, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            String deployUrl = "http://" + DEPLOY_HOSTNAME + "/" + deployKey + "/";
            appService.updateById(App.builder()
                    .id(appId)
                    .deployKey(deployKey)
                    .deployedAt(LocalDateTime.now())
                    .build());
            log.debug("部署成功，访问路径：{}", deployUrl);
            // 部署成功后，异步地去给应用截取封面图片
            Thread.startVirtualThread(() -> {
                log.debug("开始截图...");
                try {
                    // 部署接口先返回访问地址，封面截图、上传 OSS 和 coverUrl 写回在后台虚拟线程中完成。
                    byte[] coverBytes = webPageScreenshotService.captureJpeg(deployUrl);
                    String coverKey = "app-covers/" + appId + "/" + IdUtil.fastSimpleUUID() + ".jpg";
                    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(coverBytes)) {
                        rustFSClient.uploadObject(BucketType.PUBLIC, inputStream, coverKey, "image/jpeg",
                                coverBytes.length);
                    }
                    String coverUrl = rustFSClient.getPublicObjectUrl(coverKey);
                    appService.updateChain()
                            .set(APP.COVER_URL, coverUrl)
                            .where(APP.ID.eq(appId))
                            .update();
                    log.debug("截图成功");
                } catch (Exception e) {
                    log.warn("应用封面生成失败: appId={}, deployUrl={}", appId, deployUrl, e);
                }
            });
            return deployUrl;
        } catch (Exception e) {
            log.warn("应用部署失败: appId={}", appId, e);
            if (e instanceof BusinessException businessException) {
                throw businessException;
            }
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "应用部署失败");
        }

    }

}
