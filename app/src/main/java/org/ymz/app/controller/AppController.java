package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ymz.app.model.dto.app.CreateAppRequest;
import org.ymz.app.model.dto.app.CreateAppResponse;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.service.AppCreationService;
import org.ymz.app.web.response.Response;

/**
 * 应用生成管理模块
 *
 * @author ymz
 */
@Tag(name = "app")
@LoginRequired
@RestController
@RequiredArgsConstructor
@RequestMapping("/apps")
public class AppController {

    private final AppCreationService appCreationService;

    @PostMapping
    @Operation(operationId = "createApp")
    public Response<CreateAppResponse> createApp(@RequestBody @Valid CreateAppRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(appCreationService.createApp(authContext.getUserId(), request));
    }
}
