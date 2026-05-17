package org.ymz.app.model.dto.comment;

import lombok.Data;

/**
 * 评论聚合计数项。
 *
 * @author ymz
 */
@Data
public class CommentCountItem {

    private Long commentId;

    private Long count;
}
