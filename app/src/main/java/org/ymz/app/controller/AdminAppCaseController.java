package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.ymz.app.model.dto.app.AppVO;
import org.ymz.app.model.dto.app.ListAdminAppCasesRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.security.AdminRequired;
import org.ymz.app.service.AppAuditService;
import org.ymz.app.web.response.Response;

/**
 * 应用案例管理模块。
 *
 * @author ymz
 */
@Tag(name = "admin-app-case")
@AdminRequired
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/app-cases")
public class AdminAppCaseController {

    private final AppAuditService appAuditService;

    @GetMapping
    @Operation(operationId = "listAdminAppCases")
    public Response<PageResult<AppVO>> listAdminAppCases(@Validated ListAdminAppCasesRequest request) {
        return Response.ok(appAuditService.listAdminAppCases(request));
    }

    @GetMapping("/{appId}")
    @Operation(operationId = "getAdminAppCase")
    public Response<AppVO> getAdminAppCase(@PathVariable Long appId) {
        return Response.ok(appAuditService.getAdminAppCase(appId));
    }
}
