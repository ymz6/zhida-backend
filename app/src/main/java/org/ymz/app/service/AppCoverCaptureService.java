package org.ymz.app.service;

import java.time.LocalDateTime;

/**
 * 应用封面截图服务。
 *
 * @author ymz
 */
public interface AppCoverCaptureService {

    void captureCoverAsync(Long appId, String deployUrl, LocalDateTime deployedAt);
}
