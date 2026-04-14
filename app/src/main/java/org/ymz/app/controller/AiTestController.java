package org.ymz.app.controller;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ymz.app.ai.service.Assistant;
import reactor.core.publisher.Flux;

@Hidden
@Tag(name = "ai-test")
@RestController
@RequiredArgsConstructor
public class AiTestController {

    private final Assistant assistant;

    @GetMapping("/ai/test")
    public String test(@RequestParam String message) {
        return assistant.chat(message);
    }

    @GetMapping(value = "/ai/test/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> testStream(@RequestParam String message) {
        return assistant.streamChat(message);
    }
}