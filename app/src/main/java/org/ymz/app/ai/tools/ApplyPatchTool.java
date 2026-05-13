package org.ymz.app.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.springframework.stereotype.Component;
import org.ymz.app.config.AppPathProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;

/**
 * 文件补丁工具
 * 使用 diff-match-patch 补丁更新文件内容
 * 
 * @author ymz
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplyPatchTool implements BaseTool {

    private final AppPathProperties appPathProperties;

    // 警告提示词
    private static final String PROTECTED_PATCH_WARNING = """
            当前应用补丁请求已被拒绝，原因是以下文件属于受保护路径，禁止删除或修改：
            %s

            请明确回复用户：说明该路径受模板边界保护，严禁修改
            """.formatted(ProtectedTemplatePaths.listText());

    @Override
    public String toolName() {
        return "applyPatch";
    }

    @Override
    public String displayName() {
        return "应用补丁";
    }

    @Tool("对已存在文件应用局部 diff-match-patch 补丁；不用于新建文件或整文件覆盖")
    public String applyPatch(@P("相对源码根目录，例如：pages/Home.jsx、components/Button.jsx") String relativeFilePath, @P("基于目标文件当前内容生成的 diff-match-patch patchToText 格式补丁，以 @@ 开头") String patchText, @ToolMemoryId Long appId) {
        log.debug("AI 调用应用补丁工具， 请求参数：relativeFilePath={}, patchTextLength={}, appId={}", relativeFilePath, patchText == null ? 0 : patchText.length(), appId);
        try {
            if (StrUtil.isBlank(relativeFilePath) || StrUtil.isBlank(patchText) || appId == null) {
                log.warn("AI 调用应用补丁工具失败");
                return "应用补丁失败: " + relativeFilePath + ", 错误: 非法的文件路径或补丁内容";
            }

            String normalizedRelativePath = FileUtil.normalize(relativeFilePath.trim());
            Path sourceRoot = appPathProperties.getTmpDir()
                    .resolve("app-workspace")
                    .resolve(String.valueOf(appId))
                    .resolve("src")
                    .normalize();
            Path targetPath = sourceRoot.resolve(Paths.get(normalizedRelativePath)).normalize();
            // 最终路径必须仍落在 src 内，避免 AI 访问工作区外文件。
            if (!targetPath.startsWith(sourceRoot)) {
                log.warn("AI 调用应用补丁工具失败");
                return "应用补丁失败: " + relativeFilePath + ", 错误: 非法的文件路径";
            }
            if (ProtectedTemplatePaths.contains(normalizedRelativePath)) {
                log.warn("AI 调用应用补丁工具失败");
                return PROTECTED_PATCH_WARNING;
            }

            if (!Files.exists(targetPath)) {
                log.warn("AI 调用应用补丁工具失败");
                return "应用补丁失败: " + relativeFilePath + ", 错误: 文件不存在";
            }
            if (Files.isDirectory(targetPath)) {
                log.warn("AI 调用应用补丁工具失败");
                return "应用补丁失败: " + relativeFilePath + ", 错误: 不允许编辑目录";
            }

            String content = Files.readString(targetPath, StandardCharsets.UTF_8);
            DiffMatchPatch diffMatchPatch = new DiffMatchPatch();
            LinkedList<DiffMatchPatch.Patch> patches = new LinkedList<>(diffMatchPatch.patchFromText(patchText));
            Object[] patchResult = diffMatchPatch.patchApply(patches, content);
            for (boolean applied : (boolean[]) patchResult[1]) {
                if (!applied) {
                    log.warn("AI 调用应用补丁工具失败");
                    return "应用补丁失败: " + relativeFilePath + ", 错误: 补丁未能完整应用，请先读取文件后重新生成补丁";
                }
            }

            Files.writeString(targetPath, (String) patchResult[0], StandardCharsets.UTF_8);
            log.debug("AI 调用应用补丁工具成功");
            return normalizedRelativePath + " 已应用补丁";
        } catch (InvalidPathException | IOException e) {
            String errResult = "应用补丁失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.warn("AI 调用应用补丁工具失败", e);
            return errResult;
        } catch (IllegalArgumentException e) {
            String errResult = "应用补丁失败: " + relativeFilePath + ", 错误: 补丁格式非法";
            log.warn("AI 调用应用补丁工具失败", e);
            return errResult;
        }
    }

    @Override
    public String formatResponse(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath", "");
        String patchText = arguments.getStr("patchText", "");
        return String.format("""
                [工具调用] %s %s

                补丁内容：
                ```
                %s
                ```
                """, displayName(), relativeFilePath, patchText);
    }
}
