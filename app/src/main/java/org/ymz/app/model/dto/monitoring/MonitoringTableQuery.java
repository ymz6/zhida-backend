package org.ymz.app.model.dto.monitoring;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.ymz.app.model.dto.page.PageQuery;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一监控表格查询项。
 *
 * @author ymz
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MonitoringTableQuery extends PageQuery {

    @NotBlank(message = "表格查询 ID 不能为空")
    private String queryId;

    @NotBlank(message = "表格资源不能为空")
    private String resource;

    private Map<String, Object> filters = new LinkedHashMap<>();
}
