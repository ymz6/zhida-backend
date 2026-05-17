package org.ymz.app.model.dto.comment;

import lombok.Data;
import org.ymz.app.model.dto.user.UserBriefVO;

import java.time.LocalDateTime;

/**
 * 评论展示信息。
 *
 * @author ymz
 */
@Data
public class CommentVO {

    private Long id;

    private Long appId;

    private Long parentId;

    private Long rootId;

    private UserBriefVO author;

    private UserBriefVO replyToUser;

    private String content;

    private long likeCount;

    private long replyCount;

    private boolean liked;

    private LocalDateTime createdAt;
}
