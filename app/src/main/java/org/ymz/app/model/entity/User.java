package org.ymz.app.model.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户表 实体类。
 *
 * @author ymz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 自增主键
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 用户账号，唯一；业务上限制不超过32个字符
     */
    private String account;

    /**
     * 经过 BCrypt 加密后的密码
     */
    private String password;

    /**
     * 用户角色：0-普通用户，1-管理员
     */
    private Integer role;

    /**
     * 用户昵称，业务上限制不超过10个中文字符
     */
    private String nickname;

    /**
     * 个人简介，业务上限制不超过100个字符
     */
    private String profile;

    /**
     * 用户头像 URL
     */
    private String avatar;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
