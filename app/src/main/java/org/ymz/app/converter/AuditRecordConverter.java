package org.ymz.app.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.ymz.app.model.dto.audit.AuditRecordVO;
import org.ymz.app.model.entity.AuditRecord;

/**
 * 应用审核记录转换器。
 *
 * @author ymz
 */
@Mapper(componentModel = "spring")
public interface AuditRecordConverter {

    @Mapping(target = "app", ignore = true)
    AuditRecordVO toAuditRecordVO(AuditRecord auditRecord);
}
