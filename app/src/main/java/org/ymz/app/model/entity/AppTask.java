package org.ymz.app.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.io.Serializable;
import java.time.LocalDateTime;

import java.io.Serial;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 应用任务表 实体类。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("app_task")
public class AppTask implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键 ID
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 应用 ID
     */
    private Long appId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 任务类型：CREATE-创建应用，ITERATE-迭代应用，CHAT-对话答疑，DEPLOY-部署应用
     */
    private String taskType;

    /**
     * 本次任务的用户提示词或操作说明
     */
    private String prompt;

    /**
     * 任务状态：PENDING-待执行，RUNNING-执行中，SUCCESS-执行成功，FAILED-执行失败
     */
    private String status;

    /**
     * 当前执行步骤：INITIALIZING_WORKSPACE-初始化工作区，GENERATING_CODE-生成代码，CHATTING-对话中，BUILDING-构建应用，DEPLOYING-部署应用，FINISHED-已完成
     */
    private String currentStep;

    /**
     * 任务失败时的错误信息
     */
    private String errorMessage;

    /**
     * 任务执行结果摘要
     */
    private String resultSummary;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 任务开始执行时间
     */
    private LocalDateTime startedAt;

    /**
     * 任务执行完成时间
     */
    private LocalDateTime finishedAt;

}
