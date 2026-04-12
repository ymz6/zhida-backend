package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ymz.app.model.dto.admin.ListUsersRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.dto.user.UserInfo;
import org.ymz.app.security.AdminRequired;
import org.ymz.app.service.AdminUserService;
import org.ymz.app.web.response.Response;

/**
 * 用户管理模块
 * @author ymz
 */
@Tag(name = "admin-user")
@AdminRequired
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
public class AdminUserController {
    private final AdminUserService adminUserService;

    @Operation(operationId = "listUsers")
    @GetMapping
    public Response<PageResult<UserInfo>> listUsers(@Validated ListUsersRequest request) {
        return Response.ok(adminUserService.queryUserList(request));
    }
}
