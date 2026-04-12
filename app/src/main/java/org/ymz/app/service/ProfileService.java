package org.ymz.app.service;

import org.springframework.web.multipart.MultipartFile;
import org.ymz.app.model.dto.profile.UpdateProfileRequest;
import org.ymz.app.model.dto.user.UserInfo;

/**
 *
 * @author ymz
 */
public interface ProfileService {
    /**
     * 获取个人信息
     */
    UserInfo getProfile(Long userId);

    /**
     * 编辑个人信息
     */
    UserInfo updateProfile(Long userId, UpdateProfileRequest request);

    /**
     * 换头像
     */
    UserInfo changeAvatar(Long userId, MultipartFile file);
}
