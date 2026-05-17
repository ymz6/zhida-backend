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

-- 应用表
create table app
(
    id             bigint auto_increment comment '主键 ID'
        primary key,
    user_id        bigint                                not null comment '用户 ID',
    name           varchar(128)                          not null comment '应用名称',
    init_prompt    text                                  null comment '应用初始化提示词',
    cover_url      varchar(512)                          null comment '应用封面图片地址',
    deploy_key     varchar(64)                           null comment '应用部署唯一标识',
    deployed_at    datetime                              null comment '最近一次部署完成时间',
    audit_status   tinyint     default 0                 not null comment '审核状态:0草稿,1待审核,2通过,3拒绝',
    published_at   datetime                              null comment '最近审核通过时间',
    featured       tinyint(1)  default 0                 not null comment '是否精选',
    featured_at    datetime                              null comment '设置精选时间',
    created_at     datetime    default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment '应用表' collate = utf8mb4_unicode_ci;
create index idx_created_at
    on app (created_at);
create index idx_audit_featured_published
    on app (audit_status, featured, featured_at, published_at);
create unique index uk_deploy_key
    on app (deploy_key);
create index idx_user_id
    on app (user_id);

-- 应用审核记录表
create table audit_record
(
    id          bigint auto_increment comment '主键 ID'
        primary key,
    app_id      bigint                                not null comment '应用 ID',
    status      tinyint                               not null comment '审核记录状态：1待审，2通过，3拒绝，4撤回',
    auditor_id  bigint                                null comment '审核人 ID',
    remark      varchar(500)                          null comment '审核意见',
    audit_time  datetime                              null comment '审核或撤回完成时间',
    created_at  datetime    default CURRENT_TIMESTAMP not null comment '创建时间，即用户提审时间'
)
    comment '应用审核记录表' collate = utf8mb4_unicode_ci;
create index idx_audit_record_app_id
    on audit_record (app_id);
create index idx_audit_record_status_created
    on audit_record (status, created_at);

-- 大语言模型调用日志表
create table llm_log
(
    id              bigint auto_increment comment '主键 ID'
        primary key,
    model_name      varchar(128)                       null comment '模型名称',
    user_id         bigint                             not null comment '用户 ID',
    status          varchar(32)                        not null comment '调用状态：SUCCESS-成功，FAILED-失败',
    input_tokens    bigint   default 0                 not null comment '输入 Token 数',
    output_tokens   bigint   default 0                 not null comment '输出 Token 数',
    total_tokens    bigint   default 0                 not null comment '总 Token 数',
    usage_json      text                               null comment '模型返回的原始 usage JSON',
    duration_millis bigint                             null comment '调用耗时，单位毫秒',
    error_message   text                               null comment '调用失败信息',
    created_at      datetime default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment '大语言模型日志表' collate = utf8mb4_unicode_ci;

create index idx_llm_created_at
    on llm_log (created_at);

create index idx_llm_user_id
    on llm_log (user_id);


-- 应用聊天消息表
create table app_chat_message
(
    id                bigint auto_increment comment '主键 ID'
        primary key,

    app_id            bigint                             not null comment '应用 ID',
    user_id           bigint                             not null comment '用户 ID',

    role              varchar(32)                        not null comment '消息角色：USER-用户，ASSISTANT-AI 助手',
    -- 预留，后续打算支持思考模式
    reasoning_content mediumtext                         null comment '思考消息内容',
    content           mediumtext                         not null comment '消息内容',

    created_at        datetime default CURRENT_TIMESTAMP not null comment '创建时间',

    index idx_app_user_created (app_id, user_id, created_at)
)
    comment '应用对话消息表'
    collate = utf8mb4_unicode_ci;

-- 用户关注关系表
create table user_follow
(
    id          bigint auto_increment comment '主键 ID'
        primary key,
    follower_id bigint                             not null comment '关注者用户 ID',
    followee_id bigint                             not null comment '被关注者用户 ID',
    created_at  datetime default CURRENT_TIMESTAMP not null comment '关注时间',
    constraint uk_follower_followee
        unique (follower_id, followee_id)
)
    comment '用户关注关系表' collate = utf8mb4_unicode_ci;

create index idx_user_follow_follower_created
    on user_follow (follower_id, created_at);

create index idx_user_follow_followee_created
    on user_follow (followee_id, created_at);

-- 收藏夹表
create table favorite
(
    id          bigint auto_increment comment '主键 ID'
        primary key,
    user_id     bigint                                not null comment '收藏夹拥有者用户 ID',
    name        varchar(100)                          not null comment '收藏夹名称',
    description text                                  null comment '收藏夹描述',
    sort_order  int         default 0                 not null comment '排序值，越小越靠前',
    is_default  tinyint(1)  default 0                 not null comment '是否默认收藏夹',
    created_at  datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    constraint uk_favorite_user_name
        unique (user_id, name)
)
    comment '收藏夹表' collate = utf8mb4_unicode_ci;

create index idx_favorite_user_sort
    on favorite (user_id, sort_order, created_at);

-- 收藏夹-应用关联表
create table favorite_app
(
    id          bigint auto_increment comment '主键 ID'
        primary key,
    favorite_id bigint                             not null comment '收藏夹 ID',
    app_id      bigint                             not null comment '应用 ID',
    created_at  datetime default CURRENT_TIMESTAMP not null comment '收藏时间',
    constraint uk_favorite_app
        unique (favorite_id, app_id)
)
    comment '收藏夹应用关联表' collate = utf8mb4_unicode_ci;

create index idx_favorite_app_favorite_created
    on favorite_app (favorite_id, created_at);

create index idx_favorite_app_app
    on favorite_app (app_id);
