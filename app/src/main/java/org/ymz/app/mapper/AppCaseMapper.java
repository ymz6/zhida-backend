package org.ymz.app.mapper;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ymz.app.model.entity.AppCase;

/**
 * 应用案例表 映射层。
 *
 * @author ymz
 */
@Mapper
public interface AppCaseMapper extends BaseMapper<AppCase> {
}
