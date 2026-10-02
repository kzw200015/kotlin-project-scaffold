# kotlin-project-scaffold

Spring Boot 4.1 + Kotlin 2.3 + MyBatis 脚手架，由 [start.spring.io](https://start.spring.io) 生成（Gradle Kotlin DSL、Java 25），数据库使用 PostgreSQL。

## 技术选型

| 用途 | 依赖 |
| --- | --- |
| Web | spring-boot-starter-webmvc、jackson-module-kotlin（Jackson 3） |
| 并发 | 虚拟线程（`spring.threads.virtual.enabled=true`，Tomcat 请求线程与 `@Async` 等执行器均为虚拟线程）、kotlinx-coroutines（core + reactor，Spring 协程支持所需） |
| 数据访问 | mybatis-spring-boot-starter 4.1（XML mapper） |
| 迁移 | Flyway（`src/main/resources/db/migration`），当前表结构快照见 `db/schema.sql` |
| 日志 | SLF4J + Logback（Spring Boot 默认）；惰性日志用 SLF4J 2 fluent API：`log.atDebug().log { "id=$id" }` |
| 测试 | JUnit 5 + kotlin-test、Mockito + mockito-kotlin（`mock<T>()`、`whenever`），Spring 中替换 bean 用 `@MockitoBean`、`@MybatisTest`、Testcontainers |

## 快速开始

数据源默认指向 `jdbc:postgresql://localhost:5432/app`（见 `application.yaml`），连接其他数据库时用环境变量覆盖：

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://dev-db:5432/app
export SPRING_DATASOURCE_USERNAME=app
export SPRING_DATASOURCE_PASSWORD=******

./gradlew bootRun       # 启动时自动执行 Flyway 迁移
./gradlew test          # 需要 Docker，Testcontainers 启动临时 PostgreSQL
./gradlew bootTestRun   # 需要 Docker，用 Testcontainers 临时库启动应用（无需准备数据库）
./gradlew updateSchema  # 需要 Docker，迁移变更后更新 db/schema.sql
```

## 表结构

`db/schema.sql` 是 Flyway 迁移后的完整表结构快照（`pg_dump --schema-only` 导出，含约束、索引和字段注释），查看当前有哪些表、字段时以它为准，不必逐个阅读迁移文件。

- **生成**：`SchemaSnapshotTests` 用 Testcontainers 启动 PostgreSQL、执行迁移后，调用容器内的 `pg_dump` 导出（版本与数据库一致，本机无需安装）。
- **防过期**：普通 `./gradlew test` 会比对快照与真实结构，不一致即失败；执行 `./gradlew updateSchema` 重新生成并提交。
- **改表流程**：新增迁移 `V{n}__{描述}.sql`（不修改已有迁移）→ `./gradlew updateSchema` → 迁移与快照一起提交。
- **字段注释**：新表新列用 `COMMENT ON TABLE / COLUMN` 写明含义、取值范围、单位，注释会随快照导出，数据库客户端中也可见。

## MyBatis 约定

SQL 统一写在 XML 中（不用 `@Select` 等注解：注解参数只能是编译期常量，SQL 字符串无法做任何处理，动态 SQL 还要在字符串里写 `<script>`），示例见 `user/UserMapper.kt` 与 `resources/mapper/UserMapper.xml`。

- **接口**：普通 Kotlin 接口加 `@Mapper`，启动时自动扫描注册为 bean。多个参数不需要 `@Param`，XML 中直接按参数名引用（`#{keyword}`、`#{limit}`），依赖 `javaParameters` 编译选项。
- **XML**：放在 `resources/mapper/` 下（`mybatis.mapper-locations`），`namespace` 为接口全限定名，语句 `id` 与方法名一致；动态条件用 `<where>` / `<if>` / `<foreach>`。
- **完整 SQL**：每条语句写完整 SQL，不用 `<sql>` / `<include>` 抽取片段（重复的列清单、条件直接写出来，SQL 所见即所得、可直接复制到数据库客户端执行）。
- **结果映射**：开启 `map-underscore-to-camel-case` 与 `arg-name-based-constructor-auto-mapping`，`resultType` 写 data class 全限定名即可按构造参数名映射，无需无参构造或 `resultMap`；有默认参数的 data class 需在构造函数上标 `@AutomapConstructor`。
- **记录不可变**：data class 字段全部用 `val`。需要数据库生成值（自增 id、默认时间）的插入用 `INSERT ... RETURNING` 直接返回新行，而不是 `useGeneratedKeys` 回填参数对象；`<insert>` 只能返回影响行数，因此用 `<select flushCache="true">` 执行。
- **可空性**：返回单行的方法声明为可空类型（如 `UserRecord?`），查不到时为 `null`。

## 事务约定

不使用 `@Transactional`，事务边界用 `Tx`（`platform/Tx.kt`）显式写在代码里，避免 AOP 代理的隐式问题：同类内部调用时注解失效；默认只对 unchecked 异常回滚，而 Kotlin 不区分 checked 异常，抛出 `IOException` 之类时事务会照常提交。

```kotlin
@Service
class UserService(private val tx: Tx, private val mapper: UserMapper) {
    fun rename(id: Long, name: String): UserRecord = tx.write {
        check(mapper.updateName(id, name) == 1) { "用户不存在: $id" }
        mapper.findById(id)!!
    }

    fun find(id: Long): UserRecord? = tx.read { mapper.findById(id) }
}
```

- `write` / `read` 均为 REQUIRED 传播：已有事务则加入，否则新开；`read` 为只读事务，写操作会被数据库拒绝。
- block 抛出任何异常（包括 checked 异常）都会回滚，异常原样抛出；不抛异常也要回滚时调用 `status.setRollbackOnly()`：`tx.write { status -> ... }`。
- 只是单条语句、不需要原子性的读写可以不包事务（MyBatis 默认自动提交）。

## 并发约定（虚拟线程 + 协程）

MyBatis / JDBC 是阻塞 IO，吞吐由虚拟线程解决；协程只用来在一次调用内做并发。

- **Controller / Service 写普通函数**：请求跑在 Tomcat 虚拟线程上，事务用 `Tx` 显式开启（见上文「事务约定」）。
- **不要写 `suspend` 的 Controller**：Spring MVC 以不指定调度器的方式执行 `suspend` 函数，挂起恢复后会跑在 kotlinx 内部的平台线程上，此时再调阻塞的 MyBatis 会卡住该线程；且 JDBC 事务绑定在线程上，挂起恢复换线程后事务就丢了。
- **需要并发时**用 `Dispatchers.Virtual`（`platform/Coroutines.kt`，每个任务一个虚拟线程）：

```kotlin
fun dashboard(userId: Long): Dashboard = runBlocking(Dispatchers.Virtual) {
    val profile = async { profileClient.get(userId) }
    val orders = async { orderMapper.findByUserId(userId) }
    Dashboard(profile.await(), orders.await())
}
```

- **注意 ThreadLocal 不跟随**：`async` 里的代码运行在别的线程上，不在调用方的事务里，MDC、SecurityContext 也不会自动传递；需要事务的写操作放回调用方线程执行。
