package org.ymz.app.mapper;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.ymz.app.model.entity.LlmCallLog;

/**
 * 大语言模型调用明细日志 Mapper。
 *
 * @author ymz
 */
@Mapper
public interface LlmCallLogMapper extends BaseMapper<LlmCallLog> {
}
