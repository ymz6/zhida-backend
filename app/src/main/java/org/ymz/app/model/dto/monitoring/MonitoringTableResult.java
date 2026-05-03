package org.ymz.app.model.dto.monitoring;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一监控表格查询结果。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringTableResult {

    private String queryId;

    private String resource;

    private String status;

    private int pageNum;

    private int pageSize;

    private long total;

    @Builder.Default
    private List<Object> records = new ArrayList<>();

    private String errorMessage;
}
