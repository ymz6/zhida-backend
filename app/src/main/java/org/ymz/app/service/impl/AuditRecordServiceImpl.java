package org.ymz.app.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ymz.app.mapper.AuditRecordMapper;
import org.ymz.app.model.entity.AuditRecord;
import org.ymz.app.service.AuditRecordService;

/**
 * 应用审核记录表服务层实现。
 *
 * @author ymz
 */
@Service
public class AuditRecordServiceImpl extends ServiceImpl<AuditRecordMapper, AuditRecord> implements AuditRecordService {
}
