package org.ymz.app.service.impl;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ymz.app.converter.AppCaseConverter;
import org.ymz.app.model.dto.appcase.AdminAppCaseInfo;
import org.ymz.app.model.dto.appcase.AdminUpdateAppCaseRequest;
import org.ymz.app.model.dto.appcase.AppCaseDetail;
import org.ymz.app.model.dto.appcase.AppCaseSummary;
import org.ymz.app.model.dto.appcase.ListPublicAppCasesRequest;
import org.ymz.app.model.dto.appcase.MyAppCaseInfo;
import org.ymz.app.model.dto.appcase.SubmitAppCaseRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppCase;
import org.ymz.app.model.entity.User;
import org.ymz.app.model.enums.UserRole;
import org.ymz.app.model.enums.app.AppCaseStatus;
import org.ymz.app.model.enums.app.AppDeployStatus;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.security.AuthContext;
import org.ymz.app.service.AppCaseService;
import org.ymz.app.service.AppService;
import org.ymz.app.service.UserService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class AppCaseSquareServiceImplTest {

    @Test
    void listPublicCasesOnlyReturnsApprovedAndOrdersFeaturedFirst() {
        Fixture fixture = fixture();
        ListPublicAppCasesRequest request = new ListPublicAppCasesRequest();
        request.setKeyword("crm");
        AppCase appCase = approvedCase();
        when(fixture.appCaseService.page(any(Page.class), any(QueryWrapper.class)))
                .thenReturn(new Page<>(List.of(appCase), 1, 10, 1));
        when(fixture.userService.listByIds(any(Collection.class))).thenReturn(List.of(author()));

        PageResult<AppCaseSummary> result = fixture.service.listPublicCases(request);

        assertEquals(1, result.getTotal());
        assertEquals(5L, result.getList().getFirst().getId());
        assertEquals(10L, result.getList().getFirst().getAuthor().getId());
        QueryWrapper query = capturePageQuery(fixture.appCaseService);
        String sql = query.toSQL().toLowerCase();
        assertTrue(sql.contains("approved"));
        assertTrue(sql.contains("crm"));
        assertTrue(sql.contains("featured") && sql.contains("desc"));
        assertTrue(sql.contains("reviewed_at") && sql.contains("desc"));
    }

    @Test
    void submitCaseCreatesPendingCaseForOwnedDeployedApp() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(deployedApp());
        when(fixture.appCaseService.getOne(any(QueryWrapper.class))).thenReturn(null);
        when(fixture.userService.getById(10L)).thenReturn(author());

        MyAppCaseInfo result = fixture.service.submitCase(userContext(), submitRequest());

        assertEquals(AppCaseStatus.PENDING.name(), result.getStatus());
        ArgumentCaptor<AppCase> caseCaptor = ArgumentCaptor.forClass(AppCase.class);
        verify(fixture.appCaseService).save(caseCaptor.capture());
        AppCase saved = caseCaptor.getValue();
        assertEquals(1L, saved.getAppId());
        assertEquals(10L, saved.getUserId());
        assertEquals("CRM 案例", saved.getTitle());
        assertEquals(AppCaseStatus.PENDING.name(), saved.getStatus());
        assertEquals("crm app", saved.getSnapshotAppName());
        assertEquals("https://example.com/app/", saved.getSnapshotDeployUrl());
        assertEquals("https://example.com/cover.jpg", saved.getSnapshotCoverUrl());
        assertFalse(saved.getFeatured());
    }

    @Test
    void submitCaseRejectsUndeployedApp() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(App.builder()
                .id(1L)
                .userId(10L)
                .status(AppStatus.READY.name())
                .deployStatus(AppDeployStatus.UNDEPLOYED.name())
                .build());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fixture.service.submitCase(userContext(), submitRequest())
        );

        assertEquals(ResultCode.INVALID_PARAM, exception.getResultCode());
        verify(fixture.appCaseService, never()).save(any(AppCase.class));
    }

    @Test
    void submitCaseRejectsPendingDuplicate() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(deployedApp());
        when(fixture.appCaseService.getOne(any(QueryWrapper.class))).thenReturn(AppCase.builder()
                .id(5L)
                .appId(1L)
                .userId(10L)
                .status(AppCaseStatus.PENDING.name())
                .build());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fixture.service.submitCase(userContext(), submitRequest())
        );

        assertEquals(ResultCode.INVALID_PARAM, exception.getResultCode());
        verify(fixture.appCaseService, never()).updateById(any(AppCase.class));
    }

    @Test
    void resubmitRejectedCaseMovesBackToPending() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(deployedApp());
        when(fixture.appCaseService.getOne(any(QueryWrapper.class))).thenReturn(AppCase.builder()
                .id(5L)
                .appId(1L)
                .userId(10L)
                .title("旧标题")
                .summary("旧简介")
                .status(AppCaseStatus.REJECTED.name())
                .featured(false)
                .reviewRemark("质量不足")
                .build());
        when(fixture.userService.getById(10L)).thenReturn(author());

        fixture.service.submitCase(userContext(), submitRequest());

        ArgumentCaptor<AppCase> caseCaptor = ArgumentCaptor.forClass(AppCase.class);
        verify(fixture.appCaseService).updateById(caseCaptor.capture());
        assertEquals(AppCaseStatus.PENDING.name(), caseCaptor.getValue().getStatus());
        assertEquals("CRM 案例", caseCaptor.getValue().getTitle());
        assertEquals("https://example.com/app/", caseCaptor.getValue().getSnapshotDeployUrl());
        assertEquals("", caseCaptor.getValue().getReviewRemark());
    }

    @Test
    void adminSubmitCaseCreatesApprovedCase() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(deployedApp());
        when(fixture.appCaseService.getOne(any(QueryWrapper.class))).thenReturn(null);
        when(fixture.userService.getById(10L)).thenReturn(author());

        MyAppCaseInfo result = fixture.service.submitCase(adminContext(), submitRequest());

        assertEquals(AppCaseStatus.APPROVED.name(), result.getStatus());
        ArgumentCaptor<AppCase> caseCaptor = ArgumentCaptor.forClass(AppCase.class);
        verify(fixture.appCaseService).save(caseCaptor.capture());
        AppCase saved = caseCaptor.getValue();
        assertEquals(AppCaseStatus.APPROVED.name(), saved.getStatus());
        assertEquals(10L, saved.getReviewerId());
        assertNotNull(saved.getReviewedAt());
        assertEquals("crm app", saved.getSnapshotAppName());
        assertEquals("https://example.com/app/", saved.getSnapshotDeployUrl());
        assertEquals("https://example.com/cover.jpg", saved.getSnapshotCoverUrl());
        assertFalse(saved.getFeatured());
        assertEquals("", saved.getReviewRemark());
    }

    @Test
    void adminResubmitRejectedCaseMovesDirectlyToApproved() {
        Fixture fixture = fixture();
        when(fixture.appService.getById(1L)).thenReturn(deployedApp());
        when(fixture.appCaseService.getOne(any(QueryWrapper.class))).thenReturn(AppCase.builder()
                .id(5L)
                .appId(1L)
                .userId(10L)
                .title("旧标题")
                .summary("旧简介")
                .status(AppCaseStatus.REJECTED.name())
                .featured(false)
                .snapshotDeployUrl("https://old.example.com/app/")
                .reviewRemark("质量不足")
                .build());
        when(fixture.userService.getById(10L)).thenReturn(author());

        fixture.service.submitCase(adminContext(), submitRequest());

        ArgumentCaptor<AppCase> caseCaptor = ArgumentCaptor.forClass(AppCase.class);
        verify(fixture.appCaseService).updateById(caseCaptor.capture());
        AppCase updated = caseCaptor.getValue();
        assertEquals(AppCaseStatus.APPROVED.name(), updated.getStatus());
        assertEquals(10L, updated.getReviewerId());
        assertNotNull(updated.getReviewedAt());
        assertEquals("https://example.com/app/", updated.getSnapshotDeployUrl());
        assertEquals("", updated.getReviewRemark());
    }

    @Test
    void approvePendingCaseSnapshotsDeploymentAndCanSetFeatured() {
        Fixture fixture = fixture();
        when(fixture.appCaseService.getById(5L)).thenReturn(pendingCase());
        when(fixture.appService.getById(1L)).thenReturn(deployedApp());
        when(fixture.userService.getById(10L)).thenReturn(author());
        AdminUpdateAppCaseRequest request = new AdminUpdateAppCaseRequest();
        request.setStatus(AppCaseStatus.APPROVED.name());
        request.setFeatured(true);

        AdminAppCaseInfo result = fixture.service.updateAdminCase(99L, 5L, request);

        assertEquals(AppCaseStatus.APPROVED.name(), result.getStatus());
        ArgumentCaptor<AppCase> caseCaptor = ArgumentCaptor.forClass(AppCase.class);
        verify(fixture.appCaseService).updateById(caseCaptor.capture());
        AppCase updated = caseCaptor.getValue();
        assertEquals(AppCaseStatus.APPROVED.name(), updated.getStatus());
        assertEquals("crm app", updated.getSnapshotAppName());
        assertEquals("https://example.com/app/", updated.getSnapshotDeployUrl());
        assertEquals("https://example.com/cover.jpg", updated.getSnapshotCoverUrl());
        assertEquals(99L, updated.getReviewerId());
        assertTrue(updated.getFeatured());
    }

    @Test
    void rejectPendingCaseRequiresRemark() {
        Fixture fixture = fixture();
        when(fixture.appCaseService.getById(5L)).thenReturn(pendingCase());
        AdminUpdateAppCaseRequest request = new AdminUpdateAppCaseRequest();
        request.setStatus(AppCaseStatus.REJECTED.name());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fixture.service.updateAdminCase(99L, 5L, request)
        );

        assertEquals(ResultCode.INVALID_PARAM, exception.getResultCode());
        verify(fixture.appCaseService, never()).updateById(any(AppCase.class));
    }

    @Test
    void offlineApprovedCaseClearsFeatured() {
        Fixture fixture = fixture();
        AppCase appCase = approvedCase();
        appCase.setFeatured(true);
        when(fixture.appCaseService.getById(5L)).thenReturn(appCase);
        when(fixture.userService.getById(10L)).thenReturn(author());
        AdminUpdateAppCaseRequest request = new AdminUpdateAppCaseRequest();
        request.setStatus(AppCaseStatus.OFFLINE.name());

        AdminAppCaseInfo result = fixture.service.updateAdminCase(99L, 5L, request);

        assertEquals(AppCaseStatus.OFFLINE.name(), result.getStatus());
        ArgumentCaptor<AppCase> caseCaptor = ArgumentCaptor.forClass(AppCase.class);
        verify(fixture.appCaseService).updateById(caseCaptor.capture());
        assertFalse(caseCaptor.getValue().getFeatured());
    }

    @Test
    void featuredOnlyUpdateRequiresApprovedCase() {
        Fixture fixture = fixture();
        when(fixture.appCaseService.getById(5L)).thenReturn(pendingCase());
        AdminUpdateAppCaseRequest request = new AdminUpdateAppCaseRequest();
        request.setFeatured(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> fixture.service.updateAdminCase(99L, 5L, request)
        );

        assertEquals(ResultCode.INVALID_PARAM, exception.getResultCode());
        verify(fixture.appCaseService, never()).updateById(any(AppCase.class));
    }

    private Fixture fixture() {
        AppCaseService appCaseService = mock(AppCaseService.class);
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        AppCaseSquareServiceImpl service = new AppCaseSquareServiceImpl(
                appCaseService,
                appService,
                userService,
                new FakeAppCaseConverter()
        );
        return new Fixture(service, appCaseService, appService, userService);
    }

    private SubmitAppCaseRequest submitRequest() {
        SubmitAppCaseRequest request = new SubmitAppCaseRequest();
        request.setAppId(1L);
        request.setTitle(" CRM 案例 ");
        request.setSummary(" 可用于销售管理 ");
        return request;
    }

    private AuthContext userContext() {
        return AuthContext.builder()
                .userId(10L)
                .userRole(UserRole.USER)
                .build();
    }

    private AuthContext adminContext() {
        return AuthContext.builder()
                .userId(10L)
                .userRole(UserRole.ADMIN)
                .build();
    }

    private App deployedApp() {
        return App.builder()
                .id(1L)
                .userId(10L)
                .name("crm app")
                .status(AppStatus.READY.name())
                .deployStatus(AppDeployStatus.DEPLOYED.name())
                .deployUrl("https://example.com/app/")
                .coverUrl("https://example.com/cover.jpg")
                .build();
    }

    private AppCase pendingCase() {
        return AppCase.builder()
                .id(5L)
                .appId(1L)
                .userId(10L)
                .title("CRM 案例")
                .summary("可用于销售管理")
                .status(AppCaseStatus.PENDING.name())
                .featured(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private AppCase approvedCase() {
        return AppCase.builder()
                .id(5L)
                .appId(1L)
                .userId(10L)
                .title("CRM 案例")
                .summary("可用于销售管理")
                .status(AppCaseStatus.APPROVED.name())
                .featured(false)
                .snapshotAppName("crm app")
                .snapshotDeployUrl("https://example.com/app/")
                .snapshotCoverUrl("https://example.com/cover.jpg")
                .reviewedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private User author() {
        return User.builder()
                .id(10L)
                .nickname("作者")
                .avatar("https://example.com/avatar.png")
                .build();
    }

    private QueryWrapper capturePageQuery(AppCaseService appCaseService) {
        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(appCaseService).page(any(Page.class), queryCaptor.capture());
        return queryCaptor.getValue();
    }

    private record Fixture(
            AppCaseSquareServiceImpl service,
            AppCaseService appCaseService,
            AppService appService,
            UserService userService
    ) {
    }

    private static class FakeAppCaseConverter implements AppCaseConverter {

        @Override
        public AppCaseSummary toAppCaseSummary(AppCase appCase) {
            AppCaseSummary summary = new AppCaseSummary();
            fillSummary(summary, appCase);
            return summary;
        }

        @Override
        public AppCaseDetail toAppCaseDetail(AppCase appCase) {
            AppCaseDetail detail = new AppCaseDetail();
            fillSummary(detail, appCase);
            return detail;
        }

        @Override
        public MyAppCaseInfo toMyAppCaseInfo(AppCase appCase) {
            MyAppCaseInfo info = new MyAppCaseInfo();
            fillSummary(info, appCase);
            info.setStatus(appCase.getStatus());
            info.setReviewRemark(appCase.getReviewRemark());
            info.setUpdatedAt(appCase.getUpdatedAt());
            return info;
        }

        @Override
        public AdminAppCaseInfo toAdminAppCaseInfo(AppCase appCase) {
            AdminAppCaseInfo info = new AdminAppCaseInfo();
            fillSummary(info, appCase);
            info.setStatus(appCase.getStatus());
            info.setReviewRemark(appCase.getReviewRemark());
            info.setUpdatedAt(appCase.getUpdatedAt());
            info.setUserId(appCase.getUserId());
            info.setReviewerId(appCase.getReviewerId());
            return info;
        }

        private void fillSummary(AppCaseSummary summary, AppCase appCase) {
            summary.setId(appCase.getId());
            summary.setAppId(appCase.getAppId());
            summary.setTitle(appCase.getTitle());
            summary.setSummary(appCase.getSummary());
            summary.setFeatured(appCase.getFeatured());
            summary.setAppName(appCase.getSnapshotAppName());
            summary.setPreviewUrl(appCase.getSnapshotDeployUrl());
            summary.setCoverUrl(appCase.getSnapshotCoverUrl());
            summary.setReviewedAt(appCase.getReviewedAt());
            summary.setCreatedAt(appCase.getCreatedAt());
        }
    }
}
