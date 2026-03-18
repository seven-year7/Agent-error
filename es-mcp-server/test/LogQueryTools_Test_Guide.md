## LogQueryTools 功能本地测试指南

本文档说明如何在本地环境下测试 `LogQueryTools.queryFullTraceByRequestId` 功能，确认 ES 查询链路是否正常工作。

---

### 一、前置条件

- **已经启动 Elasticsearch 实例**
  - 能从当前机器访问（例如 `http://localhost:9200`，或你线上 / 测试集群地址）。
- **项目依赖已正常下载**
  - 项目能正常编译（`es-mcp-server` 模块中已引入 `elasticsearch-java`、`spring-boot` 等依赖）。
- **ES 中已有符合条件的日志数据**
  - 至少有一条包含以下字段的日志：
    - `requestId`
    - `@timestamp`
    - 以及按你约定规则生成的索引（例如 `log-<appName>-*`）。

---

### 二、理解整体调用链路

- `LogQueryTools.queryFullTraceByRequestId(...)`（MCP 对外工具）
  - 调用
- `EsLogService.queryFullTraceByRequestId(...)`（业务查询逻辑）
  - 内部会：
    - 通过 `findTraceTimestampByRequestId` 从 `tracelog-*` 索引中找到该 `requestId` 的时间戳；
    - 通过 `findEsIndicesFromHera` 生成需要查询的 ES 索引列表（当前为占位实现：`log-<appName>-*`）；
    - 遍历索引，使用 `queryLogsInIndex` 在时间窗口（`timestamp ±5s`）内按 `requestId`（和可选 `level`）查询日志。

测试目标：**验证这条从 MCP 工具到 ES 的完整调用链在本地能正常跑通，并拿到日志结果。**

---

### 三、方式一：通过 Spring Boot 启动 + MCP 工具调用（推荐）

1. **启动 Spring Boot 应用**
   - 入口类：`EsMcpApplication`
   - 在 IDE 中右键 `EsMcpApplication.main` 运行，或使用命令行：
     - 先进入 `es-mcp-server` 模块目录（如果有多模块结构），再执行：
       ```bash
       mvn spring-boot:run
       ```
   - 启动成功后，`LogQueryTools` 会作为 Spring Bean 被自动加载，且其 `@Tool` 方法会通过 `MethodToolCallbackProvider` 注册为 MCP 工具。

2. **确认 ES 连接配置正确**
   - 你的 ES 客户端配置通常在：
     - `application.yml` / `application.properties`，或
     - 某个专门的配置类（例如 `EsConfig`、`ElasticsearchConfig` 等）。
   - 确认以下信息：
     - ES 地址、端口（如 `localhost:9200`）
     - 协议（`http` / `https`）
     - 用户名、密码（如有认证）

3. **通过 MCP Client / 调试工具调用 `LogQueryTools`**
   - 当 Spring Boot 应用以 MCP Server 模式运行时，你可以使用：
     - 支持 MCP 协议的客户端（例如集成 spring-ai-mcp-server 的上游 LLM 客户端），
     - 或你自己写的调用封装，
   - 来调用工具名称：**`queryFullTraceByRequestId`**，并传入参数：
     - `requestId`: 你想排查的请求 ID（必填）
     - `appName`: 对应 ES 索引中使用的应用名（必填）
     - `iamTreePath`: IAM 树路径（按你定义的规则填写，必填）
     - `level`: 日志级别过滤（可选，如 `"INFO"`, `"ERROR"` 等）
     - `size`: 每个索引返回的最大条数（可选，默认 100）

4. **观察返回结果**
   - 正常返回示例结构大致为：
     - `requestId`: 原请求 ID
     - `appName`: 原应用名
     - `iamTreePath`: 原 IAM 路径
     - `level`: 原 level 参数
     - `size`: 原 size 参数
     - `indices`: 实际命中的索引集合
     - `logsByIndex`: `Map<String, List<Map<String, Object>>>` 形式的日志数据
   - 若：
     - `indices` 为空，或
     - `logsByIndex` 为空 Map，
   - 需要检查：
     - `tracelog-*` 中是否存在该 `requestId` 的记录；
     - `findEsIndicesFromHera` 当前占位逻辑生成的索引前缀是否与你真实的 ES 索引一致；
     - 时间窗口（±5 秒）内是否有对应日志。

---

### 四、方式二：在本地写一个直接调用 LogQueryTools 的测试类

如果你暂时不通过 MCP 客户端，只想在本地 Java 代码里直接调用 `LogQueryTools`，可以新建一个简单的 Spring Boot 测试或启动类，例如（伪代码思路）：

```java
// 示例思路（请在需要时再根据项目结构创建实际类）
@SpringBootApplication
public class LogQueryToolsLocalTestApp {

    public static void main(String[] args) throws IOException {
        ConfigurableApplicationContext context =
                SpringApplication.run(LogQueryToolsLocalTestApp.class, args);

        LogQueryTools tools = context.getBean(LogQueryTools.class);

        Map<String, Object> result = tools.queryFullTraceByRequestId(
                "your-request-id",
                "your-app-name",
                "your-iam-tree-path",
                null,
                50
        );

        System.out.println("查询结果：");
        System.out.println(result);
    }
}
```

> 上面只是一个调用 `LogQueryTools` 的基本示例，如果你需要，我可以在 `test` 目录下为你生成完整可运行的测试类。

---

### 五、常见问题排查建议

1. **返回为空 / 日志条数为 0**
   - 确认：
     - `requestId` 是否真实存在于 `tracelog-*` 索引中；
     - `appName` 与 ES 索引命名规则是否一致（当前索引模式为：`"log-" + appName + "-*"`）；
     - `iamTreePath` 是否正确（后续如果接入 Hera，需要同步这部分逻辑）。

2. **抛出 ES 连接异常**
   - 检查：
     - ES 地址是否可达（防火墙 / 网络）；
     - 协议是否匹配（`http` vs `https`）；
     - 有无认证配置缺失（用户名密码、证书等）。

3. **抛出参数校验异常**
   - 如：
     - `"requestId is required"`
     - `"appName is required"`
     - `"iamTreePath is required"`
   - 说明必填参数缺失或为空，请在调用前检查入参。

---

### 六、后续可以进一步完善的地方

- 将 `findEsIndicesFromHera` 从占位实现改为实际调用 Hera 服务，动态获取 ES 索引列表；
- 在 MCP 层（`LogQueryTools`）根据 spring-ai-mcp-server 的最终规范，调整注解和返回模型；
- 为 `LogQueryTools` 和 `EsLogService` 补充 JUnit / 集成测试，用例中使用嵌入式 ES 或测试集群。

如你希望，我也可以在 `test` 目录下为你自动生成一个实际可运行的 `LogQueryTools` 本地测试类，直接帮你把调用参数串好。

