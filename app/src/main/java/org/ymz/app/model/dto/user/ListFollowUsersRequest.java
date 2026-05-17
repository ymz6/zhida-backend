package org.ymz.app.model.dto.user;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;

/**
 * 关注/粉丝分页查询请求。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListFollowUsersRequest extends PageQuery {
    /**
     * 用户昵称关键词
     */
    private String keyword;
}
