package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.ymz.app.model.dto.app.AppVO;
import org.ymz.app.model.dto.page.PageQuery;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.service.AppQueryService;
import org.ymz.app.web.response.Response;

/**
 * 案例广场模块。
 *
 * @author ymz
 */
@Tag(name = "case")
@RestController
@RequiredArgsConstructor
@RequestMapping("/cases")
public class CaseController {

    private final AppQueryService appQueryService;

    @GetMapping
    @Operation(operationId = "listCases")
    public Response<PageResult<AppVO>> listCases(@Validated PageQuery request) {
        return Response.ok(appQueryService.listCases(request));
    }

    @GetMapping("/featured")
    @Operation(operationId = "listFeaturedCases")
    public Response<PageResult<AppVO>> listFeaturedCases(@Validated PageQuery request) {
        return Response.ok(appQueryService.listFeaturedCases(request));
    }

    // 案例详情使用应用 ID 查询，但只返回已公开的应用，区别于 /apps/{appId} 的应用管理详情。
    @GetMapping("/{appId}")
    @Operation(operationId = "getCase")
    public Response<AppVO> getCase(@PathVariable Long appId) {
        return Response.ok(appQueryService.getCase(appId));
    }
}
