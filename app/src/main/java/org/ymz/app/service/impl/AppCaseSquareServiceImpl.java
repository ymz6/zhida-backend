package org.ymz.app.service.impl;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.Brackets;
import com.mybatisflex.core.query.If;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ymz.app.converter.AppCaseConverter;
import org.ymz.app.model.dto.app.AppAuthor;
import org.ymz.app.model.dto.appcase.AdminAppCaseInfo;
import org.ymz.app.model.dto.appcase.AdminUpdateAppCaseRequest;
import org.ymz.app.model.dto.appcase.AppCaseDetail;
import org.ymz.app.model.dto.appcase.AppCaseSummary;
import org.ymz.app.model.dto.appcase.ListAdminAppCasesRequest;
import org.ymz.app.model.dto.appcase.ListMyAppCasesRequest;
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
import org.ymz.app.service.AppCaseSquareService;
import org.ymz.app.service.AppService;
import org.ymz.app.service.UserService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.ymz.app.model.entity.table.AppCaseTableDef.APP_CASE;

/**
 * 案例广场业务服务实现。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AppCaseSquareServiceImpl implements AppCaseSquareService {

    private final AppCaseService appCaseService;
    private final AppService appService;
    private final UserService userService;
    private final AppCaseConverter appCaseConverter;

    @Override
    public PageResult<AppCaseSummary> listPublicCases(ListPublicAppCasesRequest request) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP_CASE.ALL_COLUMNS)
                .from(APP_CASE)
                .where(APP_CASE.STATUS.eq(AppCaseStatus.APPROVED.name()))
                .orderBy(APP_CASE.FEATURED.desc())
                .orderBy(APP_CASE.REVIEWED_AT.desc())
                .orderBy(APP_CASE.ID.desc());
        applyKeyword(query, request.getKeyword());

        Page<AppCase> page = appCaseService.page(request.toPage(), query);
        Map<Long, AppAuthor> authors = loadAuthors(page.getRecords());
        return PageResult.of(page, appCase -> {
            AppCaseSummary summary = appCaseConverter.toAppCaseSummary(appCase);
            summary.setAuthor(authors.get(appCase.getUserId()));
            return summary;
        });
    }

    @Override
    public AppCaseDetail getPublicCase(Long caseId) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP_CASE.ALL_COLUMNS)
                .from(APP_CASE)
                .where(APP_CASE.ID.eq(caseId))
                .and(APP_CASE.STATUS.eq(AppCaseStatus.APPROVED.name()));
        AppCase appCase = appCaseService.getOne(query);
        if (appCase == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "案例不存在");
        }

        AppCaseDetail detail = appCaseConverter.toAppCaseDetail(appCase);
        detail.setAuthor(toAppAuthor(userService.getById(appCase.getUserId())));
        return detail;
    }

    @Override
    @Transactional
    public MyAppCaseInfo submitCase(AuthContext authContext, SubmitAppCaseRequest request) {
        Long userId = authContext.getUserId();
        boolean adminSubmit = UserRole.ADMIN.equals(authContext.getUserRole());
        String title = StrUtil.trim(request.getTitle());
        String summary = StrUtil.trim(request.getSummary());
        if (StrUtil.isBlank(title) || StrUtil.isBlank(summary)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "案例标题和简介不能为空");
        }

        App app = appService.getById(request.getAppId());
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!userId.equals(app.getUserId())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }
        if (!AppStatus.READY.name().equals(app.getStatus())
                || !AppDeployStatus.DEPLOYED.name().equals(app.getDeployStatus())
                || StrUtil.isBlank(app.getDeployUrl())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "仅已部署完成的可用应用可以提交案例");
        }

        AppCase appCase = getByAppId(app.getId());
        LocalDateTime now = LocalDateTime.now();
        if (appCase == null) {
            appCase = AppCase.builder()
                    .appId(app.getId())
                    .userId(userId)
                    .title(title)
                    .summary(summary)
                    .status(adminSubmit ? AppCaseStatus.APPROVED.name() : AppCaseStatus.PENDING.name())
                    .featured(false)
                    .snapshotAppName(app.getName())
                    .snapshotDeployUrl(app.getDeployUrl())
                    .snapshotCoverUrl(app.getCoverUrl())
                    .reviewerId(adminSubmit ? userId : null)
                    .reviewRemark("")
                    .reviewedAt(adminSubmit ? now : null)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            appCaseService.save(appCase);
        } else {
            AppCaseStatus currentStatus = AppCaseStatus.valueOf(appCase.getStatus());
            if (AppCaseStatus.PENDING.equals(currentStatus) || AppCaseStatus.APPROVED.equals(currentStatus)) {
                throw BusinessException.of(ResultCode.INVALID_PARAM, "当前应用案例已提交或已公开");
            }
            appCase.setTitle(title);
            appCase.setSummary(summary);
            appCase.setStatus(adminSubmit ? AppCaseStatus.APPROVED.name() : AppCaseStatus.PENDING.name());
            appCase.setFeatured(false);
            appCase.setSnapshotAppName(app.getName());
            appCase.setSnapshotDeployUrl(app.getDeployUrl());
            appCase.setSnapshotCoverUrl(app.getCoverUrl());
            if (adminSubmit) {
                appCase.setReviewerId(userId);
                appCase.setReviewedAt(now);
            }
            appCase.setReviewRemark("");
            appCase.setUpdatedAt(now);
            appCaseService.updateById(appCase);
        }

        MyAppCaseInfo info = appCaseConverter.toMyAppCaseInfo(appCase);
        info.setAuthor(toAppAuthor(userService.getById(userId)));
        return info;
    }

    @Override
    public PageResult<MyAppCaseInfo> listMyCases(Long userId, ListMyAppCasesRequest request) {
        AppCaseStatus status = parseStatus(request.getStatus());
        QueryWrapper query = QueryWrapper.create()
                .select(APP_CASE.ALL_COLUMNS)
                .from(APP_CASE)
                .where(APP_CASE.USER_ID.eq(userId))
                .and(APP_CASE.STATUS.eq(status == null ? null : status.name(), If::hasText))
                .orderBy(APP_CASE.CREATED_AT.desc())
                .orderBy(APP_CASE.ID.desc());

        Page<AppCase> page = appCaseService.page(request.toPage(), query);
        AppAuthor author = toAppAuthor(userService.getById(userId));
        return PageResult.of(page, appCase -> {
            MyAppCaseInfo info = appCaseConverter.toMyAppCaseInfo(appCase);
            info.setAuthor(author);
            return info;
        });
    }

    @Override
    public PageResult<AdminAppCaseInfo> listAdminCases(ListAdminAppCasesRequest request) {
        AppCaseStatus status = parseStatus(request.getStatus());
        QueryWrapper query = QueryWrapper.create()
                .select(APP_CASE.ALL_COLUMNS)
                .from(APP_CASE)
                .where(APP_CASE.STATUS.eq(status == null ? null : status.name(), If::hasText))
                .and(APP_CASE.FEATURED.eq(request.getFeatured(), If::notNull))
                .orderBy(APP_CASE.CREATED_AT.desc())
                .orderBy(APP_CASE.ID.desc());
        applyKeyword(query, request.getKeyword());

        Page<AppCase> page = appCaseService.page(request.toPage(), query);
        Map<Long, AppAuthor> authors = loadAuthors(page.getRecords());
        return PageResult.of(page, appCase -> {
            AdminAppCaseInfo info = appCaseConverter.toAdminAppCaseInfo(appCase);
            info.setAuthor(authors.get(appCase.getUserId()));
            return info;
        });
    }

    @Override
    @Transactional
    public AdminAppCaseInfo updateAdminCase(Long adminUserId, Long caseId, AdminUpdateAppCaseRequest request) {
        AppCase appCase = appCaseService.getById(caseId);
        if (appCase == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "案例不存在");
        }

        AppCaseStatus targetStatus = parseStatus(request.getStatus());
        Boolean featured = request.getFeatured();
        String reviewRemark = StrUtil.trim(request.getReviewRemark());
        if (targetStatus == null && featured == null) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "缺少有效的案例更新内容");
        }

        AppCaseStatus currentStatus = AppCaseStatus.valueOf(appCase.getStatus());
        LocalDateTime now = LocalDateTime.now();
        if (targetStatus != null) {
            applyStatusChange(appCase, currentStatus, targetStatus, adminUserId, reviewRemark, now);
        }

        AppCaseStatus finalStatus = AppCaseStatus.valueOf(appCase.getStatus());
        if (featured != null) {
            if (!AppCaseStatus.APPROVED.equals(finalStatus)) {
                throw BusinessException.of(ResultCode.INVALID_PARAM, "仅已公开案例可以设置精选");
            }
            appCase.setFeatured(featured);
        }

        appCase.setUpdatedAt(now);
        appCaseService.updateById(appCase);

        AdminAppCaseInfo info = appCaseConverter.toAdminAppCaseInfo(appCase);
        info.setAuthor(toAppAuthor(userService.getById(appCase.getUserId())));
        return info;
    }

    private void applyStatusChange(
            AppCase appCase,
            AppCaseStatus currentStatus,
            AppCaseStatus targetStatus,
            Long adminUserId,
            String reviewRemark,
            LocalDateTime now
    ) {
        if (currentStatus.equals(targetStatus)) {
            if (!AppCaseStatus.APPROVED.equals(currentStatus)) {
                throw BusinessException.of(ResultCode.INVALID_PARAM, "案例状态未变化");
            }
            return;
        }

        if (AppCaseStatus.PENDING.equals(currentStatus) && AppCaseStatus.APPROVED.equals(targetStatus)) {
            App app = appService.getById(appCase.getAppId());
            if (app == null) {
                throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
            }
            if (!AppStatus.READY.name().equals(app.getStatus())
                    || !AppDeployStatus.DEPLOYED.name().equals(app.getDeployStatus())
                    || StrUtil.isBlank(app.getDeployUrl())) {
                throw BusinessException.of(ResultCode.INVALID_PARAM, "当前应用未部署完成，无法通过审核");
            }
            appCase.setStatus(AppCaseStatus.APPROVED.name());
            appCase.setSnapshotAppName(app.getName());
            appCase.setSnapshotDeployUrl(app.getDeployUrl());
            appCase.setSnapshotCoverUrl(app.getCoverUrl());
            appCase.setReviewerId(adminUserId);
            appCase.setReviewRemark(StrUtil.blankToDefault(reviewRemark, ""));
            appCase.setReviewedAt(now);
            return;
        }

        if (AppCaseStatus.PENDING.equals(currentStatus) && AppCaseStatus.REJECTED.equals(targetStatus)) {
            if (StrUtil.isBlank(reviewRemark)) {
                throw BusinessException.of(ResultCode.INVALID_PARAM, "驳回案例时必须填写审核备注");
            }
            appCase.setStatus(AppCaseStatus.REJECTED.name());
            appCase.setFeatured(false);
            appCase.setReviewerId(adminUserId);
            appCase.setReviewRemark(reviewRemark);
            appCase.setReviewedAt(now);
            return;
        }

        if (AppCaseStatus.APPROVED.equals(currentStatus) && AppCaseStatus.OFFLINE.equals(targetStatus)) {
            appCase.setStatus(AppCaseStatus.OFFLINE.name());
            appCase.setFeatured(false);
            appCase.setReviewerId(adminUserId);
            appCase.setReviewRemark(StrUtil.blankToDefault(reviewRemark, ""));
            appCase.setReviewedAt(now);
            return;
        }

        throw BusinessException.of(ResultCode.INVALID_PARAM, "不支持的案例状态变更");
    }

    private AppCase getByAppId(Long appId) {
        QueryWrapper query = QueryWrapper.create()
                .select(APP_CASE.ALL_COLUMNS)
                .from(APP_CASE)
                .where(APP_CASE.APP_ID.eq(appId));
        return appCaseService.getOne(query);
    }

    private AppCaseStatus parseStatus(String status) {
        if (StrUtil.isBlank(status)) {
            return null;
        }
        try {
            return AppCaseStatus.valueOf(status.trim());
        } catch (IllegalArgumentException e) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "非法案例状态");
        }
    }

    private void applyKeyword(QueryWrapper query, String rawKeyword) {
        String keyword = StrUtil.trim(rawKeyword);
        if (StrUtil.isBlank(keyword)) {
            return;
        }
        query.and(new Brackets(APP_CASE.TITLE.like(keyword)
                .or(APP_CASE.SUMMARY.like(keyword))
                .or(APP_CASE.SNAPSHOT_APP_NAME.like(keyword))));
    }

    private Map<Long, AppAuthor> loadAuthors(List<AppCase> appCases) {
        Set<Long> userIds = appCases.stream()
                .map(AppCase::getUserId)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, AppAuthor> authors = new HashMap<>();
        for (User user : userService.listByIds(userIds)) {
            authors.put(user.getId(), toAppAuthor(user));
        }
        return authors;
    }

    private AppAuthor toAppAuthor(User user) {
        if (user == null) {
            return null;
        }

        AppAuthor author = new AppAuthor();
        author.setId(user.getId());
        author.setNickname(user.getNickname());
        author.setAvatar(user.getAvatar());
        return author;
    }
}
