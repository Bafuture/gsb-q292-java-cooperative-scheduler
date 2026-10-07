# 协作式轻量任务调度器

Pair-wise GSB 标注任务仓库（第 18 批 / 292）。

| 项目 | 内容 |
|------|------|
| 任务类型 | Feature 迭代 |
| 任务难度 | 困难 |
| 语言/框架 | Java, Maven, JUnit 5 |
| 环境可复现等级 | 无外部依赖 |
| 构建方式 | Maven（含 mvnw wrapper，无需本机安装 Maven） |

> 本仓库是**初始环境快照**：只有工程骨架，不含任何实现代码。
> 分支说明：`main` 为初始环境；`A`、`B` 为两次独立执行各自的工作分支，均从 `main` 的同一个提交拉出。

## 运行方式

```bash
./mvnw -q verify
```

## 任务提示词

以下为本题完整的 User Prompt 原文，两次执行必须使用完全相同的文本。

我们的业务任务需要在可控的位置主动让出，避免某个任务长时间独占线程。请从零实现一个协作式轻量任务调度器。仓库目前只有一个空的 Maven 工程（pom.xml 只声明 JUnit 5 与 AssertJ）。要求：1) 任务以可恢复的单元形式编写，在显式让出点挂起，由调度器决定何时恢复；2) 支持固定优先级与轮转两种调度策略，策略可配置；3) 让出点必须可枚举：调度器能记录每个任务执行过的让出点，用于复现执行路径；4) 支持任务取消：在让出点检查取消标志，取消后不得再执行任务体的后续部分；5) 支持公平性验证：等优先级任务在轮转策略下不得被长期饿死，需有测试证明；6) 支持统计：调度轮次、每个任务的执行次数、让出次数与平均每次连续执行步数；7) 说明协作式调度的局限（任务不主动让出就无法被打断），并给出实现建议；8) 测试覆盖挂起恢复、两种策略、取消、公平性、路径可复现与统计；`mvn -q verify` 一条命令跑通。

## 提交要求

1. 在本仓库中完成提示词要求的全部内容。
2. `./mvnw -q verify` 必须通过。
3. 完成后在所属分支（A 或 B）上提交，产物快照的父提交必须是初始环境快照。

## 实现说明

包 `com.example.gsb.scheduler`，纯 JDK 17、单线程、确定性执行。

### 核心模型

- **可恢复任务**：`CooperativeTask` 由一串 `TaskStep` 声明，步骤只有两种：
  `Work`（一段工作）与 `Yield`（显式让出点）。调度器每次恢复任务时，
  从上次挂起位置（任务内部游标）连续执行 Work，直到遇到下一个 Yield 挂起，
  或步骤耗尽而完成。
- **可配置策略**：`SchedulerConfig` 选择
  `FIXED_PRIORITY`（固定优先级，同级轮转）或 `ROUND_ROBIN`（严格轮转），
  两者都用"最久未被调度者优先"挑选下一个任务，保证确定性与同级公平。
- **可枚举让出点**：`declaredYieldPoints()` 返回任务声明的全部让出点；
  `executionPath()` 与 `executedYieldPoints()` 记录实际经过的让出点序列
  （`PathEntry` 含任务 id、让出点、调度轮次），相同输入重复运行路径完全一致。
- **取消**：`Scheduler.cancel(id)` 或任务内 `ctx.cancel(id)` 设置取消标志；
  调度器只在让出点（恢复点）检查，一旦取消，任务转入 `CANCELLED`，
  任务体后续部分永不再执行；任务也可用 `ctx.cancelled()` 主动合作式退出。
- **统计**：`SchedulerStats` 提供调度轮次，以及每个任务的执行（恢复）次数、
  让出次数、工作步总数和平均每次连续执行步数。

### 用法示例

```java
Scheduler scheduler = new Scheduler(SchedulerConfig.roundRobin());
scheduler.submit(CooperativeTask.builder("A").priority(1)
        .work(ctx -> load()).yieldAt("loaded")
        .work(ctx -> process()).yieldAt("processed")
        .work(ctx -> save())
        .build());
scheduler.run();                            // 也可单步 scheduler.step()
scheduler.executionPath();                  // 复现执行路径
scheduler.snapshotStats().totalRounds();    // 调度轮次
```

### 协作式调度的局限与实现建议

- **无抢占：不主动让出就无法被打断。** 一个进入长循环或阻塞 I/O 的任务
  会独占线程，轮转、优先级、取消全部失效（取消只在让出点检查）。
- 建议：
  1. 在循环体内按固定间隔插入让出点（如每 N 次迭代 `yieldAt(...)`），
     控制单次连续执行步数；
  2. 用统计中的"平均/最大连续执行步数"作为监控指标，发现长期不让出的任务；
  3. 让出点命名要有业务含义，便于基于 `executionPath()` 复现与审计；
  4. 不要在工作步骤里做阻塞操作；需要真实抢占/阻塞时改用
     `java.util.concurrent` 线程池或虚拟线程，本调度器只适合纯计算的
     可让出工作；
  5. 取消标志应同时在长任务内部通过 `TaskContext.cancelled()` 轮询，
     而不能只依赖调度器。
