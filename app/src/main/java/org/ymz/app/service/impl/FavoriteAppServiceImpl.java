package org.ymz.app.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.ymz.app.mapper.FavoriteAppMapper;
import org.ymz.app.model.entity.FavoriteApp;
import org.ymz.app.service.FavoriteAppService;

/**
 * 收藏夹应用关联服务层实现。
 *
 * @author ymz
 */
@Service
public class FavoriteAppServiceImpl extends ServiceImpl<FavoriteAppMapper, FavoriteApp> implements FavoriteAppService {
}
