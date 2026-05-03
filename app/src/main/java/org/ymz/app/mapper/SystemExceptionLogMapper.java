package org.ymz.app.mapper;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ymz.app.model.entity.SystemExceptionLog;

/**
 * 系统异常明细日志 Mapper。
 *
 * @author ymz
 */
@Mapper
public interface SystemExceptionLogMapper extends BaseMapper<SystemExceptionLog> {
}
