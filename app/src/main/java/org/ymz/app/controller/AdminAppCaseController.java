package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ymz.app.model.dto.appcase.AdminAppCaseInfo;
import org.ymz.app.model.dto.appcase.AdminUpdateAppCaseRequest;
import org.ymz.app.model.dto.appcase.ListAdminAppCasesRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.security.AdminRequired;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.service.AppCaseSquareService;
import org.ymz.app.web.response.Response;

/**
 * 管理员案例管理模块。
 *
 * @author ymz
 */
@Tag(name = "admin-app-case")
@AdminRequired
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/cases")
public class AdminAppCaseController {

    private final AppCaseSquareService appCaseSquareService;

    @GetMapping
    @Operation(operationId = "listAdminCases")
    public Response<PageResult<AdminAppCaseInfo>> listAdminCases(@Validated ListAdminAppCasesRequest request) {
        return Response.ok(appCaseSquareService.listAdminCases(request));
    }

    @PatchMapping("/{caseId}")
    @Operation(operationId = "updateAdminCase")
    public Response<AdminAppCaseInfo> updateAdminCase(
            @PathVariable Long caseId,
            @RequestBody @Valid AdminUpdateAppCaseRequest request
    ) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appCaseSquareService.updateAdminCase(authContext.getUserId(), caseId, request));
    }
}
