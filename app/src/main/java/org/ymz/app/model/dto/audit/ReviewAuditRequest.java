package org.ymz.app.model.dto.audit;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.ymz.app.model.enums.app.AppAuditStatus;

/**
 * 管理员处理待审核记录请求。
 *
 * @author ymz
 */
@Data
public class ReviewAuditRequest {

    @NotNull(message = "审核状态不能为空")
    private AppAuditStatus status;

    @Size(max = 500, message = "审核意见不得超过500个字符")
    private String remark;
}
