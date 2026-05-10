package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ymz.app.model.dto.monitoring.ListLlmCallsRequest;
import org.ymz.app.model.dto.monitoring.LlmCallLogInfo;
import org.ymz.app.model.dto.monitoring.LlmMonitoringOverview;
import org.ymz.app.model.dto.monitoring.LlmMonitoringOverviewRequest;
import org.ymz.app.model.dto.monitoring.LlmTokenUsageTrend;
import org.ymz.app.model.dto.monitoring.LlmTokenUsageTrendRequest;
import org.ymz.app.model.dto.page.PageResult;
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

    @GetMapping("/overview")
    @Operation(operationId = "getLlmMonitoringOverview")
    public Response<LlmMonitoringOverview> getLlmMonitoringOverview(@Validated LlmMonitoringOverviewRequest request) {
        return Response.ok(adminMonitoringService.getOverview(request));
    }

    @GetMapping("/token-usage-trend")
    @Operation(operationId = "getLlmTokenUsageTrend")
    public Response<LlmTokenUsageTrend> getLlmTokenUsageTrend(@Validated LlmTokenUsageTrendRequest request) {
        return Response.ok(adminMonitoringService.getTokenUsageTrend(request));
    }

    @GetMapping("/llm-calls")
    @Operation(operationId = "listLlmCalls")
    public Response<PageResult<LlmCallLogInfo>> listLlmCalls(@Validated ListLlmCallsRequest request) {
        return Response.ok(adminMonitoringService.listLlmCalls(request));
    }
}
