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
     * 应用初始化需求描述
     */
    private String initPrompt;

    /**
     * 应用状态：CREATING-创建中，GENERATING-生成中，BUILDING-构建中，READY-可使用，EDITING-编辑中，FAILED-失败
     */
    private String status;

    /**
     * 应用源码工作区路径
     */
    private String workspacePath;

    /**
     * 应用预览地址
     */
    private String previewUrl;

    /**
     * 应用封面图片地址
     */
    private String coverUrl;

    /**
     * 部署状态：UNDEPLOYED-未部署，DEPLOYING-部署中，DEPLOYED-已部署，FAILED-部署失败
     */
    private String deployStatus;

    /**
     * 应用正式部署后的访问地址
     */
    private String deployUrl;

    /**
     * 最近一次部署完成时间
     */
    private LocalDateTime deployedAt;

    /**
     * 最近一次执行的应用任务 ID
     */
    private Long latestTaskId;

    /**
     * 应用最近一次失败时的错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

}
