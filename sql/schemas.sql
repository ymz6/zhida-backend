create database if not exists  `zhida`;
use `zhida`;

-- 用户表
create table user
(
    id          bigint auto_increment comment '自增主键'
        primary key,
    account     varchar(64)                            not null comment '用户账号，唯一；业务上限制不超过32个字符',
    password    varchar(255)                           not null comment '经过 BCrypt 加密后的密码',
    role        tinyint      default 0                 not null comment '用户角色：0-普通用户，1-管理员',
    nickname    varchar(30)  default ''                not null comment '用户昵称，业务上限制不超过10个中文字符',
    profile     varchar(255) default ''                not null comment '个人简介，业务上限制不超过100个字符',
    avatar      varchar(255) default ''                not null comment '用户头像 URL',
    create_time datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint uk_account
        unique (account)
)
    comment '用户表' collate = utf8mb4_unicode_ci;

create index idx_create_time
    on user (create_time);