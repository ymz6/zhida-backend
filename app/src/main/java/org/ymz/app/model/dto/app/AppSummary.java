package org.ymz.app.model.dto.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 应用列表摘要。
 *
 * @author ymz
 */
@Data
public class AppSummary {

    private Long id;

    private String name;

    private String status;

    private String previewUrl;

    private String coverUrl;

    private String deployStatus;

    private String deployUrl;

    private Long latestTaskId;

    private String errorMessage;

    private AppAuthor author;

    private LocalDateTime createdAt;

    private LocalDateTime deployedAt;
}
