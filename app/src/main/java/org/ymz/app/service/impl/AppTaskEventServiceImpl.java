package org.ymz.app.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ymz.app.mapper.AppTaskEventMapper;
import org.ymz.app.model.entity.AppTaskEvent;
import org.ymz.app.service.AppTaskEventService;

/**
 * 应用任务运行事件表 服务实现层。
 *
 * @author ymz
 */
@Service
public class AppTaskEventServiceImpl extends ServiceImpl<AppTaskEventMapper, AppTaskEvent>
        implements AppTaskEventService {
}
