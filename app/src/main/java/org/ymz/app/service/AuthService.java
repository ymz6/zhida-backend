package org.ymz.app.service;

import org.ymz.app.model.dto.user.RegisterRequest;

/**
 *
 * @author ymz
 */
public interface AuthService {
    /**
     * 注册账号
     */
    void register(RegisterRequest request);
}
