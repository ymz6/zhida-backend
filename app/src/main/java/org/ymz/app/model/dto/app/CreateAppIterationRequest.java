package org.ymz.app.model.dto.app;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

/**
 * 创建应用迭代请求。
 *
 * @author ymz
 */
@Data
public class CreateAppIterationRequest {

    @NotBlank(message = "请输入迭代需求")
    @Length(max = 4000, message = "迭代需求不能超过4000个字符")
    private String prompt;
}
