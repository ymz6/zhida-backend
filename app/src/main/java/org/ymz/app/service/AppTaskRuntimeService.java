package org.ymz.app.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.ymz.app.model.dto.task.TaskStatusResponse;

/**
 * 应用任务运行时服务。
 *
 * @author ymz
 */
public interface AppTaskRuntimeService {

    SseEmitter streamTask(Long userId, Long taskId);

    TaskStatusResponse startTask(Long userId, Long taskId);
}
