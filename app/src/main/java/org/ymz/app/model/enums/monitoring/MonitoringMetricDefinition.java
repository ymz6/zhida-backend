package org.ymz.app.model.enums.monitoring;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * 统一监控指标白名单。
 *
 * @author ymz
 */
@Getter
public enum MonitoringMetricDefinition {

    SYSTEM_JVM_MEMORY_USED(
            "system.jvm.memory.used",
            "JVM 内存使用量",
            "bytes",
            MonitoringMetricSource.PROMETHEUS,
            "sum(jvm_memory_used_bytes)"
    ),
    SYSTEM_JVM_MEMORY_MAX(
            "system.jvm.memory.max",
            "JVM 最大内存",
            "bytes",
            MonitoringMetricSource.PROMETHEUS,
            "sum(jvm_gc_max_data_size_bytes)"
    ),
    SYSTEM_DISK_FREE(
            "system.disk.free",
            "磁盘剩余空间",
            "bytes",
            MonitoringMetricSource.PROMETHEUS,
            "sum(disk_free_bytes)"
    ),
    SYSTEM_DISK_TOTAL(
            "system.disk.total",
            "磁盘总空间",
            "bytes",
            MonitoringMetricSource.PROMETHEUS,
            "sum(disk_total_bytes)"
    ),
    SYSTEM_HTTP_REQUEST_RATE(
            "system.http.request.rate",
            "HTTP 请求速率",
            "requests/s",
            MonitoringMetricSource.PROMETHEUS,
            "sum(rate(http_server_requests_seconds_count[5m]))"
    ),
    SYSTEM_HTTP_ERROR_RATE(
            "system.http.error.rate",
            "HTTP 错误率",
            "ratio",
            MonitoringMetricSource.PROMETHEUS,
            "(sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[5m])) or vector(0)) / clamp_min((sum(rate(http_server_requests_seconds_count[5m])) or vector(0)), 0.001)"
    ),
    SYSTEM_HTTP_DURATION_AVG(
            "system.http.duration.avg",
            "HTTP 平均耗时",
            "seconds",
            MonitoringMetricSource.PROMETHEUS,
            "sum(rate(http_server_requests_seconds_sum[5m])) / clamp_min(sum(rate(http_server_requests_seconds_count[5m])), 0.001)"
    ),
    SYSTEM_DB_CONNECTIONS_ACTIVE(
            "system.db.connections.active",
            "数据库活跃连接",
            "connections",
            MonitoringMetricSource.PROMETHEUS,
            "sum(hikaricp_connections_active)"
    ),
    BUSINESS_TASK_TOTAL(
            "business.task.total",
            "任务总数",
            "count",
            MonitoringMetricSource.MYSQL,
            null
    ),
    BUSINESS_TASK_SUCCESS_RATE(
            "business.task.success_rate",
            "任务成功率",
            "ratio",
            MonitoringMetricSource.MYSQL,
            null
    ),
    BUSINESS_TASK_AVERAGE_DURATION(
            "business.task.average_duration",
            "任务平均耗时",
            "ms",
            MonitoringMetricSource.MYSQL,
            null
    ),
    EXCEPTION_COUNT(
            "exception.count",
            "异常数量",
            "count",
            MonitoringMetricSource.MYSQL,
            null
    ),
    LLM_CALL_TOTAL(
            "llm.call.total",
            "LLM 调用次数",
            "count",
            MonitoringMetricSource.MYSQL,
            null
    ),
    LLM_TOKEN_TOTAL(
            "llm.token.total",
            "LLM Token 总量",
            "tokens",
            MonitoringMetricSource.MYSQL,
            null
    ),
    LLM_DURATION_AVG(
            "llm.duration.avg",
            "LLM 平均耗时",
            "ms",
            MonitoringMetricSource.MYSQL,
            null
    );

    private final String key;
    private final String title;
    private final String unit;
    private final MonitoringMetricSource source;
    private final String prometheusQuery;

    MonitoringMetricDefinition(
            String key,
            String title,
            String unit,
            MonitoringMetricSource source,
            String prometheusQuery
    ) {
        this.key = key;
        this.title = title;
        this.unit = unit;
        this.source = source;
        this.prometheusQuery = prometheusQuery;
    }

    public static Optional<MonitoringMetricDefinition> fromKey(String key) {
        return Arrays.stream(values())
                .filter(definition -> definition.key.equals(key))
                .findFirst();
    }
}
