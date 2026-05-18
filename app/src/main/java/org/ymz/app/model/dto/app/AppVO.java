package org.ymz.app.model.dto.app;

import lombok.Data;
import org.ymz.app.model.dto.user.UserBriefVO;

import java.time.LocalDateTime;

/**
 *
 * @author ymz
 */
@Data
public class AppVO {

    private Long id;

    private UserBriefVO author;

    private String name;

    private String initPrompt;

    private String coverUrl;

    private String deployUrl;

    private String deployKey;

    private LocalDateTime deployedAt;

    private Integer auditStatus;

    private LocalDateTime publishedAt;

    private Boolean featured;

    private LocalDateTime featuredAt;

    private LocalDateTime createdAt;
}
