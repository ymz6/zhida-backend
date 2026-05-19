package org.ymz.app.service.impl;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ymz.app.converter.CommentConverter;
import org.ymz.app.converter.UserConverter;
import org.ymz.app.mapper.CommentLikeMapper;
import org.ymz.app.mapper.CommentMapper;
import org.ymz.app.model.dto.comment.CommentCountItem;
import org.ymz.app.model.dto.comment.CommentLikeStatusVO;
import org.ymz.app.model.dto.comment.CommentVO;
import org.ymz.app.model.dto.comment.CreateCommentRequest;
import org.ymz.app.model.dto.comment.ListCommentsRequest;
import org.ymz.app.model.dto.comment.ListRepliesRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.Comment;
import org.ymz.app.model.entity.User;
import org.ymz.app.model.enums.UserRole;
import org.ymz.app.model.enums.app.AppAuditStatus;
import org.ymz.app.security.AuthContext;
import org.ymz.app.service.AppService;
import org.ymz.app.service.CommentService;
import org.ymz.app.service.UserService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 评论服务层实现。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {

    private static final int MAX_CONTENT_LENGTH = 500;

    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final AppService appService;
    private final UserService userService;
    private final UserConverter userConverter;
    private final CommentConverter commentConverter;

    @Override
    @Transactional
    public CommentVO createComment(Long userId, Long appId, CreateCommentRequest request) {
        requirePublicCase(appId);

        Comment comment = Comment.builder()
                .appId(appId)
                .userId(userId)
                .content(normalizeContent(request.getContent()))
                .createdAt(LocalDateTime.now())
                .build();
        if (commentMapper.insert(comment) != 1) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "发布评论失败");
        }
        return toCommentVO(comment, userId, null);
    }

    @Override
    @Transactional
    public CommentVO replyComment(Long userId, Long commentId, CreateCommentRequest request) {
        Comment parent = requireComment(commentId);
        requirePublicCase(parent.getAppId());

        // rootId 固定指向一级评论，界面按一级评论下的楼中楼拉平展示。
        Long rootId = parent.getRootId() == null ? parent.getId() : parent.getRootId();
        Comment reply = Comment.builder()
                .appId(parent.getAppId())
                .userId(userId)
                .parentId(parent.getId())
                .rootId(rootId)
                .content(normalizeContent(request.getContent()))
                .createdAt(LocalDateTime.now())
                .build();
        if (commentMapper.insert(reply) != 1) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "回复评论失败");
        }
        return toCommentVO(reply, userId, parent);
    }

    @Override
    public PageResult<CommentVO> listRootComments(Long userId, Long appId, ListCommentsRequest request) {
        requirePublicCase(appId);

        Page<Comment> page = commentMapper.paginateRootComments(request.toPage(), appId);
        return toCommentPageResult(page, userId, true);
    }

    @Override
    public PageResult<CommentVO> listReplies(Long userId, Long rootId, ListRepliesRequest request) {
        Comment root = requireComment(rootId);
        if (root.getRootId() != null) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "只能查看一级评论下的回复");
        }
        requirePublicCase(root.getAppId());

        Page<Comment> page = commentMapper.paginateReplies(request.toPage(), rootId);
        return toCommentPageResult(page, userId, false);
    }

    @Override
    @Transactional
    public CommentLikeStatusVO toggleLike(Long userId, Long commentId) {
        Comment comment = requireComment(commentId);
        requirePublicCase(comment.getAppId());

        boolean liked;
        if (commentLikeMapper.countByUserIdAndCommentId(userId, commentId) > 0) {
            commentLikeMapper.deleteByUserIdAndCommentId(userId, commentId);
            liked = false;
        } else {
            int inserted = commentLikeMapper.insertLike(userId, commentId);
            if (inserted != 1 && commentLikeMapper.countByUserIdAndCommentId(userId, commentId) == 0) {
                throw BusinessException.of(ResultCode.SYSTEM_ERROR, "点赞失败");
            }
            liked = true;
        }

        return CommentLikeStatusVO.builder()
                .commentId(commentId)
                .liked(liked)
                .likeCount(commentLikeMapper.countByCommentId(commentId))
                .build();
    }

    @Override
    @Transactional
    public void deleteComment(AuthContext authContext, Long commentId) {
        Comment comment = requireComment(commentId);
        App app = appService.getById(comment.getAppId());
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "案例不存在");
        }

        boolean canDelete = comment.getUserId().equals(authContext.getUserId())
                || app.getUserId().equals(authContext.getUserId())
                || UserRole.ADMIN.equals(authContext.getUserRole());
        if (!canDelete) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }

        List<Long> commentIds = commentMapper.listSubtreeIds(commentId);
        if (commentIds.isEmpty()) {
            return;
        }
        // 物理删除整棵子树前，先清理所有关联点赞，避免留下孤儿数据。
        commentLikeMapper.deleteByCommentIds(commentIds);
        commentMapper.deleteByIds(commentIds);
    }

    private Comment requireComment(Long commentId) {
        Comment comment = commentMapper.selectCommentById(commentId);
        if (comment == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "评论不存在");
        }
        return comment;
    }

    private App requirePublicCase(Long appId) {
        App app = appService.getById(appId);
        if (app == null
                || !AppAuditStatus.APPROVED.getCode().equals(app.getAuditStatus())
                || app.getDeployedAt() == null
                || StrUtil.isBlank(app.getDeployKey())) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "案例不存在或未公开");
        }
        return app;
    }

    private String normalizeContent(String content) {
        String normalized = StrUtil.trimToNull(content);
        if (normalized == null) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "评论内容不能为空");
        }
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "评论内容不能超过500个字符");
        }
        return normalized;
    }

    private CommentVO toCommentVO(Comment comment, Long currentUserId, Comment parent) {
        CommentVO vo = commentConverter.toCommentVO(comment);
        User author = userService.getById(comment.getUserId());
        if (author != null) {
            vo.setAuthor(userConverter.toUserBriefVO(author));
        }
        if (comment.getParentId() != null) {
            Comment replyToComment = parent == null ? commentMapper.selectCommentById(comment.getParentId()) : parent;
            if (replyToComment != null) {
                User replyToUser = userService.getById(replyToComment.getUserId());
                if (replyToUser != null) {
                    vo.setReplyToUser(userConverter.toUserBriefVO(replyToUser));
                }
            }
        }
        vo.setLikeCount(commentLikeMapper.countByCommentId(comment.getId()));
        vo.setReplyCount(comment.getRootId() == null ? getReplyCount(comment.getId()) : 0);
        vo.setLiked(commentLikeMapper.countByUserIdAndCommentId(currentUserId, comment.getId()) > 0);
        return vo;
    }

    private PageResult<CommentVO> toCommentPageResult(Page<Comment> page, Long currentUserId, boolean fillReplyCount) {
        List<Comment> comments = page.getRecords();
        if (comments.isEmpty()) {
            return PageResult.of(page, commentConverter::toCommentVO);
        }

        List<Long> commentIds = comments.stream().map(Comment::getId).toList();
        Map<Long, Long> likeCountMap = toCountMap(commentLikeMapper.countByCommentIds(commentIds));
        Map<Long, Long> replyCountMap = fillReplyCount
                ? toCountMap(commentMapper.countRepliesByRootIds(commentIds))
                : Map.of();
        Set<Long> likedIds = commentLikeMapper.listLikedCommentIds(currentUserId, commentIds).stream()
                .collect(Collectors.toSet());

        Map<Long, Comment> parentMap = loadParentCommentMap(comments);
        Map<Long, User> userMap = loadUserMap(comments, parentMap);

        return PageResult.of(page, comment -> {
            CommentVO vo = commentConverter.toCommentVO(comment);
            User author = userMap.get(comment.getUserId());
            if (author != null) {
                vo.setAuthor(userConverter.toUserBriefVO(author));
            }
            // 一级评论没有 parentId，不能用 null key 访问 Map.of() 返回的空映射。
            Comment parent = comment.getParentId() == null ? null : parentMap.get(comment.getParentId());
            if (parent != null) {
                User replyToUser = userMap.get(parent.getUserId());
                if (replyToUser != null) {
                    vo.setReplyToUser(userConverter.toUserBriefVO(replyToUser));
                }
            }
            vo.setLikeCount(likeCountMap.getOrDefault(comment.getId(), 0L));
            vo.setReplyCount(replyCountMap.getOrDefault(comment.getId(), 0L));
            vo.setLiked(likedIds.contains(comment.getId()));
            return vo;
        });
    }

    private long getReplyCount(Long rootId) {
        return toCountMap(commentMapper.countRepliesByRootIds(List.of(rootId))).getOrDefault(rootId, 0L);
    }

    private Map<Long, Long> toCountMap(List<CommentCountItem> items) {
        Map<Long, Long> result = new LinkedHashMap<>();
        for (CommentCountItem item : items) {
            result.put(item.getCommentId(), item.getCount() == null ? 0L : item.getCount());
        }
        return result;
    }

    private Map<Long, Comment> loadParentCommentMap(List<Comment> comments) {
        List<Long> parentIds = comments.stream()
                .map(Comment::getParentId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return commentMapper.listByIds(parentIds).stream()
                .collect(Collectors.toMap(Comment::getId, Function.identity()));
    }

    private Map<Long, User> loadUserMap(List<Comment> comments, Map<Long, Comment> parentMap) {
        List<Long> userIds = comments.stream()
                .map(Comment::getUserId)
                .toList();
        List<Long> replyToUserIds = parentMap.values().stream()
                .map(Comment::getUserId)
                .toList();
        List<Long> allUserIds = java.util.stream.Stream.concat(userIds.stream(), replyToUserIds.stream())
                .distinct()
                .toList();
        if (allUserIds.isEmpty()) {
            return Map.of();
        }
        return userService.listByIds(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
