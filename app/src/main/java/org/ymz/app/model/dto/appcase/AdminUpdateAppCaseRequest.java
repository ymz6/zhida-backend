package org.ymz.app.model.dto.appcase;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员更新案例审核和展示状态请求。
 *
 * @author ymz
 */
@Data
public class AdminUpdateAppCaseRequest {

    private String status;

    private Boolean featured;

    @Size(max = 512, message = "审核备注不能超过512个字符")
    private String reviewRemark;
}
