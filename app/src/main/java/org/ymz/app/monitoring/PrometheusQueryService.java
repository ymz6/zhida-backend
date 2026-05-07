package org.ymz.app.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.ymz.app.config.AppDevConfig;
import org.ymz.app.model.dto.monitoring.MonitoringPoint;
import org.ymz.app.model.dto.monitoring.MonitoringSeries;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Prometheus HTTP API 查询服务。
 *
 * @author ymz
 */
@Service
public class PrometheusQueryService {

    private static final String SUCCESS_STATUS = "success";

    private final RestClient restClient;
    private final ZoneId zoneId;

    @Autowired
    public PrometheusQueryService(AppDevConfig appDevConfig, RestClient.Builder restClientBuilder) {
        this(restClientBuilder.baseUrl(appDevConfig.getPrometheusBaseUrl()).build(), ZoneId.systemDefault());
    }

    PrometheusQueryService(RestClient restClient, ZoneId zoneId) {
        this.restClient = restClient;
        this.zoneId = zoneId;
    }

    public Double queryInstant(String query, LocalDateTime time) {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query")
                        .queryParam("query", "{query}")
                        .queryParam("time", "{time}")
                        .build(query, toEpochSeconds(time)))
                .retrieve()
                .body(JsonNode.class);
        validateResponse(root);

        JsonNode results = root.path("data").path("result");
        if (!results.isArray() || results.size() == 0) {
            return null;
        }

        double total = 0;
        boolean hasValue = false;
        for (JsonNode result : results) {
            JsonNode value = result.path("value");
            if (value.isArray() && value.size() >= 2) {
                total += value.path(1).asDouble();
                hasValue = true;
            }
        }
        return hasValue ? total : null;
    }

    public List<MonitoringSeries> queryRange(
            String query,
            LocalDateTime startTime,
            LocalDateTime endTime,
            long stepSeconds
    ) {
        JsonNode root = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/query_range")
                        .queryParam("query", "{query}")
                        .queryParam("start", "{start}")
                        .queryParam("end", "{end}")
                        .queryParam("step", "{step}")
                        .build(query, toEpochSeconds(startTime), toEpochSeconds(endTime), stepSeconds))
                .retrieve()
                .body(JsonNode.class);
        validateResponse(root);

        JsonNode results = root.path("data").path("result");
        if (!results.isArray() || results.size() == 0) {
            return List.of();
        }

        List<MonitoringSeries> seriesList = new ArrayList<>(results.size());
        for (JsonNode result : results) {
            MonitoringSeries series = new MonitoringSeries();
            series.setName(seriesName(result.path("metric")));
            JsonNode values = result.path("values");
            if (values.isArray()) {
                for (JsonNode value : values) {
                    if (value.isArray() && value.size() >= 2) {
                        long epochSeconds = value.path(0).asLong();
                        series.getPoints().add(new MonitoringPoint(
                                LocalDateTime.ofInstant(java.time.Instant.ofEpochSecond(epochSeconds), zoneId),
                                value.path(1).asDouble()
                        ));
                    }
                }
            }
            seriesList.add(series);
        }
        return seriesList;
    }

    private void validateResponse(JsonNode root) {
        if (root == null || !SUCCESS_STATUS.equals(root.path("status").asText())) {
            throw new IllegalStateException("Prometheus 查询失败");
        }
    }

    private long toEpochSeconds(LocalDateTime time) {
        return time.atZone(zoneId).toEpochSecond();
    }

    private String seriesName(JsonNode metric) {
        if (metric == null || metric.isMissingNode()) {
            return "value";
        }

        var properties = metric.properties();
        if (properties.isEmpty()) {
            return "value";
        }

        List<String> labels = new ArrayList<>();
        for (Map.Entry<String, JsonNode> field : properties) {
            labels.add(field.getKey() + "=" + field.getValue().asText());
        }
        return String.join(",", labels);
    }
}
