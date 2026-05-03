package org.ymz.app.monitoring;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.ymz.app.model.dto.monitoring.MonitoringSeries;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PrometheusQueryServiceTest {

    @Test
    void queryInstantParsesVectorValue() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(containsString("/api/v1/query?")))
                .andRespond(withSuccess("""
                        {
                          "status": "success",
                          "data": {
                            "resultType": "vector",
                            "result": [
                              {
                                "metric": {},
                                "value": [1710000000, "3.5"]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Double value = fixture.service.queryInstant("up", LocalDateTime.of(2026, 5, 1, 0, 0));

        assertEquals(3.5D, value);
        fixture.server.verify();
    }

    @Test
    void queryInstantReturnsNullForEmptyVector() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(containsString("/api/v1/query?")))
                .andRespond(withSuccess("""
                        {
                          "status": "success",
                          "data": {
                            "resultType": "vector",
                            "result": []
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Double value = fixture.service.queryInstant("up", LocalDateTime.of(2026, 5, 1, 0, 0));

        assertNull(value);
        fixture.server.verify();
    }

    @Test
    void queryInstantEncodesPromqlLabelMatchers() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(containsString("/api/v1/query?")))
                .andRespond(withSuccess("""
                        {
                          "status": "success",
                          "data": {
                            "resultType": "vector",
                            "result": [
                              {
                                "metric": {},
                                "value": [1710000000, "0"]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Double value = fixture.service.queryInstant(
                "sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[5m])) or vector(0)",
                LocalDateTime.of(2026, 5, 1, 0, 0)
        );

        assertEquals(0D, value);
        fixture.server.verify();
    }

    @Test
    void queryRangeParsesMatrixValues() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(containsString("/api/v1/query_range?")))
                .andRespond(withSuccess("""
                        {
                          "status": "success",
                          "data": {
                            "resultType": "matrix",
                            "result": [
                              {
                                "metric": {"job": "app"},
                                "values": [
                                  [1710000000, "1"],
                                  [1710000060, "2"]
                                ]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        List<MonitoringSeries> series = fixture.service.queryRange(
                "up",
                LocalDateTime.of(2026, 5, 1, 0, 0),
                LocalDateTime.of(2026, 5, 1, 0, 1),
                60
        );

        assertEquals(1, series.size());
        assertEquals("job=app", series.getFirst().getName());
        assertEquals(2, series.getFirst().getPoints().size());
        assertEquals(1D, series.getFirst().getPoints().getFirst().getValue());
        fixture.server.verify();
    }

    @Test
    void prometheusErrorStatusThrows() {
        Fixture fixture = fixture();
        fixture.server.expect(requestTo(containsString("/api/v1/query?")))
                .andRespond(withSuccess("""
                        {
                          "status": "error",
                          "error": "bad query"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThrows(
                IllegalStateException.class,
                () -> fixture.service.queryInstant("bad", LocalDateTime.of(2026, 5, 1, 0, 0))
        );
        fixture.server.verify();
    }

    private Fixture fixture() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://prometheus");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PrometheusQueryService service = new PrometheusQueryService(builder.build(), ZoneId.of("UTC"));
        return new Fixture(service, server);
    }

    private record Fixture(PrometheusQueryService service, MockRestServiceServer server) {
    }
}
