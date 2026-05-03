package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ymz.app.model.dto.monitoring.MonitoringDashboard;
import org.ymz.app.model.dto.monitoring.MonitoringDashboardRequest;
import org.ymz.app.model.dto.monitoring.MonitoringQueryRequest;
import org.ymz.app.model.dto.monitoring.MonitoringQueryResult;
import org.ymz.app.security.AdminRequired;
import org.ymz.app.service.AdminMonitoringService;
import org.ymz.app.web.response.Response;

/**
 * 运行与用量监控管理接口。
 *
 * @author ymz
 */
@Tag(name = "admin-monitoring")
@AdminRequired
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/monitoring")
public class AdminMonitoringController {

    private final AdminMonitoringService adminMonitoringService;

    @GetMapping("/dashboard")
    @Operation(operationId = "getMonitoringDashboard")
    public Response<MonitoringDashboard> getMonitoringDashboard(@Validated MonitoringDashboardRequest request) {
        return Response.ok(adminMonitoringService.getDashboard(request));
    }

    @PostMapping("/query")
    @Operation(operationId = "queryMonitoring")
    public Response<MonitoringQueryResult> queryMonitoring(@Valid @RequestBody MonitoringQueryRequest request) {
        return Response.ok(adminMonitoringService.query(request));
    }
}
