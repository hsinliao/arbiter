# arbiter-agent

> 一个面向生产环境的 Java Agent 工程底座：以稳定抽象契约为核心，分离控制面与数据面，将可观测性、评估与成本控制作为一等设计约束，支持模型适配、记忆分层、工具治理与运行时编排。

## 简介

`arbiter-agent` 是一个基于 Maven 多模块构建的 Agent 工程底座。项目目标不是做一个“全能黑盒 Agent”，而是提供一套可组合、可扩展、可观测、可评估的 Agent 基础设施，使业务团队能够在此基础上快速构建、治理和演进自己的 Agent 应用。

核心设计原则：

- **控制面 / 数据面分离**：决策与编排、状态存储、工具执行解耦。
- **抽象先行，框架中立**：`core` 与 `spi` 定义稳定契约，框架作为实现细节。
- **显式状态**：执行状态是 `core` 的一等对象，不由模型上下文隐式承载。
- **可观测、可评估、可回放**：可观测性为独立模块，评估闭环从阶段 0 开始。
- **成本与延迟是一等约束**：模型路由与预算熔断在架构层预留，而非上线后补救。

## 模块结构

```text
arbiter-agent
├── arbiter-agent-core           # 核心抽象契约：Agent / Task / Tool / Workflow / State / Event
├── arbiter-agent-runtime        # 执行引擎：Agent Loop、显式状态机、Checkpoint、重试/熔断、策略执行
├── arbiter-agent-llm            # 模型供给：Model Adapter、模型路由、结构化输出
├── arbiter-agent-memory         # 记忆分层：短期 / 工作 / 长期记忆、RAG 检索
├── arbiter-agent-tool           # 内置工具：工具注册、分组与按需加载、描述质量治理
├── arbiter-agent-plugin         # 运行时扩展：插件加载、沙箱隔离、生命周期管理
├── arbiter-agent-observability  # 证据与治理：事件模型、Trace/Metrics/Logs、回放、评估消费
├── arbiter-agent-spi            # 对外扩展契约：Memory / Tool / Model / Plugin / Evaluation SPI
├── pom.xml                      # 聚合与依赖管理
└── .flattened-pom.xml           # flatten-maven-plugin 本地构建产物，建议加入 .gitignore
```

## 已实现能力

| 模块 | 当前实现 |
| --- | --- |
| `core` | Task、Context、Config、Result、显式状态、事件和插件元数据注册表 |
| `runtime` | ReAct Agent Loop、状态机、检查点恢复、模型/工具超时、重试、熔断、Token/成本预算 |
| `llm` | Jackson 3 原生 OpenAI-compatible 客户端、成本/延迟/优先级路由、JSON 结构化输出提取 |
| `memory` | 短期窗口、工作记忆、长期记忆、词法检索与向量余弦检索 |
| `tool` | 工具注册、分组按需暴露、执行超时、描述质量校验和内置系统/文本工具 |
| `plugin` | `@AgentPlugin`、ServiceLoader/JAR 加载、类加载器隔离和生命周期管理 |
| `observability` | Trace 事件记录、指标聚合、时间线、事件回放和评估消费 |
| `spi` | SPI 2.0 不可变模型、统一调用上下文、Provider 错误、Memory、Tool、Plugin、Evaluation 契约 |

SPI 2.0 将 `ModelRequest`/`ModelResponse` 等值对象设计为不可变 record；控制面路由约束通过 `RoutingRequirements` 传递，不进入模型供应商协议。同步模型实现 `ModelClient`，流式模型额外实现 `StreamingModelClient`。

## 快速开始

运行时只依赖抽象契约；内置客户端直接调用 OpenAI-compatible
`/chat/completions`，也可以替换为企业网关或其他 `ModelClient` 实现：

```java
ToolRegistry tools = new ToolRegistry();
tools.register(new ClockTool());

RecordingEventStore events = new RecordingEventStore();
RuntimeAgent agent = RuntimeAgent.builder()
        .modelClient(new OpenAiCompatibleModelClient(
                "https://api.openai.com/v1",
                apiKey,
                "your-model",
                Duration.ofSeconds(5),
                Duration.ofSeconds(60)))
        .toolRegistry(tools)
        .memory(new TieredMemory())
        .eventSink(events)
        .build();

AgentTask task = AgentTask.of("分析当前时间并回答用户");
AgentContext context = AgentContext.create(task.id());
AgentConfig config = AgentConfig.builder()
        .systemPrompt("先使用工具获取事实，再给出简洁答案。")
        .toolGroups(Set.of("system", "text"))
        .maxIterations(6)
        .maxTotalTokens(20_000)
        .timeout(Duration.ofSeconds(60))
        .build();

AgentResult result = agent.run(task, context, config);
```

`AgentResult.status()` 区分成功、失败和取消；失败或中断后，以相同的 `taskId + sessionId` 再次调用 `run`，运行时会读取最近检查点并继续执行。`maxIterations` 表示最多允许的模型推理轮数，达到上限后不会继续执行有副作用的工具。

记忆是显式能力：未注入 `MemoryStore` 时使用 `NoopMemory`，不会自动保存用户输入或模型输出。工具可通过 `ToolResult.data()` 更新 `AgentContext` 变量，这些变量会随检查点一起恢复。

## 扩展点

- 自定义模型：实现同步 `ModelClient`；需要流式能力时额外实现 `StreamingModelClient`，终止回调必须恰好触发一次。
- 统一调用上下文：Provider 接收 `InvocationContext`，其中包含 trace、deadline、取消信号、路由约束和扩展属性。
- 路由约束：`RoutingRequirements` 属于控制面，不进入提供给具体模型的 `ModelRequest`。
- 错误语义：Provider 应抛出带 `ProviderErrorKind`、`retryable` 和 `retryAfter` 的 `ProviderException`。
- 自定义工具：实现 `AgentTool`；`ToolDefinition` 包含输入/输出 Schema、风险等级、幂等性和审批要求。
- 持久化记忆：实现 `MemoryStore.upsert/search/delete`；检索返回带分数和来源的 `MemoryMatch`。
- 持久化检查点：实现 `CheckpointStore`；默认实现是 `InMemoryCheckpointStore`。
- 插件扩展：实现 `AgentPluginProvider.descriptor()` 并注册 `META-INF/services`，或使用 `@AgentPlugin` 注册对象；加载时会校验 SPI 主版本。
- 评估消费：实现 `Evaluator.evaluate(EvaluationCase, EvaluationContext)`，接入离线或在线评估。

## 边界说明

- 插件类加载器提供依赖隔离，但不是操作系统级安全沙箱；不可信插件应放入独立进程或容器运行。
- `TieredMemory` 与默认检查点只保存在当前 JVM，生产部署应实现数据库、Redis 或对象存储适配。
- 取消依赖模型客户端和工具响应线程中断；忽略中断的阻塞实现仍可能占用线程直到底层调用自行返回。
- 检查点在工具批次完成后写入；进程在工具批次中途崩溃时，恢复后可能重复调用该批次工具，副作用工具需要实现幂等或去重键。
- `CostAwareModelRouter` 使用模型声明的静态成本与延迟做策略选择；动态质量、真实延迟和失败率可通过自定义 `ModelRouter` 接入。
- 工具描述校验用于防止模型接口信息缺失，不替代工具参数的业务校验和权限控制。
- Provider 生命周期通过可选 `ProviderLifecycle` 管理；Runtime 默认不接管共享 Provider，需显式调用 `manageModelLifecycle(true)`。

## SPI 2.0 迁移

- `LlmClient.chat/streamChat` 拆分为 `ModelClient.chat(ModelRequest, InvocationContext)` 和可选 `StreamingModelClient.stream(...)`。
- `MemoryStore.add` 改为 `upsert(record, context)`，`search` 返回 `MemoryMatch`。
- `PluginLifecycle.init/destroy` 改为 `start/stop`；`AgentPluginProvider` 通过 `descriptor()` 暴露完整插件元数据。
- `Evaluator` 改为接收 `EvaluationCase` 与 `EvaluationContext`。

## 构建与测试

```bash
mvn -f arbiter-agent/pom.xml test
```
