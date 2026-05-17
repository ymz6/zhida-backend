package org.ymz.app.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.ymz.app.model.dto.comment.CommentVO;
import org.ymz.app.model.entity.Comment;

/**
 * 评论转换器。
 *
 * @author ymz
 */
@Mapper(componentModel = "spring")
public interface CommentConverter {

    @Mapping(target = "author", ignore = true)
    @Mapping(target = "replyToUser", ignore = true)
    @Mapping(target = "likeCount", ignore = true)
    @Mapping(target = "replyCount", ignore = true)
    @Mapping(target = "liked", ignore = true)
    CommentVO toCommentVO(Comment comment);
}
