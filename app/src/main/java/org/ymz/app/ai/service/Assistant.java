package org.ymz.app.ai.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

/**
 * 测试
 * @author ymz
 */
@AiService
public interface Assistant {
    @SystemMessage("你是一个有用的AI助手")
    String chat(String message);

    @SystemMessage("你是一个有用的中文助手")
    Flux<String> streamChat(String message);
}
