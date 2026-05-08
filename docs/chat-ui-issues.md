# Chat UI Issues

## 背景

ChatScreen 使用 Jetpack Compose 的 `LazyColumn` 展示消息列表，每条消息通过 `MessageBubble` 渲染。其中 `ThinkingBubble` 和 `ToolCallBubble` 支持点击展开/收起内容。

## 核心矛盾

`LazyColumn` 的 `reverseLayout = true` 带来了一个不可调和的矛盾：

### reverseLayout = true 的优点
- IME（键盘）处理天然正确：列表锚定在底部，键盘弹出时内容自动上移
- 自动滚动天然正确：新消息出现在底部（index 0），`animateScrollToItem(0)` 即是滚动到最新
- 消息顺序直观：最新消息在底部，符合聊天应用预期

### reverseLayout = true 的缺点
- 列表项锚定在底部。当 item 高度增长时（展开），底部位置固定，顶部向上移动
- `animateContentSize` 的扩展方向在视觉上是"向上"的（内容出现在标题上方，向旧消息方向扩展）
- 无论有无动画，展开内容的最终布局结果都是"向上"的，用户期望的是"向下"展开（内容出现在标题下方，向输入框方向扩展）

### 不用 reverseLayout 的优点
- 列表项锚定在顶部，`animateContentSize` 自然向下扩展
- 展开内容出现在标题下方，符合用户预期

### 不用 reverseLayout 的缺点
- IME 处理需要手动介入：键盘弹出时需显式调用 `scrollToItem` 滚动到底部
- 自动滚动需要手动管理：新消息和流式输出都需 `LaunchedEffect` + `scrollToItem`
- 流式输出时滚动不稳定：`scrollToItem` / `animateScrollToItem` 在内容频繁变化时可能失效或与手动滚动冲突

## 已尝试的方案

### 方案 A：reverseLayout = true + animateContentSize
展开方向向上。用户不接受。

### 方案 B：reverseLayout = true + AnimatedVisibility(expandVertically)
展开方向仍然向上。`AnimatedVisibility` 的 `expandFrom` 参数只控制动画内部对齐，不改变 LazyColumn 的布局锚定方式。

### 方案 C：去除 reverseLayout + animateScrollToItem 自动滚动
- 展开方向正确（向下）
- 但流式输出时 `LaunchedEffect` 反复触发 `animateScrollToItem`，动画队列堆积，导致：
  1. 滚动卡在中间无法继续
  2. 手动划动被自动滚动打断
  3. 键盘弹出后内容被遮挡
  4. 键盘收起后内容跳到中间

### 方案 D：去除 reverseLayout + scrollToItem（即时滚动）+ 近底部判断
- 展开方向正确
- 用 `scrollToItem` 代替 `animateScrollToItem` 避免动画队列
- 仅在 `lastVisibleIndex >= totalItems - 3` 时才自动滚动
- 键盘弹出时独立触发 `scrollToItem`
- "卡死"现象理论上减轻，但 `scrollToItem` 在流式输出频繁调用时行为仍不确定
- 可能与方案 C 有相同的问题（尚未充分验证）

## 已验证的稳定组合

| 需求 | reverseLayout=true | reverseLayout=false |
|------|-------------------|---------------------|
| 展开方向向下 | ❌ 不可修复 | ✅ |
| IME/键盘处理 | ✅ | ⚠️ 需手动 scrollToItem |
| 流式输出自动滚动 | ✅ | ⚠️ 需手动 scrollToItem |
| 手动翻阅不被中断 | ✅ | ⚠️ 需近底部判断 |
| animateContentSize 方向 | 向上 | 向下 |

## 建议的备选方案：浮层展开

保留 `reverseLayout = true`，但展开内容不放在 LazyColumn item 内部，而是以浮层形式出现在标题下方：

```kotlin
// 伪代码
Box {
    Surface(onClick = { expanded = !expanded }) {
        // 标题行（高度不变，LazyColumn item 不会增长）
    }
    if (expanded) {
        Popup(alignment = Alignment.TopStart) {
            // 展开内容，覆盖在聊天列表上方
            Surface(shape = ..., color = ...) {
                MarkdownContent(...)
            }
        }
    }
}
```

优点：
- LazyColumn item 高度不会变化，无展开方向问题
- `reverseLayout = true` 的所有优点保留
- 展开内容作为覆盖层，不影响列表布局

缺点：
- 展开内容覆盖在新消息上面，可能遮挡后续消息
- 需要处理 Popup 的关闭逻辑（点击外部、滚动时关闭等）
- 视觉上与"内联展开"不同

## 相关文件

- `app/src/main/java/com/noexcs/indolent/ui/ChatScreen.kt` — 主聊天界面
- `app/src/main/java/com/noexcs/indolent/AgentViewModel.kt` — 消息流管理
- `app/src/main/java/com/noexcs/indolent/ui/ConversationListScreen.kt` — 抽屉导航
