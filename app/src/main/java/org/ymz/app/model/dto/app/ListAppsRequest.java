package org.ymz.app.model.dto.app;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;

/**
 * 分页查询应用列表
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ListAppsRequest extends PageQuery {
}
