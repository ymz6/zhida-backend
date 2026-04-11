package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.web.response.Response;

/**
 * 健康检查
 * @author ymz
 */
@Hidden
@RestController
public class HealthCheckController {

    @LoginRequired
    @GetMapping("/")
    public Response<String> checkHealth() {
        return Response.ok("ok");
    }
}
