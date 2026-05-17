package org.ymz.app.model.dto.favorite;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏夹展示信息。
 *
 * @author ymz
 */
@Data
public class FavoriteVO {

    private Long id;

    private Long userId;

    private String name;

    private String description;

    private Integer sortOrder;

    private Boolean isDefault;

    private Long appCount;

    private LocalDateTime createdAt;
}
