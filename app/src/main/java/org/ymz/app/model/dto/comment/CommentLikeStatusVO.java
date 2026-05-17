package org.ymz.app.model.dto.comment;

import lombok.Builder;
import lombok.Data;

/**
 * 评论点赞状态。
 *
 * @author ymz
 */
@Data
@Builder
public class CommentLikeStatusVO {

    private Long commentId;

    private boolean liked;

    private long likeCount;
}
