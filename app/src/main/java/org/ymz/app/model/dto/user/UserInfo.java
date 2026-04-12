package org.ymz.app.model.dto.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfo {
    private Long id;
    private String account;
    private Integer role;
    private String roleText;
    private String nickname;
    private String avatar;
    private String profile;
    private LocalDateTime createTime;
}
