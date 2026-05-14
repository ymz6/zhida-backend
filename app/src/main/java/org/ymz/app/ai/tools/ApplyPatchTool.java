package org.ymz.app.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.LevenshteinDistance;
import org.springframework.stereotype.Component;
import org.ymz.app.config.AppPathProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件局部编辑工具
 * AI 提交 oldString / newString，后端按三级降级匹配（精确 → 归一化空白 → Levenshtein 滑窗）。
 *
 * 本工具是交给 Claude Code 来写的，之前简单写的几版调用的成功率太低，这一版就非常好，虽然非常复杂，但是能跑就行，别动了！
 * 
 * @author ymz
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplyPatchTool implements BaseTool {

    private final AppPathProperties appPathProperties;

    private static final String PROTECTED_PATCH_WARNING = """
            当前应用补丁请求已被拒绝，原因是以下文件属于受保护路径，禁止删除或修改：
            %s

            请明确回复用户：说明该路径受模板边界保护，严禁修改
            """.formatted(ProtectedTemplatePaths.listText());

    private static final double L3_THRESHOLD = 0.85;
    private static final double L3_MARGIN = 0.05;
    private static final int L3_MAX_LINES = 200;
    private static final int SIMILAR_SNIPPET_MAX_LINES = 30;
    private static final int FORMAT_SNIPPET_MAX_CHARS = 2000;

    private static final LevenshteinDistance LEVENSHTEIN = LevenshteinDistance.getDefaultInstance();

    @Override
    public String toolName() {
        return "applyPatch";
    }

    @Override
    public String displayName() {
        return "应用补丁";
    }

    @Tool("""
            对已存在文件做一次精准局部替换：把 oldString 替换为 newString。
            oldString 必须在文件中唯一出现；如果片段不唯一，请把上下相邻几行也包含在 oldString 中。
            一次调用只改一处。需要在同一文件改多处时，分多次调用本工具。
            不用于新建文件或整文件覆盖（请使用 writeFile）。
            """)
    public String applyPatch(
            @P("相对源码根目录，例如：pages/Home.jsx、components/Button.jsx") String relativeFilePath,
            @P("文件中要被替换的原文片段。必须与当前内容逐字一致（含缩进与换行），且在文件中唯一出现") String oldString,
            @P("替换后的新内容；若要删除 oldString 片段，传空字符串") String newString,
            @ToolMemoryId Long appId) {
        log.debug("AI 调用应用补丁工具， 请求参数：relativeFilePath={}, oldStringLength={}, newStringLength={}, appId={}",
                relativeFilePath,
                oldString == null ? 0 : oldString.length(),
                newString == null ? 0 : newString.length(),
                appId);
        try {
            if (StrUtil.isBlank(relativeFilePath) || appId == null) {
                log.warn("AI 调用应用补丁工具失败");
                return "应用补丁失败: " + relativeFilePath + ", 错误: 非法的文件路径";
            }
            if (StrUtil.isEmpty(oldString)) {
                log.warn("AI 调用应用补丁工具失败");
                return "应用补丁失败: " + relativeFilePath + ", 错误: oldString 不能为空（如需创建新文件请使用 writeFile）";
            }
            if (newString == null) {
                newString = "";
            }

            String normalizedRelativePath = FileUtil.normalize(relativeFilePath.trim());
            Path sourceRoot = appPathProperties.getTmpDir()
                    .resolve("app-workspace")
                    .resolve(String.valueOf(appId))
                    .resolve("src")
                    .normalize();
            Path targetPath = sourceRoot.resolve(Paths.get(normalizedRelativePath)).normalize();
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

            String rawContent = Files.readString(targetPath, StandardCharsets.UTF_8);
            String content = rawContent.replace("\r\n", "\n");
            String oldStr = oldString.replace("\r\n", "\n");
            String newStr = newString.replace("\r\n", "\n");

            MatchResult result = match(content, oldStr);
            if (!result.ok()) {
                log.warn("AI 调用应用补丁工具失败: {}", result.reason());
                return "应用补丁失败: " + relativeFilePath + ", 错误: " + result.reason();
            }

            String updated = content.substring(0, result.start()) + newStr + content.substring(result.end());
            Files.writeString(targetPath, updated, StandardCharsets.UTF_8);
            log.debug("AI 调用应用补丁工具成功");
            return normalizedRelativePath + " 已应用补丁";
        } catch (InvalidPathException | IOException e) {
            String errResult = "应用补丁失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.warn("AI 调用应用补丁工具失败", e);
            return errResult;
        }
    }

    @Override
    public String formatRequest(JSONObject arguments) {
        return "\n【选择工具】%s：`%s`\n".formatted(this.displayName(), arguments.getStr("relativeFilePath", ""));
    }

    @Override
    public String formatResponse(JSONObject arguments, String result) {
        return """
                \n【工具调用结果】文件 `%s` 已应用补丁
                旧内容：
                ```text
                %s
                ```
                新内容：
                ```text
                %s
                ```\n
                """.formatted(
                arguments.getStr("relativeFilePath", ""),
                formatSnippet(arguments.getStr("oldString", "")),
                formatSnippet(arguments.getStr("newString", "")));
    }

    private static String formatSnippet(String content) {
        if (content == null || content.length() <= FORMAT_SNIPPET_MAX_CHARS) {
            return content == null ? "" : content;
        }

        // 展示补丁片段即可，避免大段内容把聊天记录冲散。
        return content.substring(0, FORMAT_SNIPPET_MAX_CHARS) + "\n...（内容过长，已截断）";
    }

    /**
     * 三级降级匹配：精确 → 归一化空白 → Levenshtein 滑窗。
     */
    private MatchResult match(String content, String oldStr) {
        int first = content.indexOf(oldStr);
        if (first >= 0) {
            int second = content.indexOf(oldStr, first + 1);
            if (second >= 0) {
                int count = countOccurrences(content, oldStr);
                return MatchResult.fail("oldString 在文件中匹配到 " + count
                        + " 处，请把上下相邻 2-3 行加入 oldString 使其唯一");
            }
            return MatchResult.ok(first, first + oldStr.length());
        }

        MatchResult l2 = matchByNormalizedLines(content, oldStr);
        if (l2 != null) {
            return l2;
        }

        return matchByLevenshtein(content, oldStr);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    /**
     * L2：按行归一化（去首尾空白 + 行首空白压缩）后定位，在原文行区间上替换。
     */
    private MatchResult matchByNormalizedLines(String content, String oldStr) {
        List<String> contentLines = splitLinesKeepEmpty(content);
        List<String> oldLines = splitLinesKeepEmpty(oldStr);
        if (oldLines.isEmpty() || oldLines.size() > contentLines.size()) {
            return null;
        }

        List<String> normContent = new ArrayList<>(contentLines.size());
        for (String line : contentLines) {
            normContent.add(normalize(line));
        }
        List<String> normOld = new ArrayList<>(oldLines.size());
        for (String line : oldLines) {
            normOld.add(normalize(line));
        }

        int firstHit = -1;
        int hitCount = 0;
        int limit = contentLines.size() - oldLines.size();
        for (int i = 0; i <= limit; i++) {
            if (normContent.subList(i, i + oldLines.size()).equals(normOld)) {
                if (firstHit < 0) {
                    firstHit = i;
                }
                hitCount++;
                if (hitCount > 1) {
                    return MatchResult.fail("oldString 在文件中匹配到多处（已忽略空白差异），请扩大上下文使其唯一");
                }
            }
        }
        if (firstHit < 0) {
            return null;
        }

        int[] offsets = lineStartOffsets(content, contentLines);
        int startOffset = offsets[firstHit];
        int endLineIndex = firstHit + oldLines.size();
        int endOffset = endLineIndex < offsets.length ? offsets[endLineIndex] : content.length();
        return MatchResult.ok(startOffset, endOffset);
    }

    /**
     * L3：基于行窗口 Levenshtein 相似度的模糊定位。
     */
    private MatchResult matchByLevenshtein(String content, String oldStr) {
        List<String> contentLines = splitLinesKeepEmpty(content);
        List<String> oldLines = splitLinesKeepEmpty(oldStr);
        if (oldLines.isEmpty() || oldLines.size() > L3_MAX_LINES || oldLines.size() > contentLines.size()) {
            return MatchResult.fail("oldString 未匹配，请重新 readFile 后基于真实内容重试");
        }

        String normOldBlock = normalizeBlock(oldLines);
        double bestSim = -1;
        int bestStartLine = -1;
        double secondSim = -1;

        int limit = contentLines.size() - oldLines.size();
        for (int i = 0; i <= limit; i++) {
            String normWindow = normalizeBlock(contentLines.subList(i, i + oldLines.size()));
            double sim = similarity(normWindow, normOldBlock);
            if (sim > bestSim) {
                secondSim = bestSim;
                bestSim = sim;
                bestStartLine = i;
            } else if (sim > secondSim) {
                secondSim = sim;
            }
        }

        int[] offsets = lineStartOffsets(content, contentLines);
        if (bestStartLine >= 0 && bestSim >= L3_THRESHOLD && (bestSim - secondSim) >= L3_MARGIN) {
            int startOffset = offsets[bestStartLine];
            int endLineIndex = bestStartLine + oldLines.size();
            int endOffset = endLineIndex < offsets.length ? offsets[endLineIndex] : content.length();
            return MatchResult.ok(startOffset, endOffset);
        }

        String snippet = buildSimilarSnippet(contentLines, bestStartLine, oldLines.size());
        String reason = "oldString 未匹配。文件中最相似的片段（相似度 "
                + formatSim(bestSim) + "）：\n"
                + "=== begin ===\n"
                + snippet
                + "\n=== end ===\n"
                + "请对照修正 oldString 后重试；若文件已被先前调用修改，请先 readFile 重新获取最新内容。";
        return MatchResult.fail(reason);
    }

    private static double similarity(String a, String b) {
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen == 0) {
            return 1.0;
        }
        int distance = LEVENSHTEIN.apply(a, b);
        return 1.0 - (double) distance / maxLen;
    }

    private static String normalize(String line) {
        return line.strip();
    }

    private static String normalizeBlock(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(normalize(lines.get(i)));
        }
        return sb.toString();
    }

    /**
     * 按 \n 切分但保留末尾空行（"a\n" 切出 ["a", ""]）。
     */
    private static List<String> splitLinesKeepEmpty(String s) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                lines.add(s.substring(start, i));
                start = i + 1;
            }
        }
        lines.add(s.substring(start));
        return lines;
    }

    /**
     * 返回每行在原文中的起始 offset，长度 = 行数 + 1，末尾元素 = 总长度的哨兵。
     */
    private static int[] lineStartOffsets(String content, List<String> lines) {
        int[] offsets = new int[lines.size() + 1];
        int offset = 0;
        for (int i = 0; i < lines.size(); i++) {
            offsets[i] = offset;
            offset += lines.get(i).length() + 1;
        }
        offsets[lines.size()] = content.length();
        return offsets;
    }

    private static String buildSimilarSnippet(List<String> contentLines, int startLine, int windowSize) {
        if (startLine < 0) {
            return "(文件为空或无可对比窗口)";
        }
        int end = Math.min(startLine + windowSize, contentLines.size());
        int take = Math.min(end - startLine, SIMILAR_SNIPPET_MAX_LINES);
        StringBuilder sb = new StringBuilder();
        for (int i = startLine; i < startLine + take; i++) {
            if (i > startLine) {
                sb.append('\n');
            }
            sb.append(contentLines.get(i));
        }
        if (take < end - startLine) {
            sb.append("\n... (truncated)");
        }
        return sb.toString();
    }

    private static String formatSim(double sim) {
        if (sim < 0) {
            return "N/A";
        }
        return String.format("%.2f", sim);
    }

    private record MatchResult(boolean ok, int start, int end, String reason) {
        static MatchResult ok(int s, int e) {
            return new MatchResult(true, s, e, null);
        }

        static MatchResult fail(String reason) {
            return new MatchResult(false, -1, -1, reason);
        }
    }
}
