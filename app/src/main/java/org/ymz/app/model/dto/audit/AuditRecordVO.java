package org.ymz.app.model.dto.audit;

import lombok.Data;
import org.ymz.app.model.dto.app.AppVO;

import java.time.LocalDateTime;

/**
 * 应用审核记录展示对象。
 *
 * @author ymz
 */
@Data
public class AuditRecordVO {

    private Long id;

    private Long appId;

    private AppVO app;

    private Integer status;

    private Long auditorId;

    private String remark;

    private LocalDateTime auditTime;

    private LocalDateTime createdAt;
}
