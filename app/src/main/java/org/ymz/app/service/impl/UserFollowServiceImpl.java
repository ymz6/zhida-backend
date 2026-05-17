package org.ymz.app.service.impl;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ymz.app.converter.UserConverter;
import org.ymz.app.mapper.UserFollowMapper;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.dto.user.FollowStatusVO;
import org.ymz.app.model.dto.user.ListFollowUsersRequest;
import org.ymz.app.model.dto.user.UserBriefVO;
import org.ymz.app.model.entity.User;
import org.ymz.app.model.entity.UserFollow;
import org.ymz.app.service.UserFollowService;
import org.ymz.app.service.UserService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户关注关系服务层实现。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class UserFollowServiceImpl extends ServiceImpl<UserFollowMapper, UserFollow> implements UserFollowService {

    private final UserFollowMapper userFollowMapper;
    private final UserService userService;
    private final UserConverter userConverter;

    @Override
    public void follow(Long followerId, Long followeeId) {
        if (followerId.equals(followeeId)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "不能关注自己");
        }
        if (userService.getById(followeeId) == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "用户不存在");
        }

        // 数据库唯一约束兜底去重，重复关注保持幂等成功。
        userFollowMapper.insertIgnore(followerId, followeeId);
    }

    @Override
    public void unfollow(Long followerId, Long followeeId) {
        if (userService.getById(followeeId) == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "用户不存在");
        }

        // 未关注时删除 0 行也视为成功，便于前端重试和状态同步。
        userFollowMapper.deleteByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    @Override
    public PageResult<UserBriefVO> listFollowing(Long currentUserId, Long targetUserId, ListFollowUsersRequest request) {
        if (userService.getById(targetUserId) == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "用户不存在");
        }

        Page<User> page = userFollowMapper.paginateFollowing(request.toPage(), targetUserId,
                StrUtil.trimToNull(request.getKeyword()));
        return toUserBriefPageResult(currentUserId, page);
    }

    @Override
    public PageResult<UserBriefVO> listFollowers(Long currentUserId, Long targetUserId, ListFollowUsersRequest request) {
        if (userService.getById(targetUserId) == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "用户不存在");
        }

        Page<User> page = userFollowMapper.paginateFollowers(request.toPage(), targetUserId,
                StrUtil.trimToNull(request.getKeyword()));
        return toUserBriefPageResult(currentUserId, page);
    }

    @Override
    public FollowStatusVO getFollowStatus(Long currentUserId, Long targetUserId) {
        if (userService.getById(targetUserId) == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "用户不存在");
        }

        return FollowStatusVO.builder()
                .isFollowing(userFollowMapper.countByFollowerIdAndFolloweeId(currentUserId, targetUserId) > 0)
                .isFollowed(userFollowMapper.countByFollowerIdAndFolloweeId(targetUserId, currentUserId) > 0)
                .build();
    }

    @Override
    public Map<Long, Boolean> batchGetFollowingStatus(Long currentUserId, List<Long> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctTargetIds = targetUserIds.stream()
                .distinct()
                .toList();
        Set<Long> followingIds = userFollowMapper.listFolloweeIds(currentUserId, distinctTargetIds).stream()
                .collect(Collectors.toSet());

        Map<Long, Boolean> result = new LinkedHashMap<>();
        distinctTargetIds.forEach(userId -> result.put(userId, followingIds.contains(userId)));
        return result;
    }

    @Override
    public Map<Long, Boolean> batchGetFollowedStatus(Long currentUserId, List<Long> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctTargetIds = targetUserIds.stream()
                .distinct()
                .toList();
        Set<Long> followedIds = userFollowMapper.listFollowerIds(currentUserId, distinctTargetIds).stream()
                .collect(Collectors.toSet());

        Map<Long, Boolean> result = new LinkedHashMap<>();
        distinctTargetIds.forEach(userId -> result.put(userId, followedIds.contains(userId)));
        return result;
    }

    private PageResult<UserBriefVO> toUserBriefPageResult(Long currentUserId, Page<User> page) {
        List<Long> userIds = page.getRecords().stream()
                .map(User::getId)
                .toList();
        Set<Long> followingIds = userIds.isEmpty()
                ? Set.of()
                : userFollowMapper.listFolloweeIds(currentUserId, userIds).stream().collect(Collectors.toSet());
        Set<Long> followedIds = userIds.isEmpty()
                ? Set.of()
                : userFollowMapper.listFollowerIds(currentUserId, userIds).stream().collect(Collectors.toSet());

        return PageResult.of(page, user -> {
            UserBriefVO vo = userConverter.toUserBriefVO(user);
            vo.setIsFollowing(followingIds.contains(user.getId()));
            vo.setIsFollowed(followedIds.contains(user.getId()));
            return vo;
        });
    }
}
