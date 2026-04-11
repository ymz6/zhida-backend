package org.ymz.app.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ymz.app.enums.UserRole;
import org.ymz.app.model.dto.user.RegisterRequest;
import org.ymz.app.model.entity.User;
import org.ymz.app.service.AuthService;
import org.ymz.app.service.UserService;
import org.ymz.app.utils.BCryptHashUtils;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.LocalDateTime;

import static org.ymz.app.model.entity.table.UserTableDef.USER;


/**
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    public final UserService userService;

    @Override
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
}
