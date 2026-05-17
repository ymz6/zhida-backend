package org.ymz.app.mapper;

import com.mybatisflex.core.BaseMapper;
import com.mybatisflex.core.paginate.Page;
import org.apache.ibatis.annotations.Param;
import org.ymz.app.model.entity.User;
import org.ymz.app.model.entity.UserFollow;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户关注关系映射层。
 *
 * @author ymz
 */
public interface UserFollowMapper extends BaseMapper<UserFollow> {

    int insertIgnore(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    int deleteByFollowerIdAndFolloweeId(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    long countByFollowerIdAndFolloweeId(@Param("followerId") Long followerId, @Param("followeeId") Long followeeId);

    default Page<User> paginateFollowing(Page<User> page, @Param("userId") Long userId, @Param("keyword") String keyword) {
        // 自定义 XML 分页必须走 xmlPaginate，避免 MyBatis 把 Page 返回值当单条记录处理。
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("keyword", keyword);
        return xmlPaginate("paginateFollowing", page, params);
    }

    default Page<User> paginateFollowers(Page<User> page, @Param("userId") Long userId, @Param("keyword") String keyword) {
        // 自定义 XML 分页必须走 xmlPaginate，避免 MyBatis 把 Page 返回值当单条记录处理。
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("keyword", keyword);
        return xmlPaginate("paginateFollowers", page, params);
    }

    List<Long> listFolloweeIds(@Param("followerId") Long followerId, @Param("followeeIds") Collection<Long> followeeIds);

    List<Long> listFollowerIds(@Param("followeeId") Long followeeId, @Param("followerIds") Collection<Long> followerIds);
}
