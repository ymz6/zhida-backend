package org.ymz.app.service;

import cn.hutool.core.util.IdUtil;
import com.mybatisflex.core.query.QueryWrapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ymz.app.converter.UserConverter;
import org.ymz.app.model.enums.UserRole;
import org.ymz.app.model.dto.auth.LoginRequest;
import org.ymz.app.model.dto.auth.LoginResponse;
import org.ymz.app.model.dto.auth.RegisterRequest;
import org.ymz.app.model.entity.User;
import org.ymz.app.security.AccessTokenBlacklistManager;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.JwtClaimName;
import org.ymz.app.security.JwtHelper;
import org.ymz.app.utils.BCryptHashUtils;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

import static org.ymz.app.model.entity.table.UserTableDef.USER;

/**
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserService userService;
    private final JwtHelper jwtHelper;
    private final UserConverter userConverter;
    private final AccessTokenBlacklistManager accessTokenBlacklistManager;

    // 5小时
    private static final long ACCESS_TOKEN_EXPIRE_SECONDS = 5 * 60 * 60L;

    public void register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "两次密码不一致");
        }

        QueryWrapper condition = QueryWrapper.create().where(USER.ACCOUNT.eq(request.getAccount()));
        if (userService.getOne(condition) != null) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "账号已存在");
        }

        User user = User.builder()
                .account(request.getAccount())
                // 对密码Hash加密
                .password(BCryptHashUtils.hash(request.getPassword()))
                .role(UserRole.USER.getCode())
                .createTime(LocalDateTime.now())
                .build();

        if (!userService.save(user)) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "保存新用户信息失败");
        }
    }

    public LoginResponse login(LoginRequest request) {
        // 根据账号查询用户信息
        QueryWrapper userQuery = QueryWrapper.create()
                .where(USER.ACCOUNT.eq(request.getAccount()));
        User user = userService.getOne(userQuery);
        if (user == null) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "登录失败");
        }

        // 校验密码
        if (!BCryptHashUtils.matches(request.getPassword(), user.getPassword())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "登录失败");
        }

        // 下发 Access Token
        String jti = IdUtil.fastSimpleUUID();
        Claims accessTokenClaims = Jwts.claims()
                .add(JwtClaimName.USER_ID, user.getId())
                .add(JwtClaimName.ROLE, user.getRole())
                .add(JwtClaimName.JTI, jti)
                .build();
        String accessToken = jwtHelper.generate(accessTokenClaims, ACCESS_TOKEN_EXPIRE_SECONDS);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .userVO(userConverter.toUserVO(user))
                .build();
    }

    public void logout(AuthContext authContext) {
        // 计算 access token 的剩余过期时间，然后将其标识加入到黑名单中
        accessTokenBlacklistManager.revoke(authContext.getTokenId(),
                Duration.between(Instant.now(), authContext.getTokenExpiration().toInstant()));
    }
}
