# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.



## 常用命令

```bash
./mvnw clean package                        # 构建所有模块
./mvnw spring-boot:run -pl app              # 启动应用
./mvnw test -pl app -Dtest=TestClassName    # 运行单个测试类
./mvnw test -pl app -Dtest=TestClassName#methodName  # 运行单个测试方法
```

## 模块结构

多模块 Maven 项目（Java 21，Spring Boot 3.5.11）：

- `app`：主应用模块
- `mybatis-flex-code-gen`：MyBatis-Flex 代码生成工具（严禁扫描此模块）

## 架构概览

### 技术栈

- ORM: MyBatis-Flex
- Bean 映射: MapStruct
- 参数校验：Spring Validation
- AOP：Spring AOP
- OpenAPI 文档: SpringDoc
- 工具库: Hutool
- 数据库: MySQL8

### 主应用模块目录结构

```
src/main/java/org/ymz/app/
├── config/                  # 全局配置类
├── ai/                      # AI 模块
├── controller/              # 控制器层
├── converter/               # MapStruct 转换器
├── deployment/              # 应用部署与封面生成能力
├── mapper/                  # 数据库访问层
├── model/                   # 数据模型层
│   ├── dto/                 # 数据传输对象
│   ├── enums/               # 枚举类
│   └── entity/              # 数据库实体类
├── oss/                     # OSS 对象存储服务
├── security/                # 安全认证与授权
├── service/                 # 业务逻辑层接口
│   └── impl/                # 业务逻辑层实现
├── utils/                   # 工具类
├── web/                     # Web 层通用基础设施
│   ├── exception/           # 全局异常处理
│   └── response/            # 统一响应封装
└── Application.java         # 启动类
```

## 关键约定

### 统一响应

- 所有 Controller 返回 `Response<T>`实例
- 使用静态工厂方法（`Response.ok`、`Response.fail`）构建`Response<T>`实例

### 分页

- 通用的分页请求封装类位于`model/dto/page`中
- 基础分页必须继承 `PageQuery`
- 需要排序支持时必须继承 `SortablePageQuery` ，且必须实现 `resolveSortColumn()` 以将前端字段名映射为 `QueryColumn`
- Controller 返回 `PageResult<T>`，使用 `PageResult.of(page, converter)` 将实体转换为 DTO

### MyBatis-Flex 查询风格

使用 APT 生成的 `TableDef` 构建查询

示例写法：

```
QueryWrapper query = new QueryWrapper()
    .select(USER.ALL_COLUMNS)
    .from(USER)
    .where(USER.STATUS.eq(1))
    .orderBy(USER.CREATE_TIME.desc());
```

### MapStruct

统一使用 MapStruct 的 Mapper （converter）完成 entity 与 dto 的相互转换

创建 converter：定义 Converter 接口并添加 `@Mapper(componentModel = "spring")` 注解，以将 Converter 实例移交 Spring 容器管理

### 代码风格

- 业务实现禁止过度封装
- 过度封装的行为包括但不限于抽取仅调用一次的私有工具方法、使用一次的静态常量等

### SpringDoc注解

Controller 需遵守以下规则：

- 每个 Controller 类添加 `@Tag(name = "...")` 指定Kebab Case风格的简洁的英文分组名，例如`admin-user`、`auth`
- 每个接口方法添加 `@Operation(operationId = "...")`，大部分情况下保持和请求方法名同名
- 不加 `summary`、`description`、`@Schema` 等额外注解，保持最小侵入
