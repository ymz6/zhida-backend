package org.ymz.app.model.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求
 *
 * @author ymz
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "账号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_]{3,64}$", message = "账号格式非法")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, message = "密码格式非法")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}