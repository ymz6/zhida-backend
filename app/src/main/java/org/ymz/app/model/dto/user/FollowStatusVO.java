package org.ymz.app.model.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户与目标用户的关注关系。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FollowStatusVO {
    private Boolean isFollowing;
    private Boolean isFollowed;
}
