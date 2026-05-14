package org.ymz.app.model.dto.audit;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员设置精选请求。
 *
 * @author ymz
 */
@Data
public class SetFeaturedRequest {

    @NotNull(message = "精选状态不能为空")
    private Boolean featured;
}
