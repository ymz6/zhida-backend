package org.ymz.app.model.dto.app;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 *
 * @author ymz
 */
@Data
public class EditAppRequest {
    @NotBlank(message = "应用名称不能为空")
    @Size(max = 20, message = "应用名称不得超过20个字符")
    private String name;
}
