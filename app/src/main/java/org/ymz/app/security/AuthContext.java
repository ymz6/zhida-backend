package org.ymz.app.security;

import lombok.Builder;
import lombok.Getter;
import org.ymz.app.enums.UserRole;

import java.util.Date;


/**
 * 当前请求认证上下文类
 *
 * @author ymz
 */
@Getter
@Builder
public class AuthContext {
    private final Long userId;
    private final UserRole userRole;
    private final String tokenId;
    private final Date tokenExpiration;
}
