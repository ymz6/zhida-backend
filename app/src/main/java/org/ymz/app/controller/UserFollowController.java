package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.dto.user.FollowStatusVO;
import org.ymz.app.model.dto.user.ListFollowUsersRequest;
import org.ymz.app.model.dto.user.UserBriefVO;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.service.UserFollowService;
import org.ymz.app.web.response.Response;

import java.util.List;
import java.util.Map;

/**
 * 用户关注关系模块。
 *
 * @author ymz
 */
@Tag(name = "user-follow")
@LoginRequired
@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserFollowController {

    private final UserFollowService userFollowService;

    @PostMapping("/{userId}/follow")
    @Operation(operationId = "followUser")
    public Response<Void> followUser(@PathVariable Long userId) {
        AuthContext authContext = AuthContextHolder.get();
        userFollowService.follow(authContext.getUserId(), userId);
        return Response.ok();
    }

    @DeleteMapping("/{userId}/follow")
    @Operation(operationId = "unfollowUser")
    public Response<Void> unfollowUser(@PathVariable Long userId) {
        AuthContext authContext = AuthContextHolder.get();
        userFollowService.unfollow(authContext.getUserId(), userId);
        return Response.ok();
    }

    @GetMapping("/{userId}/following")
    @Operation(operationId = "listFollowing")
    public Response<PageResult<UserBriefVO>> listFollowing(
            @PathVariable Long userId,
            @Validated ListFollowUsersRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(userFollowService.listFollowing(authContext.getUserId(), userId, request));
    }

    @GetMapping("/{userId}/followers")
    @Operation(operationId = "listFollowers")
    public Response<PageResult<UserBriefVO>> listFollowers(
            @PathVariable Long userId,
            @Validated ListFollowUsersRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(userFollowService.listFollowers(authContext.getUserId(), userId, request));
    }

    @GetMapping("/{userId}/follow-status")
    @Operation(operationId = "getFollowStatus")
    public Response<FollowStatusVO> getFollowStatus(@PathVariable Long userId) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(userFollowService.getFollowStatus(authContext.getUserId(), userId));
    }

    @GetMapping("/follow-status")
    @Operation(operationId = "batchGetFollowStatus")
    public Response<Map<Long, Boolean>> batchGetFollowStatus(@RequestParam List<Long> userIds) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(userFollowService.batchGetFollowingStatus(authContext.getUserId(), userIds));
    }
}
