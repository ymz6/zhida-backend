package org.ymz.app.mapper;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.ymz.app.model.dto.comment.CommentCountItem;
import org.ymz.app.model.entity.CommentLike;

import java.util.List;

/**
 * 评论点赞映射层。
 *
 * @author ymz
 */
public interface CommentLikeMapper extends BaseMapper<CommentLike> {

    long countByUserIdAndCommentId(@Param("userId") Long userId, @Param("commentId") Long commentId);

    long countByCommentId(@Param("commentId") Long commentId);

    int insertLike(@Param("userId") Long userId, @Param("commentId") Long commentId);

    int deleteByUserIdAndCommentId(@Param("userId") Long userId, @Param("commentId") Long commentId);

    int deleteByCommentIds(@Param("commentIds") List<Long> commentIds);

    List<CommentCountItem> countByCommentIds(@Param("commentIds") List<Long> commentIds);

    List<Long> listLikedCommentIds(
            @Param("userId") Long userId,
            @Param("commentIds") List<Long> commentIds);
}
