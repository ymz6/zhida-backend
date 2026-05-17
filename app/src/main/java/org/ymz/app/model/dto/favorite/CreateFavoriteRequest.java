package org.ymz.app.model.dto.favorite;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建收藏夹请求。
 *
 * @author ymz
 */
@Data
public class CreateFavoriteRequest {

    @NotBlank(message = "收藏夹名称不能为空")
    @Size(max = 100, message = "收藏夹名称不能超过100个字符")
    private String name;

    @Size(max = 500, message = "收藏夹描述不能超过500个字符")
    private String description;
}
