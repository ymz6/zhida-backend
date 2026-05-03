package org.ymz.app.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ymz.app.mapper.SystemExceptionLogMapper;
import org.ymz.app.model.entity.SystemExceptionLog;
import org.ymz.app.service.SystemExceptionLogService;

/**
 * 系统异常明细日志服务实现。
 *
 * @author ymz
 */
@Service
public class SystemExceptionLogServiceImpl
        extends ServiceImpl<SystemExceptionLogMapper, SystemExceptionLog>
        implements SystemExceptionLogService {
}
