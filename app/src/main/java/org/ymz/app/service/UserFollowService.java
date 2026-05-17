package org.ymz.app.service;

import com.mybatisflex.core.service.IService;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.dto.user.FollowStatusVO;
import org.ymz.app.model.dto.user.ListFollowUsersRequest;
import org.ymz.app.model.dto.user.UserBriefVO;
import org.ymz.app.model.entity.UserFollow;

import java.util.List;
import java.util.Map;

/**
 * 用户关注关系服务层。
 *
 * @author ymz
 */
public interface UserFollowService extends IService<UserFollow> {

    void follow(Long followerId, Long followeeId);

    void unfollow(Long followerId, Long followeeId);

    PageResult<UserBriefVO> listFollowing(Long currentUserId, Long targetUserId, ListFollowUsersRequest request);

    PageResult<UserBriefVO> listFollowers(Long currentUserId, Long targetUserId, ListFollowUsersRequest request);

    FollowStatusVO getFollowStatus(Long currentUserId, Long targetUserId);

    Map<Long, Boolean> batchGetFollowingStatus(Long currentUserId, List<Long> targetUserIds);

    Map<Long, Boolean> batchGetFollowedStatus(Long currentUserId, List<Long> targetUserIds);
}
