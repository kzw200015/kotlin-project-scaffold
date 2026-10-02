# kotlin-project-scaffold

Spring Boot 4.1 + Kotlin 2.3 + MyBatis 脚手架，由 [start.spring.io](https://start.spring.io) 生成（Gradle Kotlin DSL、Java 25），数据库使用 PostgreSQL。

## 技术选型

| 用途 | 依赖 |
| --- | --- |
| Web | spring-boot-starter-webmvc、jackson-module-kotlin（Jackson 3） |
| 并发 | 虚拟线程（`spring.threads.virtual.enabled=true`，Tomcat 请求线程与 `@Async` 等执行器均为虚拟线程）、kotlinx-coroutines（core + reactor，Spring 协程支持所需） |
| 数据访问 | mybatis-spring-boot-starter 4.1、mybatis-dynamic-sql 2.0（Kotlin DSL，类型安全的 SQL 构建） |
| 迁移 | Flyway（`src/main/resources/db/migration`） |
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
```

## MyBatis 约定

示例见 `src/main/kotlin/com/example/scaffold/user`：

- `UserDynamicSqlSupport`：表与列的元模型（`AliasableSqlTable` + `column<T>()`）
- `UserMapper`：mapper 接口只声明 `@SelectProvider` / `@InsertProvider` 等基础方法，查询通过扩展函数用 Kotlin DSL 编写：

```kotlin
mapper.select {
    where { email isLike "%@example.com" }
    orderBy(name)
}
```

- 结果映射：开启 `map-underscore-to-camel-case` 与 `arg-name-based-constructor-auto-mapping`，并配合 Kotlin 的 `javaParameters` 编译选项，data class 直接按构造参数名映射，无需无参构造或 XML resultMap。

## 事务约定

不使用 `@Transactional`，事务边界用 `Tx`（`platform/Tx.kt`）显式写在代码里，避免 AOP 代理的隐式问题：同类内部调用时注解失效；默认只对 unchecked 异常回滚，而 Kotlin 不区分 checked 异常，抛出 `IOException` 之类时事务会照常提交。

```kotlin
@Service
class UserService(private val tx: Tx, private val mapper: UserMapper) {
    fun register(name: String, email: String): UserRecord = tx.write {
        val row = UserRecord(name = name, email = email)
        mapper.insert(row)
        row
    }

    fun find(id: Long): UserRecord? = tx.read { mapper.selectById(id) }
}
```

- `write` / `read` 均为 REQUIRED 传播：已有事务则加入，否则新开；`read` 为只读事务，写操作会被数据库拒绝。
- block 抛出任何异常（包括 checked 异常）都会回滚，异常原样抛出；不抛异常也要回滚时调用 `status.setRollbackOnly()`：`tx.write { status -> ... }`。
- 只是单条语句、不需要原子性的读写可以不包事务（MyBatis 默认自动提交）。

## 并发约定（虚拟线程 + 协程）

MyBatis / JDBC 是阻塞 IO，吞吐由虚拟线程解决；协程只用来在一次调用内做并发。

- **Controller / Service 写普通函数**：请求跑在 Tomcat 虚拟线程上，事务用 `Tx` 显式开启（见下文「事务约定」）。
- **不要写 `suspend` 的 Controller**：Spring MVC 以不指定调度器的方式执行 `suspend` 函数，挂起恢复后会跑在 kotlinx 内部的平台线程上，此时再调阻塞的 MyBatis 会卡住该线程；且 JDBC 事务绑定在线程上，挂起恢复换线程后事务就丢了。
- **需要并发时**用 `Dispatchers.Virtual`（`platform/Coroutines.kt`，每个任务一个虚拟线程）：

```kotlin
fun dashboard(userId: Long): Dashboard = runBlocking(Dispatchers.Virtual) {
    val profile = async { profileClient.get(userId) }
    val orders = async { orderMapper.select { where { OrderDynamicSqlSupport.userId isEqualTo userId } } }
    Dashboard(profile.await(), orders.await())
}
```

- **注意 ThreadLocal 不跟随**：`async` 里的代码运行在别的线程上，不在调用方的事务里，MDC、SecurityContext 也不会自动传递；需要事务的写操作放回调用方线程执行。
