package org.ymz.app.model.dto.app;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Data;

/**
 * 工作台预览会话创建结果。
 *
 * @author ymz
 */
@Data
@Builder
public class PreviewSessionVO {

    /**
     * 前端 iframe 或新窗口可访问的预览入口。
     */
    private String previewUrl;

    /**
     * 预览会话有效秒数。
     */
    private long expiresIn;

    /**
     * 仅用于后端写入 HttpOnly Cookie，不暴露给前端 JSON。
     */
    @JsonIgnore
    private String token;
}
