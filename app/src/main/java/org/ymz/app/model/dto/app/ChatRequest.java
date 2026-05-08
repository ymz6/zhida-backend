package org.ymz.app.model.dto.app;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.hibernate.validator.constraints.Length;
import org.ymz.app.model.enums.app.AppChatMode;

/**
 * 应用内对话请求。RESUME 模式不需要 prompt；CODE/CHAT 模式必须提供 prompt。
 *
 * @author ymz
 */
@Data
public class ChatRequest {

    @NotNull(message = "请选择对话模式")
    private AppChatMode mode;

    @Length(max = 4000, message = "对话内容不能超过4000个字符")
    private String prompt;
}
