package org.ymz.app.service;

import org.ymz.app.model.dto.app.CreateAppIterationRequest;
import org.ymz.app.model.dto.app.CreateAppTaskResponse;

/**
 * 应用后续对话迭代服务。
 *
 * @author ymz
 */
public interface AppIterationService {

    CreateAppTaskResponse createAppIteration(Long userId, Long appId, CreateAppIterationRequest request);
}
