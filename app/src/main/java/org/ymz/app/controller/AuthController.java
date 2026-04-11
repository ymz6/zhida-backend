package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ymz.app.model.dto.auth.LoginRequest;
import org.ymz.app.model.dto.auth.LoginResponse;
import org.ymz.app.model.dto.auth.RegisterRequest;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.service.AuthService;
import org.ymz.app.web.response.Response;

/**
 * 用户认证
 * @author ymz
 */
@Tag(name = "auth")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @Operation(operationId = "register")
    public Response<Void> register(@RequestBody @Valid RegisterRequest request) {
        authService.register(request);
        return Response.ok();
    }

    @PostMapping("/login")
    @Operation(operationId = "login")
    public Response<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return Response.ok(authService.login(request));
    }

    @LoginRequired
    @PostMapping("/logout")
    @Operation(operationId = "logout")
    public Response<Void> logout() {
        AuthContext authContext = AuthContextHolder.get();
        authService.logout(authContext);
        return Response.ok();
    }

}
