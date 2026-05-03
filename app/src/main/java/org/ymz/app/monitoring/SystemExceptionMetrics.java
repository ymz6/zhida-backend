package org.ymz.app.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 系统异常 Prometheus 指标记录器。
 *
 * @author ymz
 */
@Component
@RequiredArgsConstructor
public class SystemExceptionMetrics {

    private final MeterRegistry meterRegistry;

    public void record(String exceptionType, int resultCode, String requestPath) {
        Counter.builder("zhida.system.exception")
                .tag("exception_type", tag(exceptionType))
                .tag("result_code", String.valueOf(resultCode))
                .tag("request_path", tag(requestPath))
                .register(meterRegistry)
                .increment();
    }

    private String tag(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}
