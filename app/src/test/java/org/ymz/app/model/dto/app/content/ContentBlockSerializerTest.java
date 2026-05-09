package org.ymz.app.model.dto.app.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.ymz.app.ai.codegen.event.CodeGenerationMessageRecorder;
import org.ymz.app.ai.codegen.event.CodeGenerationTaskSseBroker;
import org.ymz.app.service.AppChatMessageService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

class ContentBlockSerializerTest {

    @Test
    void serializesAndDeserializesPolymorphicBlocks() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        CodeGenerationMessageRecorder recorder = new CodeGenerationMessageRecorder(
                mock(AppChatMessageService.class),
                mock(CodeGenerationTaskSseBroker.class),
                objectMapper
        );

        String json = recorder.serializeBlocks(List.of(
                new TextBlock("开始处理"),
                new ToolUseBlock("write_file", Map.of("path", "src/App.jsx"), "ok")
        ));

        List<ContentBlock> blocks = objectMapper.readValue(
                json,
                new TypeReference<List<ContentBlock>>() {
                }
        );
        TextBlock textBlock = assertInstanceOf(TextBlock.class, blocks.get(0));
        ToolUseBlock toolUseBlock = assertInstanceOf(ToolUseBlock.class, blocks.get(1));
        assertEquals("开始处理", textBlock.text());
        assertEquals("write_file", toolUseBlock.name());
        assertEquals(Map.of("path", "src/App.jsx"), toolUseBlock.input());
        assertEquals("ok", toolUseBlock.result());
    }
}
