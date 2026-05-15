package org.ymz.app.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.If;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ymz.app.converter.LlmLogConverter;
import org.ymz.app.mapper.LlmLogMapper;
import org.ymz.app.model.dto.monitoring.ListLlmLogsRequest;
import org.ymz.app.model.dto.monitoring.LlmLogOverviewRequest;
import org.ymz.app.model.dto.monitoring.LlmLogOverviewVO;
import org.ymz.app.model.dto.monitoring.LlmLogVO;
import org.ymz.app.model.dto.monitoring.LlmModelCallCountVO;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.entity.LlmLog;

import java.time.LocalDateTime;
import java.util.List;

import static org.ymz.app.model.entity.table.LlmLogTableDef.LLM_LOG;

/**
 * LLM 调用监控查询业务。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class LlmMonitoringService {

    private final LlmLogMapper llmLogMapper;
    private final LlmLogService llmLogService;
    private final LlmLogConverter llmLogConverter;

    public LlmLogOverviewVO getOverview(LlmLogOverviewRequest request) {
        LocalDateTime endTime = request.getEndTime() == null ? LocalDateTime.now() : request.getEndTime();
        LocalDateTime startTime = request.getStartTime() == null ? endTime.minusHours(1) : request.getStartTime();

        LlmLogOverviewVO overview = llmLogMapper.selectOverview(startTime, endTime);
        if (overview == null) {
            overview = new LlmLogOverviewVO();
        }

        List<LlmModelCallCountVO> modelCallCounts = llmLogMapper.selectModelCallCounts(startTime, endTime);
        // 失败日志当前没有稳定的模型名，这里只按表中已有 model_name 聚合。
        overview.setModelCallCounts(modelCallCounts == null ? List.of() : modelCallCounts);
        return overview;
    }

    public PageResult<LlmLogVO> listLlmLogs(ListLlmLogsRequest request) {
        QueryWrapper query = QueryWrapper.create()
                .select(LLM_LOG.ALL_COLUMNS)
                .from(LLM_LOG)
                .where(LLM_LOG.CREATED_AT.ge(request.getStartTime(), If::notNull))
                .and(LLM_LOG.CREATED_AT.lt(request.getEndTime(), If::notNull))
                .orderBy(LLM_LOG.CREATED_AT.desc());

        Page<LlmLog> page = llmLogService.page(request.toPage(), query);
        return PageResult.of(page, llmLogConverter::toLlmLogVO);
    }
}
