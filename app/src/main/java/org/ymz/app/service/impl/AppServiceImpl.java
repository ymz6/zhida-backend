package org.ymz.app.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.ymz.app.model.entity.App;
import org.ymz.app.mapper.AppMapper;
import org.ymz.app.service.AppService;
import org.springframework.stereotype.Service;

/**
 * 应用表 服务层实现。
 *
 * @author ymz
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

}
