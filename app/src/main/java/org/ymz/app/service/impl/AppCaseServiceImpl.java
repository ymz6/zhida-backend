package org.ymz.app.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ymz.app.mapper.AppCaseMapper;
import org.ymz.app.model.entity.AppCase;
import org.ymz.app.service.AppCaseService;

/**
 * 应用案例表 服务层实现。
 *
 * @author ymz
 */
@Service
public class AppCaseServiceImpl extends ServiceImpl<AppCaseMapper, AppCase> implements AppCaseService {
}
