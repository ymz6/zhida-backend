package org.ymz.app.converter;

import org.mapstruct.Mapper;
import org.ymz.app.model.dto.monitoring.LlmCallLogInfo;
import org.ymz.app.model.dto.monitoring.SystemExceptionLogInfo;
import org.ymz.app.model.entity.LlmCallLog;
import org.ymz.app.model.entity.SystemExceptionLog;

/**
 * 监控模块转换器。
 *
 * @author ymz
 */
@Mapper(componentModel = "spring")
public interface MonitoringConverter {

    SystemExceptionLogInfo toSystemExceptionLogInfo(SystemExceptionLog log);

    LlmCallLogInfo toLlmCallLogInfo(LlmCallLog log);
}
