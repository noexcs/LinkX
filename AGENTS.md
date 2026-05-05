# Project: Indolent (TwilightRain)

Android AI assistant app — LLM-driven agent with tool-calling, scheduled tasks, and heartbeat-based proactive execution.

## Architecture

```
app/src/main/java/com/noexcs/indolent/
├── agent/            # AI agent core (Agent, LLMClient, streaming, tool definitions)
│   ├── tools/        # Tools exposed to the AI (see below)
│   │   ├── termux/         # Termux shell integration tools
│   │   ├── finance/        # Fund/finance data query tools
│   │   ├── scheduledTask/  # Scheduled task CRUD tools
│   │   ├── filesystem/     # File read/write/list/delete/storage info (File API + SAF)
│   │   ├── sensor/         # Hardware sensor data (light, motion, environment, steps)
│   │   ├── setting/        # System settings read/write + audio volume control
│   │   ├── interact/       # Bidirectional user interaction (ask_user dialog)
│   │   └── notification/   # Notification create/dismiss/list/manage tools
│   └── termux/       # Termux executor & result receiver
├── data/             # Settings, memory, chat history persistence
├── logging/          # Centralized logging system (Lumberjack)
├── task/             # Scheduled task & heartbeat models, repositories
│   └── scheduler/    # Alarm-based scheduling, WorkManager workers, receivers
└── ui/               # Jetpack Compose UI (chat, settings, task list, heartbeat history)
    └── interact/     # Transparent dialog Activity for ask_user tool
```

## Tool Convention

All tools implement `AgentTool` interface and live under `agent/tools/`.

```kotlin
class MyTool(context: Context) : AgentTool {
    override val name = "my_tool"
    override val description = """...""".trimIndent()
    override val parameters = listOf(ToolParameter(...))
    override suspend fun execute(args: Map<String, Any?>): String { ... }
}
```

- Constructor-inject `Context` when Android APIs are needed; `object` singleton for stateless tools (e.g. `LogQueryTool`, `GetCurrentTimeTool`).
- Register in both `AgentViewModel.buildTools()` (chat UI) and `TaskExecutionWorker.buildTools()` (background tasks).
- Declare permissions in `AndroidManifest.xml`.
- Some tool groups are gated behind `SettingsManager` flags (`termuxToolsEnabled`, `fundToolsEnabled`). Others are unconditional.

### Tool Categories

| Directory | Purpose | Key tools | Registration |
|---|---|---|---|
| (root) | General device info & utilities | `GetAppInfo`, `BatteryInfo`, `NetworkStatus`, `CurrentScreenInfo`, `Clipboard`, `Calendar`, `LogQuery`, `GetCurrentTime`, `Intent`, `UpdateMemory`, `Subagent` | Unconditional |
| `termux/` | Termux shell integration | `TermuxExecuteCommand`, `TermuxReadFile`, `TermuxWriteFile`, `TermuxDialog` | Gated: `termuxToolsEnabled` + `RUN_COMMAND` permission |
| `filesystem/` | Android-native file operations | `ReadFile` (`fs_read`), `WriteFile` (`fs_write`), `ListFiles` (`fs_list`), `DeleteFile` (`fs_delete`), `GetStorageInfo` (`fs_storage_info`) | Unconditional |
| `sensor/` | Hardware sensor data | `GetSensorData` (`get_sensor_data`) — light, accelerometer, gyroscope, step counter, etc. | Unconditional |
| `setting/` | System settings & audio | `SystemSetting` (`system_setting`), `AudioControl` (`audio_control`) | Unconditional |
| `interact/` | User interaction | `AskUser` (`ask_user`) — screen-centered dialog with 8 widget types | Unconditional |
| `notification/` | Notification management | `CreateNotification`, `UpdateNotification`, `ListActiveNotifications`, `ManageNotificationChannel`, etc. | Unconditional |
| `scheduledTask/` | Task CRUD | `CreateScheduledTask`, `ListScheduledTasks`, `EditScheduledTask`, `DeleteScheduledTask` | Unconditional |
| `finance/` | Mutual fund data | `FundInfoIndex`, `FundOverview`, `FundPortfolio`, `FundRank`, `FundXq`, etc. | Gated: `fundToolsEnabled` |

### Key Design Patterns

**Async user interaction** (`interact/`):
- `AskUserTool` launches a transparent `ComponentActivity` (`InteractDialogActivity`) with `FLAG_ACTIVITY_NEW_TASK`
- Full-screen intent notification as fallback for background contexts (scheduled tasks, heartbeat)
- Response captured via dynamic `BroadcastReceiver` + `suspendCancellableCoroutine`
- Widget types: `text`, `confirm`, `checkbox`, `radio`, `counter`, `date`, `time`, `speech`

**SAF filesystem access** (`filesystem/`):
- App-scoped directories via `java.io.File`; external storage via `DocumentFile` (SAF content URIs)
- SAF roots persisted in `SettingsManager.safRoots`; authorized through settings UI

**Sensor reading** (`sensor/`):
- `HandlerThread` + `SensorEventListener` bridged to coroutine via `delay()`
- IDLE/private sensors auto-filtered; duplicate types deduplicated; high-frequency sensors auto-summarized
- Summary mode (`sensor="summary"`) for compact overview

**System settings** (`setting/`):
- `WRITE_SETTINGS` permission required for writes; auto-opens grant page if missing
- Audio control via `AudioManager` — no permissions needed

## Logging — Lumberjack

All app logging flows through `com.noexcs.indolent.logging.Lumberjack` (NOT `android.util.Log`).

```kotlin
import com.noexcs.indolent.logging.Lumberjack

Lumberjack.v("Tag", "Verbose debug detail")
Lumberjack.d("Tag", "Debug info")
Lumberjack.i("Tag", "Normal operational event")
Lumberjack.w("Tag", "Recoverable anomaly, missing permission, config issue")
Lumberjack.e("Tag", "Operation failed", throwable)
Lumberjack.f("Tag", "Fatal / crash", throwable)
```

- Lumberjack writes to in-memory ring buffer (2000 entries) + rotating files + logcat bridge.
- AI can query logs via `query_logs` tool with filters: count, level, tag, query, since, before, offset.
- LogFileWriter keeps using `android.util.Log` internally to avoid circular dependency.
- Note: `Lumberjack.w()` accepts only `(tag, message)` — no throwable parameter. Use `Lumberjack.e()` for exceptions.

### Logging Coverage Rules

**Every significant code path MUST be logged. This is non-negotiable.**

1. **Every `catch` block must log at E level** with full context and the exception:
   ```kotlin
   } catch (e: Exception) {
       Lumberjack.e("Tag", "What was being attempted when it failed", e)
       // handle or rethrow
   }
   ```
   Never silently swallow exceptions. If a catch block intentionally ignores an error, log at W level and explain why.

2. **Normal operations must log at I level** — every lifecycle event the AI would need to trace:
   - Task/heartbeat start, completion (with duration and result size), re-schedule
   - Alarm firings, boot recovery
   - API call start (model, prompt length) and completion
   - Permission grant/denial decisions
   - Configuration changes
   - Tool entry (action + key params) and exit (result summary)

3. **Degraded or edge-case paths must log at W level**:
   - Missing permissions, missing configuration
   - Resource not found, feature disabled
   - Fallback code paths, default values used
   - Deprecated API usage
   - Timeouts waiting for user response
   - Direct Activity launch failure (notification fallback)

4. **Per-component coverage** — when adding or modifying any module, add I-level logs for:
   - Entry point (method called with key params)
   - Each branch outcome (success / skipped / degraded)
   - Exit point (result summary)

5. **Tool execution** — every tool's `execute()` must log the action being taken and its outcome. At minimum:
   ```kotlin
   Lumberjack.i("ToolName", "Executing action=$action ...")
   // ...
   Lumberjack.i("ToolName", "Completed action=$action (result details)")
   ```

The goal: AI calling `query_logs(level="I", tag="TaskScheduler", since="1h")` should see a complete, chronological story of what happened — no gaps, no guesswork.

## Code Style

- No comments explaining WHAT code does (well-named identifiers handle that). Comment WHY only when non-obvious.
- No half-finished implementations. No premature abstractions.
- Prefer editing existing files over creating new ones.
- Follow existing patterns in the codebase; don't introduce new frameworks or libraries without discussion.
