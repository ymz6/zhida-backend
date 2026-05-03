package org.ymz.app.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 应用任务 Prometheus 指标记录器。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class AppTaskMetrics {

    private final MeterRegistry meterRegistry;

    public void recordCreated(AppTaskType taskType) {
        Counter.builder("zhida.app.task.created")
                .tag("task_type", tag(taskType == null ? null : taskType.name()))
                .register(meterRegistry)
                .increment();
    }

    public void recordStarted(AppTask task) {
        Counter.builder("zhida.app.task.started")
                .tag("task_type", tag(task == null ? null : task.getTaskType()))
                .register(meterRegistry)
                .increment();
    }

    public void recordCompleted(AppTask task, AppTaskStatus status, LocalDateTime finishedAt) {
        String taskType = tag(task == null ? null : task.getTaskType());
        String taskStatus = tag(status == null ? null : status.name());
        Counter.builder("zhida.app.task.completed")
                .tag("task_type", taskType)
                .tag("status", taskStatus)
                .register(meterRegistry)
                .increment();

        if (task == null || task.getStartedAt() == null || finishedAt == null) {
            return;
        }
        long durationMillis = Duration.between(task.getStartedAt(), finishedAt).toMillis();
        if (durationMillis < 0) {
            return;
        }
        Timer.builder("zhida.app.task.duration")
                .tag("task_type", taskType)
                .tag("status", taskStatus)
                .register(meterRegistry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    private String tag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
