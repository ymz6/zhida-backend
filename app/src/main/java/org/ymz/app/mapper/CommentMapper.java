package org.ymz.app.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import org.apache.ibatis.annotations.Param;
import org.ymz.app.model.dto.comment.CommentCountItem;
import org.ymz.app.model.entity.Comment;

import java.util.List;

/**
 * 评论映射层。
 *
 * @author ymz
 */
public interface CommentMapper extends BaseMapper<Comment> {

    Comment selectCommentById(@Param("commentId") Long commentId);

    List<Comment> listByIds(@Param("commentIds") List<Long> commentIds);

    Page<Comment> paginateRootComments(Page<Comment> page, @Param("appId") Long appId);

    Page<Comment> paginateReplies(Page<Comment> page, @Param("rootId") Long rootId);

    List<CommentCountItem> countRepliesByRootIds(@Param("rootIds") List<Long> rootIds);

    List<Long> listSubtreeIds(@Param("commentId") Long commentId);

    int deleteByIds(@Param("commentIds") List<Long> commentIds);
}
