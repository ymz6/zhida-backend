package org.ymz.app.model.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户登录请求
 * @author ymz
 */
@Data
public class LoginRequest {
    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,64}$", message = "登录失败")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "登录失败")
    private String password;
}
