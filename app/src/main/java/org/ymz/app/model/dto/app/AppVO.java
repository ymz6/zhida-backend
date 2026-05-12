package org.ymz.app.model.dto.app;

import lombok.Data;
import org.ymz.app.model.dto.user.UserVO;

import java.time.LocalDateTime;

/**
 *
 * @author ymz
 */
@Data
public class AppVO {

    private Long id;

    private UserVO author;

    private String name;

    private String coverUrl;

    private String deployKey;

    private LocalDateTime deployedAt;

    private LocalDateTime createdAt;
}
