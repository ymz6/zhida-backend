package org.ymz.app.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.If;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ymz.app.converter.UserConverter;
import org.ymz.app.model.dto.admin.ListUsersRequest;
import org.ymz.app.model.dto.page.PageResult;
import org.ymz.app.model.dto.page.SortablePageQuery;
import org.ymz.app.model.dto.user.UserVO;
import org.ymz.app.model.entity.User;
import static org.ymz.app.model.entity.table.UserTableDef.USER;

/**
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {
    private final UserService userService;
    private final UserConverter userConverter;

    public PageResult<UserVO> queryUserList(ListUsersRequest request) {
        QueryColumn sortColumn = request.resolveSortColumn();

        QueryWrapper query = QueryWrapper.create()
                .select(USER.ALL_COLUMNS)
                .from(USER)
                .where(USER.ACCOUNT.like(request.getAccount(), If::hasText))
                .and(USER.CREATE_TIME.ge(request.getCreateTimeStart()))
                .and(USER.CREATE_TIME.le(request.getCreateTimeEnd()));

        if (sortColumn == null) {
            query.orderBy(USER.CREATE_TIME.desc());
        } else {
            query.orderBy(sortColumn, SortablePageQuery.SortDirection.ASC.equals(request.getSortOrder()));
        }

        Page<User> page = userService.page(request.toPage(), query);
        return PageResult.of(page, userConverter::toUserVO);
    }
}
