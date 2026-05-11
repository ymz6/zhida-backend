package org.ymz.app.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.ymz.app.model.entity.LlmLog;
import org.ymz.app.mapper.LlmLogMapper;
import org.ymz.app.service.LlmLogService;
import org.springframework.stereotype.Service;

/**
 * 大语言模型日志表 服务层实现。
 *
 * @author ymz
 */
@Service
public class LlmLogServiceImpl extends ServiceImpl<LlmLogMapper, LlmLog>  implements LlmLogService{

}
