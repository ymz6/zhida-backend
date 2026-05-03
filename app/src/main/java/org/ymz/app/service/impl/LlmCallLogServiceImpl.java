package org.ymz.app.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ymz.app.mapper.LlmCallLogMapper;
import org.ymz.app.model.entity.LlmCallLog;
import org.ymz.app.service.LlmCallLogService;

/**
 * 大语言模型调用明细日志服务实现。
 *
 * @author ymz
 */
@Service
public class LlmCallLogServiceImpl
        extends ServiceImpl<LlmCallLogMapper, LlmCallLog>
        implements LlmCallLogService {
}
