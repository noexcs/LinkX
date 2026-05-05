# Termux Integration Setup Guide (Official RUN_COMMAND Method)

## Overview

This app uses the **official Termux RUN_COMMAND Intent** method to execute commands in Termux and receive results back. This is the recommended approach documented in the [Termux Wiki](https://github.com/termux/termux-app/wiki).

## Prerequisites (必须条件)

### 1. Install Required Apps (安装必要应用)

**Important**: Download both apps from the **SAME source** (both from F-Droid OR both from Play Store)

#### Option A: F-Droid (Recommended)
1. Download **Termux** from F-Droid: https://f-droid.org/packages/com.termux/
2. Download **Termux:API** from F-Droid: https://f-droid.org/packages/com.termux.api/

#### Option B: Google Play Store
1. Install **Termux** from Play Store
2. Install **Termux:API** from Play Store

### 2. Setup Termux (配置 Termux)

Open Termux app and run these commands:

```bash
# Update package lists
pkg update && pkg upgrade

# Install termux-api package (required for external app integration)
pkg install termux-api

# Grant storage access
termux-setup-storage
```

### 3. Configure Termux Properties (重要配置)

Create or edit `~/.termux/termux.properties` in Termux:

```bash
echo "allow-external-apps=true" >> ~/.termux/termux.properties
```

Or manually edit the file:
```bash
nano ~/.termux/termux.properties
```

Add this line:
```
allow-external-apps=true
```

Then restart Termux or run:
```bash
termux-reload-settings
```

### 4. Grant Permissions (授予权限)

On your Android device:

1. Go to **Settings** → **Apps** → **Termux** → **Permissions**
2. Grant all necessary permissions:
   - Storage / Files and Media
   - Display over other apps (Android 10+)
   - Any other permissions you want to allow

3. Also grant permissions to your Localagent app if prompted

### 5. Battery Optimization (电池优化)

Disable battery optimization for Termux to prevent it from being killed:

1. Go to **Settings** → **Apps** → **Termux** → **Battery**
2. Select **Unrestricted** or **Don't optimize**

## How It Works (工作原理)

The app uses the official Termux `RUN_COMMAND` Intent API:

1. **Background Mode**: Executes command in background and captures stdout/stderr separately
2. **Foreground Mode**: Opens a new terminal session in Termux app

The implementation follows the official documentation:
- Uses `com.termux.shared.termux:termux-shared` library
- Sends Intent to `RunCommandService`
- Receives results via `PendingIntent` callback
- Supports separate stdout/stderr for background commands

## Usage (使用方法)

### First Time Setup

1. Install Termux and Termux:API
2. Complete the setup steps above
3. Build and run the Localagent app

### Using the App

1. Open the Localagent app
2. Enter a shell command (e.g., `ls -la`, `pwd`, `whoami`)
3. Choose execution mode:
   - **Execute (Background)**: Runs in background, shows output in app
   - **Execute (Foreground)**: Opens Termux terminal session
4. View the results with stdout, stderr, and exit code

### Example Commands

```bash
# Basic system info
uname -a
whoami
pwd
ls -la

# File operations
echo "Hello" > test.txt
cat test.txt
rm test.txt

# Network commands
curl https://api.ipify.org
ping -c 4 google.com

# Process info
ps aux
top -n 1
```

## Technical Implementation

### Dependencies

```kotlin
implementation("com.termux.termux-app:termux-shared:0.118.0")
implementation("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")
```

### AndroidManifest Configuration

```xml
<uses-permission android:name="com.termux.permission.RUN_COMMAND" />

<queries>
    <package android:name="com.termux" />
</queries>

<service android:name=".TermuxResultService" />
```

### Key Components

1. **MainActivity.kt**: UI and command execution logic
2. **TermuxResultService.kt**: Receives command results from Termux
3. **CommandResult data class**: Holds stdout, stderr, exit code

## Limitations (限制)

⚠️ **Important Notes**:

1. **Android Version Restrictions**: 
   - Android 10+: Requires "Display over other apps" permission
   - Android 11+: Package visibility requires `<queries>` tag

2. **Output Size Limits**:
   - stdout/stderr limited to ~100KB combined
   - Original length provided if truncated

3. **Termux Must Be Installed**: App won't work without Termux

4. **Security**: Commands run with Termux user permissions

## Troubleshooting (故障排除)

### Issue: "Package not found" or Intent fails

**Solution**: 
- Ensure Termux is installed
- Check that `<queries>` tag is in AndroidManifest.xml
- Verify targetSdkVersion >= 30 package visibility rules

### Issue: Command doesn't execute

**Solution**:
- Verify `allow-external-apps=true` in `~/.termux/termux.properties`
- Grant RUN_COMMAND permission to Localagent app
- Check Termux has necessary permissions
- Disable battery optimization for Termux

### Issue: Cannot receive result callback

**Solution**:
- Ensure TermuxResultService is declared in AndroidManifest.xml
- Check PendingIntent flags for Android 12+ compatibility
- Verify execution IDs are unique

### Issue: Permission denied for storage paths

**Solution**:
- Grant storage permission to Termux
- Run `termux-setup-storage` in Termux
- For Android 11+, use "Allow management of all files"

### Issue: Command not found

**Solution**:
- Install required packages in Termux (e.g., `pkg install coreutils`)
- Use full path to executables
- Check `$PATH` environment variable

## Security Considerations (安全考虑)

⚠️ **Warning**: Allowing arbitrary command execution is risky!

**Best Practices**:
- Validate and sanitize all user inputs
- Consider implementing a whitelist of allowed commands
- Don't expose this functionality to untrusted users
- Be cautious with commands that modify files or system settings
- Log all command executions for auditing

## Advanced Features

### Session Transcript vs Separate Output

- **Foreground Commands** (`BACKGROUND=false`): Returns session transcript (stdout + stderr combined)
- **Background Commands** (`BACKGROUND=true`): Returns separate stdout and stderr

### Custom Working Directory

Change the working directory:
```kotlin
putExtra(RUN_COMMAND_SERVICE.EXTRA_WORKDIR, "/sdcard")
```

### Pass stdin Data

Send data to command's stdin (Termux >= 0.109):
```kotlin
putExtra(RUN_COMMAND_SERVICE.EXTRA_STDIN, "input data")
```

### Command Metadata

Add labels and descriptions:
```kotlin
putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_LABEL, "My Command")
putExtra(RUN_COMMAND_SERVICE.EXTRA_COMMAND_DESCRIPTION, "Description...")
```

## References

- [Termux RUN_COMMAND Intent Documentation](https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent)
- [Termux Libraries Documentation](https://github.com/termux/termux-app/wiki/Termux-Libraries)
- [Termux GitHub Repository](https://github.com/termux/termux-app)
- [Termux:API Repository](https://github.com/termux/termux-api)

## Testing Checklist

- [ ] Termux installed from F-Droid/Play Store
- [ ] Termux:API installed from same source
- [ ] `pkg install termux-api` executed in Termux
- [ ] `allow-external-apps=true` set in termux.properties
- [ ] Storage permission granted to Termux
- [ ] RUN_COMMAND permission granted to Localagent
- [ ] Battery optimization disabled for Termux
- [ ] Test basic command: `echo "test"`
- [ ] Test file command: `ls -la`
- [ ] Test network command: `curl https://example.com`
- [ ] Verify stdout/stderr separation works
- [ ] Verify exit code is captured correctly
