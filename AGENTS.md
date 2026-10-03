# AGENTS.md

Spring Boot 4.1 + Kotlin 2.3 + MyBatis + PostgreSQL。完整约定见 README.md，下面是写代码前必须知道的要点。

## 表结构

- **当前表结构以 `db/schema.sql` 为准**（含字段类型、约束、索引和字段注释），不要从迁移文件推断。
- 改表结构：在 `src/main/resources/db/migration/` 新增 `V{n}__{描述}.sql`（不改已有迁移），新表新列用 `COMMENT ON` 写清含义、取值、单位，然后执行 `./gradlew updateSchema` 更新快照。快照过期时 `./gradlew test` 会失败。

## 编码约定

- **SQL**：统一写在 `src/main/resources/mapper/*.xml`，每条语句写完整 SQL，不用 `<sql>` / `<include>`，不用 SQL 注解，不引入 SQL DSL。动态条件用 `<where>` / `<if>` / `<foreach>`。`ILIKE` 拼接用户输入前用 `escapeLike()` 转义并加 `ESCAPE '\'`。
- **结果类型**：data class，字段全部 `val`。需要数据库生成值的插入用 `INSERT ... RETURNING`，写在 `<select flushCache="true">` 里返回新行，不用 `useGeneratedKeys`。
- **分层**：Controller（`@Valid` 校验，直接返回资源，不包装；创建用 `@ResponseStatus(HttpStatus.CREATED)`，无返回内容用 `NO_CONTENT`）→ Service（业务逻辑、事务边界）→ Mapper。Controller 不直接调用 Mapper。示例见 `user/`。
- **错误**：业务错误抛 `AppException(code, message)`，业务码 5 位、前三位为 HTTP 状态码；领域错误集中定义在各自包内，写成直接抛出异常的函数（返回 `Nothing`），调用处 `?: UserErrors.notFound(id)`，见 `user/UserErrors.kt`。所有错误由 `platform/ErrorHandler` 写成 Problem Details（RFC 9457，`application/problem+json`）：业务错误的 message 为 `detail`、业务码为扩展字段 `code`，其他异常只有状态码和 `title`，没有 `code`；它同时接管 `/error`，Filter 中的异常、`sendError` 也返回同样格式。唯一性冲突靠数据库约束 + 捕获 `DuplicateKeyException`（仅在无外层事务时有效），不先查再插。
- **鉴权**：Spring Security + 有状态 Bearer token：token 是随机数，会话存 Redis 自动过期（`auth/TokenService.kt`，它同时是 `OpaqueTokenIntrospector`，每个请求查 Redis 得到用户 id、从数据库加载角色），注销即删除会话。授权规则和 401/403 处理在 `auth/SecurityConfig.kt`，除其中列出的公开接口外都要求登录，新增公开接口时加到那里。角色存 `user_roles` 表、枚举 `auth/Role.kt`，改角色立即生效；按 URL 能确定的角色规则写在 `SecurityConfig`，依赖方法参数的用 `@PreAuthorize` 写在 Controller 方法上，两者的 403 都由 `ExceptionTranslationFilter` 统一处理。Controller 取当前用户用参数 `authentication: Authentication` + `authentication.userId`。密码只存 `PasswordEncoder` 生成的哈希，记录类不含密码哈希字段，登录用的凭证单独查（`UserCredential`）。邮箱入库和查询前用 `normalizeEmail()` 去空白、转小写。
- **事务**：不用 `@Transactional`，用 `platform/Tx.kt` 显式开启：`tx.write { }` / `tx.read { }`。
- **并发**：Controller / Service 写普通函数（跑在虚拟线程上），不写 `suspend` Controller；需要并发时写 `runBlocking { async(Dispatchers.Virtual) { } }`，不要把调度器传给 `runBlocking`。
- **分包**：按领域分包（如 `user/`；登录、会话、角色和授权规则在 `auth/`），跨领域基础设施放 `platform/`。Service 可以直接用其他领域的 Mapper（如 `auth` 用 `UserMapper` 查凭证），包之间不要循环依赖：`auth` 依赖 `user`，`user` 不依赖 `auth`。
- **测试**：需要数据库的测试类实现 `PostgresContainer` 接口，所有测试共用一个 PostgreSQL 容器（不要改回 `@Bean` 声明：任一上下文关闭都会停掉它）；Mapper / Service 用 `@MybatisTest` 跑真实 SQL（Service 再导入 `XxxService::class` 及其依赖的 bean，如 `Tx::class`、用到密码时的 `PasswordEncoderConfig::class`）；Controller 用 `@WebMvcTest` + `@Import(SecurityConfig::class)` + `@MockitoBean` mock Service 和 `OpaqueTokenIntrospector`，需要登录的请求加 `with(opaqueToken())`（它不经过 TokenService，需要角色时用 `.authorities(SimpleGrantedAuthority("ROLE_ADMIN"))` 直接给出；常用的 `asUser(id)` / `asAdmin()` 见 `MockMvcSupport.kt`），验证响应格式与错误码；经过 `/error` 的响应体（Filter 异常、401、403）用 `RANDOM_PORT` + `@AutoConfigureRestTestClient` 启动真实服务器、注入 `RestTestClient` 验证，见 `auth/AuthTests.kt`（需要 Redis 的测试再实现 `RedisContainer`）；mock 用 mockito-kotlin。
- **命名**：业务代码（含类名）用英文 camelCase；测试方法名用中文描述被测场景，不用反引号句子，也不加 `@DisplayName`（如 `fun 只读事务拒绝写操作()`）。方法名中不能有空格和中文标点，需要分隔时用下划线。

## 常用命令

```bash
./gradlew build          # 编译 + 全部测试（需要 Docker）
./gradlew updateSchema   # 迁移变更后更新 db/schema.sql
./gradlew bootRun        # 启动，需要 PostgreSQL 和 Redis；用 SPRING_DATASOURCE_* / SPRING_DATA_REDIS_* 环境变量覆盖
```
