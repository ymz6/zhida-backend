package org.ymz.app.mapper;

import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.ymz.app.model.dto.monitoring.LlmLogOverviewVO;
import org.ymz.app.model.dto.monitoring.LlmModelCallCountVO;
import org.ymz.app.model.entity.LlmLog;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大语言模型日志表 映射层。
 *
 * @author ymz
 */
public interface LlmLogMapper extends BaseMapper<LlmLog> {

    LlmLogOverviewVO selectOverview(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    List<LlmModelCallCountVO> selectModelCallCounts(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
