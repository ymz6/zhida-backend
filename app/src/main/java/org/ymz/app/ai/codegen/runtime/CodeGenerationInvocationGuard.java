package org.ymz.app.ai.codegen.runtime;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 保证同一应用的生成调用串行执行，避免破坏 app 级记忆。
 *
 * @author ymz
 */
@Component
public class CodeGenerationInvocationGuard {

    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public <T> T withAppLock(Long appId, CheckedSupplier<T> supplier) {
        ReentrantLock lock = locks.computeIfAbsent(appId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get();
    }
}
