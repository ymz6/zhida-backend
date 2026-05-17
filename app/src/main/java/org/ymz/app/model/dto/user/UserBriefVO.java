package org.ymz.app.model.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公开用户摘要信息。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBriefVO {
    private Long id;
    private String nickname;
    private String avatar;
    private String profile;
    private Boolean isFollowing;
    private Boolean isFollowed;
}
