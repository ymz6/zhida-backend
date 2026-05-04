package org.ymz.app.model.dto.appcase;

import lombok.Data;
import org.ymz.app.model.dto.app.AppAuthor;

import java.time.LocalDateTime;

/**
 * 公开案例摘要。
 *
 * @author ymz
 */
@Data
public class AppCaseSummary {

    private Long id;

    private Long appId;

    private String title;

    private String summary;

    private Boolean featured;

    private String appName;

    private String previewUrl;

    private String coverUrl;

    private AppAuthor author;

    private LocalDateTime reviewedAt;

    private LocalDateTime createdAt;
}
