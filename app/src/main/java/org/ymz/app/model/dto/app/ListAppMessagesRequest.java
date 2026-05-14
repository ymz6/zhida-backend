package org.ymz.app.model.dto.app;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.CursorQuery;

/**
 * 游标查询应用聊天消息列表。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListAppMessagesRequest extends CursorQuery {
}
