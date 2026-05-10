package org.ymz.app.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.If;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ymz.app.converter.MonitoringConverter;
import org.ymz.app.model.dto.monitoring.ListLlmCallsRequest;
import org.ymz.app.model.dto.monitoring.LlmCallLogInfo;
import org.ymz.app.model.dto.monitoring.LlmMonitoringOverview;
import org.ymz.app.model.dto.monitoring.LlmMonitoringOverviewRequest;
import org.ymz.app.model.dto.monitoring.LlmTokenUsageTrend;
import org.ymz.app.model.dto.monitoring.LlmTokenUsageTrendPoint;
import org.ymz.app.model.dto.monitoring.LlmTokenUsageTrendRequest;
import org.ymz.app.model.dto.monitoring.LlmTokenUsageTrendSeries;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.entity.LlmCallLog;
import org.ymz.app.model.enums.monitoring.LlmCallStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.ymz.app.model.entity.table.LlmCallLogTableDef.LLM_CALL_LOG;

/**
 * 管理端 LLM 调用监控服务。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AdminMonitoringService {

    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final int TARGET_SERIES_POINTS = 240;
    private static final long MIN_STEP_SECONDS = 60;

    private final LlmCallLogService llmCallLogService;
    private final MonitoringConverter monitoringConverter;

    public LlmMonitoringOverview getOverview(LlmMonitoringOverviewRequest request) {
        TimeRange range = timeRange(request.getStartTime(), request.getEndTime());
        List<LlmCallLog> logs = llmCallLogService.list(baseLlmQuery(range));
        OverviewAccumulator accumulator = new OverviewAccumulator();
        for (LlmCallLog log : logs) {
            accumulator.add(log);
        }
        return accumulator.toOverview(range);
    }

    public LlmTokenUsageTrend getTokenUsageTrend(LlmTokenUsageTrendRequest request) {
        TimeRange range = timeRange(request.getStartTime(), request.getEndTime());
        long stepSeconds = stepSeconds(request.getStepSeconds(), range);
        List<MetricBucket> buckets = buckets(range, stepSeconds);
        List<LlmCallLog> logs = llmCallLogService.list(baseLlmQuery(range));
        for (LlmCallLog log : logs) {
            bucket(buckets, range, stepSeconds, log.getCreatedAt()).ifPresent(item -> item.add(log));
        }

        return LlmTokenUsageTrend.builder()
                .startTime(range.startTime())
                .endTime(range.endTime())
                .stepSeconds(stepSeconds)
                .series(List.of(
                        toSeries("inputTokens", buckets, item -> (double) item.inputTokens),
                        toSeries("outputTokens", buckets, item -> (double) item.outputTokens),
                        toSeries("totalTokens", buckets, item -> (double) item.totalTokens)
                ))
                .build();
    }

    public PageResult<LlmCallLogInfo> listLlmCalls(ListLlmCallsRequest request) {
        TimeRange range = timeRange(request.getStartTime(), request.getEndTime());
        QueryWrapper wrapper = baseLlmQuery(range)
                .and(LLM_CALL_LOG.SCENARIO.eq(request.getScenario(), If::hasText))
                .and(LLM_CALL_LOG.MODEL_NAME.eq(request.getModelName(), If::hasText))
                .and(LLM_CALL_LOG.STATUS.eq(request.getStatus(), If::hasText))
                .and(LLM_CALL_LOG.FINISH_REASON.eq(request.getFinishReason(), If::hasText))
                .and(LLM_CALL_LOG.ERROR_TYPE.like(request.getErrorType(), If::hasText))
                .and(LLM_CALL_LOG.APP_ID.eq(request.getAppId(), If::notNull))
                .and(LLM_CALL_LOG.TASK_ID.eq(request.getTaskId(), If::notNull))
                .orderBy(LLM_CALL_LOG.CREATED_AT.desc())
                .orderBy(LLM_CALL_LOG.ID.desc());
        Page<LlmCallLog> page = llmCallLogService.page(request.toPage(), wrapper);
        return PageResult.of(page, monitoringConverter::toLlmCallLogInfo);
    }

    private QueryWrapper baseLlmQuery(TimeRange range) {
        return QueryWrapper.create()
                .select(LLM_CALL_LOG.ALL_COLUMNS)
                .from(LLM_CALL_LOG)
                .where(LLM_CALL_LOG.CREATED_AT.ge(range.startTime()))
                .and(LLM_CALL_LOG.CREATED_AT.le(range.endTime()));
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

    private Optional<MetricBucket> bucket(
            List<MetricBucket> buckets,
            TimeRange range,
            long stepSeconds,
            LocalDateTime time) {
        if (time == null || time.isBefore(range.startTime()) || time.isAfter(range.endTime())) {
            return Optional.empty();
        }
        int index = (int) (Duration.between(range.startTime(), time).getSeconds() / stepSeconds);
        if (index < 0 || index >= buckets.size()) {
            return Optional.empty();
        }
        return Optional.of(buckets.get(index));
    }

    private LlmTokenUsageTrendSeries toSeries(
            String name,
            List<MetricBucket> buckets,
            Function<MetricBucket, Double> value) {
        List<LlmTokenUsageTrendPoint> points = new ArrayList<>(buckets.size());
        for (MetricBucket bucket : buckets) {
            points.add(new LlmTokenUsageTrendPoint(bucket.startTime, value.apply(bucket)));
        }
        return LlmTokenUsageTrendSeries.builder()
                .name(name)
                .points(points)
                .build();
    }

    private double rate(double part, double total) {
        if (total == 0) {
            return 0D;
        }
        return part / total;
    }

    private long value(Long value) {
        return value == null ? 0 : value;
    }

    private record TimeRange(LocalDateTime startTime, LocalDateTime endTime) {
    }

    private class OverviewAccumulator {

        private long callTotal;
        private long success;
        private long errorCount;
        private long durationTotal;
        private long durationCount;
        private long inputTokens;
        private long outputTokens;
        private long totalTokens;

        private void add(LlmCallLog log) {
            callTotal++;
            if (LlmCallStatus.SUCCESS.name().equals(log.getStatus())) {
                success++;
            } else {
                errorCount++;
            }
            if (log.getDurationMillis() != null) {
                durationTotal += log.getDurationMillis();
                durationCount++;
            }
            inputTokens += value(log.getInputTokens());
            outputTokens += value(log.getOutputTokens());
            totalTokens += value(log.getTotalTokens());
        }

        private LlmMonitoringOverview toOverview(TimeRange range) {
            return LlmMonitoringOverview.builder()
                    .startTime(range.startTime())
                    .endTime(range.endTime())
                    .callTotal(callTotal)
                    .successRate(rate(success, callTotal))
                    .averageDurationMillis(Math.round(rate(durationTotal, durationCount)))
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .totalTokens(totalTokens)
                    .errorCount(errorCount)
                    .build();
        }
    }

    private class MetricBucket {

        private final LocalDateTime startTime;
        private long inputTokens;
        private long outputTokens;
        private long totalTokens;

        private MetricBucket(LocalDateTime startTime) {
            this.startTime = startTime;
        }

        private void add(LlmCallLog log) {
            inputTokens += value(log.getInputTokens());
            outputTokens += value(log.getOutputTokens());
            totalTokens += value(log.getTotalTokens());
        }
    }
}
