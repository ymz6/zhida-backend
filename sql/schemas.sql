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
    init_prompt    text                                  null comment '应用初始化需求描述',
    status         varchar(32) default 'CREATING'        not null comment '应用状态：CREATING-创建中，GENERATING-生成中，BUILDING-构建中，READY-可使用，EDITING-编辑中，FAILED-失败',
    workspace_path varchar(512)                          null comment '应用源码工作区路径',
    preview_url    varchar(512)                          null comment '应用预览地址',
    cover_url      varchar(512)                          null comment '应用封面图片地址',
    deploy_status  varchar(32) default 'UNDEPLOYED'      not null comment '部署状态：UNDEPLOYED-未部署，DEPLOYING-部署中，DEPLOYED-已部署，FAILED-部署失败',
    deploy_url     varchar(512)                          null comment '应用正式部署后的访问地址',
    deployed_at    datetime                              null comment '最近一次部署完成时间',
    latest_task_id bigint                                null comment '最近一次执行的应用任务 ID',
    error_message  text                                  null comment '应用最近一次失败时的错误信息',
    created_at     datetime    default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment '应用表' collate = utf8mb4_unicode_ci;

create index idx_created_at
    on app (created_at);

create index idx_deploy_status
    on app (deploy_status);

create index idx_latest_task_id
    on app (latest_task_id);

create index idx_status
    on app (status);

create index idx_user_id
    on app (user_id);

-- 应用任务表
create table app_task
(
    id             bigint auto_increment comment '主键 ID'
        primary key,
    app_id         bigint                                not null comment '应用 ID',
    user_id        bigint                                not null comment '用户 ID',
    task_type      varchar(32)                           not null comment '任务类型：CREATE-创建应用，EDIT-修改应用，REPAIR-修复应用，DEPLOY-部署应用',
    prompt         text                                  null comment '本次任务的用户提示词或操作说明',
    status         varchar(32) default 'PENDING'         not null comment '任务状态：PENDING-待执行，RUNNING-执行中，SUCCESS-执行成功，FAILED-执行失败，CANCELED-已取消',
    current_step   varchar(64)                           null comment '当前执行步骤：INITIALIZING_WORKSPACE-初始化工作区，ANALYZING-需求分析，GENERATING_CODE-生成代码，BUILDING-构建应用，DEPLOYING-部署应用，FINISHED-已完成',
    error_message  text                                  null comment '任务失败时的错误信息',
    result_summary text                                  null comment '任务执行结果摘要',
    created_at     datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    started_at     datetime                              null comment '任务开始执行时间',
    finished_at    datetime                              null comment '任务执行完成时间'
)
    comment '应用任务表' collate = utf8mb4_unicode_ci;

create index idx_app_created_at
    on app_task (app_id, created_at);

create index idx_app_id
    on app_task (app_id);

create index idx_app_status
    on app_task (app_id, status);

create index idx_status
    on app_task (status);

create index idx_task_type
    on app_task (task_type);

create index idx_user_created_at
    on app_task (user_id, created_at);

create index idx_user_id
    on app_task (user_id);

-- 应用聊天消息表
create table app_chat_message
(
    id           bigint auto_increment comment '主键 ID'
        primary key,
    app_id       bigint                                not null comment '应用 ID',
    task_id      bigint                                null comment '任务 ID，可为空，普通历史消息可以不绑定具体任务',
    role         varchar(32)                           not null comment '消息角色：USER-用户，ASSISTANT-AI 助手，TOOL-工具，SYSTEM-系统',
    message_type varchar(32) default 'CHAT'            not null comment '消息类型：CHAT-普通对话，PLAN-执行计划，TOOL_CALL-工具调用，TOOL_RESULT-工具结果，BUILD_LOG-构建日志，ERROR-错误信息',
    content      mediumtext                            not null comment '消息内容',
    metadata     text                                  null comment '消息附加信息，JSON 字符串格式，例如工具名称、文件路径、构建状态等',
    created_at   datetime    default CURRENT_TIMESTAMP not null comment '创建时间'
)
    comment '应用对话消息表' collate = utf8mb4_unicode_ci;

create index idx_app_created_at
    on app_chat_message (app_id, created_at);

create index idx_app_id
    on app_chat_message (app_id);

create index idx_message_type
    on app_chat_message (message_type);

create index idx_role
    on app_chat_message (role);

create index idx_task_created_at
    on app_chat_message (task_id, created_at);

create index idx_task_id
    on app_chat_message (task_id);
