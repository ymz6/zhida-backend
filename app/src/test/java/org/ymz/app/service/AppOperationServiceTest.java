package org.ymz.app.service;

import jakarta.annotation.Resource;
import org.springframework.boot.test.context.SpringBootTest;
import org.ymz.app.model.dto.app.ChatRequest;

@SpringBootTest
class AppOperationServiceTest {

    @Resource
    private AppOperationService appOperationService;

    // @Test
    void chat() throws InterruptedException {
        ChatRequest chatRequest = new ChatRequest();
        // chatRequest.setPrompt("做一个简易的Todo List应用");
        chatRequest.setPrompt("加一个已完成/未完成统计的功能，要用一个单独的卡片来统计这些信息");

        appOperationService.chat(1L, 5L, chatRequest)
                // 打印前端实际会收到的流式内容
                .doOnNext(System.out::print)

                // 打印错误
                .doOnError(Throwable::printStackTrace)

                // 等待 Flux 执行完成
                .blockLast();
    }
}