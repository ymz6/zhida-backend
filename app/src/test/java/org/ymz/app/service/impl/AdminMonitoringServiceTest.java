package org.ymz.app.service.impl;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.ymz.app.converter.MonitoringConverter;
import org.ymz.app.model.dto.monitoring.ListLlmCallsRequest;
import org.ymz.app.model.dto.monitoring.LlmCallLogInfo;
import org.ymz.app.model.dto.monitoring.LlmMonitoringOverview;
import org.ymz.app.model.dto.monitoring.LlmMonitoringOverviewRequest;
import org.ymz.app.model.dto.monitoring.LlmTokenUsageTrend;
import org.ymz.app.model.dto.monitoring.LlmTokenUsageTrendRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.entity.LlmCallLog;
import org.ymz.app.model.enums.monitoring.LlmCallStatus;
import org.ymz.app.service.AdminMonitoringService;
import org.ymz.app.service.LlmCallLogService;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminMonitoringServiceTest {

    @Test
    void getOverviewReturnsLlmAggregates() {
        Fixture fixture = fixture();
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 5, 1, 1, 0);
        when(fixture.llmCallLogService.list(any(QueryWrapper.class))).thenReturn(List.of(
                LlmCallLog.builder()
                        .status(LlmCallStatus.SUCCESS.name())
                        .inputTokens(100L)
                        .outputTokens(50L)
                        .totalTokens(150L)
                        .durationMillis(1000L)
                        .createdAt(startTime.plusMinutes(5))
                        .build(),
                LlmCallLog.builder()
                        .status(LlmCallStatus.FAILED.name())
                        .inputTokens(50L)
                        .outputTokens(0L)
                        .totalTokens(50L)
                        .durationMillis(3000L)
                        .createdAt(startTime.plusMinutes(10))
                        .build()));
        LlmMonitoringOverviewRequest request = new LlmMonitoringOverviewRequest();
        request.setStartTime(startTime);
        request.setEndTime(endTime);

        LlmMonitoringOverview overview = fixture.service.getOverview(request);

        assertEquals(startTime, overview.getStartTime());
        assertEquals(endTime, overview.getEndTime());
        assertEquals(2L, overview.getCallTotal());
        assertEquals(0.5D, overview.getSuccessRate());
        assertEquals(2000L, overview.getAverageDurationMillis());
        assertEquals(150L, overview.getInputTokens());
        assertEquals(50L, overview.getOutputTokens());
        assertEquals(200L, overview.getTotalTokens());
        assertEquals(1L, overview.getErrorCount());
    }

    @Test
    void getTokenUsageTrendReturnsFixedSeriesAndEmptyBuckets() {
        Fixture fixture = fixture();
        LocalDateTime startTime = LocalDateTime.of(2026, 5, 1, 0, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 5, 1, 0, 2);
        when(fixture.llmCallLogService.list(any(QueryWrapper.class))).thenReturn(List.of(
                LlmCallLog.builder()
                        .inputTokens(100L)
                        .outputTokens(50L)
                        .totalTokens(150L)
                        .createdAt(startTime.plusSeconds(10))
                        .build()));
        LlmTokenUsageTrendRequest request = new LlmTokenUsageTrendRequest();
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setStepSeconds(60L);

        LlmTokenUsageTrend trend = fixture.service.getTokenUsageTrend(request);

        assertEquals(startTime, trend.getStartTime());
        assertEquals(endTime, trend.getEndTime());
        assertEquals(60L, trend.getStepSeconds());
        assertEquals(3, trend.getSeries().size());
        assertEquals("inputTokens", trend.getSeries().get(0).getName());
        assertEquals(100D, trend.getSeries().get(0).getPoints().get(0).getValue());
        assertEquals(0D, trend.getSeries().get(0).getPoints().get(1).getValue());
        assertEquals("outputTokens", trend.getSeries().get(1).getName());
        assertEquals(50D, trend.getSeries().get(1).getPoints().get(0).getValue());
        assertEquals("totalTokens", trend.getSeries().get(2).getName());
        assertEquals(150D, trend.getSeries().get(2).getPoints().get(0).getValue());
    }

    @Test
    void listLlmCallsReturnsPagedDetails() {
        Fixture fixture = fixture();
        LlmCallLog log = LlmCallLog.builder()
                .id(2L)
                .scenario("CODE_GENERATION_CREATE")
                .createdAt(LocalDateTime.now())
                .build();
        LlmCallLogInfo info = new LlmCallLogInfo();
        info.setId(2L);
        info.setScenario("CODE_GENERATION_CREATE");
        when(fixture.llmCallLogService.page(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(page(List.of(log), 1));
        when(fixture.monitoringConverter.toLlmCallLogInfo(log)).thenReturn(info);
        ListLlmCallsRequest request = new ListLlmCallsRequest();
        request.setScenario("CODE_GENERATION_CREATE");
        request.setModelName("deepseek-v4-pro");
        request.setFinishReason("STOP");
        request.setAppId(1L);
        request.setTaskId(2L);
        request.setPageNum(1);
        request.setPageSize(10);

        PageResult<LlmCallLogInfo> result = fixture.service.listLlmCalls(request);

        assertEquals(1, result.getTotal());
        assertEquals(1, result.getList().size());
        assertEquals(2L, result.getList().getFirst().getId());
        assertEquals("CODE_GENERATION_CREATE", result.getList().getFirst().getScenario());
        assertEquals(1, result.getPageNum());
        assertEquals(10, result.getPageSize());
    }

    private Fixture fixture() {
        LlmCallLogService llmCallLogService = mock(LlmCallLogService.class);
        MonitoringConverter monitoringConverter = mock(MonitoringConverter.class);
        AdminMonitoringService service = new AdminMonitoringService(
                llmCallLogService,
                monitoringConverter);
        return new Fixture(
                service,
                llmCallLogService,
                monitoringConverter);
    }

    private <T> Page<T> page(List<T> records, long total) {
        Page<T> page = Page.of(1, 10);
        page.setRecords(records);
        page.setTotalRow(total);
        return page;
    }

    private record Fixture(
            AdminMonitoringService service,
            LlmCallLogService llmCallLogService,
            MonitoringConverter monitoringConverter) {
    }
}
