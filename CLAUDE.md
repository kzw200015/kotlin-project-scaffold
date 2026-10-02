# CLAUDE.md

Spring Boot 4.1 + Kotlin 2.3 + MyBatis + PostgreSQL。完整约定见 README.md，下面是写代码前必须知道的要点。

## 表结构

- **当前表结构以 `db/schema.sql` 为准**（含字段类型、约束、索引和字段注释），不要从迁移文件推断。
- 改表结构：在 `src/main/resources/db/migration/` 新增 `V{n}__{描述}.sql`（不改已有迁移），新表新列用 `COMMENT ON` 写清含义、取值、单位，然后执行 `./gradlew updateSchema` 更新快照。快照过期时 `./gradlew test` 会失败。

## 编码约定

- **SQL**：统一写在 `src/main/resources/mapper/*.xml`，每条语句写完整 SQL，不用 `<sql>` / `<include>`，不用 SQL 注解，不引入 SQL DSL。动态条件用 `<where>` / `<if>` / `<foreach>`。`ILIKE` 拼接用户输入前用 `escapeLike()` 转义并加 `ESCAPE '\'`。
- **结果类型**：data class，字段全部 `val`。需要数据库生成值的插入用 `INSERT ... RETURNING`，写在 `<select flushCache="true">` 里返回新行，不用 `useGeneratedKeys`。
- **分层**：Controller（`@Valid` 校验、`ApiResponse.ok(...)` 显式包装）→ Service（业务逻辑、事务边界）→ Mapper。Controller 不直接调用 Mapper。示例见 `user/`。
- **错误**：业务错误抛 `AppException(code, message)`，业务码 5 位、前三位为 HTTP 状态码；领域错误集中定义在各自包内，写成直接抛出异常的函数（返回 `Nothing`），调用处 `?: UserErrors.notFound(id)`，见 `user/UserErrors.kt`。其他异常由 `platform/ErrorHandler` 只返回 HTTP 状态码，`code`、`message` 为 null。唯一性冲突靠数据库约束 + 捕获 `DuplicateKeyException`（仅在无外层事务时有效），不先查再插。
- **事务**：不用 `@Transactional`，用 `platform/Tx.kt` 显式开启：`tx.write { }` / `tx.read { }`。
- **并发**：Controller / Service 写普通函数（跑在虚拟线程上），不写 `suspend` Controller；需要并发时写 `runBlocking { async(Dispatchers.Virtual) { } }`，不要把调度器传给 `runBlocking`。
- **分包**：按领域分包（如 `user/`），跨领域基础设施放 `platform/`。
- **测试**：Mapper / Service 用 `@MybatisTest` + Testcontainers 跑真实 SQL（Service 需 `@Import(XxxService::class, Tx::class)`）；Controller 用 `@WebMvcTest` + `@MockitoBean` mock Service，验证响应格式与错误码；mock 用 mockito-kotlin。
- **命名**：业务代码（含类名）用英文 camelCase；测试方法名用中文描述被测场景，不用反引号句子，也不加 `@DisplayName`（如 `fun 只读事务拒绝写操作()`）。方法名中不能有空格和中文标点，需要分隔时用下划线。

## 常用命令

```bash
./gradlew build          # 编译 + 全部测试（需要 Docker）
./gradlew updateSchema   # 迁移变更后更新 db/schema.sql
./gradlew bootRun        # 启动，数据源用 SPRING_DATASOURCE_* 环境变量覆盖
```
