package org.ymz.app.service;

import org.ymz.app.model.dto.app.CreateAppIterationRequest;
import org.ymz.app.model.dto.app.CreateAppIterationResponse;

/**
 * 应用后续对话迭代服务。
 *
 * @author ymz
 */
public interface AppIterationService {

    CreateAppIterationResponse createAppIteration(Long userId, Long appId, CreateAppIterationRequest request);
}
