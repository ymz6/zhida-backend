package org.ymz.app.model.dto.favorite;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑收藏夹请求。
 *
 * @author ymz
 */
@Data
public class UpdateFavoriteRequest {

    @Size(max = 100, message = "收藏夹名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "收藏夹描述不能超过500个字符")
    private String description;

    @Min(value = 0, message = "排序值不能小于0")
    private Integer sortOrder;
}
