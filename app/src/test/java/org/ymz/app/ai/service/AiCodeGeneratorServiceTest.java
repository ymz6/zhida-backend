package org.ymz.app.ai.service;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.ymz.app.ai.AiCodeGenerator;
import org.ymz.app.ai.dto.HtmlCodeResult;
import org.ymz.app.ai.dto.MultiFileCodeResult;
import org.ymz.app.model.enums.CodeGenType;

@SpringBootTest
class AiCodeGeneratorServiceTest {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    @Resource
    private AiCodeGenerator aiCodeGenerator;

    @Test
    void generateHtmlCode() {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode("我想做一个简洁的程序员的个人博客，总体上不超过30行代码");
        Assertions.assertNotNull(result);
    }

    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode("我想做一个简洁的个人留言板应用，总体上不超过100行代码");
        Assertions.assertNotNull(result);
    }

    @Test
    void generateStreamingHtmlCode() {
        Assertions.assertDoesNotThrow(() ->
                aiCodeGenerator.generateStreaming(
                                CodeGenType.HTML,
                                "我想做一个简洁的程序员个人博客，总体上不超过30行代码"
                        )
                        .doOnNext(System.out::print)
                        .blockLast()
        );
    }

    @Test
    void generateStreamingMultiFileCode() {
        Assertions.assertDoesNotThrow(() ->
                aiCodeGenerator.generateStreaming(
                                CodeGenType.MULTI_FILE,
                                "我想做一个简洁的个人留言板应用，总体上不超过100行代码"
                        )
                        .doOnNext(System.out::print)
                        .blockLast()
        );
    }
}