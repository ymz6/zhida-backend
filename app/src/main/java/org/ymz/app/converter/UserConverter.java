package org.ymz.app.converter;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import org.ymz.app.model.enums.UserRole;
import org.ymz.app.model.dto.user.UserBriefVO;
import org.ymz.app.model.dto.user.UserVO;
import org.ymz.app.model.entity.User;


/**
 * 用户转换器
 *
 * @author ymz
 */
@Mapper(componentModel = "spring")
public interface UserConverter {

    /**
     * User 转 UserVO（不包含 password 字段）
     */
    @Mapping(target = "roleText", expression = "java(toRoleText(user.getRole()))")
    UserVO toUserVO(User user);

    /**
     * User 转公开用户摘要信息。
     */
    @Mapping(target = "isFollowing", ignore = true)
    @Mapping(target = "isFollowed", ignore = true)
    UserBriefVO toUserBriefVO(User user);

    default String toRoleText(Integer roleCode) {
        UserRole role = UserRole.fromCode(roleCode);
        return role.getText();
    }
}
