package org.ymz.app.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import org.apache.ibatis.annotations.Param;
import org.ymz.app.model.dto.comment.CommentCountItem;
import org.ymz.app.model.entity.Comment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评论映射层。
 *
 * @author ymz
 */
public interface CommentMapper extends BaseMapper<Comment> {

    Comment selectCommentById(@Param("commentId") Long commentId);

    List<Comment> listByIds(@Param("commentIds") List<Long> commentIds);

    default Page<Comment> paginateRootComments(Page<Comment> page, @Param("appId") Long appId) {
        // 自定义 XML 分页必须走 xmlPaginate，避免 MyBatis 把 Page 返回值当单条记录处理。
        Map<String, Object> params = new HashMap<>();
        params.put("appId", appId);
        return xmlPaginate("paginateRootComments", page, params);
    }

    default Page<Comment> paginateReplies(Page<Comment> page, @Param("rootId") Long rootId) {
        // 自定义 XML 分页必须走 xmlPaginate，避免 MyBatis 把 Page 返回值当单条记录处理。
        Map<String, Object> params = new HashMap<>();
        params.put("rootId", rootId);
        return xmlPaginate("paginateReplies", page, params);
    }

    List<CommentCountItem> countRepliesByRootIds(@Param("rootIds") List<Long> rootIds);

    List<Long> listSubtreeIds(@Param("commentId") Long commentId);

    int deleteByIds(@Param("commentIds") List<Long> commentIds);
}
