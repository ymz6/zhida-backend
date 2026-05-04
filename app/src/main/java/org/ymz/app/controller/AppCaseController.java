package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ymz.app.model.dto.appcase.AppCaseDetail;
import org.ymz.app.model.dto.appcase.AppCaseSummary;
import org.ymz.app.model.dto.appcase.ListMyAppCasesRequest;
import org.ymz.app.model.dto.appcase.ListPublicAppCasesRequest;
import org.ymz.app.model.dto.appcase.MyAppCaseInfo;
import org.ymz.app.model.dto.appcase.SubmitAppCaseRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.service.AppCaseSquareService;
import org.ymz.app.web.response.Response;

/**
 * 案例广场模块。
 *
 * @author ymz
 */
@Tag(name = "app-case")
@RestController
@RequiredArgsConstructor
@RequestMapping("/cases")
public class AppCaseController {

    private final AppCaseSquareService appCaseSquareService;

    @GetMapping
    @Operation(operationId = "listPublicCases")
    public Response<PageResult<AppCaseSummary>> listPublicCases(@Validated ListPublicAppCasesRequest request) {
        return Response.ok(appCaseSquareService.listPublicCases(request));
    }

    @GetMapping("/mine")
    @LoginRequired
    @Operation(operationId = "listMyCases")
    public Response<PageResult<MyAppCaseInfo>> listMyCases(@Validated ListMyAppCasesRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appCaseSquareService.listMyCases(authContext.getUserId(), request));
    }

    @GetMapping("/{caseId}")
    @Operation(operationId = "getPublicCase")
    public Response<AppCaseDetail> getPublicCase(@PathVariable Long caseId) {
        return Response.ok(appCaseSquareService.getPublicCase(caseId));
    }

    @PostMapping
    @LoginRequired
    @Operation(operationId = "submitCase")
    public Response<MyAppCaseInfo> submitCase(@RequestBody @Valid SubmitAppCaseRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appCaseSquareService.submitCase(authContext, request));
    }
}
