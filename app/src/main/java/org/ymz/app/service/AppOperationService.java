package org.ymz.app.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessageType;
import dev.langchain4j.invocation.InvocationParameters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ymz.app.ai.services.CodeGenerateAiService;
import org.ymz.app.ai.services.TitleGenerateAiService;
import org.ymz.app.ai.tools.AiToolRegistry;
import org.ymz.app.browser.WebPageScreenshotService;
import org.ymz.app.config.AppPathProperties;
import org.ymz.app.model.dto.app.*;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppChatMessage;
import org.ymz.app.model.enums.UserRole;
import org.ymz.app.model.enums.app.AppAuditStatus;
import org.ymz.app.model.enums.app.ChatStreamMessageType;
import org.ymz.app.model.enums.app.FileNodeType;
import org.ymz.app.model.enums.oss.BucketType;
import org.ymz.app.oss.OssClient;
import org.ymz.app.security.AuthContext;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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

    private static final Set<String> PREVIEWABLE_CONTENT_TYPES = Set.of(
            "application/json",
            "application/javascript",
            "application/xml",
            "image/svg+xml");
    /**
     * 同一 appId 对应 LangChain4j 的同一 MemoryId，流式生成期间不能并发调用 AI Service。
     */
    private final ConcurrentMap<Long, Semaphore> appChatSemaphores = new ConcurrentHashMap<>();

    private final TitleGenerateAiService titleGenerateAiService;
    private final CodeGenerateAiService codeGenerateAiService;
    private final AppService appService;
    private final AppQueryService appQueryService;
    private final WebPageScreenshotService webPageScreenshotService;
    private final OssClient ossClient;
    private final AppPathProperties appPathProperties;
    private final AppChatMessageService appChatMessageService;
    private final AiToolRegistry aiToolRegistry;
    private final AppUrlBuilder appUrlBuilder;

    /**
     * 创建应用
     */
    public Flux<CreateAppStreamMessage> createApp(Long userId, CreateAppRequest request) {
        return Flux.create(createAppFluxSink -> Thread.startVirtualThread(() -> {
            try {
                Long appId = createApp(userId, request, createAppFluxSink::next);
                createAppFluxSink.next(CreateAppStreamMessage.done(appId, "应用创建完成"));
                createAppFluxSink.complete();
            } catch (BusinessException e) {
                createAppFluxSink.next(CreateAppStreamMessage.error(e.getMessage()));
                createAppFluxSink.complete();
            } catch (Exception e) {
                log.warn("应用创建失败: userId={}", userId, e);
                createAppFluxSink.next(CreateAppStreamMessage.error("应用创建失败"));
                createAppFluxSink.complete();
            }
        }));
    }

    private Long createApp(Long userId, CreateAppRequest request, Consumer<CreateAppStreamMessage> progressConsumer) {
        // 创建应用成功后返回应用 ID
        String initPrompt = request.getInitPrompt().trim();
        // 标题生成 AI Service 来对于用户初始提示词进行初筛

        InvocationParameters parameters = InvocationParameters.from(Map.of(
                "userId", userId
        ));

        TitleGenerateResult titleGenerateResult = titleGenerateAiService.chat(initPrompt, parameters);

        if (titleGenerateResult == null) {
            // AI 调用出错
            log.warn("应用标题生成失败");
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "标题生成失败");
        }
        if (!titleGenerateResult.isAccepted()) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, titleGenerateResult.getReason().getDescription());
        }
        progressConsumer.accept(CreateAppStreamMessage.progress("APP_CREATING", "正在创建应用"));
        App app = App.builder()
                .name(titleGenerateResult.getTitle())
                .initPrompt(initPrompt)
                .userId(userId)
                .build();
        boolean ok = appService.save(app);
        if (!ok) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "应用创建失败");
        }
        // 初始化应用工作区
        // 将项目模板（project-template/zhida-react-project）复制到应用工作区（tmp/app-workspace/{appId}）
        Long appId = app.getId();
        Path workspaceRootPath = appPathProperties.getTmpDir().resolve("app-workspace").normalize();
        Path workspacePath = workspaceRootPath.resolve(String.valueOf(appId)).normalize();
        try {
            log.debug("开始初始化应用{}的工作区", appId);
            Path templatePath = appPathProperties.getTemplateDir();
            if (!Files.isDirectory(templatePath)) {
                throw new IllegalStateException("项目模板目录不存在");
            }

            progressConsumer.accept(CreateAppStreamMessage.progress("TEMPLATE_COPYING", "正在初始化项目模板"));
            try (Stream<Path> stream = Files.walk(templatePath)) {
                log.debug("复制项目模板中...");
                for (Path sourceItem : stream.toList()) {
                    // 保留模板内部目录结构，复制成当前应用独立工作区。
                    Path targetItem = workspacePath.resolve(templatePath.relativize(sourceItem)).normalize();
                    if (Files.isDirectory(sourceItem)) {
                        Files.createDirectories(targetItem);
                    } else {
                        Files.createDirectories(targetItem.getParent());
                        Files.copy(sourceItem, targetItem, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            // 安装项目依赖
            progressConsumer.accept(CreateAppStreamMessage.progress("DEPENDENCY_INSTALLING", "正在安装依赖"));
            log.debug("依赖安装中...");
            String pnpm = System.getProperty("os.name").toLowerCase().contains("win") ? "pnpm.cmd" : "pnpm";
            Process process = new ProcessBuilder(pnpm, "install")
                    .directory(workspacePath.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("依赖安装超时：pnpm install");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("依赖安装失败：pnpm install");
            }
        } catch (Exception e) {
            log.warn("应用工作区初始化失败: appId={}", appId, e);
            try {
                // 数据库记录已经创建成功，初始化失败时必须回滚，避免留下僵尸应用。
                boolean removed = appService.removeById(appId);
                if (!removed) {
                    log.warn("应用创建失败后数据库记录回滚失败: appId={}", appId);
                }
            } catch (Exception rollbackException) {
                log.warn("应用创建失败后数据库记录回滚异常: appId={}", appId, rollbackException);
            }
            try {
                if (workspacePath.startsWith(workspaceRootPath) && Files.exists(workspacePath)) {
                    try (Stream<Path> stream = Files.walk(workspacePath)) {
                        // 先删子文件和子目录，再删父目录，避免目录非空导致删除失败。
                        for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                            Files.deleteIfExists(item);
                        }
                    }
                }
            } catch (Exception cleanupException) {
                log.warn("应用创建失败后工作区清理异常: appId={}", appId, cleanupException);
            }
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "应用工作区初始化失败");
        }
        log.debug("应用{}工作区初始化完成", appId);
        return appId;
    }

    /**
     * 编辑应用信息
     */
    public AppVO editApp(AuthContext authContext, Long appId, EditAppRequest request) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND);
        }
        String appName = request.getName().trim();
        // 仅有应用作者或管理员可以编辑应用信息

        if (!app.getUserId().equals(authContext.getUserId()) && !UserRole.ADMIN.equals(authContext.getUserRole())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }
        ensureNotPendingAudit(app);

        appService.updateById(
                App.builder()
                        .id(appId)
                        .name(appName)
                        .build());
        return appQueryService.getApp(authContext, appId);
    }

    /**
     * 删除应用
     */
    public void deleteApp(AuthContext authContext, Long appId) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND);
        }
        if (!app.getUserId().equals(authContext.getUserId()) && !UserRole.ADMIN.equals(authContext.getUserRole())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }
        // 先删除数据库记录，成功后再去异步清理当前应用关联资源（工作区、预览文件、部署文件）
        boolean ok = appService.removeById(appId);
        if (!ok) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "应用删除失败");
        }
        // 异步删除应用资源
        Thread.startVirtualThread(() -> {
            try {
                log.debug("开始删除应用{}的相关资源", appId);
                Path workspaceRootPath = appPathProperties.getTmpDir().resolve("app-workspace").normalize();
                Path workspacePath = appPathProperties.getTmpDir()
                        .resolve("app-workspace")
                        .resolve(String.valueOf(appId))
                        .normalize();
                if (workspacePath.startsWith(workspaceRootPath) && Files.exists(workspacePath)) {
                    log.debug("开始删除应用{}的工作区文件", appId);
                    try (Stream<Path> stream = Files.walk(workspacePath)) {
                        // 先删子文件和子目录，再删父目录，避免目录非空导致删除失败。
                        for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                            Files.deleteIfExists(item);
                        }
                    }
                }

                Path previewRootPath = appPathProperties.getTmpDir().resolve("app-previews").normalize();
                Path previewPath = appPathProperties.getTmpDir()
                        .resolve("app-previews")
                        .resolve(String.valueOf(appId))
                        .normalize();
                if (previewPath.startsWith(previewRootPath) && Files.exists(previewPath)) {
                    log.debug("开始删除应用{}的预览文件", appId);
                    try (Stream<Path> stream = Files.walk(previewPath)) {
                        // 先删子文件和子目录，再删父目录，避免目录非空导致删除失败。
                        for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                            Files.deleteIfExists(item);
                        }
                    }
                }

                if (StrUtil.isNotBlank(app.getDeployKey())) {
                    Path deployRootPath = appPathProperties.getTmpDir().resolve("app-deploy").normalize();
                    Path deployPath = deployRootPath.resolve(app.getDeployKey()).normalize();
                    if (deployPath.startsWith(deployRootPath) && Files.exists(deployPath)) {
                        log.debug("开始删除应用{}的部署文件", appId);
                        try (Stream<Path> stream = Files.walk(deployPath)) {
                            // 先删子文件和子目录，再删父目录，避免目录非空导致删除失败。
                            for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                                Files.deleteIfExists(item);
                            }
                        }
                    }
                }
                log.debug("应用{}的相关资源删除成功", appId);
            } catch (Exception e) {
                log.warn("应用资源异步清理失败: appId={}", appId, e);
            }
        });
    }

    /**
     * 打开应用工作区文件或目录
     */
    public FileNode openAppFile(AuthContext authContext, Long appId, String path) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        // 文件树只能给应用作者或管理员预览，避免泄露其他用户源码。
        if (!authContext.getUserId().equals(app.getUserId()) && !UserRole.ADMIN.equals(authContext.getUserRole())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }

        String relativePath = StrUtil.trimToEmpty(path);
        Path workspaceRootPath = appPathProperties.getTmpDir().resolve("app-workspace").normalize();
        Path workspacePath = workspaceRootPath.resolve(String.valueOf(appId)).normalize();
        if (!workspacePath.startsWith(workspaceRootPath) || !Files.isDirectory(workspacePath)) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用源码目录不存在");
        }

        Path targetPath = relativePath.isEmpty()
                ? workspacePath
                : workspacePath.resolve(relativePath).normalize();
        // 所有用户输入路径都必须落在当前应用工作区内，防止 ../../ 访问外部文件。
        if (!targetPath.startsWith(workspacePath)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM);
        }
        if (isIgnoredPath(targetPath, workspacePath)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM);
        }
        if (!Files.exists(targetPath)) {
            throw BusinessException.of(ResultCode.NOT_FOUND);
        }

        try {
            if (Files.isDirectory(targetPath)) {
                // 打开目录时只返回当前层级，前端需要展开子目录时再请求一次接口。
                try (Stream<Path> stream = Files.list(targetPath)) {
                    List<FileNode> children = stream
                            .filter(item -> {
                                // 目录列表中隐藏依赖目录、构建产物和日志文件。
                                return !isIgnoredPath(item, workspacePath);
                            })
                            .sorted(Comparator
                                    // 展示顺序固定为目录在前、文件在后，同类按名称排序。
                                    .<Path, Boolean>comparing(item -> !Files.isDirectory(item))
                                    .thenComparing(item -> item.getFileName().toString(),
                                            String.CASE_INSENSITIVE_ORDER))
                            .map(item -> {
                                String itemRelativePath = workspacePath.relativize(item).toString().replace('\\', '/');
                                return FileNode.builder()
                                        .title(item.getFileName().toString())
                                        .path(itemRelativePath)
                                        .type(Files.isDirectory(item) ? FileNodeType.DIRECTORY : FileNodeType.FILE)
                                        .build();
                            })
                            .toList();
                    return FileNode.builder()
                            .title(relativePath.isEmpty() ? "/" : targetPath.getFileName().toString())
                            .path(relativePath)
                            .type(FileNodeType.DIRECTORY)
                            .children(children)
                            .build();
                }
            }

            String contentType = Files.probeContentType(targetPath);
            // 优先根据文件类型判断是否可预览；jsx 等源码文件可能识别不到类型，识别不到时交给 UTF-8 读取处理。
            if (contentType != null
                    && !contentType.startsWith("text/")
                    && !PREVIEWABLE_CONTENT_TYPES.contains(contentType)) {
                throw BusinessException.of(ResultCode.INVALID_PARAM, "暂不支持打开该文件");
            }
            // 只有真正打开文件时才读取内容，目录列表里的文件节点不携带 content。
            return FileNode.builder()
                    .title(targetPath.getFileName().toString())
                    .path(relativePath)
                    .type(FileNodeType.FILE)
                    .content(Files.readString(targetPath, StandardCharsets.UTF_8))
                    .build();
        } catch (IOException e) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "打开应用文件失败", e);
        }
    }

    private boolean isIgnoredPath(Path path, Path workspacePath) {
        Path relativePath = workspacePath.relativize(path);
        for (Path name : relativePath) {
            String itemName = name.toString();
            if (StrUtil.equalsAny(itemName, "node_modules", "dist")
                    || itemName.endsWith(".log")
                    || itemName.startsWith("pnpm-debug.log")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 下载应用源码压缩包
     */
    public void downloadAppSourceCode(AuthContext authContext, Long appId, OutputStream outputStream) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!authContext.getUserId().equals(app.getUserId()) && !UserRole.ADMIN.equals(authContext.getUserRole())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }

        Path workspaceRootPath = appPathProperties.getTmpDir().resolve("app-workspace").normalize();
        Path workspacePath = workspaceRootPath.resolve(String.valueOf(appId)).normalize();
        if (!workspacePath.startsWith(workspaceRootPath) || !Files.isDirectory(workspacePath)) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用源码目录不存在");
        }

        ZipUtil.zip(outputStream, StandardCharsets.UTF_8, false, file -> {
            String fileName = file.getName();
            // 跳过项目依赖、构建产物和日志文件
            return !("node_modules".equals(fileName)
                    || "dist".equals(fileName)
                    || fileName.endsWith(".log")
                    || fileName.startsWith("pnpm-debug.log"));
        }, workspacePath.toFile());
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
        ensureNotPendingAudit(app);
        // 校验 deployeKey
        String deployKey = app.getDeployKey();

        if (StrUtil.isBlank(deployKey)) {
            // 如果为空，则生成一个 16 位的唯一Key
            // TODO 本科毕业设计演示场景下，deployKey重复的概率极低，可作为后续优化点
            deployKey = RandomUtil.randomString(16);
        }

        try {
            Path sourcePath = appPathProperties.getTmpDir()
                    .resolve("app-workspace")
                    .resolve(String.valueOf(appId))
                    .normalize();
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
            Path deployPath = appPathProperties.getTmpDir()
                    .resolve("app-deploy")
                    .resolve(deployKey)
                    .normalize();
            if (Files.exists(deployPath)) {
                try (Stream<Path> stream = Files.walk(deployPath)) {
                    // 先删除子文件和子目录，再删除父目录，避免目录非空导致删除失败。
                    for (Path item : stream.sorted(Comparator.reverseOrder()).toList()) {
                        Files.deleteIfExists(item);
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

            String deployUrl = appUrlBuilder.buildDeployUrl(deployKey);
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
                        ossClient.uploadObject(BucketType.PUBLIC, inputStream, coverKey, "image/jpeg",
                                coverBytes.length);
                    }
                    String coverUrl = ossClient.getPublicObjectUrl(coverKey);
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

    /**
     * 通过对话生成应用
     * <br/>
     * Notes:
     * LangChain4j 官方文档指出：对于相同的 @MemoryId（当前项目中指的是 appId），不应该并发调用 Ai Service ，否则可能导致
     * ChatMemory 损坏。
     * 因此我应该对这个重要的聊天方法加上合适的并发限制
     */
    public Flux<ChatStreamMessage> chat(Long userId, Long appId, ChatRequest request) {
        // 校验应用是否存在
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND);
        }
        // 只允许作者本人在自己的应用内和 AI 对话
        if (!app.getUserId().equals(userId)) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }
        ensureNotPendingAudit(app);

        String userMessage = request.getPrompt().trim();

        return Flux.defer(() -> {
            Semaphore appChatSemaphore = appChatSemaphores.computeIfAbsent(appId, ignored -> new Semaphore(1));
            if (!appChatSemaphore.tryAcquire()) {
                return Flux.error(BusinessException.of(ResultCode.TOO_MANY_REQUESTS, "当前应用正在生成中，请稍后再试"));
            }
            AtomicBoolean released = new AtomicBoolean(false);
            Runnable releaseAppChatSemaphore = () -> {
                if (released.compareAndSet(false, true)) {
                    appChatSemaphore.release();
                }
            };
            // 遇到过 AI 绕过 exitToolCall 工具直接结束的边界情况，以下代码是最后一道防线，在此之前我对 exitToolCall
            // 工具的描述以及系统提示词进行了加固
            // TODO 但是目前无法做到完美，这一步只能提示用户和记录日志，系统无法办到让 AI 重试
            // 退出工具调用过么？
            AtomicBoolean exitCalled = new AtomicBoolean(false);
            // 退出工具调用成功了么？
            AtomicBoolean exitPassed = new AtomicBoolean(false);

            return Flux.create(chatFluxSink -> {
                try {
                    // 拿到当前 appId 的生成许可后，再记录用户消息，避免被拒绝的并发请求污染对话记录。
                    appChatMessageService.save(
                            AppChatMessage.builder()
                                    .appId(appId)
                                    .userId(userId)
                                    .role(ChatMessageType.USER.name())
                                    .content(userMessage)
                                    .build());

                    // 处理 TokenStream
                    StringBuffer reasoningContentBuffer = new StringBuffer();
                    StringBuffer finalContentBuffer = new StringBuffer();
                    InvocationParameters parameters = InvocationParameters.from(Map.of(
                            "userId", userId
                    ));
                    codeGenerateAiService.chat(appId, userMessage, parameters)
                            .onPartialThinking(partialThinking -> {
                                // 思考内容单独发送和留存，便于前端分区展示。
                                String thinkingText = partialThinking.text();
                                reasoningContentBuffer.append(thinkingText);
                                chatFluxSink.next(ChatStreamMessage.of(
                                        ChatStreamMessageType.REASONING,
                                        thinkingText));
                            })
                            .onPartialResponse(partialResponse -> {
                                // 处理模型生成的文本片段，后端留存后，再直接发送给前端
                                finalContentBuffer.append(partialResponse);
                                chatFluxSink.next(ChatStreamMessage.of(
                                        ChatStreamMessageType.CONTENT,
                                        partialResponse));
                            })
                            .beforeToolExecution(beforeToolExecution -> {
                                // 工具执行前
                                ToolExecutionRequest req = beforeToolExecution.request();
                                String toolName = req.name();
                                String argumentsJsonStr = req.arguments();
                                String content = aiToolRegistry.getByName(toolName)
                                        .formatRequest(JSONUtil.parseObj(argumentsJsonStr));
                                finalContentBuffer.append(content);
                                chatFluxSink.next(ChatStreamMessage.of(
                                        ChatStreamMessageType.CONTENT,
                                        content));
                            })
                            .onToolExecuted(toolExecution -> {
                                // 工具执行后
                                ToolExecutionRequest req = toolExecution.request();
                                String toolName = req.name();
                                String argumentsJsonStr = req.arguments();
                                String result = toolExecution.result();

                                // 特殊判断是否调用过退出工具以及是否完成
                                if ("exitToolCalling".equals(toolName)) {
                                    exitCalled.set(true);
                                    exitPassed.set("系统验收通过，请停止调用工具并输出最终结果".equals(result));
                                }

                                String content = aiToolRegistry.getByName(toolName)
                                        .formatResponse(JSONUtil.parseObj(argumentsJsonStr), result);
                                finalContentBuffer.append(content);
                                chatFluxSink.next(ChatStreamMessage.of(
                                        ChatStreamMessageType.CONTENT,
                                        content));
                            })
                            .onCompleteResponse(response -> {
                                try {
                                    // AI 回复结束
                                    log.debug("AI 最终输出的内容为：{}", response.aiMessage().text());

                                    // TODO 一个较为无奈的 AI 绕过 exitToolCall 工具的处理方案
                                    if (!exitCalled.get() || !exitPassed.get()) {
                                        String guardContent = exitCalled.get()
                                                ? "\n【系统拦截】AI 已调用 exitToolCalling，但系统验收未通过，本轮生成未作为有效交付。\n"
                                                : "\n【系统拦截】AI 未调用 exitToolCalling，已绕过强制验收流程，本轮生成未作为有效交付。\n";
                                        log.warn("AI 未通过退出工具验收即结束，userId={}, appId={}, exitCalled={}, exitPassed={}",
                                                userId, appId, exitCalled.get(), exitPassed.get());
                                        finalContentBuffer.append(guardContent);

                                        appChatMessageService.save(
                                                AppChatMessage.builder()
                                                        .appId(appId)
                                                        .userId(userId)
                                                        .role(ChatMessageType.AI.name())
                                                        .reasoningContent(reasoningContentBuffer.toString())
                                                        .content(finalContentBuffer.toString())
                                                        .build());

                                        chatFluxSink.next(ChatStreamMessage.of(
                                                ChatStreamMessageType.CONTENT,
                                                guardContent));
                                        chatFluxSink.complete();
                                        return;
                                    }
                                    // 存储 AI 完整回复内容
                                    appChatMessageService.save(
                                            AppChatMessage.builder()
                                                    .appId(appId)
                                                    .userId(userId)
                                                    .role(ChatMessageType.AI.name())
                                                    .reasoningContent(reasoningContentBuffer.toString())
                                                    .content(finalContentBuffer.toString())
                                                    .build());
                                    chatFluxSink.complete();
                                } finally {
                                    releaseAppChatSemaphore.run();
                                }
                            })
                            .onError(error -> {
                                try {
                                    // 回复出现异常时
                                    log.error("AI 回复时出现异常，userId={}, appId={}", userId, appId, error);
                                    String errorContent = "\n\n【错误】AI 回复失败，请稍后重试。\n";
                                    finalContentBuffer.append(errorContent);
                                    // 通知前端显示错误信息
                                    chatFluxSink.next(ChatStreamMessage.of(
                                            ChatStreamMessageType.CONTENT,
                                            errorContent));

                                    // 错误时也要保存一条 AI 消息
                                    appChatMessageService.save(
                                            AppChatMessage.builder()
                                                    .appId(appId)
                                                    .userId(userId)
                                                    .role(ChatMessageType.AI.name())
                                                    .reasoningContent(reasoningContentBuffer.toString())
                                                    .content(finalContentBuffer.toString())
                                                    .build());
                                    chatFluxSink.complete();
                                } finally {
                                    releaseAppChatSemaphore.run();
                                }
                            }).start();
                } catch (Exception e) {
                    releaseAppChatSemaphore.run();
                    chatFluxSink.error(e);
                }
            });
        });
    }

    private void ensureNotPendingAudit(App app) {
        // 待审核期间冻结应用内容，用户需要先撤回审核再修改。
        if (AppAuditStatus.PENDING.getCode().equals(app.getAuditStatus())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "应用正在审核中，请撤回审核后再修改");
        }
    }
}
