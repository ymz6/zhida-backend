package org.ymz.app.model.dto.admin;

import com.mybatisflex.core.query.QueryColumn;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.ymz.app.model.dto.page.SortablePageQuery;

import java.time.LocalDateTime;

import static org.ymz.app.model.entity.table.UserTableDef.USER;

/**
 * 分页查询用户列表
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListUsersRequest extends SortablePageQuery {

    /**
     * 账号（模糊匹配）
     */
    private String account;

    /**
     * 创建时间开始
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createTimeStart;

    /**
     * 创建时间结束
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createTimeEnd;

    @Schema(hidden = true)
    @AssertTrue(message = "创建时间范围不合法")
    public boolean isCreateTimeRangeValid() {
        return createTimeStart == null || createTimeEnd == null || !createTimeStart.isAfter(createTimeEnd);
    }

    @Override
    public QueryColumn resolveSortColumn() {
        if (!hasSort()) {
            return null;
        }
        return switch (getSortField()) {
            case "createTime" -> USER.CREATE_TIME;
            default -> null;
        };
    }
}
