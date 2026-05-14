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
     * 审核状态：0-草稿，1-待审核，2-审核通过，3-审核拒绝
     */
    private Integer auditStatus;

    /**
     * 最近一次审核通过时间
     */
    private LocalDateTime publishedAt;

    /**
     * 是否精选
     */
    private Boolean featured;

    /**
     * 设置精选时间
     */
    private LocalDateTime featuredAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

}
