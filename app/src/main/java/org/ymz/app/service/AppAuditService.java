package org.ymz.app.service;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.If;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ymz.app.converter.AppConverter;
import org.ymz.app.converter.AuditRecordConverter;
import org.ymz.app.model.dto.app.AppVO;
import org.ymz.app.model.dto.app.ListAdminAppCasesRequest;
import org.ymz.app.model.dto.audit.AuditRecordVO;
import org.ymz.app.model.dto.audit.ListAuditRecordsRequest;
import org.ymz.app.model.dto.audit.ReviewAuditRequest;
import org.ymz.app.model.dto.audit.SetFeaturedRequest;
import org.ymz.app.model.dto.audit.SwitchAuditStatusRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AuditRecord;
import org.ymz.app.model.entity.User;
import org.ymz.app.model.enums.UserRole;
import org.ymz.app.model.enums.app.AppAuditStatus;
import org.ymz.app.security.AuthContext;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.ymz.app.model.entity.table.AppTableDef.APP;
import static org.ymz.app.model.entity.table.AuditRecordTableDef.AUDIT_RECORD;

/**
 * 应用案例广场审核业务。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AppAuditService {

    private final AppService appService;
    private final AuditRecordService auditRecordService;
    private final UserService userService;
    private final AppConverter appConverter;
    private final AuditRecordConverter auditRecordConverter;
    private final AppUrlBuilder appUrlBuilder;

    @Transactional
    public void submitAudit(AuthContext authContext, Long appId) {
        App app = requireOwnedApp(authContext, appId);
        if (!AppAuditStatus.DRAFT.getCode().equals(app.getAuditStatus())
                && !AppAuditStatus.REJECTED.getCode().equals(app.getAuditStatus())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "当前应用状态不可提交审核");
        }
        if (app.getDeployedAt() == null || StrUtil.isBlank(app.getDeployKey())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "请先部署应用后再提交审核");
        }
        LocalDateTime now = LocalDateTime.now();
        if (UserRole.ADMIN.equals(authContext.getUserRole())) {
            // 管理员提交时直接通过审核，并默认设为精选。
            boolean appUpdated = appService.updateById(App.builder()
                    .id(appId)
                    .auditStatus(AppAuditStatus.APPROVED.getCode())
                    .publishedAt(now)
                    .featured(true)
                    .featuredAt(now)
                    .build());
            boolean recordSaved = auditRecordService.save(AuditRecord.builder()
                    .appId(appId)
                    .status(AppAuditStatus.APPROVED.getCode())
                    .auditorId(authContext.getUserId())
                    .remark("管理员提交自动通过")
                    .auditTime(now)
                    .createdAt(now)
                    .build());
            if (!appUpdated || !recordSaved) {
                throw BusinessException.of(ResultCode.SYSTEM_ERROR, "提交审核失败");
            }
            return;
        }

        boolean appUpdated = appService.updateById(App.builder()
                .id(appId)
                .auditStatus(AppAuditStatus.PENDING.getCode())
                .build());
        boolean recordSaved = auditRecordService.save(AuditRecord.builder()
                .appId(appId)
                .status(AppAuditStatus.PENDING.getCode())
                .build());
        if (!appUpdated || !recordSaved) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "提交审核失败");
        }
    }

    @Transactional
    public void withdrawAudit(AuthContext authContext, Long appId) {
        App app = requireOwnedApp(authContext, appId);
        if (!AppAuditStatus.PENDING.getCode().equals(app.getAuditStatus())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "当前应用不在审核中");
        }

        QueryWrapper pendingRecordQuery = QueryWrapper.create()
                .select(AUDIT_RECORD.ALL_COLUMNS)
                .from(AUDIT_RECORD)
                .where(AUDIT_RECORD.APP_ID.eq(appId))
                .and(AUDIT_RECORD.STATUS.eq(AppAuditStatus.PENDING.getCode()))
                .orderBy(AUDIT_RECORD.CREATED_AT.desc())
                .limit(1);
        AuditRecord pendingRecord = auditRecordService.getOne(pendingRecordQuery);
        if (pendingRecord == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "待审核记录不存在");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean recordUpdated = auditRecordService.updateById(AuditRecord.builder()
                .id(pendingRecord.getId())
                .status(AppAuditStatus.WITHDRAWN.getCode())
                .remark("用户撤回审核")
                .auditTime(now)
                .build());
        boolean appUpdated = appService.updateById(App.builder()
                .id(appId)
                .auditStatus(AppAuditStatus.DRAFT.getCode())
                .build());
        if (!recordUpdated || !appUpdated) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "撤回审核失败");
        }
    }

    @Transactional
    public void reviewPendingAudit(AuthContext authContext, Long recordId, ReviewAuditRequest request) {
        AppAuditStatus targetStatus = request.getStatus();
        validateAuditResult(targetStatus, request.getRemark());
        Integer targetStatusCode = targetStatus.getCode();

        AuditRecord record = auditRecordService.getById(recordId);
        if (record == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "审核记录不存在");
        }
        if (!AppAuditStatus.PENDING.getCode().equals(record.getStatus())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "该审核记录已被处理");
        }

        App app = appService.getById(record.getAppId());
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!AppAuditStatus.PENDING.getCode().equals(app.getAuditStatus())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "应用当前不在审核中");
        }

        LocalDateTime now = LocalDateTime.now();
        boolean recordUpdated = auditRecordService.updateById(AuditRecord.builder()
                .id(recordId)
                .status(targetStatusCode)
                .auditorId(authContext.getUserId())
                .remark(StrUtil.trimToNull(request.getRemark()))
                .auditTime(now)
                .build());

        App.AppBuilder appBuilder = App.builder()
                .id(app.getId())
                .auditStatus(targetStatusCode);
        if (AppAuditStatus.APPROVED.equals(targetStatus)) {
            appBuilder.publishedAt(now);
        }
        boolean appUpdated = appService.updateById(appBuilder.build());
        if (!recordUpdated || !appUpdated) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "审核处理失败");
        }
    }

    @Transactional
    public void switchAuditStatus(AuthContext authContext, Long appId, SwitchAuditStatusRequest request) {
        AppAuditStatus targetStatus = request.getStatus();
        validateAuditResult(targetStatus, request.getRemark());
        Integer targetStatusCode = targetStatus.getCode();

        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!AppAuditStatus.APPROVED.getCode().equals(app.getAuditStatus())
                && !AppAuditStatus.REJECTED.getCode().equals(app.getAuditStatus())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "只有已通过或已拒绝的应用可以切换审核状态");
        }
        if (targetStatusCode.equals(app.getAuditStatus())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "应用已处于目标审核状态");
        }

        LocalDateTime now = LocalDateTime.now();
        App.AppBuilder appBuilder = App.builder()
                .id(appId)
                .auditStatus(targetStatusCode);
        if (AppAuditStatus.APPROVED.equals(targetStatus)) {
            appBuilder.publishedAt(now);
        } else {
            // 切换为未通过时，不再进入案例广场，同时取消精选标记。
            appBuilder.publishedAt(null)
                    .featured(false)
                    .featuredAt(null);
        }
        boolean appUpdated = appService.updateById(appBuilder.build());
        boolean recordSaved = auditRecordService.save(AuditRecord.builder()
                .appId(appId)
                .status(targetStatusCode)
                .auditorId(authContext.getUserId())
                .remark(StrUtil.trimToNull(request.getRemark()))
                .auditTime(now)
                .createdAt(now)
                .build());
        if (!appUpdated || !recordSaved) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "切换审核状态失败");
        }
    }

    @Transactional
    public void setFeatured(AuthContext authContext, Long appId, SetFeaturedRequest request) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!AppAuditStatus.APPROVED.getCode().equals(app.getAuditStatus())) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "只有审核通过的应用可以设置精选");
        }

        boolean featured = Boolean.TRUE.equals(request.getFeatured());
        boolean ok = appService.updateById(App.builder()
                .id(appId)
                .featured(featured)
                .featuredAt(featured ? LocalDateTime.now() : null)
                .build());
        if (!ok) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "设置精选失败");
        }
    }

    public PageResult<AuditRecordVO> listAdminAuditRecords(ListAuditRecordsRequest request) {
        // 请求层用枚举表达业务状态，数据库仍按状态 code 过滤。
        Integer statusCode = request.getStatus() == null ? null : request.getStatus().getCode();
        QueryWrapper query = QueryWrapper.create()
                .select(AUDIT_RECORD.ALL_COLUMNS)
                .from(AUDIT_RECORD)
                .where(AUDIT_RECORD.STATUS.eq(statusCode, If::notNull))
                .orderBy(AUDIT_RECORD.CREATED_AT.desc());

        Page<AuditRecord> page = auditRecordService.page(request.toPage(), query);
        return toAuditRecordPageResult(page);
    }

    public AuditRecordVO getAdminAuditRecord(Long recordId) {
        AuditRecord record = auditRecordService.getById(recordId);
        if (record == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "审核记录不存在");
        }

        App app = appService.getById(record.getAppId());
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }

        User author = userService.getById(app.getUserId());
        AuditRecordVO vo = auditRecordConverter.toAuditRecordVO(record);
        vo.setApp(toAppVO(app, author));
        return vo;
    }

    public PageResult<AppVO> listAdminAppCases(ListAdminAppCasesRequest request) {
        Integer statusCode = request.getStatus() == null ? null : request.getStatus().getCode();
        List<Integer> caseStatusCodes = List.of(
                AppAuditStatus.APPROVED.getCode(),
                AppAuditStatus.REJECTED.getCode());
        QueryWrapper query = QueryWrapper.create()
                .select(APP.ALL_COLUMNS)
                .from(APP)
                // 应用案例管理只看已进入案例体系的应用，不展示草稿或待审核应用。
                .where(statusCode == null
                        ? APP.AUDIT_STATUS.in(caseStatusCodes)
                        : APP.AUDIT_STATUS.eq(statusCode))
                .and(APP.FEATURED.eq(request.getFeatured(), If::notNull))
                .and(APP.NAME.like(request.getKeyword(), If::hasText))
                .orderBy(APP.FEATURED.desc(), APP.FEATURED_AT.desc(), APP.PUBLISHED_AT.desc());

        Page<App> page = appService.page(request.toPage(), query);
        return toAppPageResult(page);
    }

    public AppVO getAdminAppCase(Long appId) {
        App app = appService.getById(appId);
        if (app == null || !isAppCaseStatus(app.getAuditStatus())) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用案例不存在");
        }

        User author = userService.getById(app.getUserId());
        return toAppVO(app, author);
    }

    public PageResult<AuditRecordVO> listAppAuditRecords(
            AuthContext authContext,
            Long appId,
            ListAuditRecordsRequest request) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!app.getUserId().equals(authContext.getUserId())
                && !UserRole.ADMIN.equals(authContext.getUserRole())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }

        // 请求层用枚举表达业务状态，数据库仍按状态 code 过滤。
        Integer statusCode = request.getStatus() == null ? null : request.getStatus().getCode();
        QueryWrapper query = QueryWrapper.create()
                .select(AUDIT_RECORD.ALL_COLUMNS)
                .from(AUDIT_RECORD)
                .where(AUDIT_RECORD.APP_ID.eq(app.getId()))
                .and(AUDIT_RECORD.STATUS.eq(statusCode, If::notNull))
                .orderBy(AUDIT_RECORD.CREATED_AT.desc());

        Page<AuditRecord> page = auditRecordService.page(request.toPage(), query);
        return toAuditRecordPageResult(page);
    }

    private App requireOwnedApp(AuthContext authContext, Long appId) {
        App app = appService.getById(appId);
        if (app == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "应用不存在");
        }
        if (!app.getUserId().equals(authContext.getUserId())) {
            throw BusinessException.of(ResultCode.NO_PERMISSION);
        }
        return app;
    }

    private void validateAuditResult(AppAuditStatus status, String remark) {
        if (!AppAuditStatus.APPROVED.equals(status)
                && !AppAuditStatus.REJECTED.equals(status)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "审核状态不合法");
        }
        if (AppAuditStatus.REJECTED.equals(status) && StrUtil.isBlank(remark)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "拒绝原因不能为空");
        }
    }

    private PageResult<AuditRecordVO> toAuditRecordPageResult(Page<AuditRecord> page) {
        List<Long> appIds = page.getRecords().stream()
                .map(AuditRecord::getAppId)
                .distinct()
                .toList();
        Map<Long, AppVO> appMap;
        if (appIds.isEmpty()) {
            appMap = Map.of();
        } else {
            List<App> apps = appService.listByIds(appIds);
            List<Long> userIds = apps.stream()
                    .map(App::getUserId)
                    .distinct()
                    .toList();
            Map<Long, User> userMap = userIds.isEmpty()
                    ? Map.of()
                    : userService.listByIds(userIds).stream()
                            .collect(Collectors.toMap(User::getId, user -> user));
            appMap = apps.stream()
                    .collect(Collectors.toMap(
                            App::getId,
                            app -> {
                                AppVO vo = appConverter.toAppVO(app, userMap.get(app.getUserId()));
                                if (vo != null) {
                                    vo.setDeployUrl(appUrlBuilder.buildDeployUrl(app.getDeployKey()));
                                }
                                return vo;
                            }));
        }
        return PageResult.of(page, record -> {
            AuditRecordVO vo = auditRecordConverter.toAuditRecordVO(record);
            vo.setApp(appMap.get(record.getAppId()));
            return vo;
        });
    }

    private PageResult<AppVO> toAppPageResult(Page<App> page) {
        List<Long> userIds = page.getRecords().stream()
                .map(App::getUserId)
                .distinct()
                .toList();
        Map<Long, User> userMap = userIds.isEmpty()
                ? Map.of()
                : userService.listByIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user));

        return PageResult.of(page, app -> toAppVO(app, userMap.get(app.getUserId())));
    }

    private AppVO toAppVO(App app, User author) {
        AppVO vo = appConverter.toAppVO(app, author);
        if (vo != null) {
            vo.setDeployUrl(appUrlBuilder.buildDeployUrl(app.getDeployKey()));
        }
        return vo;
    }

    private boolean isAppCaseStatus(Integer auditStatus) {
        return AppAuditStatus.APPROVED.getCode().equals(auditStatus)
                || AppAuditStatus.REJECTED.getCode().equals(auditStatus);
    }
}
