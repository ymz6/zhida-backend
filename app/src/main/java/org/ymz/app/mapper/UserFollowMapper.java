package org.ymz.app.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import org.apache.ibatis.annotations.Param;
import org.ymz.app.model.entity.User;
import org.ymz.app.model.entity.UserFollow;

import java.util.Collection;
import java.util.List;

/**
 * 用户关注关系映射层。
 *
 * @author ymz
 */
public interface UserFollowMapper extends BaseMapper<UserFollow> {

    int insertIgnore(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    int deleteByFollowerIdAndFolloweeId(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    long countByFollowerIdAndFolloweeId(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    Page<User> paginateFollowing(Page<User> page, @Param("userId") Long userId, @Param("keyword") String keyword);

    Page<User> paginateFollowers(Page<User> page, @Param("userId") Long userId, @Param("keyword") String keyword);

    List<Long> listFolloweeIds(@Param("followerId") Long followerId, @Param("followeeIds") Collection<Long> followeeIds);

    List<Long> listFollowerIds(@Param("followeeId") Long followeeId, @Param("followerIds") Collection<Long> followerIds);
}
