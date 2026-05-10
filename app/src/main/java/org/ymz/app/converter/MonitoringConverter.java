package org.ymz.app.converter;

import org.mapstruct.Mapper;
import org.ymz.app.model.dto.monitoring.LlmCallLogInfo;
import org.ymz.app.model.entity.LlmCallLog;

/**
 * 监控模块转换器。
 *
 * @author ymz
 */
@Mapper(componentModel = "spring")
public interface MonitoringConverter {

    LlmCallLogInfo toLlmCallLogInfo(LlmCallLog log);
}
