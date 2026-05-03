package org.ymz.app.service;

import org.ymz.app.model.dto.monitoring.MonitoringDashboard;
import org.ymz.app.model.dto.monitoring.MonitoringDashboardRequest;
import org.ymz.app.model.dto.monitoring.MonitoringQueryRequest;
import org.ymz.app.model.dto.monitoring.MonitoringQueryResult;

/**
 * 管理端运行与用量监控服务。
 *
 * @author ymz
 */
public interface AdminMonitoringService {

    MonitoringDashboard getDashboard(MonitoringDashboardRequest request);

    MonitoringQueryResult query(MonitoringQueryRequest request);
}
