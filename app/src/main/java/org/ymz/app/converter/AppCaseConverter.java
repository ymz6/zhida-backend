package org.ymz.app.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.ymz.app.model.dto.appcase.AdminAppCaseInfo;
import org.ymz.app.model.dto.appcase.AppCaseDetail;
import org.ymz.app.model.dto.appcase.AppCaseSummary;
import org.ymz.app.model.dto.appcase.MyAppCaseInfo;
import org.ymz.app.model.entity.AppCase;

/**
 * 应用案例模块转换器。
 *
 * @author ymz
 */
@Mapper(componentModel = "spring")
public interface AppCaseConverter {

    @Mapping(target = "appName", source = "snapshotAppName")
    @Mapping(target = "previewUrl", source = "snapshotDeployUrl")
    @Mapping(target = "coverUrl", source = "snapshotCoverUrl")
    @Mapping(target = "author", ignore = true)
    AppCaseSummary toAppCaseSummary(AppCase appCase);

    @Mapping(target = "appName", source = "snapshotAppName")
    @Mapping(target = "previewUrl", source = "snapshotDeployUrl")
    @Mapping(target = "coverUrl", source = "snapshotCoverUrl")
    @Mapping(target = "author", ignore = true)
    AppCaseDetail toAppCaseDetail(AppCase appCase);

    @Mapping(target = "appName", source = "snapshotAppName")
    @Mapping(target = "previewUrl", source = "snapshotDeployUrl")
    @Mapping(target = "coverUrl", source = "snapshotCoverUrl")
    @Mapping(target = "author", ignore = true)
    MyAppCaseInfo toMyAppCaseInfo(AppCase appCase);

    @Mapping(target = "appName", source = "snapshotAppName")
    @Mapping(target = "previewUrl", source = "snapshotDeployUrl")
    @Mapping(target = "coverUrl", source = "snapshotCoverUrl")
    @Mapping(target = "author", ignore = true)
    AdminAppCaseInfo toAdminAppCaseInfo(AppCase appCase);
}
