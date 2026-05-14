package org.ymz.app.model.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑个人信息请求
 *
 * @author ymz
 */
@Data
public class UpdateProfileRequest {
    @NotBlank(message = "昵称不能为空")
    @Size(max = 10, message = "昵称最多10个字符")
    private String nickname;
    @Size(max = 100, message = "个人简介最多100个字符")
    private String profile;
}

