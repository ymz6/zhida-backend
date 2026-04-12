package org.ymz.app.service;

import org.ymz.app.model.dto.admin.ListUsersRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.dto.user.UserInfo;

/**
 *
 * @author ymz
 */
public interface AdminUserService {
    PageResult<UserInfo> queryUserList(ListUsersRequest request);
}
