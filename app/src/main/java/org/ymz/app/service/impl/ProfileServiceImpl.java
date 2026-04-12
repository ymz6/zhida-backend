package org.ymz.app.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.ymz.app.converter.UserConverter;
import org.ymz.app.model.dto.profile.UpdateProfileRequest;
import org.ymz.app.model.dto.user.UserInfo;
import org.ymz.app.model.entity.User;
import org.ymz.app.oss.BucketType;
import org.ymz.app.oss.RustFSClient;
import org.ymz.app.service.ProfileService;
import org.ymz.app.service.UserService;
import org.ymz.app.web.exception.BusinessException;
import org.ymz.app.web.response.ResultCode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 *
 * @author ymz
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileServiceImpl implements ProfileService {

    private static final long MAX_AVATAR_SIZE = 1024 * 1024L;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final String AVATAR_PREFIX = "avatar/";
    private static final Tika TIKA = new Tika();

    private final UserService userService;
    private final UserConverter userConverter;
    private final RustFSClient rustFSClient;

    @Override
    public UserInfo getProfile(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "用户不存在");
        }
        return userConverter.toUserInfo(user);
    }

    @Override
    public UserInfo updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userService.getById(userId);
        if (user == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "用户不存在");
        }

        User userToUpdate = User.builder()
                .id(userId)
                .nickname(StrUtil.trim(request.getNickname()))
                .profile(StrUtil.trim(request.getProfile()))
                .build();

        if (!userService.updateById(userToUpdate)) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "更新个人信息失败");
        }
        return userConverter.toUserInfo(userService.getById(userId));
    }

    @Override
    public UserInfo changeAvatar(Long userId, MultipartFile file) {
        User user = userService.getById(userId);
        if (user == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "用户不存在");
        }

        if (file == null || file.isEmpty()) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "头像文件不能为空");
        }

        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "头像文件大小不能超过1MB");
        }

        // 先按文件后缀做快速过滤，尽早拦截明显不合法的上传请求
        // 注意：后缀名只能作为基础校验，不能替代真实文件类型检测
        String extension = FileUtil.extName(file.getOriginalFilename());
        if (StrUtil.isBlank(extension)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "头像文件后缀不合法");
        }
        extension = extension.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "头像仅支持jpg/jpeg/png/webp格式");
        }
        // 严格校验：识别文件的真实类型
        // 由于头像文件体积较小（<= 1MB），这里一次性读取为 byte[]，便于后续复用同一份内容做 MIME 检测、图片解码校验和 OSS 上传。
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "读取头像文件失败", e);
        }
        // 使用 Tika 识别文件真实 MIME 类型，防止伪造后缀绕过校验
        String detectedMimeType = TIKA.detect(fileBytes);
        if (!ALLOWED_MIME_TYPES.contains(detectedMimeType)) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "头像文件格式不合法");
        }
        // 再尝试对图片进行解码，进一步确认文件内容未损坏且可被正常读取
        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            BufferedImage decodedImage = ImageIO.read(is);
            if (decodedImage == null) {
                throw BusinessException.of(ResultCode.INVALID_PARAM, "头像文件损坏或格式不合法");
            }
        } catch (IOException e) {
            throw BusinessException.of(ResultCode.INVALID_PARAM, "头像文件损坏或格式不合法");
        }

        // 先上传新头像，再更新数据库中的头像地址
        // 如果数据库更新失败，需要尽量删除刚上传成功的对象，避免产生孤儿文件
        String avatarKey = AVATAR_PREFIX + IdUtil.fastSimpleUUID() + "." + extension;
        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            rustFSClient.uploadObject(BucketType.PUBLIC, is, avatarKey, detectedMimeType, fileBytes.length);
        } catch (IOException e) {
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "读取头像文件失败", e);
        }
        String avatarUrl = rustFSClient.getPublicObjectUrl(avatarKey);

        User userToUpdate = User.builder()
                .id(userId)
                .avatar(avatarUrl)
                .build();
        if (!userService.updateById(userToUpdate)) {
            // 更新失败，尽量删除刚刚上传成功的文件
            try {
                rustFSClient.deleteObject(BucketType.PUBLIC, avatarKey);
            } catch (Exception ex) {
                log.warn("数据库更新失败后，回滚头像文件失败: key={}", avatarKey, ex);
            }
            throw BusinessException.of(ResultCode.SYSTEM_ERROR, "更新个人头像失败");
        }
        return userConverter.toUserInfo(userService.getById(userId));
    }
}
