package org.ymz.app.service;

import org.ymz.app.model.dto.app.CreateAppRequest;
import org.ymz.app.model.dto.app.CreateAppTaskResponse;

/**
 * 应用创建流程服务。
 *
 * @author ymz
 */
public interface AppCreationService {

    CreateAppTaskResponse createApp(Long userId, CreateAppRequest request);
}
