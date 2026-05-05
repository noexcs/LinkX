# Conditional Trigger — 条件触发实现原理

## 概述

条件触发系统允许 AI Agent 基于设备状态（电池、传感器、系统设置）自动触发任务执行。与传统的时间触发的定时任务不同，条件触发是**事件驱动的**——当设备条件满足时立即执行。

核心设计思路：**轮询 + 被动监听**的混合架构，兼顾实时性和省电。

## 架构

```
Settings / BootReceiver
    │
    ▼
ConditionMonitorScheduler       ← AlarmManager 定时唤醒（默认 2min，自适应加速至 30s）
    │
    ▼
ConditionMonitorReceiver        ← BroadcastReceiver，将闹钟转交给 WorkManager
    │
    ▼
ConditionMonitorWorker          ← CoroutineWorker + ForegroundService
    │
    ├── PassiveConditionMonitor.start()   ← 动态注册电池/设置被动监听器
    │
    ├── ConditionEvaluator.evaluateAll()
    │   ├── BatteryConditionProvider.getState()      ← ACTION_BATTERY_CHANGED
    │   ├── SettingConditionProvider.getState()      ← Settings.System
    │   ├── SensorConditionProvider.sampleAll()      ← 传感器采样（500ms/个）
    │   └── PowerConditionProvider.getState()        ← PowerManager
    │
    ├── TriggerDispatcher.dispatch()      ← 冷却检查 → 构建上下文 Prompt → Agent.execute()
    │
    ├── PassiveConditionMonitor.stop()
    │
    └── evaluator.getRecommendedIntervalMs() → Scheduler.scheduleWithInterval(adaptiveMs)
```

## 可靠性与冗余

上图显示的是**主路径**（AlarmManager 轮询）。此外，`PassiveConditionMonitor` 作为**实时加速路径**也在运行：

```
电池每 1% 变化 ──→ BroadcastReceiver ──→ 立即评估 ──→ 触发 Agent
系统设置变化  ──→ ContentObserver ──→ 立即评估 ──→ 触发 Agent
```

两条路径共享同一个 `ConditionEvaluator` + `TriggerDispatcher`，通过 cooldown 防重，互不冲突。当进程死亡时被动路径自动失效，AlarmManager 轮询路径作为保底。

## 核心组件

### `ConditionalTrigger` — 数据模型

```kotlin
ConditionalTrigger(
    id, title, conditions: List<TriggerCondition>,
    prompt, cooldownMs=5min, maxFiresPerDay=10,
    notifyEnabled, enabled, lastTriggeredAt, fireCount
)

TriggerCondition(
    source: BATTERY | SYSTEM_SETTING | SENSOR | POWER,
    field: String,       // 例如 "level", "brightness", "light"
    operator: > >= < <= == != changed becomes_true becomes_false,
    targetValue: String?  // 阈值，state-change 运算符可为 null
)
```

### `ConditionEvaluator` — 条件评估器

核心职责：
- 加载所有启用的触发器，按需读取设备状态（只读取被触发器引用到的 source 类型）
- 逐条件评估：数值比较（自动识别 int/float）、字符串比较、状态变化追踪（`changed`/`becomes_true`/`becomes_false` 依赖 SharedPreferences 存储历史值）
- `getRecommendedIntervalMs()`：计算自适应轮询间隔。当任意条件当前值接近阈值 10% 以内时，间隔缩短至 25%（最快 30s）

### `TriggerDispatcher` — 触发执行器

- **冷却检查**：距上次触发不足 `cooldownMs` 则跳过
- **日频次限制**：超过 `maxFiresPerDay` 则跳过
- **上下文构建**：`buildContextualPrompt()` 将条件信息 + 当前设备值注入 Prompt：

```
# Condition Trigger Context

This task was automatically triggered because the following conditions were met:
  - BATTERY.level LESS_THAN 20 (current: 15)
  - POWER.is_charging EQUAL false (current: false)

## Task Instructions

<用户定义的 prompt>
```

- Agent 执行带 5 分钟超时保护
- 执行完毕后通过 `CreateNotificationTool` 推送通知，记录到 `TaskExecutionRepository`

### `PassiveConditionMonitor` — 被动监听器

实时加速层，在 ConditionMonitorWorker 运行期间动态注册：

| 监听方式 | 监听内容 | 延迟 |
|---------|---------|------|
| BroadcastReceiver | `ACTION_BATTERY_CHANGED` | 毫秒级 |
| ContentObserver | `Settings.System.CONTENT_URI` | 毫秒级 |

两种监听器只能在进程存活时工作。进程死亡后由 AlarmManager 轮询兜底。

### `ConditionMonitorScheduler` — 调度器

- 使用 `AlarmManager.setAlarmClock()`（最高优先级，穿透 Doze）
- `scheduleWithInterval(ms)`：支持外部传入自适应间隔
- `rescheduleAll()`：开机时由 `BootReceiver` 调用，恢复所有调度

### `ConditionMonitorWorker` — 工作线程

```kotlin
doWork() {
    passiveMonitor.start()
    evaluator.evaluateAll()  →  triggers
    triggers.forEach { dispatcher.dispatch(it) }
    delay(10s)  // 维持进程存活以捕获后续变化
    adaptiveInterval = evaluator.getRecommendedIntervalMs()
    scheduler.scheduleWithInterval(adaptiveInterval)
    passiveMonitor.stop()
}
```

> 注意：WorkManager 需要 `ExistingWorkPolicy.APPEND_OR_REPLACE` 以避免连续闹钟之间的竞态条件。

### `ConditionProvider` 子包 — 设备状态读取器

| Provider | 读取来源 | 字段数 | 说明 |
|----------|---------|--------|------|
| `BatteryConditionProvider` | `BatteryManager` + `Intent.ACTION_BATTERY_CHANGED` | 12 | level, status, temperature, plugged, health, is_charging, battery_saver 等 |
| `SettingConditionProvider` | `Settings.System` | 17 | brightness, auto_brightness, screen_timeout, font_scale, animator_scale 等 |
| `SensorConditionProvider` | `SensorManager` + `SensorUtils` | 14 种传感器 | light, proximity, temperature, humidity, pressure, accelerometer, gyroscope, step_counter 等 |
| `PowerConditionProvider` | `PowerManager` | 3 | is_power_save, is_interactive, screen_on |

## 数据流（完整生命周期）

```
1. 创建
   AI Agent / 用户 → CreateConditionalTriggerTool → ConditionalTriggerRepository.save()
   → ConditionMonitorScheduler.schedule()

2. 首次评估 (AlarmManager 触发)
   AlarmManager → ConditionMonitorReceiver → WorkManager → ConditionMonitorWorker.doWork()
   → ConditionEvaluator.evaluateAll()
   → 不满足 → 计算自适应间隔 → 重新调度 AlarmManager

3. 条件满足
   条件满足 → TriggerDispatcher.dispatch()
   → 冷却/日频检查 → 更新 lastTriggeredAt/fireCount
   → buildContextualPrompt(trigger) → 注入条件上下文 + 当前值
   → Agent.execute(contextualPrompt, systemPrompt, tools)
   → 保存 TaskExecutionRecord → 发送通知 → 重新调度

4. 被动加速（在 Worker 生命周期内）
   ACTION_BATTERY_CHANGED / Settings.System change
   → PassiveConditionMonitor → ConditionEvaluator → TriggerDispatcher
   → 共享 cooldown 机制，防重复触发

5. 开机恢复
   BOOT_COMPLETED → BootReceiver
   → ConditionMonitorScheduler.rescheduleAll()
```

## 关键设计决策

### 为什么不用持续传感器监听？

持续 `SensorEventListener` 注册会强制传感器硬件保持开启，功耗约 0.5-5mA。采用间歇采样（500ms/次）可让硬件在两次采样之间休眠，节省 70-90% 电量。

### 自适应间隔算法

```
当前值距阈值 ≥ 50% → 使用基础间隔 (2min)
当前值距阈值 25-50% → 基础间隔 × 0.75
当前值距阈值 10-25% → 基础间隔 × 0.5
当前值距阈值 < 10% → 基础间隔 × 0.25 (最快 30s)
```

### 冷却和日频限制

- `cooldownMs` 默认 5 分钟，防止阈值附近振荡导致反复触发
- `maxFiresPerDay` 默认 10 次，防止失控的 Agent 调用
- 两者在 `TriggerDispatcher.dispatch()` 中先于 Agent 执行检查

### 状态变化运算符与滞后

`CHANGED` / `BECOMES_TRUE` / `BECOMES_FALSE` 通过 `SharedPreferences` 存储上一次值，只在**状态转换**时触发，不会在每次评估时重复触发。这避免了对"当前值已满足"的重复响应。

## 如何添加新的条件来源

1. 创建新的 `XxxConditionProvider`，实现一个返回 `Map<String, String>` 的方法
2. 在 `ConditionSource` 枚举中添加新类型
3. 在 `ConditionEvaluator.resolveValue()` 中添加新来源的分支
4. 在 `TriggerDispatcher.buildContextualPrompt()` 中添加新来源的当前值读取
5. （可选）在 `getRecommendedIntervalMs()` 中添加新来源的接近度计算

## 配置

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| `conditionMonitorEnabled` | true | 是否启用条件监控 |
| `conditionMonitorIntervalMinutes` | 2 | 基础轮询间隔（自适应会在此基础上加速） |

## 相关文件

### 任务系统
```
task/conditional/
├── ConditionalTrigger.kt              — 数据模型
├── ConditionalTriggerRepository.kt    — JSON 文件持久化
├── ConditionEvaluator.kt              — 条件评估 + 状态追踪 + 自适应间隔
├── TriggerDispatcher.kt               — 冷却/日频控制 + 上下文构建 + Agent 执行
├── PassiveConditionMonitor.kt         — 被动监听（电池 & 设置实时响应）
├── ConditionMonitorScheduler.kt       — AlarmManager 调度
├── ConditionMonitorReceiver.kt        — 闹钟接收 → WorkManager
├── ConditionMonitorWorker.kt          — 前台服务轮询 + 自适应调度
└── conditionProvider/
    ├── BatteryConditionProvider.kt    — 电池状态读取
    ├── SettingConditionProvider.kt    — 系统设置读取
    ├── SensorConditionProvider.kt     — 传感器采样
    └── PowerConditionProvider.kt      — 电源状态读取
```

### AI 工具（Agent 可自主管理条件触发）
```
agent/tools/conditional/
├── CreateConditionalTriggerTool.kt
├── ListConditionalTriggersTool.kt
├── EditConditionalTriggerTool.kt
└── DeleteConditionalTriggerTool.kt
```

### 集成点
```
data/SettingsManager.kt              — conditionMonitorEnabled, conditionMonitorIntervalMinutes
task/scheduler/BootReceiver.kt       — 开机恢复调度
AgentViewModel.kt                    — 注册 4 个条件触发工具
AndroidManifest.xml                  — ConditionMonitorReceiver 声明
```
