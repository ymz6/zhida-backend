package org.ymz.app.model.dto.app;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.ymz.app.model.enums.app.ChatStreamMessageType;

/**
 * 应用聊天流式消息。
 *
 * @author ymz
 */
@Data
@AllArgsConstructor(staticName = "of")
public class ChatStreamMessage {

    private ChatStreamMessageType type;

    private String content;
}
