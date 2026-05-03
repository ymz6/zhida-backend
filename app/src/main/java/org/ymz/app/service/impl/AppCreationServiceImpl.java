package org.ymz.app.service.impl;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ymz.app.ai.dto.TitleGenerateResult;
import org.ymz.app.ai.service.TitleGenerateAssistant;
import org.ymz.app.model.dto.app.CreateAppRequest;
import org.ymz.app.model.dto.app.CreateAppTaskResponse;
import org.ymz.app.model.entity.App;
import org.ymz.app.model.entity.AppTask;
import org.ymz.app.model.enums.app.AppChatMessageRole;
import org.ymz.app.model.enums.app.AppChatMessageType;
import org.ymz.app.model.enums.app.AppDeployStatus;
import org.ymz.app.model.enums.app.AppStatus;
import org.ymz.app.model.enums.app.AppTaskStatus;
import org.ymz.app.model.enums.app.AppTaskType;
import org.ymz.app.monitoring.AppTaskMetrics;
import org.ymz.app.service.AppCreationService;
import org.ymz.app.service.AppService;
import org.ymz.app.service.AppTaskService;
import org.ymz.app.service.generation.AppTaskLogPublisher;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 创建应用和任务记录，但不直接启动耗时生成流程。
 *
 * @author ymz
 */
@Service
@RequiredArgsConstructor
public class AppCreationServiceImpl implements AppCreationService {

    private final TitleGenerateAssistant titleGenerateAssistant;
    private final AppService appService;
    private final AppTaskService appTaskService;
    private final AppTaskLogPublisher appTaskLogPublisher;
    private final AppTaskMetrics appTaskMetrics;

    @Override
    @Transactional
    public CreateAppTaskResponse createApp(Long userId, CreateAppRequest request) {
        String prompt = StrUtil.trim(request.getPrompt());
        TitleGenerateResult titleResult = titleGenerateAssistant.chat(prompt);
        if (titleResult == null || !titleResult.isAccepted() || StrUtil.isBlank(titleResult.getTitle())) {
            String reason = titleResult == null || titleResult.getReason() == null
                    ? "无法识别应用需求"
                    : titleResult.getReason().getDescription();
            throw BusinessException.of(ResultCode.INVALID_PARAM, reason);
        }

        LocalDateTime now = LocalDateTime.now();
        App app = App.builder()
                .userId(userId)
                .name(titleResult.getTitle())
                .initPrompt(prompt)
                .status(AppStatus.CREATING.name())
                .deployStatus(AppDeployStatus.UNDEPLOYED.name())
                .createdAt(now)
                .build();
        appService.save(app);

        AppTask task = AppTask.builder()
                .appId(app.getId())
                .userId(userId)
                .taskType(AppTaskType.CREATE.name())
                .prompt(prompt)
                .status(AppTaskStatus.PENDING.name())
                .createdAt(now)
                .build();
        appTaskService.save(task);
        appTaskMetrics.recordCreated(AppTaskType.CREATE);

        app.setLatestTaskId(task.getId());
        appService.updateById(app);

        appTaskLogPublisher.appendMessage(
                app.getId(),
                task.getId(),
                AppChatMessageRole.USER,
                AppChatMessageType.CHAT,
                prompt,
                Map.of(
                        "title", titleResult.getTitle(),
                        "reason", titleResult.getReason() == null ? "OK" : titleResult.getReason().name()
                )
        );

        return CreateAppTaskResponse.builder()
                .appId(app.getId())
                .taskId(task.getId())
                .name(app.getName())
                .status(app.getStatus())
                .build();
    }
}
