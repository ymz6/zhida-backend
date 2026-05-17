package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.ymz.app.model.dto.comment.CommentLikeStatusVO;
import org.ymz.app.model.dto.comment.CommentVO;
import org.ymz.app.model.dto.comment.CreateCommentRequest;
import org.ymz.app.model.dto.comment.ListCommentsRequest;
import org.ymz.app.model.dto.comment.ListRepliesRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.service.CommentService;
import org.ymz.app.web.response.Response;

/**
 * 案例评论模块。
 *
 * @author ymz
 */
@Tag(name = "comment")
@LoginRequired
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/cases/{appId}/comments")
    @Operation(operationId = "createComment")
    public Response<CommentVO> createComment(
            @PathVariable Long appId,
            @RequestBody @Valid CreateCommentRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(commentService.createComment(authContext.getUserId(), appId, request));
    }

    @GetMapping("/cases/{appId}/comments")
    @Operation(operationId = "listRootComments")
    public Response<PageResult<CommentVO>> listRootComments(
            @PathVariable Long appId,
            @Validated ListCommentsRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(commentService.listRootComments(authContext.getUserId(), appId, request));
    }

    @PostMapping("/comments/{commentId}/replies")
    @Operation(operationId = "replyComment")
    public Response<CommentVO> replyComment(
            @PathVariable Long commentId,
            @RequestBody @Valid CreateCommentRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(commentService.replyComment(authContext.getUserId(), commentId, request));
    }

    @GetMapping("/comments/{rootId}/replies")
    @Operation(operationId = "listReplies")
    public Response<PageResult<CommentVO>> listReplies(
            @PathVariable Long rootId,
            @Validated ListRepliesRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(commentService.listReplies(authContext.getUserId(), rootId, request));
    }

    @PostMapping("/comments/{commentId}/like")
    @Operation(operationId = "toggleLike")
    public Response<CommentLikeStatusVO> toggleLike(@PathVariable Long commentId) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(commentService.toggleLike(authContext.getUserId(), commentId));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(operationId = "deleteComment")
    public Response<Void> deleteComment(@PathVariable Long commentId) {
        AuthContext authContext = AuthContextHolder.get();
        commentService.deleteComment(authContext, commentId);
        return Response.ok();
    }
}
