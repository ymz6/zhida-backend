package org.ymz.app.service;

import com.mybatisflex.core.service.IService;
import org.ymz.app.model.dto.comment.CommentLikeStatusVO;
import org.ymz.app.model.dto.comment.CommentVO;
import org.ymz.app.model.dto.comment.CreateCommentRequest;
import org.ymz.app.model.dto.comment.ListCommentsRequest;
import org.ymz.app.model.dto.comment.ListRepliesRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.entity.Comment;
import org.ymz.app.security.AuthContext;

/**
 * 评论服务层。
 *
 * @author ymz
 */
public interface CommentService extends IService<Comment> {

    CommentVO createComment(Long userId, Long appId, CreateCommentRequest request);

    CommentVO replyComment(Long userId, Long commentId, CreateCommentRequest request);

    PageResult<CommentVO> listRootComments(Long userId, Long appId, ListCommentsRequest request);

    PageResult<CommentVO> listReplies(Long userId, Long rootId, ListRepliesRequest request);

    CommentLikeStatusVO toggleLike(Long userId, Long commentId);

    void deleteComment(AuthContext authContext, Long commentId);
}
