package org.ymz.app.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Locale;

/**
 * 用户角色
 * @author ymz
 */
@Getter
@AllArgsConstructor
public enum UserRole {

    USER(0),
    ADMIN(1);

    private final int code;

    public static UserRole fromCode(int code) {
        for (UserRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown UserRole code: " + code);
    }

    public String getText() {
        return name().toLowerCase(Locale.ROOT);
    }
}
