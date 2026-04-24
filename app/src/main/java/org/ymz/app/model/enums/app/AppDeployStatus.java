package org.ymz.app.model.enums.app;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 应用部署状态
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum AppDeployStatus {

    UNDEPLOYED("未部署"),
    DEPLOYING("部署中"),
    DEPLOYED("已部署"),
    FAILED("部署失败");

    private final String description;
}
