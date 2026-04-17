package org.ymz.app.ai;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ReUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.ai.dto.HtmlCodeResult;
import org.ymz.app.ai.dto.MultiFileCodeResult;
import org.ymz.app.ai.service.AiCodeGeneratorService;
import org.ymz.app.model.enums.CodeGenType;
import reactor.core.publisher.Flux;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 外部调用入口
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class AiCodeGenerator {
    private static final String OUTPUT_ROOT_DIR = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "code_output";
    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile("```css\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_CODE_PATTERN = Pattern.compile("```(?:js|javascript)\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final AiCodeGeneratorService aiCodeGeneratorService;

    /**
     * 生成并保存代码
     * @param codeGenType 代码生成类型
     * @param userMessage 用户消息
     * @return 保存生成代码的目录
     */
    public File generate(CodeGenType codeGenType, String userMessage) {
        return switch (codeGenType) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                String dirPath = createOutputDir(codeGenType);
                FileUtil.writeString(
                        result.getHtmlContent(),
                        dirPath + File.separator + "index.html",
                        StandardCharsets.UTF_8
                );
                yield new File(dirPath);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                String dirPath = createOutputDir(codeGenType);
                FileUtil.writeString(
                        result.getHtmlContent(),
                        dirPath + File.separator + "index.html",
                        StandardCharsets.UTF_8
                );
                FileUtil.writeString(
                        result.getCssContent(),
                        dirPath + File.separator + "style.css",
                        StandardCharsets.UTF_8
                );
                FileUtil.writeString(
                        result.getJsContent(),
                        dirPath + File.separator + "script.js",
                        StandardCharsets.UTF_8
                );
                yield new File(dirPath);
            }
        };
    }

    /**
     * 流式生成代码内容
     *
     * @param codeGenType 代码生成类型
     * @param userMessage 用户消息
     * @return 流式返回的代码内容片段
     */
    public Flux<String> generateStreaming(CodeGenType codeGenType, String userMessage) {
        // 用于拼接 AI 的返回结果
        StringBuilder contentBuilder = new StringBuilder();
        return switch (codeGenType) {
            case HTML -> aiCodeGeneratorService.generateHtmlCodeStreaming(userMessage)
                    // 实时收集 AI 返回的结果
                    .doOnNext(contentBuilder::append)
                    .doOnComplete(() -> {
                        // AI 返回结束后，解析内容并保存到文件
                        // 1. 拿到 AI 完整返回内容
                        String content = contentBuilder.toString();
                        // 2. 解析 html 代码块
                        // 提取 html 代码块，并将空内容统一归一化为 null
                        String htmlContent = CharSequenceUtil.trimToNull(ReUtil.get(HTML_CODE_PATTERN, content, 1));
                        if (htmlContent == null) {
                            htmlContent = CharSequenceUtil.trimToEmpty(content);
                        }
                        // 3. 创建目录并写文件
                        String dirPath = createOutputDir(codeGenType);
                        FileUtil.writeString(htmlContent, dirPath + File.separator + "index.html", StandardCharsets.UTF_8);
                    });
            case MULTI_FILE -> aiCodeGeneratorService.generateMultiFileCodeStreaming(userMessage)
                    .doOnNext(contentBuilder::append)
                    .doOnComplete(() -> {
                        // 1. 拿到 AI 完整返回内容
                        String content = contentBuilder.toString();

                        // 2. 解析 html / css / js 代码块
                        String htmlContent = CharSequenceUtil.trimToNull(ReUtil.get(HTML_CODE_PATTERN, content, 1));
                        String cssContent = CharSequenceUtil.trimToNull(ReUtil.get(CSS_CODE_PATTERN, content, 1));
                        String jsContent = CharSequenceUtil.trimToNull(ReUtil.get(JS_CODE_PATTERN, content, 1));

                        // 3. 创建目录并写文件
                        String dirPath = createOutputDir(codeGenType);

                        if (htmlContent != null) {
                            FileUtil.writeUtf8String(htmlContent, dirPath + File.separator + "index.html");
                        }
                        if (cssContent != null) {
                            FileUtil.writeUtf8String(cssContent, dirPath + File.separator + "style.css");
                        }
                        if (jsContent != null) {
                            FileUtil.writeUtf8String(jsContent, dirPath + File.separator + "script.js");
                        }
                    });
        };
    }

    private String createOutputDir(CodeGenType codeGenType) {
        // 代码目录名规则：{代码生成类型}_{雪花ID}
        String dirPath = OUTPUT_ROOT_DIR + File.separator + codeGenType.getText() + "_" + IdUtil.getSnowflakeNextIdStr();
        FileUtil.mkdir(dirPath);
        return dirPath;
    }
}
