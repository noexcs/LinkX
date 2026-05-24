I now have a thorough understanding of the codebase. Here's your interview preparation guide.

---

## LinkX 面试准备指南

### 一、你必须能清晰回答的核心问题

#### 1. Agent Loop 是怎么工作的？

用 30 秒版本开场，然后可以按需展开：

> "Agent Loop 是一个 while 循环：用户输入 → 构建 messages（含 system prompt + 记忆 + 历史）→ 调用 LLM（流式）→ 如果 finish_reason 是 tool_calls，就并行执行工具，把结果追加到历史，继续循环；如果是 stop 就结束。期间有上下文预算管理，超过 85% token 就触发 LLM 摘要压缩。"

面试官大概率会追问的细节：
- **为什么工具要并行执行？** 减少 round-trip，多个独立工具调用不需要串行等待。
- **上下文预算怎么估算 token？** 分角色估算字符/token 比率，75% 警告，85% 触发摘要，超预算时按整轮对话裁剪。
- **流式和非流式两套模式分别用于什么场景？** 流式给聊天 UI（用户需要实时反馈），非流式给后台任务（心跳、定时任务、条件触发）。

#### 2. 记忆/RAG 系统怎么做的？

> "基于 ONNX Runtime 本地运行 all-MiniLM-L6-v2 做 embedding，结合 Tantivy 做 BM25 关键词检索，最终混合打分（0.6 BM25 + 0.4 向量余弦相似度）。记忆文件是 Markdown 格式，按 `## ` 标题分块，用滑动窗口处理过长段落。检索 query 由用户当前消息 + 最近 3 轮对话拼接而成，top-5 相关块注入 system prompt。"

追问点：
- **为什么选 all-MiniLM-L6-v2？** 384 维，足够轻量在手机上跑，延迟可控。
- **为什么要混合 BM25？** 纯向量检索对专有名词、数字、代码片段不敏感，BM25 补了关键词匹配的短板。
- **如果模型加载失败怎么办？** 优雅降级——回退到全量记忆 dump，系统仍然可用。
- **分词器怎么实现的？** 纯 Kotlin 实现 WordPiece tokenizer，兼容 HuggingFace tokenizer.json 格式。为什么不调 Python？减少跨语言调用开销。

#### 3. 主动 Agent 机制怎么实现的？

三层递进：

| 机制     | 触发方式                              | 适用场景                          |
| -------- | ------------------------------------- | --------------------------------- |
| 心跳     | AlarmManager setAlarmClock 定时唤醒   | 周期性检查、主动发现信息          |
| 定时任务 | AlarmManager + 用户设定时间/frequency | 日报、提醒、定时抓数据            |
| 条件触发 | 被动广播 + 主动轮询（自适应间隔）     | 电量低于 X 时提醒、设置变化时执行 |

追问点：
- **怎么保证后台不被系统杀死？** setAlarmClock 是最高优先级闹钟，可穿透 Doze；WorkManager setExpedited 保证紧急任务执行。
- **自适应轮询间隔是什么？** 当条件值接近阈值时，轮询间隔缩短到基线的 25%（最低 30 秒），远离时恢复到正常间隔，兼顾响应速度和省电。
- **心跳提示词怎么写？** 让 LLM 先看记忆和上次结果避免重复 → 用工具找有用信息 → 值得关注就发通知 → 更新记忆。

#### 4. 工具调用框架怎么设计的？

> "AgentTool 接口只有 name、description、parameters、execute 四个成员。ToolRegistry 枚举了所有 ~75 个工具，ToolProvider 是一个工厂，根据 SettingsManager 里的开关（粗粒度分类开关 + 细粒度每个工具的开关）来构建工具列表。工具调用时先做 clipboard 插值（`{{agent_clipboard}}` → 替换为存储内容），然后并行执行。"

追问点：
- **工具参数 Schema 怎么定义？** ToolParameter 数据类（name, type, description, required, defaultValue），支持 "array of <type>" 语法。
- **MCP 工具怎么集成？** McpToolAdapter 把 MCP 工具的 JSON Schema 转成 ToolParameter 列表，包装成 AgentTool。工具名加 server 前缀防冲突。
- **安全怎么考虑？** Intent 工具有 blocklist（禁止拨号、安装/卸载、设备管理、重启等危险操作），文件系统工具有路径穿越检测。

---

### 二、按 STAR 法则准备的场景题

面试官可能会问："讲一个你做这个项目时遇到的最难的技术问题。"

建议准备 2-3 个场景：

**场景 1：上下文窗口管理（推荐首选）**
- S：Agent 在多轮工具调用后，历史消息迅速膨胀，超过 LLM 上下文限制导致请求失败。
- T：需要一个自动管理机制，既不丢失关键信息，又能持续对话。
- A：实现了三层管理——token 估算（分角色比率）→ LLM 摘要（85% 触发）→ 结构化裁剪（按整轮删除）。用 Kotlin Flow 发射事件让 UI 感知。
- R：支持了上百轮连续对话，token 使用透明可追踪。

**场景 2：Python 运行时在 Android 上的坑**
- S：akshare 依赖 aiohttp、curl_cffi 等无 Android wheel 的包，直接安装失败。
- T：让 akshare 在 Android 上跑起来。
- A：写虚拟存根包让 Python import 不报错；monkey-patch lxml → html.parser；gfortran 动态加载兼容 numpy。
- R：akshare 核心功能可用，50+ 金融工具跑通。

**场景 3：混合检索的调参**
- S：纯向量检索对股票代码 "600519" 这类查询无效。
- T：提高对精确关键词的召回率。
- A：引入 Tantivy BM25 做混合检索，经过实验选择 0.6 BM25 + 0.4 向量的权重。
- R：关键词查询的命中率显著提升。

---

### 三、可能会被 challenge 的点（提前准备好回应）

| 可能被问到的                        | 准备好的回应方向                                                                   |
| ----------------------------------- | ---------------------------------------------------------------------------------- |
| "为什么不用 LangChain/LlamaIndex？" | 它们的抽象在 Android 上太重，直接控制循环更灵活；Kotlin 生态没有成熟替代           |
| "ONNX 推理延迟多少？"               | 实测数据（如果有的话）；384 维轻量模型；单次 embedding 通常在几百 ms 内            |
| "后台任务耗电怎么样？"              | 自适应轮询 + AlarmManager 精准唤醒 + WorkManager 批处理；心跳默认 30 分钟间隔      |
| "MCP 和原生工具有什么区别？"        | MCP 是外部扩展协议，工具发现是动态的；原生工具是编译时注册的，有更细粒度的设置控制 |
| "安全性怎么保证？"                  | Intent blocklist、路径穿越检测、SAF 权限隔离、Termux 命令需要用户主动授权          |
| "为什么选择单模块而不是多模块？"    | 早期项目快速迭代，工具和功能的边界还在演化，过早分模块会增加构建复杂度             |

---

### 四、你应该准备的 demo

建议准备一个 **3-5 分钟的实际演示视频** 或 **现场 demo 流程**：

1. 展示聊天界面和工具调用的实时流式渲染
2. 让 Agent 用金融工具查一支股票
3. 展示记忆功能——让 Agent 记住一件事，然后在新对话中检验它是否召回
4. 展示主动机制——设一个条件触发器或定时任务

---

### 五、会写到简历上的关键词，都必须能解释

确保你能用中文随口说出来：
- **Agent Loop / Tool Calling** → 能讲清楚循环的每一步
- **ONNX Runtime / sentence-transformers** → 知道模型名和维度
- **向量检索 + BM25 混合** → 知道权重和为什么混合
- **心跳唤醒 / 定时任务 / 条件触发器** → 各自用什么 Android API
- **MCP** → Model Context Protocol，用于接入外部工具服务器
- **Chaquopy / Termux** → 分别是嵌入式 Python 运行时和命令行执行
- **Subagent** → 新建独立 Agent 实例处理子任务，隔离上下文

---

### 六、建议做的事

1. **跑一遍完整流程**，记下每个环节的延迟数据，面试时有量化指标会很加分。
2. **准备一份架构图**（文字版也行），能 5 秒讲清楚整体架构。
3. **review 你最近的 commit diff**，确保每个模块的最新实现你心里有数。
4. 如果面的是大模型/AI 方向，准备好回答"你为什么自研而不是用现成框架"；如果面的是 Android 方向，多强调后台保活、省电、权限这些系统工程挑战。