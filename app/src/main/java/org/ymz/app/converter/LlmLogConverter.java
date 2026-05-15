package org.ymz.app.converter;

import org.mapstruct.Mapper;
import org.ymz.app.model.dto.monitoring.LlmLogVO;
import org.ymz.app.model.entity.LlmLog;

/**
 * LLM 调用日志转换器。
 *
 * @author ymz
 */
@Mapper(componentModel = "spring")
public interface LlmLogConverter {

    LlmLogVO toLlmLogVO(LlmLog llmLog);
}
