package org.ymz.app.ai;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.ymz.app.model.enums.CodeGenType;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiCodeGeneratorTest {

    @Resource
    private AiCodeGenerator aiCodeGenerator;

    @Test
    void generateHtmlCode() {
        File file = aiCodeGenerator.generate(CodeGenType.HTML, "我想做一个简洁的程序员的个人博客，总体上不超过50行代码");
        Assertions.assertNotNull(file);
    }

    @Test
    void generateMultiFileCode() {
        File file = aiCodeGenerator.generate(CodeGenType.MULTI_FILE, "我想做一个简洁的个人留言板应用，总体上不超过100行代码");
        Assertions.assertNotNull(file);
    }
}