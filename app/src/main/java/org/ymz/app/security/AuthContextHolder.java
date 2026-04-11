package org.ymz.app.security;

import org.springframework.core.NamedThreadLocal;

/**
 * 认证上下文持有类
 *
 * @author ymz
 */
public class AuthContextHolder {

    private static final ThreadLocal<AuthContext> AUTH_CONTEXT =
            new NamedThreadLocal<>("auth-context");

    public static void set(AuthContext authContext) {
        AUTH_CONTEXT.set(authContext);
    }

    public static AuthContext get() {
        return AUTH_CONTEXT.get();
    }

    public static void clear() {
        AUTH_CONTEXT.remove();
    }
}
