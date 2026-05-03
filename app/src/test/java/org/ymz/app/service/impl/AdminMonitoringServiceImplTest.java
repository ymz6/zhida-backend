package org.ymz.app.service.impl;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.ymz.app.converter.MonitoringConverter;
import org.ymz.app.model.dto.monitoring.MonitoringDashboard;
import org.ymz.app.model.dto.monitoring.MonitoringDashboardRequest;
import org.ymz.app.model.dto.monitoring.MonitoringMetricQuery;
import org.ymz.app.model.dto.monitoring.MonitoringPoint;
import org.ymz.app.model.dto.monitoring.MonitoringQueryRequest;
import org.ymz.app.model.dto.monitoring.MonitoringQueryResult;
import org.ymz.app.model.dto.monitoring.MonitoringSeries;
import org.ymz.app.model.dto.monitoring.MonitoringTableQuery;
import org.ymz.app.model.dto.monitoring.MonitoringTableResult;
import org.ymz.app.model.dto.monitoring.SystemExceptionLogInfo;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.entity.LlmCallLog;
import org.ymz.app.model.entity.SystemExceptionLog;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.monitoring.PrometheusQueryService;
import org.ymz.app.service.AppTaskService;
import org.ymz.app.service.LlmCallLogService;
import org.ymz.app.service.SystemExceptionLogService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminMonitoringServiceImplTest {

        @Test
        void queryReturnsPrometheusMysqlMetricsAndTaskTable() {
                Fixture fixture = fixture();
                LocalDateTime startTime = LocalDateTime.of(2026, 5, 1, 0, 0);
                LocalDateTime endTime = LocalDateTime.of(2026, 5, 1, 0, 10);
                when(fixture.prometheusQueryService.queryInstant(any(), any())).thenReturn(12D);
                when(fixture.prometheusQueryService.queryRange(any(), any(), any(), any(Long.class)))
                                .thenReturn(List.of(
                                                series("requests", startTime, 1D, 2D)));
                when(fixture.appTaskService.list(any(QueryWrapper.class))).thenReturn(List.of(
                                task(AppTaskType.CREATE, AppTaskStatus.SUCCESS, startTime.plusMinutes(1), 1000),
                                task(AppTaskType.ITERATE, AppTaskStatus.FAILED, startTime.plusMinutes(2), 3000)));

                MonitoringQueryRequest request = new MonitoringQueryRequest();
                request.setStartTime(startTime);
                request.setEndTime(endTime);
                request.setStepSeconds(60L);
                request.getMetricQueries().add(metricQuery("httpRate", "system.http.request.rate"));
                request.getMetricQueries().add(metricQuery("taskTotal", "business.task.total"));
                request.getTableQueries().add(tableQuery("taskTable", "TASK_STAT", Map.of()));

                MonitoringQueryResult result = fixture.service.query(request);

                assertEquals(startTime, result.getStartTime());
                assertEquals(endTime, result.getEndTime());
                assertEquals(60, result.getStepSeconds());
                assertEquals(2, result.getMetrics().size());
                assertEquals("SUCCESS", result.getMetrics().getFirst().getStatus());
                assertEquals(12D, result.getMetrics().getFirst().getLatestValue());
                assertEquals(2D, result.getMetrics().get(1).getLatestValue());
                assertEquals(1, result.getTables().size());

                MonitoringTableResult table = result.getTables().getFirst();
                assertEquals("TASK_STAT", table.getResource());
                assertEquals(3, table.getTotal());
                assertEquals(3, table.getRecords().size());
        }

        @Test
        void queryRejectsUnknownMetricKeyAndUnsupportedFilters() {
                Fixture fixture = fixture();
                MonitoringQueryRequest unknownMetricRequest = new MonitoringQueryRequest();
                unknownMetricRequest.getMetricQueries().add(metricQuery("unknown", "unknown.metric"));

                BusinessException unknownMetric = assertThrows(
                                BusinessException.class,
                                () -> fixture.service.query(unknownMetricRequest));
                assertEquals(ResultCode.INVALID_PARAM, unknownMetric.getResultCode());

                MonitoringQueryRequest unsupportedFilterRequest = new MonitoringQueryRequest();
                unsupportedFilterRequest.getTableQueries().add(tableQuery("taskTable", "TASK_STAT", Map.of(
                                "status", "SUCCESS")));

                BusinessException unsupportedFilter = assertThrows(
                                BusinessException.class,
                                () -> fixture.service.query(unsupportedFilterRequest));
                assertEquals(ResultCode.INVALID_PARAM, unsupportedFilter.getResultCode());
        }

        @Test
        void queryReturnsPagedExceptionAndLlmTables() {
                Fixture fixture = fixture();
                SystemExceptionLog exceptionLog = SystemExceptionLog.builder()
                                .id(1L)
                                .exceptionType("BusinessException")
                                .createdAt(LocalDateTime.now())
                                .build();
                SystemExceptionLogInfo exceptionInfo = new SystemExceptionLogInfo();
                exceptionInfo.setId(1L);
                when(fixture.systemExceptionLogService.page(any(Page.class), any(QueryWrapper.class)))
                                .thenReturn(page(List.of(exceptionLog), 1));
                when(fixture.monitoringConverter.toSystemExceptionLogInfo(exceptionLog)).thenReturn(exceptionInfo);

                LlmCallLog llmCallLog = LlmCallLog.builder()
                                .id(2L)
                                .scenario("CODE_GENERATE")
                                .createdAt(LocalDateTime.now())
                                .build();
                when(fixture.llmCallLogService.page(any(Page.class), any(QueryWrapper.class)))
                                .thenReturn(page(List.of(llmCallLog), 1));

                MonitoringQueryRequest request = new MonitoringQueryRequest();
                request.getTableQueries().add(tableQuery("exceptions", "EXCEPTION_LOG", Map.of(
                                "exceptionType", "Business",
                                "resultCode", 40000)));
                request.getTableQueries().add(tableQuery("llm", "LLM_CALL_LOG", Map.of(
                                "scenario", "CODE_GENERATE",
                                "appId", 1,
                                "taskId", 2)));

                MonitoringQueryResult result = fixture.service.query(request);

                assertEquals(2, result.getTables().size());
                assertEquals("EXCEPTION_LOG", result.getTables().getFirst().getResource());
                assertEquals(1, result.getTables().getFirst().getTotal());
                assertEquals(1, result.getTables().getFirst().getRecords().size());
                assertEquals("LLM_CALL_LOG", result.getTables().get(1).getResource());
                assertEquals(1, result.getTables().get(1).getTotal());
        }

        @Test
        void dashboardDegradesSystemMetricsWhenPrometheusIsUnavailable() {
                Fixture fixture = fixture();
                LocalDateTime now = LocalDateTime.now();
                when(fixture.prometheusQueryService.queryInstant(any(), any())).thenThrow(new RuntimeException("down"));
                when(fixture.appTaskService.list(any(QueryWrapper.class))).thenReturn(List.of(
                                task(AppTaskType.CREATE, AppTaskStatus.SUCCESS, now.minusMinutes(3), 1000)));
                when(fixture.systemExceptionLogService.list(any(QueryWrapper.class))).thenReturn(List.of(
                                SystemExceptionLog.builder().createdAt(now.minusMinutes(2)).build()));
                when(fixture.llmCallLogService.list(any(QueryWrapper.class))).thenReturn(List.of(
                                LlmCallLog.builder().totalTokens(30L).durationMillis(100L)
                                                .createdAt(now.minusMinutes(1)).build()));

                MonitoringDashboard dashboard = fixture.service.getDashboard(new MonitoringDashboardRequest());

                assertEquals("DEGRADED", dashboard.getOverallStatus());
                assertEquals("UNAVAILABLE", dashboard.getSystemCards().getFirst().getStatus());
                assertEquals(1D, dashboard.getTaskCards().getFirst().getValue());
                assertEquals(1D, dashboard.getExceptionCards().getFirst().getValue());
                assertEquals(1D, dashboard.getLlmCards().getFirst().getValue());
                assertEquals(4, dashboard.getCharts().size());
        }

        private Fixture fixture() {
                AppTaskService appTaskService = mock(AppTaskService.class);
                SystemExceptionLogService systemExceptionLogService = mock(SystemExceptionLogService.class);
                LlmCallLogService llmCallLogService = mock(LlmCallLogService.class);
                PrometheusQueryService prometheusQueryService = mock(PrometheusQueryService.class);
                MonitoringConverter monitoringConverter = mock(MonitoringConverter.class);
                AdminMonitoringServiceImpl service = new AdminMonitoringServiceImpl(
                                appTaskService,
                                systemExceptionLogService,
                                llmCallLogService,
                                monitoringConverter,
                                prometheusQueryService);
                return new Fixture(
                                service,
                                appTaskService,
                                systemExceptionLogService,
                                llmCallLogService,
                                prometheusQueryService,
                                monitoringConverter);
        }

        private MonitoringMetricQuery metricQuery(String queryId, String key) {
                MonitoringMetricQuery query = new MonitoringMetricQuery();
                query.setQueryId(queryId);
                query.setKey(key);
                return query;
        }

        private MonitoringTableQuery tableQuery(String queryId, String resource, Map<String, Object> filters) {
                MonitoringTableQuery query = new MonitoringTableQuery();
                query.setQueryId(queryId);
                query.setResource(resource);
                query.setPageNum(1);
                query.setPageSize(10);
                query.setFilters(filters);
                return query;
        }

        private AppTask task(AppTaskType type, AppTaskStatus status, LocalDateTime createdAt, Integer durationMillis) {
                LocalDateTime startedAt = durationMillis == null ? null : createdAt;
                LocalDateTime finishedAt = durationMillis == null ? null
                                : startedAt.plusNanos(durationMillis * 1_000_000L);
                return AppTask.builder()
                                .taskType(type.name())
                                .status(status.name())
                                .startedAt(startedAt)
                                .finishedAt(finishedAt)
                                .createdAt(createdAt)
                                .build();
        }

        private MonitoringSeries series(String name, LocalDateTime startTime, Double... values) {
                MonitoringSeries series = new MonitoringSeries();
                series.setName(name);
                for (int i = 0; i < values.length; i++) {
                        series.getPoints().add(new MonitoringPoint(startTime.plusMinutes(i), values[i]));
                }
                return series;
        }

        private <T> Page<T> page(List<T> records, long total) {
                Page<T> page = Page.of(1, 10);
                page.setRecords(records);
                page.setTotalRow(total);
                return page;
        }

        private record Fixture(
                        AdminMonitoringServiceImpl service,
                        AppTaskService appTaskService,
                        SystemExceptionLogService systemExceptionLogService,
                        LlmCallLogService llmCallLogService,
                        PrometheusQueryService prometheusQueryService,
                        MonitoringConverter monitoringConverter) {
        }
}
