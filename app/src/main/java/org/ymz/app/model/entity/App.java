package org.ymz.app.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 应用表 实体类。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app")
public class App implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 应用名称
     */
    private String name;

    /**
     * 应用初始化提示词
     */
    private String initPrompt;

    /**
     * 应用封面图片地址
     */
    private String coverUrl;

    /**
     * 应用部署唯一标识
     */
    private String deployKey;

    /**
     * 最近一次部署完成时间
     */
    private LocalDateTime deployedAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

}
