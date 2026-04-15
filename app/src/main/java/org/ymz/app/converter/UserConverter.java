package org.ymz.app.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import org.ymz.app.model.enums.UserRole;
import org.ymz.app.model.dto.user.UserInfo;
import org.ymz.app.model.entity.User;


/**
 * 用户转换器
 *
 * @author ymz
 */
@Mapper(componentModel = "spring")
public interface UserConverter {

    /**
     * User 转 UserInfo（不包含 password 字段）
     */
    @Mapping(target = "roleText", expression = "java(toRoleText(user.getRole()))")
    UserInfo toUserInfo(User user);

    default String toRoleText(Integer roleCode) {
        UserRole role = UserRole.fromCode(roleCode);
        return role.getText();
    }
}
