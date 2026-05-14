package org.ymz.app.model.dto.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 应用内对话请求。RESUME 模式不需要 prompt；CODE/CHAT 模式必须提供 prompt。
 *
 * @author ymz
 */
@Data
public class ChatRequest {
    @NotBlank()
    @Size(max = 1000, message = "对话内容不能超过1000个字符")
    private String prompt;
}