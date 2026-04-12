package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.ymz.app.model.dto.profile.UpdateProfileRequest;
import org.ymz.app.model.dto.user.UserInfo;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.service.ProfileService;
import org.ymz.app.web.response.Response;

/**
 * 用户个人信息管理模块
 * @author ymz
 */
@Tag(name = "profile")
@LoginRequired
@RestController
@RequiredArgsConstructor
@RequestMapping("/profile")
public class ProfileController {
    private final ProfileService profileService;

    @GetMapping
    @Operation(operationId = "getProfile")
    public Response<UserInfo> getProfile() {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(profileService.getProfile(authContext.getUserId()));
    }

    @PutMapping
    @Operation(operationId = "updateProfile")
    public Response<UserInfo> updateProfile(@RequestBody @Valid UpdateProfileRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(profileService.updateProfile(authContext.getUserId(), request));
    }

    @Operation(operationId = "changeAvatar")
    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<UserInfo> changeAvatar(@RequestParam("file") MultipartFile file) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(profileService.changeAvatar(authContext.getUserId(), file));
    }
}
