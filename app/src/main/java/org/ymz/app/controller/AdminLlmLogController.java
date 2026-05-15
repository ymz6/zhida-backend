package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ymz.app.model.dto.monitoring.ListLlmLogsRequest;
import org.ymz.app.model.dto.monitoring.LlmLogOverviewRequest;
import org.ymz.app.model.dto.monitoring.LlmLogOverviewVO;
import org.ymz.app.model.dto.monitoring.LlmLogVO;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.security.AdminRequired;
import org.ymz.app.service.LlmMonitoringService;
import org.ymz.app.web.response.Response;

/**
 * LLM 调用监控模块。
 *
 * @author ymz
 */
@Tag(name = "admin-llm-log")
@AdminRequired
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/llm-logs")
public class AdminLlmLogController {

    private final LlmMonitoringService llmMonitoringService;

    @GetMapping("/overview")
    @Operation(operationId = "getLlmLogOverview")
    public Response<LlmLogOverviewVO> getLlmLogOverview(@Validated LlmLogOverviewRequest request) {
        return Response.ok(llmMonitoringService.getOverview(request));
    }

    @GetMapping
    @Operation(operationId = "listLlmLogs")
    public Response<PageResult<LlmLogVO>> listLlmLogs(@Validated ListLlmLogsRequest request) {
        return Response.ok(llmMonitoringService.listLlmLogs(request));
    }
}
