# LinkX

An on-device AI assistant agent for Android. LinkX connects to any OpenAI-compatible LLM API and equips the model with 70+ tools to read sensors, manage files, query notifications, execute scheduled tasks, access Chinese financial markets, and more — all through a streaming chat interface built with Jetpack Compose and Material Design 3.

## Features

### Core Agent
- Streaming, tool-calling LLM loop with automatic retry and exponential backoff
- Compatible with any OpenAI-compatible endpoint (primary target: DeepSeek)
- Thinking / reasoning token support (DeepSeek V4 chain-of-thought)
- Persistent conversation history with search, rename, and delete
- Token usage tracking per conversation
- Subagent support — spawn independent agents with shared clipboard state
- MCP protocol support — dynamically discover tools from external servers

### Tools (70+)
| Category | Capabilities |
|---|---|
| **System Info** | Battery, network, screen, app info, CPU, memory, processes |
| **File System** | Read, write, list, delete, find in SAF-authorized directories |
| **Sensors** | Light, accelerometer, gyroscope, step counter, and more |
| **Settings** | Read/write system settings, audio volume control, theme switching |
| **Notifications** | Create, update, dismiss, list, query notifications |
| **Termux** | Execute shell commands, read/write files via Termux API |
| **Scheduled Tasks** | CRUD for time-based recurring tasks |
| **Conditional Triggers** | CRUD for condition-based triggers + execution history |
| **Common** | Clipboard, agent clipboard, calendar, intents, subagent, memory update, time, HTTP |
| **Interaction** | Bidirectional user prompts with 8 widget types |
| **Self** | AI can query its own internal logs for debugging |
| **Screen** | Read screen content, click, scroll, screenshot, text input via accessibility service |
| **Finance** | 50+ tools for Chinese stocks, funds, boards, portfolios, and technical indicators |
| **MCP** | Dynamic tool discovery from external Model Context Protocol servers |

### Proactive Automation
- **Heartbeat:** Periodic AI wake-up with configurable interval and focus area
- **Scheduled Tasks:** Recurring tasks (daily, weekdays, weekly, once) with push notifications
- **Conditional Triggers:** Fire tasks when device conditions match (battery, sensors, settings, power state)

### Persistent Memory
- Markdown-based memory that persists across all conversations
- AI can self-update memory via the `update_memory` tool
- Local vector retrieval with ONNX Runtime for semantic search
- BM25 hybrid retrieval with tunable weights and CJK tokenizer

### Finance Data (embedded Python)
- Chaquopy-powered Python 3.12 runtime with `akshare` library
- Real-time Chinese stock quotes, historical data, intraday data
- Fund rankings, manager data, portfolio analysis
- Technical indicators (trend, oscillator, volume, momentum, directional, energy)

### Logging System
- Custom 6-level logging facade (Verbose through Fatal)
- In-memory ring buffer (10,000 entries) + rotating file writer
- AI can self-query logs via the `query_logs` tool

## Screenshots

*Coming soon*

## Requirements

- Android 13 (API 33) or later
- [Termux](https://github.com/termux/termux-app) (optional, for shell command execution)
- A DeepSeek API key or any OpenAI-compatible endpoint

## Build

```bash
# Clone the repository
git clone https://github.com/noexcs/LinkX.git && cd LinkX

# Set up signing (optional, for release builds)
# Create app/key.properties with:
#   storeFile=../keystore.jks
#   storePassword=...
#   keyAlias=...
#   keyPassword=...

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

The APK outputs to `app/build/outputs/apk/` as `LinkX-release.apk`.

## Architecture

```
app/src/main/java/com/noexcs/indolent/
├── MainActivity.kt              # Single-activity entry point, screen navigation
├── AgentViewModel.kt            # ViewModel bridging chat UI and agent lifecycle
├── agent/                       # AI core: streaming loop, LLM client, tool definitions
│   ├── Agent.kt                 # Tool-calling agent with streaming SSE
│   ├── LLMClient.kt             # OkHttp-based OpenAI-compatible API client
│   ├── SystemPromptBuilder.kt   # Composable system prompt from multiple sources
│   ├── ContextSummarizer.kt     # LLM-driven conversation history summarization
│   ├── skills/                  # Pluggable skill system (Markdown-based)
│   ├── mcp/                     # MCP client manager and tool adapter
│   ├── memory/                  # Local vector store, embedding model, BM25 scorer
│   └── tools/                   # 70+ AgentTool implementations
├── data/                        # Persistence layer: settings, sessions, memory
├── task/                        # Background automation
│   ├── scheduler/               # AlarmManager + WorkManager scheduled tasks
│   ├── heartbeat/               # Periodic proactive AI wake-up
│   └── conditional/             # Device-condition-based triggers
├── ui/                          # Compose screens (Chat, Settings, Notes, Todo, etc.)
└── logging/                     # Custom logging system (Lumberjack)
```

## Setup

### API Configuration
1. Open the app and navigate to **Settings** > **API Settings**
2. Enter your API key, base URL, and model name
3. Configure thinking mode and reasoning effort as needed

### Termux Integration
For shell command execution tools, install [Termux](https://github.com/termux/termux-app) and follow the setup guide in [TERMUX_SETUP.md](TERMUX_SETUP.md).

### Finance Tools
Finance tools require Chaquopy (embedded Python). The first use will automatically initialize the Python runtime and install dependencies. This may take a moment.

## Permissions

| Permission | Purpose |
|---|---|
| Internet | LLM API calls and financial data queries |
| Notifications | Push notifications from scheduled tasks and heartbeat |
| Exact Alarm | Precise timing for tasks, heartbeat, and condition monitors |
| Boot Completed | Re-schedule alarms after device reboot |
| Foreground Service | Background task execution via WorkManager |
| Storage (SAF) | File read/write in user-authorized directories |
| Calendar | Calendar event read/write |
| Usage Stats | App usage statistics |
| System Settings | Audio volume and system setting modification |
| Termux RUN_COMMAND | Shell command execution (requires Termux app) |
| Accessibility Service | Screen reading, clicks, and interaction |

## Tech Stack

- **Kotlin 2.3** — Language
- **Jetpack Compose + Material Design 3** — UI
- **OkHttp 4** — HTTP client with SSE streaming
- **WorkManager + AlarmManager** — Background task scheduling
- **Chaquopy** — Embedded Python 3.12 runtime
- **ONNX Runtime** — Local embedding model inference
- **Ktor** — HTTP client for MCP transport
- **kotlinx.serialization** — JSON serialization

## License

MIT — see [LICENSE](LICENSE) for details.
