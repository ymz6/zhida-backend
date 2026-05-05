package org.ymz.app.service;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.If;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ymz.app.converter.MonitoringConverter;
import org.ymz.app.model.dto.monitoring.LlmCallLogInfo;
import org.ymz.app.model.dto.monitoring.MonitoringDashboard;
import org.ymz.app.model.dto.monitoring.MonitoringDashboardCard;
import org.ymz.app.model.dto.monitoring.MonitoringDashboardRequest;
import org.ymz.app.model.dto.monitoring.MonitoringMetricQuery;
import org.ymz.app.model.dto.monitoring.MonitoringMetricResult;
import org.ymz.app.model.dto.monitoring.MonitoringPoint;
import org.ymz.app.model.dto.monitoring.MonitoringQueryRequest;
import org.ymz.app.model.dto.monitoring.MonitoringQueryResult;
import org.ymz.app.model.dto.monitoring.MonitoringSeries;
import org.ymz.app.model.dto.monitoring.MonitoringTableQuery;
import org.ymz.app.model.dto.monitoring.MonitoringTableResult;
import org.ymz.app.model.dto.monitoring.SystemExceptionLogInfo;
import org.ymz.app.model.dto.monitoring.TaskMonitoringStat;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.entity.LlmCallLog;
import org.ymz.app.model.entity.SystemExceptionLog;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.model.enums.monitoring.MonitoringMetricDefinition;
import org.ymz.app.model.enums.monitoring.MonitoringMetricSource;
import org.ymz.app.monitoring.PrometheusQueryService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.ymz.app.model.entity.table.AppTaskTableDef.APP_TASK;
import static org.ymz.app.model.entity.table.LlmCallLogTableDef.LLM_CALL_LOG;
import static org.ymz.app.model.entity.table.SystemExceptionLogTableDef.SYSTEM_EXCEPTION_LOG;

/**
 * 管理端运行与用量监控服务实现。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AdminMonitoringService {

    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final int TARGET_SERIES_POINTS = 240;
    private static final long MIN_STEP_SECONDS = 60;
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String DASHBOARD_STATUS_UP = "UP";
    private static final String DASHBOARD_STATUS_DEGRADED = "DEGRADED";
    private static final String RESOURCE_TASK_STAT = "TASK_STAT";
    private static final String RESOURCE_EXCEPTION_LOG = "EXCEPTION_LOG";
    private static final String RESOURCE_LLM_CALL_LOG = "LLM_CALL_LOG";
    private static final Set<String> TASK_STAT_FILTERS = Set.of("taskType");
    private static final Set<String> EXCEPTION_LOG_FILTERS = Set.of("exceptionType", "resultCode", "requestPath");
    private static final Set<String> LLM_CALL_LOG_FILTERS = Set.of("scenario", "modelName", "status", "appId",
            "taskId");
    private static final List<String> DASHBOARD_SYSTEM_KEYS = List.of(
            "system.jvm.memory.used",
            "system.jvm.memory.max",
            "system.disk.free",
            "system.disk.total",
            "system.http.request.rate",
            "system.http.error.rate",
            "system.http.duration.avg",
            "system.db.connections.active");
    private static final List<String> DASHBOARD_TASK_KEYS = List.of(
            "business.task.total",
            "business.task.success_rate",
            "business.task.average_duration");
    private static final List<String> DASHBOARD_EXCEPTION_KEYS = List.of("exception.count");
    private static final List<String> DASHBOARD_LLM_KEYS = List.of(
            "llm.call.total",
            "llm.token.total",
            "llm.duration.avg");
    private static final List<String> DASHBOARD_CHART_KEYS = List.of(
            "system.http.request.rate",
            "business.task.success_rate",
            "exception.count",
            "llm.token.total");

    private final AppTaskService appTaskService;
    private final SystemExceptionLogService systemExceptionLogService;
    private final LlmCallLogService llmCallLogService;
    private final MonitoringConverter monitoringConverter;
    private final PrometheusQueryService prometheusQueryService;

    public MonitoringDashboard getDashboard(MonitoringDashboardRequest request) {
        TimeRange range = timeRange(request.getStartTime(), request.getEndTime());
        MonitoringQueryRequest queryRequest = new MonitoringQueryRequest();
        queryRequest.setStartTime(range.startTime());
        queryRequest.setEndTime(range.endTime());

        addMetricQueries(queryRequest, "system.card.", DASHBOARD_SYSTEM_KEYS);
        addMetricQueries(queryRequest, "task.card.", DASHBOARD_TASK_KEYS);
        addMetricQueries(queryRequest, "exception.card.", DASHBOARD_EXCEPTION_KEYS);
        addMetricQueries(queryRequest, "llm.card.", DASHBOARD_LLM_KEYS);
        addMetricQueries(queryRequest, "chart.", DASHBOARD_CHART_KEYS);

        MonitoringQueryResult queryResult = query(queryRequest);
        Map<String, MonitoringMetricResult> metrics = new LinkedHashMap<>();
        for (MonitoringMetricResult metric : queryResult.getMetrics()) {
            metrics.put(metric.getQueryId(), metric);
        }

        List<MonitoringDashboardCard> systemCards = toCards(metrics, "system.card.", DASHBOARD_SYSTEM_KEYS);
        return MonitoringDashboard.builder()
                .startTime(queryResult.getStartTime())
                .endTime(queryResult.getEndTime())
                .overallStatus(overallStatus(systemCards))
                .systemCards(systemCards)
                .taskCards(toCards(metrics, "task.card.", DASHBOARD_TASK_KEYS))
                .exceptionCards(toCards(metrics, "exception.card.", DASHBOARD_EXCEPTION_KEYS))
                .llmCards(toCards(metrics, "llm.card.", DASHBOARD_LLM_KEYS))
                .charts(toMetrics(metrics, "chart.", DASHBOARD_CHART_KEYS))
                .build();
    }

    public MonitoringQueryResult query(MonitoringQueryRequest request) {
        TimeRange range = timeRange(request.getStartTime(), request.getEndTime());
        long stepSeconds = stepSeconds(request.getStepSeconds(), range);
        List<MonitoringMetricQuery> metricQueries = request.getMetricQueries() == null
                ? List.of()
                : request.getMetricQueries();
        List<MonitoringMetricResult> metrics = new ArrayList<>(metricQueries.size());
        for (MonitoringMetricQuery metricQuery : metricQueries) {
            metrics.add(queryMetric(metricQuery, range, stepSeconds));
        }

        List<MonitoringTableQuery> tableQueries = request.getTableQueries() == null
                ? List.of()
                : request.getTableQueries();
        List<MonitoringTableResult> tables = new ArrayList<>(tableQueries.size());
        for (MonitoringTableQuery tableQuery : tableQueries) {
            tables.add(queryTable(tableQuery, range));
        }
        return MonitoringQueryResult.builder()
                .startTime(range.startTime())
                .endTime(range.endTime())
                .stepSeconds(stepSeconds)
                .metrics(metrics)
                .tables(tables)
                .build();
    }

    private MonitoringMetricResult queryMetric(MonitoringMetricQuery query, TimeRange range, long stepSeconds) {
        MonitoringMetricDefinition definition = MonitoringMetricDefinition.fromKey(query.getKey())
                .orElseThrow(() -> invalidParam("未知监控指标：" + query.getKey()));
        MonitoringMetricResult result = MonitoringMetricResult.builder()
                .queryId(query.getQueryId())
                .key(definition.getKey())
                .title(definition.getTitle())
                .unit(definition.getUnit())
                .source(definition.getSource().name())
                .build();

        if (MonitoringMetricSource.PROMETHEUS.equals(definition.getSource())) {
            fillPrometheusMetric(result, definition, range, stepSeconds);
        } else {
            fillMysqlMetric(result, definition, range, stepSeconds);
        }
        return result;
    }

    private void fillPrometheusMetric(
            MonitoringMetricResult result,
            MonitoringMetricDefinition definition,
            TimeRange range,
            long stepSeconds) {
        try {
            Double latestValue = prometheusQueryService.queryInstant(definition.getPrometheusQuery(), range.endTime());
            List<MonitoringSeries> series = prometheusQueryService.queryRange(
                    definition.getPrometheusQuery(),
                    range.startTime(),
                    range.endTime(),
                    stepSeconds);
            result.setLatestValue(latestValue);
            result.setSeries(series);
            if (latestValue == null && series.stream().allMatch(item -> item.getPoints().isEmpty())) {
                result.setStatus(STATUS_UNAVAILABLE);
                result.setErrorMessage("Prometheus 未返回指标数据");
            } else {
                result.setStatus(STATUS_SUCCESS);
            }
        } catch (RuntimeException ex) {
            result.setStatus(STATUS_UNAVAILABLE);
            result.setErrorMessage("Prometheus 指标暂不可用");
        }
    }

    private void fillMysqlMetric(
            MonitoringMetricResult result,
            MonitoringMetricDefinition definition,
            TimeRange range,
            long stepSeconds) {
        switch (definition) {
            case BUSINESS_TASK_TOTAL -> fillTaskTotalMetric(result, range, stepSeconds);
            case BUSINESS_TASK_SUCCESS_RATE -> fillTaskSuccessRateMetric(result, range, stepSeconds);
            case BUSINESS_TASK_AVERAGE_DURATION -> fillTaskAverageDurationMetric(result, range, stepSeconds);
            case EXCEPTION_COUNT -> fillExceptionCountMetric(result, range, stepSeconds);
            case LLM_CALL_TOTAL -> fillLlmCallTotalMetric(result, range, stepSeconds);
            case LLM_TOKEN_TOTAL -> fillLlmTokenTotalMetric(result, range, stepSeconds);
            case LLM_DURATION_AVG -> fillLlmDurationAvgMetric(result, range, stepSeconds);
            default -> throw invalidParam("非业务监控指标：" + definition.getKey());
        }
        result.setStatus(STATUS_SUCCESS);
    }

    private void fillTaskTotalMetric(MonitoringMetricResult result, TimeRange range, long stepSeconds) {
        List<AppTask> tasks = appTaskService.list(taskQuery(range, null));
        List<MetricBucket> buckets = buckets(range, stepSeconds);
        for (AppTask task : tasks) {
            bucket(buckets, range, stepSeconds, task.getCreatedAt()).ifPresent(item -> item.value++);
        }
        result.setLatestValue((double) tasks.size());
        result.getSeries().add(toSeries(result.getTitle(), buckets, item -> item.value));
    }

    private void fillTaskSuccessRateMetric(MonitoringMetricResult result, TimeRange range, long stepSeconds) {
        List<AppTask> tasks = appTaskService.list(taskQuery(range, null));
        List<MetricBucket> buckets = buckets(range, stepSeconds);
        long success = 0;
        for (AppTask task : tasks) {
            if (AppTaskStatus.SUCCESS.name().equals(task.getStatus())) {
                success++;
            }
            bucket(buckets, range, stepSeconds, task.getCreatedAt()).ifPresent(item -> {
                item.total++;
                if (AppTaskStatus.SUCCESS.name().equals(task.getStatus())) {
                    item.value++;
                }
            });
        }
        result.setLatestValue(rate(success, tasks.size()));
        result.getSeries().add(toSeries(result.getTitle(), buckets, item -> rate(item.value, item.total)));
    }

    private void fillTaskAverageDurationMetric(MonitoringMetricResult result, TimeRange range, long stepSeconds) {
        List<AppTask> tasks = appTaskService.list(taskQuery(range, null));
        List<MetricBucket> buckets = buckets(range, stepSeconds);
        long durationTotal = 0;
        long durationCount = 0;
        for (AppTask task : tasks) {
            Long duration = taskDurationMillis(task);
            if (duration == null) {
                continue;
            }
            durationTotal += duration;
            durationCount++;
            bucket(buckets, range, stepSeconds, task.getCreatedAt()).ifPresent(item -> {
                item.value += duration;
                item.total++;
            });
        }
        result.setLatestValue(average(durationTotal, durationCount));
        result.getSeries().add(toSeries(result.getTitle(), buckets, item -> average(item.value, item.total)));
    }

    private void fillExceptionCountMetric(MonitoringMetricResult result, TimeRange range, long stepSeconds) {
        List<SystemExceptionLog> logs = systemExceptionLogService.list(exceptionQuery(range, null, null, null));
        List<MetricBucket> buckets = buckets(range, stepSeconds);
        for (SystemExceptionLog log : logs) {
            bucket(buckets, range, stepSeconds, log.getCreatedAt()).ifPresent(item -> item.value++);
        }
        result.setLatestValue((double) logs.size());
        result.getSeries().add(toSeries(result.getTitle(), buckets, item -> item.value));
    }

    private void fillLlmCallTotalMetric(MonitoringMetricResult result, TimeRange range, long stepSeconds) {
        List<LlmCallLog> logs = llmCallLogService.list(llmQuery(range, null, null, null, null, null));
        List<MetricBucket> buckets = buckets(range, stepSeconds);
        for (LlmCallLog log : logs) {
            bucket(buckets, range, stepSeconds, log.getCreatedAt()).ifPresent(item -> item.value++);
        }
        result.setLatestValue((double) logs.size());
        result.getSeries().add(toSeries(result.getTitle(), buckets, item -> item.value));
    }

    private void fillLlmTokenTotalMetric(MonitoringMetricResult result, TimeRange range, long stepSeconds) {
        List<LlmCallLog> logs = llmCallLogService.list(llmQuery(range, null, null, null, null, null));
        List<MetricBucket> buckets = buckets(range, stepSeconds);
        long tokenTotal = 0;
        for (LlmCallLog log : logs) {
            long tokens = log.getTotalTokens() == null ? 0 : log.getTotalTokens();
            tokenTotal += tokens;
            bucket(buckets, range, stepSeconds, log.getCreatedAt()).ifPresent(item -> item.value += tokens);
        }
        result.setLatestValue((double) tokenTotal);
        result.getSeries().add(toSeries(result.getTitle(), buckets, item -> item.value));
    }

    private void fillLlmDurationAvgMetric(MonitoringMetricResult result, TimeRange range, long stepSeconds) {
        List<LlmCallLog> logs = llmCallLogService.list(llmQuery(range, null, null, null, null, null));
        List<MetricBucket> buckets = buckets(range, stepSeconds);
        long durationTotal = 0;
        long durationCount = 0;
        for (LlmCallLog log : logs) {
            if (log.getDurationMillis() == null) {
                continue;
            }
            durationTotal += log.getDurationMillis();
            durationCount++;
            bucket(buckets, range, stepSeconds, log.getCreatedAt()).ifPresent(item -> {
                item.value += log.getDurationMillis();
                item.total++;
            });
        }
        result.setLatestValue(average(durationTotal, durationCount));
        result.getSeries().add(toSeries(result.getTitle(), buckets, item -> average(item.value, item.total)));
    }

    private MonitoringTableResult queryTable(MonitoringTableQuery query, TimeRange range) {
        String resource = normalizeResource(query.getResource());
        return switch (resource) {
            case RESOURCE_TASK_STAT -> queryTaskStatTable(query, range, resource);
            case RESOURCE_EXCEPTION_LOG -> queryExceptionLogTable(query, range, resource);
            case RESOURCE_LLM_CALL_LOG -> queryLlmCallLogTable(query, range, resource);
            default -> throw invalidParam("未知监控表格资源：" + query.getResource());
        };
    }

    private MonitoringTableResult queryTaskStatTable(MonitoringTableQuery query, TimeRange range, String resource) {
        validateFilters(query.getFilters(), TASK_STAT_FILTERS);
        List<TaskMonitoringStat> stats = listTaskStats(range, stringFilter(query.getFilters(), "taskType"));
        return tableResult(query, resource, stats);
    }

    private MonitoringTableResult queryExceptionLogTable(MonitoringTableQuery query, TimeRange range, String resource) {
        validateFilters(query.getFilters(), EXCEPTION_LOG_FILTERS);
        QueryWrapper wrapper = exceptionQuery(
                range,
                stringFilter(query.getFilters(), "exceptionType"),
                integerFilter(query.getFilters(), "resultCode"),
                stringFilter(query.getFilters(), "requestPath")).orderBy(SYSTEM_EXCEPTION_LOG.CREATED_AT.desc())
                .orderBy(SYSTEM_EXCEPTION_LOG.ID.desc());
        Page<SystemExceptionLog> page = systemExceptionLogService.page(query.toPage(), wrapper);
        List<SystemExceptionLogInfo> records = page.getRecords().stream()
                .map(monitoringConverter::toSystemExceptionLogInfo)
                .toList();
        return tableResult(query, resource, page.getTotalRow(), records);
    }

    private MonitoringTableResult queryLlmCallLogTable(MonitoringTableQuery query, TimeRange range, String resource) {
        validateFilters(query.getFilters(), LLM_CALL_LOG_FILTERS);
        QueryWrapper wrapper = llmQuery(
                range,
                stringFilter(query.getFilters(), "scenario"),
                stringFilter(query.getFilters(), "modelName"),
                stringFilter(query.getFilters(), "status"),
                longFilter(query.getFilters(), "appId"),
                longFilter(query.getFilters(), "taskId")).orderBy(LLM_CALL_LOG.CREATED_AT.desc())
                .orderBy(LLM_CALL_LOG.ID.desc());
        Page<LlmCallLog> page = llmCallLogService.page(query.toPage(), wrapper);
        List<LlmCallLogInfo> records = page.getRecords().stream()
                .map(monitoringConverter::toLlmCallLogInfo)
                .toList();
        return tableResult(query, resource, page.getTotalRow(), records);
    }

    private List<TaskMonitoringStat> listTaskStats(TimeRange range, String taskType) {
        List<AppTask> tasks = appTaskService.list(taskQuery(range, taskType));
        Map<String, TaskAccumulator> accumulators = new LinkedHashMap<>();
        for (AppTaskType type : AppTaskType.values()) {
            if (StrUtil.isBlank(taskType) || type.name().equals(taskType)) {
                accumulators.put(type.name(), new TaskAccumulator(type.name()));
            }
        }

        for (AppTask task : tasks) {
            String resolvedTaskType = StrUtil.blankToDefault(task.getTaskType(), "UNKNOWN");
            accumulators.computeIfAbsent(resolvedTaskType, TaskAccumulator::new).add(task);
        }

        return accumulators.values().stream()
                .map(TaskAccumulator::toStat)
                .toList();
    }

    private MonitoringTableResult tableResult(MonitoringTableQuery query, String resource, List<?> allRecords) {
        int fromIndex = Math.min((query.getPageNum() - 1) * query.getPageSize(), allRecords.size());
        int toIndex = Math.min(fromIndex + query.getPageSize(), allRecords.size());
        return tableResult(query, resource, allRecords.size(), allRecords.subList(fromIndex, toIndex));
    }

    private MonitoringTableResult tableResult(
            MonitoringTableQuery query,
            String resource,
            long total,
            List<?> records) {
        return MonitoringTableResult.builder()
                .queryId(query.getQueryId())
                .resource(resource)
                .status(STATUS_SUCCESS)
                .pageNum(query.getPageNum())
                .pageSize(query.getPageSize())
                .total(total)
                .records(new ArrayList<>(records))
                .build();
    }

    private QueryWrapper taskQuery(TimeRange range, String taskType) {
        return QueryWrapper.create()
                .select(APP_TASK.ALL_COLUMNS)
                .from(APP_TASK)
                .where(APP_TASK.CREATED_AT.ge(range.startTime()))
                .and(APP_TASK.CREATED_AT.le(range.endTime()))
                .and(APP_TASK.TASK_TYPE.eq(taskType, If::hasText));
    }

    private QueryWrapper exceptionQuery(
            TimeRange range,
            String exceptionType,
            Integer resultCode,
            String requestPath) {
        return QueryWrapper.create()
                .select(SYSTEM_EXCEPTION_LOG.ALL_COLUMNS)
                .from(SYSTEM_EXCEPTION_LOG)
                .where(SYSTEM_EXCEPTION_LOG.CREATED_AT.ge(range.startTime()))
                .and(SYSTEM_EXCEPTION_LOG.CREATED_AT.le(range.endTime()))
                .and(SYSTEM_EXCEPTION_LOG.EXCEPTION_TYPE.like(exceptionType, If::hasText))
                .and(SYSTEM_EXCEPTION_LOG.RESULT_CODE.eq(resultCode, If::notNull))
                .and(SYSTEM_EXCEPTION_LOG.REQUEST_PATH.like(requestPath, If::hasText));
    }

    private QueryWrapper llmQuery(
            TimeRange range,
            String scenario,
            String modelName,
            String status,
            Long appId,
            Long taskId) {
        return QueryWrapper.create()
                .select(LLM_CALL_LOG.ALL_COLUMNS)
                .from(LLM_CALL_LOG)
                .where(LLM_CALL_LOG.CREATED_AT.ge(range.startTime()))
                .and(LLM_CALL_LOG.CREATED_AT.le(range.endTime()))
                .and(LLM_CALL_LOG.SCENARIO.eq(scenario, If::hasText))
                .and(LLM_CALL_LOG.MODEL_NAME.eq(modelName, If::hasText))
                .and(LLM_CALL_LOG.STATUS.eq(status, If::hasText))
                .and(LLM_CALL_LOG.APP_ID.eq(appId, If::notNull))
                .and(LLM_CALL_LOG.TASK_ID.eq(taskId, If::notNull));
    }

    private TimeRange timeRange(LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime end = endTime == null ? LocalDateTime.now() : endTime;
        LocalDateTime start = startTime == null ? end.minusDays(DEFAULT_RANGE_DAYS) : startTime;
        return new TimeRange(start, end);
    }

    private long stepSeconds(Long requestedStepSeconds, TimeRange range) {
        if (requestedStepSeconds != null) {
            return requestedStepSeconds;
        }
        long rangeSeconds = Math.max(1, Duration.between(range.startTime(), range.endTime()).getSeconds());
        return Math.max(MIN_STEP_SECONDS, (long) Math.ceil((double) rangeSeconds / TARGET_SERIES_POINTS));
    }

    private List<MetricBucket> buckets(TimeRange range, long stepSeconds) {
        List<MetricBucket> buckets = new ArrayList<>();
        LocalDateTime cursor = range.startTime();
        while (!cursor.isAfter(range.endTime())) {
            buckets.add(new MetricBucket(cursor));
            cursor = cursor.plusSeconds(stepSeconds);
        }
        if (buckets.isEmpty()) {
            buckets.add(new MetricBucket(range.startTime()));
        }
        return buckets;
    }

    private java.util.Optional<MetricBucket> bucket(
            List<MetricBucket> buckets,
            TimeRange range,
            long stepSeconds,
            LocalDateTime time) {
        if (time == null || time.isBefore(range.startTime()) || time.isAfter(range.endTime())) {
            return java.util.Optional.empty();
        }
        int index = (int) (Duration.between(range.startTime(), time).getSeconds() / stepSeconds);
        if (index < 0 || index >= buckets.size()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(buckets.get(index));
    }

    private MonitoringSeries toSeries(String name, List<MetricBucket> buckets, Function<MetricBucket, Double> value) {
        List<MonitoringPoint> points = new ArrayList<>(buckets.size());
        for (MetricBucket bucket : buckets) {
            points.add(new MonitoringPoint(bucket.startTime, value.apply(bucket)));
        }
        return MonitoringSeries.builder()
                .name(name)
                .points(points)
                .build();
    }

    private Long taskDurationMillis(AppTask task) {
        if (task.getStartedAt() == null || task.getFinishedAt() == null) {
            return null;
        }
        long duration = Duration.between(task.getStartedAt(), task.getFinishedAt()).toMillis();
        return duration < 0 ? null : duration;
    }

    private Double rate(double part, double total) {
        if (total == 0) {
            return 0D;
        }
        return part / total;
    }

    private Double average(double total, double count) {
        if (count == 0) {
            return 0D;
        }
        return total / count;
    }

    private void addMetricQueries(MonitoringQueryRequest request, String prefix, List<String> keys) {
        for (String key : keys) {
            MonitoringMetricQuery query = new MonitoringMetricQuery();
            query.setQueryId(prefix + key);
            query.setKey(key);
            request.getMetricQueries().add(query);
        }
    }

    private List<MonitoringDashboardCard> toCards(
            Map<String, MonitoringMetricResult> metrics,
            String prefix,
            List<String> keys) {
        List<MonitoringDashboardCard> cards = new ArrayList<>(keys.size());
        for (String key : keys) {
            MonitoringMetricResult metric = metrics.get(prefix + key);
            if (metric != null) {
                cards.add(MonitoringDashboardCard.builder()
                        .key(metric.getKey())
                        .title(metric.getTitle())
                        .unit(metric.getUnit())
                        .source(metric.getSource())
                        .status(metric.getStatus())
                        .value(metric.getLatestValue())
                        .errorMessage(metric.getErrorMessage())
                        .build());
            }
        }
        return cards;
    }

    private List<MonitoringMetricResult> toMetrics(
            Map<String, MonitoringMetricResult> metrics,
            String prefix,
            List<String> keys) {
        List<MonitoringMetricResult> result = new ArrayList<>(keys.size());
        for (String key : keys) {
            MonitoringMetricResult metric = metrics.get(prefix + key);
            if (metric != null) {
                result.add(metric);
            }
        }
        return result;
    }

    private String overallStatus(List<MonitoringDashboardCard> systemCards) {
        boolean degraded = systemCards.stream()
                .anyMatch(card -> STATUS_UNAVAILABLE.equals(card.getStatus()));
        return degraded ? DASHBOARD_STATUS_DEGRADED : DASHBOARD_STATUS_UP;
    }

    private void validateFilters(Map<String, Object> filters, Set<String> allowedFilters) {
        if (filters == null || filters.isEmpty()) {
            return;
        }
        Set<String> unsupportedFilters = new LinkedHashSet<>(filters.keySet());
        unsupportedFilters.removeAll(allowedFilters);
        if (!unsupportedFilters.isEmpty()) {
            throw invalidParam("不支持的监控查询筛选项：" + String.join(",", unsupportedFilters));
        }
    }

    private String stringFilter(Map<String, Object> filters, String key) {
        Object value = filterValue(filters, key);
        String text = value == null ? null : String.valueOf(value);
        return StrUtil.isBlank(text) ? null : text;
    }

    private Integer integerFilter(Map<String, Object> filters, String key) {
        Object value = filterValue(filters, key);
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw invalidParam("非法整数筛选值：" + key);
        }
    }

    private Long longFilter(Map<String, Object> filters, String key) {
        Object value = filterValue(filters, key);
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw invalidParam("非法长整数筛选值：" + key);
        }
    }

    private Object filterValue(Map<String, Object> filters, String key) {
        return filters == null ? null : filters.get(key);
    }

    private String normalizeResource(String resource) {
        return resource.trim().toUpperCase(Locale.ROOT);
    }

    private BusinessException invalidParam(String message) {
        return BusinessException.of(ResultCode.INVALID_PARAM, message);
    }

    private record TimeRange(LocalDateTime startTime, LocalDateTime endTime) {
    }

    private static class MetricBucket {

        private final LocalDateTime startTime;
        private double value;
        private double total;

        private MetricBucket(LocalDateTime startTime) {
            this.startTime = startTime;
        }
    }

    private class TaskAccumulator {

        private final String taskType;
        private long total;
        private long pending;
        private long running;
        private long success;
        private long failed;
        private long canceled;
        private long durationTotal;
        private long durationCount;

        private TaskAccumulator(String taskType) {
            this.taskType = taskType;
        }

        private void add(AppTask task) {
            total++;
            if (AppTaskStatus.PENDING.name().equals(task.getStatus())) {
                pending++;
            } else if (AppTaskStatus.RUNNING.name().equals(task.getStatus())) {
                running++;
            } else if (AppTaskStatus.SUCCESS.name().equals(task.getStatus())) {
                success++;
            } else if (AppTaskStatus.FAILED.name().equals(task.getStatus())) {
                failed++;
            } else if (AppTaskStatus.CANCELED.name().equals(task.getStatus())) {
                canceled++;
            }

            Long duration = taskDurationMillis(task);
            if (duration != null) {
                durationTotal += duration;
                durationCount++;
            }
        }

        private TaskMonitoringStat toStat() {
            return TaskMonitoringStat.builder()
                    .taskType(taskType)
                    .total(total)
                    .pending(pending)
                    .running(running)
                    .success(success)
                    .failed(failed)
                    .canceled(canceled)
                    .successRate(rate(success, total))
                    .failedRate(rate(failed, total))
                    .averageDurationMillis(Math.round(average(durationTotal, durationCount)))
                    .build();
        }
    }
}
