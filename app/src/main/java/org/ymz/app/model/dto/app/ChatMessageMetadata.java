package org.ymz.app.model.dto.app;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 用户可见聊天消息的业务元信息。
 *
 * @author ymz
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessageMetadata(
        String previewUrl,
        ErrorInfo error
) {

    public record ErrorInfo(String errorType, String detail) {
    }
}
