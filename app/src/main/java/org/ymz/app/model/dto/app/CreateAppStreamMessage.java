package org.ymz.app.model.dto.app;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 创建应用流式进度消息。
 *
 * @author ymz
 */
@Data
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateAppStreamMessage {

    private String step;

    private String message;

    private Long appId;

    public static CreateAppStreamMessage progress(String step, String message) {
        return new CreateAppStreamMessage(step, message, null);
    }

    public static CreateAppStreamMessage done(Long appId, String message) {
        return new CreateAppStreamMessage("DONE", message, appId);
    }

    public static CreateAppStreamMessage error(String message) {
        return new CreateAppStreamMessage("ERROR", message, null);
    }
}
