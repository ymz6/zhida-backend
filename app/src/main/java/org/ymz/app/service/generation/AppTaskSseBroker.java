package org.ymz.app.service.generation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 任务事件的内存分发器，持久化历史仍以 app_chat_message 为准。
 *
 * @author ymz
 */
@Slf4j
@Component
public class AppTaskSseBroker {

    private static final long TASK_STREAM_TIMEOUT_MILLIS = 60 * 60 * 1000L;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(Long taskId) {
        SseEmitter emitter = new SseEmitter(TASK_STREAM_TIMEOUT_MILLIS);
        emitters.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onTimeout(() -> remove(taskId, emitter));
        emitter.onError(ignored -> remove(taskId, emitter));
        return emitter;
    }

    public void send(SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
        }
    }

    public void publish(Long taskId, String eventName, Object data) {
        List<SseEmitter> taskEmitters = emitters.get(taskId);
        if (taskEmitters == null || taskEmitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : taskEmitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                log.debug("Failed to send task stream event, taskId={}", taskId, e);
                remove(taskId, emitter);
                emitter.completeWithError(e);
            }
        }
    }

    public void complete(Long taskId) {
        List<SseEmitter> taskEmitters = emitters.remove(taskId);
        if (taskEmitters == null) {
            return;
        }
        for (SseEmitter emitter : taskEmitters) {
            emitter.complete();
        }
    }

    private void remove(Long taskId, SseEmitter emitter) {
        List<SseEmitter> taskEmitters = emitters.get(taskId);
        if (taskEmitters == null) {
            return;
        }
        taskEmitters.remove(emitter);
        if (taskEmitters.isEmpty()) {
            emitters.remove(taskId);
        }
    }
}
