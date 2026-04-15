package org.ymz.app.ai;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.ai.dto.HtmlCodeResult;
import org.ymz.app.ai.dto.MultiFileCodeResult;
import org.ymz.app.ai.service.AiCodeGeneratorService;
import org.ymz.app.model.enums.CodeGenType;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 外部调用入口
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class AiCodeGenerator {
    private static final String OUTPUT_ROOT_DIR = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "code_output";

    private final AiCodeGeneratorService aiCodeGeneratorService;

    /**
     * 生成并保存代码
     * @param codeGenType 代码生成类型
     * @param userMessage 用户消息
     * @return 代码所在文件目录的文件对象
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

    private String createOutputDir(CodeGenType codeGenType) {
        // 代码目录名规则：{代码生成类型}_{雪花ID}
        String dirPath = OUTPUT_ROOT_DIR + File.separator + codeGenType.getText() + "_" + IdUtil.getSnowflakeNextIdStr();
        FileUtil.mkdir(dirPath);
        return dirPath;
    }
}
