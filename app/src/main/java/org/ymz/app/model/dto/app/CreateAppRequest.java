package org.ymz.app.model.dto.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建应用请求
 * @author ymz
 */
@Data
public class CreateAppRequest {
    @NotBlank(message = "初始提示词不能为空")
    @Size(max = 6000, message = "初始提示词不得超过6000个字符")
    private String initPrompt;
}
