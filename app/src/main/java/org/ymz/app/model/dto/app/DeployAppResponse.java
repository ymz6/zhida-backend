package org.ymz.app.model.dto.app;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 应用部署响应。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeployAppResponse {

    private Long appId;

    private String deployStatus;

    private String deployUrl;

    private LocalDateTime deployedAt;
}
