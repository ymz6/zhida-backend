package org.ymz.app.service;

import org.ymz.app.model.dto.app.DeployAppResponse;

/**
 * 应用部署服务。
 *
 * @author ymz
 */
public interface AppDeploymentService {

    DeployAppResponse deployApp(Long userId, Long appId);
}
