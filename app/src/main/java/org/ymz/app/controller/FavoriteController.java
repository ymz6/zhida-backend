package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.ymz.app.model.dto.app.AppVO;
import org.ymz.app.model.dto.favorite.AddFavoriteAppRequest;
import org.ymz.app.model.dto.favorite.CreateFavoriteRequest;
import org.ymz.app.model.dto.favorite.FavoriteStatusVO;
import org.ymz.app.model.dto.favorite.FavoriteVO;
import org.ymz.app.model.dto.favorite.ListFavoriteAppsRequest;
import org.ymz.app.model.dto.favorite.MoveFavoriteAppRequest;
import org.ymz.app.model.dto.favorite.SortFavoritesRequest;
import org.ymz.app.model.dto.favorite.UpdateFavoriteRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.security.AuthContext;
import org.ymz.app.security.AuthContextHolder;
import org.ymz.app.security.LoginRequired;
import org.ymz.app.service.FavoriteService;
import org.ymz.app.web.response.Response;

import java.util.List;

/**
 * 案例收藏模块。
 *
 * @author ymz
 */
@Tag(name = "favorite")
@LoginRequired
@RestController
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping("/favorites")
    @Operation(operationId = "createFavorite")
    public Response<FavoriteVO> createFavorite(@RequestBody @Valid CreateFavoriteRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(favoriteService.createFavorite(authContext.getUserId(), request));
    }

    @GetMapping("/favorites")
    @Operation(operationId = "listFavorites")
    public Response<List<FavoriteVO>> listFavorites() {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(favoriteService.listFavorites(authContext.getUserId()));
    }

    @GetMapping("/favorites/{favoriteId}")
    @Operation(operationId = "getFavorite")
    public Response<FavoriteVO> getFavorite(@PathVariable Long favoriteId) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(favoriteService.getFavorite(authContext.getUserId(), favoriteId));
    }

    @PutMapping("/favorites/{favoriteId}")
    @Operation(operationId = "updateFavorite")
    public Response<FavoriteVO> updateFavorite(
            @PathVariable Long favoriteId,
            @RequestBody @Valid UpdateFavoriteRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(favoriteService.updateFavorite(authContext.getUserId(), favoriteId, request));
    }

    @DeleteMapping("/favorites/{favoriteId}")
    @Operation(operationId = "deleteFavorite")
    public Response<Void> deleteFavorite(@PathVariable Long favoriteId) {
        AuthContext authContext = AuthContextHolder.get();
        favoriteService.deleteFavorite(authContext.getUserId(), favoriteId);
        return Response.ok();
    }

    @PutMapping("/favorites/sort")
    @Operation(operationId = "sortFavorites")
    public Response<Void> sortFavorites(@RequestBody @Valid SortFavoritesRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        favoriteService.sortFavorites(authContext.getUserId(), request);
        return Response.ok();
    }

    @PostMapping("/apps/{appId}/favorite")
    @Operation(operationId = "addFavoriteApp")
    public Response<Void> addFavoriteApp(
            @PathVariable Long appId,
            @RequestBody(required = false) @Valid AddFavoriteAppRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        favoriteService.addFavoriteApp(authContext.getUserId(), appId, request);
        return Response.ok();
    }

    @GetMapping("/apps/{appId}/favorite-status")
    @Operation(operationId = "getFavoriteStatus")
    public Response<FavoriteStatusVO> getFavoriteStatus(@PathVariable Long appId) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(favoriteService.getFavoriteStatus(authContext.getUserId(), appId));
    }

    @DeleteMapping("/favorites/{favoriteId}/apps/{appId}")
    @Operation(operationId = "removeFavoriteApp")
    public Response<Void> removeFavoriteApp(@PathVariable Long favoriteId, @PathVariable Long appId) {
        AuthContext authContext = AuthContextHolder.get();
        favoriteService.removeFavoriteApp(authContext.getUserId(), favoriteId, appId);
        return Response.ok();
    }

    @GetMapping("/favorites/{favoriteId}/apps")
    @Operation(operationId = "listFavoriteApps")
    public Response<PageResult<AppVO>> listFavoriteApps(
            @PathVariable Long favoriteId,
            @Validated ListFavoriteAppsRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        return Response.ok(favoriteService.listFavoriteApps(authContext.getUserId(), favoriteId, request));
    }

    @PostMapping("/favorites/{favoriteId}/apps/move")
    @Operation(operationId = "moveFavoriteApp")
    public Response<Void> moveFavoriteApp(
            @PathVariable Long favoriteId,
            @RequestBody @Valid MoveFavoriteAppRequest request) {
        AuthContext authContext = AuthContextHolder.get();
        favoriteService.moveFavoriteApp(authContext.getUserId(), favoriteId, request);
        return Response.ok();
    }
}
