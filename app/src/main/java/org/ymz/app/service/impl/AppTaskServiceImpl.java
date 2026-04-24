package org.ymz.app.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.mapper.AppTaskMapper;
import org.ymz.app.service.AppTaskService;
import org.springframework.stereotype.Service;

/**
 * 应用任务表 服务层实现。
 *
 * @author ymz
 */
@Service
public class AppTaskServiceImpl extends ServiceImpl<AppTaskMapper, AppTask>  implements AppTaskService{

}
