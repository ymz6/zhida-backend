package org.ymz.app.model.dto.app;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 创建应用请求。
 *
 * @author ymz
 */
@Data
public class CreateAppRequest {

    @NotBlank(message = "请输入应用需求")
    @Length(max = 4000, message = "应用需求不能超过4000个字符")
    private String prompt;
}
