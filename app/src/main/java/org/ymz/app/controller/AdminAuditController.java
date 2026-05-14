package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.ymz.app.model.dto.audit.AuditRecordVO;
import org.ymz.app.model.dto.audit.ListAuditRecordsRequest;
import org.ymz.app.model.dto.audit.ReviewAuditRequest;
import org.ymz.app.model.dto.audit.SetFeaturedRequest;
import org.ymz.app.model.dto.audit.SwitchAuditStatusRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.security.AdminRequired;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.service.AppAuditService;
import org.ymz.app.web.response.Response;

/**
 * 案例审核管理模块。
 *
 * @author ymz
 */
@Tag(name = "admin-audit")
@AdminRequired
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin")
public class AdminAuditController {

    private final AppAuditService appAuditService;

    @GetMapping("/audits")
    @Operation(operationId = "listAudits")
    public Response<PageResult<AuditRecordVO>> listAudits(@Validated ListAuditRecordsRequest request) {
        return Response.ok(appAuditService.listAdminAuditRecords(request));
    }

    @PutMapping("/audits/{recordId}")
    @Operation(operationId = "reviewAudit")
    public Response<Void> reviewAudit(
            @PathVariable Long recordId,
            @RequestBody @Valid ReviewAuditRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        appAuditService.reviewPendingAudit(authContext, recordId, request);
        return Response.ok();
    }

    @PutMapping("/apps/{appId}/audit-status")
    @Operation(operationId = "switchAppAuditStatus")
    public Response<Void> switchAppAuditStatus(
            @PathVariable Long appId,
            @RequestBody @Valid SwitchAuditStatusRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        appAuditService.switchAuditStatus(authContext, appId, request);
        return Response.ok();
    }

    @PutMapping("/apps/{appId}/featured")
    @Operation(operationId = "setAppFeatured")
    public Response<Void> setAppFeatured(
            @PathVariable Long appId,
            @RequestBody @Valid SetFeaturedRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        appAuditService.setFeatured(authContext, appId, request);
        return Response.ok();
    }
}
