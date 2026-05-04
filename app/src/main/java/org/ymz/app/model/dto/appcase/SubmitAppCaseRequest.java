package org.ymz.app.model.dto.appcase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交应用案例请求。
 *
 * @author ymz
 */
@Data
public class SubmitAppCaseRequest {

    @NotNull(message = "应用 ID 不能为空")
    private Long appId;

    @NotBlank(message = "案例标题不能为空")
    @Size(max = 128, message = "案例标题不能超过128个字符")
    private String title;

    @NotBlank(message = "案例简介不能为空")
    @Size(max = 512, message = "案例简介不能超过512个字符")
    private String summary;
}
