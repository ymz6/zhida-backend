package org.ymz.app.service;

import org.ymz.app.model.dto.auth.LoginRequest;
import org.ymz.app.model.dto.auth.LoginResponse;
import org.ymz.app.model.dto.auth.RegisterRequest;

/**
 *
 * @author ymz
 */
public interface AuthService {
    /**
     * 注册账号
     */
    void register(RegisterRequest request);

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);
}
