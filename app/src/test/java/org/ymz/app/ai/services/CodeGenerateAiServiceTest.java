package org.ymz.app.ai.services;

import dev.langchain4j.invocation.InvocationParameters;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@SpringBootTest
class CodeGenerateAiServiceTest {

    @Resource
    CodeGenerateAiService codeGenerateAiService;

//    @Test
    void chat() throws InterruptedException {
        // 用于阻塞测试线程，方便看到 TokenStream 的效果
        // 1 的含义：这个 latch 需要被 countDown() 一次，等待它的线程才会放行。
        CountDownLatch latch = new CountDownLatch(1);

        StringBuilder answerBuilder = new StringBuilder();
        AtomicInteger tokenCount = new AtomicInteger();
        AtomicInteger toolCount = new AtomicInteger();

        codeGenerateAiService.chat(4L, "帮我做一个简单的Todo List 应用，支持代办事项的创建、删除", InvocationParameters.from(Map.of()))
                // 1. 普通文本流式输出：最核心，用来观察打字机效果
                .onPartialResponse(partialResponse -> {
                    tokenCount.incrementAndGet();
                    answerBuilder.append(partialResponse);
                    // 测试时用 print 更直观，不要用 println，否则每个 token 都换行
                    System.out.print(partialResponse);
                })
                // 3. 中间模型响应：通常在工具调用循环中出现
                .onIntermediateResponse(intermediateResponse -> {
                    log.info("\n\n[onIntermediateResponse] 中间响应：{}", intermediateResponse);
                })
                // 4. 工具执行前：适合观察 AI 准备调用哪个工具
                .beforeToolExecution(beforeToolExecution -> {
                    int index = toolCount.incrementAndGet();

                    log.info("\n\n[beforeToolExecution] 第 {} 次工具调用，准备执行：{}",
                            index,
                            beforeToolExecution
                    );
                })
                // 5. 工具执行后：适合观察工具结果
                .onToolExecuted(toolExecution -> {
                    log.info("\n\n[onToolExecuted] 工具执行完成：{}", toolExecution);
                })
                // 6. 最终完整响应：适合做日志、保存最终结果、统计 usage
                .onCompleteResponse(completeResponse -> {
                    log.info("\n\n[onCompleteResponse] 流式响应完成");
                    log.info("[onCompleteResponse] token 片段数量：{}", tokenCount.get());
                    log.info("[onCompleteResponse] 工具调用次数：{}", toolCount.get());
                    log.info("[onCompleteResponse] 最终拼接文本：\n{}", answerBuilder);
                    log.info("[onCompleteResponse] 完整 ChatResponse：{}", completeResponse);
                    latch.countDown();
                })
                // 7. 异常处理：必须加，否则测试失败时不好排查
                .onError(error -> {
                    log.error("\n\n[onError] 流式响应异常", error);
                    latch.countDown();
                })
                // 8. 真正启动流式请求
                .start();

        // 阻塞测试线程
        latch.await();
    }
}
