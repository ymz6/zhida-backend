package org.ymz.app.service;

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
import org.ymz.app.security.AuthContext;

/**
 * 案例广场业务服务。
 *
 * @author ymz
 */
public interface AppCaseSquareService {

    PageResult<AppCaseSummary> listPublicCases(ListPublicAppCasesRequest request);

    AppCaseDetail getPublicCase(Long caseId);

    MyAppCaseInfo submitCase(AuthContext authContext, SubmitAppCaseRequest request);

    PageResult<MyAppCaseInfo> listMyCases(Long userId, ListMyAppCasesRequest request);

    PageResult<AdminAppCaseInfo> listAdminCases(ListAdminAppCasesRequest request);

    AdminAppCaseInfo updateAdminCase(Long adminUserId, Long caseId, AdminUpdateAppCaseRequest request);
}
