package com.noexcs.indolent.agent.tools.screen

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.noexcs.indolent.logging.Lumberjack
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class LinkXAccessibilityService : AccessibilityService() {

    private var screenWidth: Int = 1080
    private var screenHeight: Int = 2400

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels

        // Programmatic service info is required on some Android versions —
        // XML flags alone are not always sufficient.
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 100
        }
        setServiceInfo(info)

        Lumberjack.i(TAG, "Accessibility service connected (${screenWidth}x${screenHeight}) flags=${info.flags}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events are received but we use on-demand tree traversal via tools
    }

    override fun onInterrupt() {
        Lumberjack.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Lumberjack.i(TAG, "Accessibility service destroyed")
    }

    // ── Screen reading ──

    fun getScreenDescription(mode: String = "summary", filterText: String? = null): String {
        val root = rootInActiveWindow
            ?: return "Error: No active window. The device may be locked or on the home screen with no accessible content."

        val nodes = mutableListOf<NodeDesc>()
        flattenTree(root, nodes, 0)

        val filtered = when (mode) {
            "full" -> nodes
            "interactive" -> nodes.filter { it.isClickable || it.isScrollable || it.isEditable || it.text.isNotBlank() }
            else -> nodes.filter { it.isClickable || it.isScrollable || it.isEditable || it.text.isNotBlank() || it.depth <= 2 }
        }

        val textFiltered = if (filterText.isNullOrBlank()) {
            filtered
        } else {
            filtered.filter {
                it.text.contains(filterText, ignoreCase = true) ||
                    it.contentDesc.contains(filterText, ignoreCase = true) ||
                    it.resourceId.contains(filterText, ignoreCase = true)
            }
        }

        val maxNodes = 150
        val displayNodes = if (textFiltered.size > maxNodes) textFiltered.take(maxNodes) else textFiltered

        root.recycle()

        if (displayNodes.isEmpty()) {
            return "No matching UI elements found on screen."
        }

        return buildString {
            appendLine("Screen: ${screenWidth}x${screenHeight} | mode=$mode | elements=${displayNodes.size}")
            if (textFiltered.size > maxNodes) appendLine("(truncated from ${textFiltered.size})")
            appendLine()
            for ((i, node) in displayNodes.withIndex()) {
                val indent = "  ".repeat(node.depth.coerceAtMost(6))
                append("[$i]$indent${node.className}")

                if (node.text.isNotBlank()) append(" \"${node.text.take(60)}\"")
                if (node.contentDesc.isNotBlank() && node.contentDesc != node.text) {
                    append(" cd=\"${node.contentDesc.take(60)}\"")
                }

                val flags = mutableListOf<String>()
                if (node.isClickable) flags.add("clickable")
                if (node.isScrollable) flags.add("scrollable")
                if (node.isEditable) flags.add("editable")
                if (node.isChecked) flags.add("checked")
                if (node.isFocused) flags.add("focused")
                if (flags.isNotEmpty()) append(" [${flags.joinToString()}]")

                if (node.resourceId.isNotBlank()) append(" id=${node.resourceId}")

                val bounds = node.bounds
                if (bounds != null && !bounds.isEmpty) {
                    append(" (${bounds.left},${bounds.top})-(${bounds.right},${bounds.bottom})")
                }

                appendLine()
            }
        }
    }

    private data class NodeDesc(
        val className: String,
        val text: String,
        val contentDesc: String,
        val resourceId: String,
        val bounds: Rect?,
        val isClickable: Boolean,
        val isScrollable: Boolean,
        val isEditable: Boolean,
        val isChecked: Boolean,
        val isFocused: Boolean,
        val depth: Int
    )

    private fun flattenTree(node: AccessibilityNodeInfo, list: MutableList<NodeDesc>, depth: Int) {
        val desc = NodeDesc(
            className = node.className?.toString()?.substringAfterLast('.') ?: "View",
            text = node.text?.toString()?.trim() ?: "",
            contentDesc = node.contentDescription?.toString()?.trim() ?: "",
            resourceId = node.viewIdResourceName?.substringAfterLast('/') ?: "",
            bounds = Rect().also { node.getBoundsInScreen(it) },
            isClickable = node.isClickable,
            isScrollable = node.isScrollable,
            isEditable = node.isEditable,
            isChecked = node.isChecked,
            isFocused = node.isFocused,
            depth = depth
        )
        list.add(desc)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (child.isVisibleToUser) {
                flattenTree(child, list, depth + 1)
            }
            child.recycle()
        }
    }

    // ── Element finding ──

    data class FindResult(val nodeInfo: AccessibilityNodeInfo?, val index: Int, val error: String?)

    fun findNode(
        text: String?,
        contentDesc: String?,
        resourceId: String?,
        index: Int?,
        x: Int?,
        y: Int?
    ): FindResult {
        val root = rootInActiveWindow ?: return FindResult(null, -1, "No active window")

        // Determine search mode
        val searchByCoords = x != null && y != null
        val searchByIndex = index != null
        val searchById = !resourceId.isNullOrBlank()
        val searchByText = !text.isNullOrBlank()
        val searchByDesc = !contentDesc.isNullOrBlank()

        if (!searchByCoords && !searchByIndex && !searchById && !searchByText && !searchByDesc) {
            root.recycle()
            return FindResult(null, -1, "No search criteria provided")
        }

        if (searchByIndex) {
            val allNodes = mutableListOf<AccessibilityNodeInfo>()
            collectClickableNodes(root, allNodes)
            val target = allNodes.getOrNull(index)
            allNodes.forEachIndexed { i, n -> if (i != index) n.recycle() }
            root.recycle()
            return if (target != null) {
                FindResult(target, index, null)
            } else {
                FindResult(null, -1, "Index $index out of range (${allNodes.size} elements)")
            }
        }

        if (searchByCoords) {
            // Find the deepest node at the given coordinates
            val target = findNodeAtCoordinates(root, x!!, y!!)
            root.recycle()
            return if (target != null) {
                FindResult(target, -1, null)
            } else {
                FindResult(null, -1, "No element at coordinates ($x, $y)")
            }
        }

        // Text / contentDesc / resourceId search
        val results = mutableListOf<AccessibilityNodeInfo>()
        findNodesByCriteria(root, text, contentDesc, resourceId, results)
        root.recycle()

        if (results.isEmpty()) {
            val criteria = listOfNotNull(
                if (searchByText) "text='$text'" else null,
                if (searchByDesc) "contentDesc='$contentDesc'" else null,
                if (searchById) "resourceId='$resourceId'" else null
            ).joinToString(", ")
            return FindResult(null, -1, "No element matching $criteria found")
        }

        if (results.size > 1) {
            val previews = results.take(5).joinToString(" | ") { node ->
                val t = node.text?.toString()?.take(30) ?: ""
                val cd = node.contentDescription?.toString()?.take(30) ?: ""
                if (t.isNotBlank()) "\"$t\"" else if (cd.isNotBlank()) "cd=\"$cd\"" else node.className.toString().substringAfterLast('.')
            }
            results.forEach { it.recycle() }
            return FindResult(null, -1, "Ambiguous: ${results.size} matches found. Preview: $previews. Use more specific criteria or target by index from screen_read.")
        }

        val target = results[0]
        return FindResult(target, -1, null)
    }

    private fun collectClickableNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.isVisibleToUser && (node.isClickable || node.isEditable || node.isScrollable)) {
            list.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectClickableNodes(child, list)
            child.recycle()
        }
    }

    private fun findNodeAtCoordinates(node: AccessibilityNodeInfo, x: Int, y: Int): AccessibilityNodeInfo? {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (!rect.contains(x, y)) return null

        // Check children first (deepest match)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findNodeAtCoordinates(child, x, y)
            child.recycle()
            if (found != null) return found
        }

        // No child matched — return this node if it's interactive
        return if (node.isClickable || node.isEditable) {
            AccessibilityNodeInfo.obtain(node)
        } else {
            // Walk up to find the nearest clickable ancestor
            null
        }
    }

    private fun findNodesByCriteria(
        node: AccessibilityNodeInfo,
        text: String?,
        contentDesc: String?,
        resourceId: String?,
        results: MutableList<AccessibilityNodeInfo>
    ) {
        val nodeText = node.text?.toString()?.trim() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.trim() ?: ""
        val nodeId = node.viewIdResourceName ?: ""
        val shortId = nodeId.substringAfterLast('/')

        var matches = true
        if (!text.isNullOrBlank()) {
            matches = matches && (nodeText.contains(text, ignoreCase = true) ||
                nodeDesc.contains(text, ignoreCase = true))
        }
        if (!contentDesc.isNullOrBlank()) {
            matches = matches && nodeDesc.contains(contentDesc, ignoreCase = true)
        }
        if (!resourceId.isNullOrBlank()) {
            matches = matches && (shortId.equals(resourceId, ignoreCase = true) ||
                nodeId.contains(resourceId, ignoreCase = true))
        }

        if (matches && (node.isClickable || node.isEditable || node.isScrollable || text != null || contentDesc != null)) {
            results.add(AccessibilityNodeInfo.obtain(node))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findNodesByCriteria(child, text, contentDesc, resourceId, results)
            child.recycle()
        }
    }

    // ── Click operations ──

    fun clickNode(node: AccessibilityNodeInfo): String {
        try {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val centerX = bounds.centerX()
            val centerY = bounds.centerY()

            if (node.isClickable) {
                val success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                node.recycle()
                return if (success) "Clicked at ($centerX, $centerY)." else "Click action failed."
            }

            // Fallback: dispatch gesture at center of node bounds
            node.recycle()
            return dispatchClick(centerX.toFloat(), centerY.toFloat())
        } catch (e: Exception) {
            try { node.recycle() } catch (_: Exception) {}
            Lumberjack.e(TAG, "clickNode failed", e)
            return "Error clicking node: ${e.message}"
        }
    }

    fun dispatchClick(x: Float, y: Float): String {
        val latch = CountDownLatch(1)
        var result = "Click dispatched at ($x, $y)."

        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                latch.countDown()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                result = "Click gesture cancelled at ($x, $y)."
                latch.countDown()
            }
        }, null)

        if (!dispatched) {
            return "Failed to dispatch click gesture at ($x, $y)."
        }

        latch.await(500, TimeUnit.MILLISECONDS)
        return result
    }

    fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float): String {
        val latch = CountDownLatch(1)
        var result = "Swipe dispatched from ($startX, $startY) to ($endX, $endY)."

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                latch.countDown()
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                result = "Swipe gesture cancelled."
                latch.countDown()
            }
        }, null)

        if (!dispatched) {
            return "Failed to dispatch swipe gesture."
        }

        latch.await(500, TimeUnit.MILLISECONDS)
        return result
    }

    // ── Scroll ──

    fun scroll(direction: String, containerText: String?, containerIndex: Int?): String {
        val root = rootInActiveWindow ?: return "Error: No active window"

        val scrollable = if (containerIndex != null) {
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            collectScrollable(root, nodes)
            val target = nodes.getOrNull(containerIndex)
            nodes.forEachIndexed { i, n -> if (i != containerIndex) n.recycle() }
            target
        } else if (!containerText.isNullOrBlank()) {
            findScrollableByText(root, containerText)
        } else {
            findFirstScrollable(root)
        }

        root.recycle()

        if (scrollable == null) {
            return "Error: No scrollable container found."
        }

        val directionLower = direction.lowercase()
        return when (directionLower) {
            "down" -> {
                val ok = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                scrollable.recycle()
                if (ok) "Scrolled $direction." else "Scroll $direction failed."
            }
            "up" -> {
                val ok = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                scrollable.recycle()
                if (ok) "Scrolled $direction." else "Scroll $direction failed."
            }
            "left", "right" -> {
                val bounds = Rect()
                scrollable.getBoundsInScreen(bounds)
                scrollable.recycle()
                val centerY = bounds.centerY().toFloat()
                val startX = if (directionLower == "left") bounds.right * 0.8f else bounds.left * 1.2f
                val endX = if (directionLower == "left") bounds.left * 1.2f else bounds.right * 0.8f
                dispatchSwipe(startX, centerY, endX, centerY)
            }
            else -> {
                scrollable.recycle()
                "Error: Unknown direction '$direction'. Use 'up', 'down', 'left', or 'right'."
            }
        }
    }

    private fun collectScrollable(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.isScrollable && node.isVisibleToUser) {
            list.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectScrollable(child, list)
            child.recycle()
        }
    }

    private fun findScrollableByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodeText = node.text?.toString()?.trim() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.trim() ?: ""

        if (node.isScrollable && node.isVisibleToUser &&
            (nodeText.contains(text, ignoreCase = true) || nodeDesc.contains(text, ignoreCase = true))
        ) {
            return AccessibilityNodeInfo.obtain(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableByText(child, text)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findFirstScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable && node.isVisibleToUser) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstScrollable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    // ── Text input ──

    fun inputText(text: String, targetText: String?, targetIndex: Int?): String {
        val root = rootInActiveWindow ?: return "Error: No active window"

        val inputNode = if (targetIndex != null) {
            val editables = mutableListOf<AccessibilityNodeInfo>()
            collectEditableNodes(root, editables)
            val target = editables.getOrNull(targetIndex)
            editables.forEachIndexed { i, n -> if (i != targetIndex) n.recycle() }
            target
        } else if (!targetText.isNullOrBlank()) {
            findEditableNearText(root, targetText)
        } else {
            findFirstEditable(root)
        }

        root.recycle()

        if (inputNode == null) {
            return "Error: No editable input field found."
        }

        // Focus first
        if (!inputNode.isFocused) {
            inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        inputNode.recycle()

        return if (success) "Text input (${text.length} chars)." else "Text input failed."
    }

    private fun collectEditableNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        if (node.isEditable && node.isVisibleToUser) {
            list.add(AccessibilityNodeInfo.obtain(node))
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectEditableNodes(child, list)
            child.recycle()
        }
    }

    private fun findEditableNearText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        // Look for a sibling/parent text label near the target text, then find the editable next to it
        val nodeText = node.text?.toString()?.trim() ?: ""
        val nodeDesc = node.contentDescription?.toString()?.trim() ?: ""

        if (nodeText.contains(text, ignoreCase = true) || nodeDesc.contains(text, ignoreCase = true)) {
            // Found a label — check siblings for editable
            val parent = node.parent
            if (parent != null) {
                val editable = findFirstEditable(parent)
                parent.recycle()
                if (editable != null) return editable
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNearText(child, text)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun findFirstEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isVisibleToUser) {
            return AccessibilityNodeInfo.obtain(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findFirstEditable(child)
            child.recycle()
            if (found != null) return found
        }
        return null
    }

    // ── Screenshot ──

    // Dedicated executor for screenshot callbacks — NOT the main executor,
    // to avoid deadlock if the main looper is blocked.
    private val screenshotExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "screenshot-callback").apply { isDaemon = true }
    }

    fun captureScreenshot(outputDir: File): String {
        Lumberjack.i(TAG, "captureScreenshot called, SDK=${Build.VERSION.SDK_INT}")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val result = captureScreenshotApi34(outputDir)
            // If API 34 path timed out or returned a clear failure, fall back to screencap
            if (result.startsWith("Screenshot: callback did not fire") ||
                result.startsWith("Screenshot failed, errorCode")
            ) {
                Lumberjack.w(TAG, "API 34 screenshot failed ($result), trying legacy screencap fallback")
                captureScreenshotLegacy(outputDir)
            } else {
                result
            }
        } else {
            captureScreenshotLegacy(outputDir)
        }
    }

    private fun captureScreenshotApi34(outputDir: File): String {
        val latch = CountDownLatch(1)
        var result = "Screenshot: callback did not fire within timeout (5s)"

        // Must be called from a thread with a Looper — post to main handler.
        Handler(Looper.getMainLooper()).post {
            try {
                Lumberjack.i(TAG, "Calling takeScreenshot on main thread...")
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    screenshotExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            try {
                                Lumberjack.i(TAG, "takeScreenshot onSuccess")
                                val bitmap = Bitmap.wrapHardwareBuffer(
                                    screenshot.hardwareBuffer, screenshot.colorSpace
                                )
                                if (bitmap == null) {
                                    result = "Error: Failed to wrap screenshot HardwareBuffer"
                                    screenshot.hardwareBuffer.close()
                                    latch.countDown()
                                    return
                                }
                                outputDir.mkdirs()
                                val file = File(outputDir, "screen_${System.currentTimeMillis()}.png")
                                FileOutputStream(file).use { fos ->
                                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos)
                                }
                                bitmap.recycle()
                                screenshot.hardwareBuffer.close()
                                result = "Screenshot saved: ${file.absolutePath} (${file.length()} bytes)"
                                Lumberjack.i(TAG, result)
                            } catch (e: Exception) {
                                result = "Error saving screenshot: ${e.message}"
                                Lumberjack.e(TAG, "Screenshot save failed", e)
                                try { screenshot.hardwareBuffer.close() } catch (_: Exception) {}
                            }
                            latch.countDown()
                        }

                        override fun onFailure(errorCode: Int) {
                            result = "Screenshot failed, errorCode=$errorCode (screen may be off, locked, or showing secure content)"
                            Lumberjack.e(TAG, result)
                            latch.countDown()
                        }
                    }
                )
            } catch (e: Exception) {
                result = "takeScreenshot call threw: ${e.message}"
                Lumberjack.e(TAG, "takeScreenshot exception", e)
                latch.countDown()
            }
        }

        val gotResult = latch.await(5, TimeUnit.SECONDS)
        if (!gotResult) {
            Lumberjack.e(TAG, "takeScreenshot timeout — callback never invoked")
        }
        return result
    }

    private fun captureScreenshotLegacy(outputDir: File): String {
        return try {
            outputDir.mkdirs()
            val file = File(outputDir, "screen_${System.currentTimeMillis()}.png")
            Lumberjack.i(TAG, "Legacy screenshot to ${file.absolutePath}")
            val process = Runtime.getRuntime().exec(
                arrayOf("screencap", "-p", file.absolutePath)
            )
            val exitCode = process.waitFor()
            Lumberjack.i(TAG, "screencap exit=$exitCode, file exists=${file.exists()}, size=${if (file.exists()) file.length() else 0}")
            if (file.exists() && file.length() > 0) {
                "Screenshot saved: ${file.absolutePath} (${file.length()} bytes)"
            } else {
                val err = process.errorStream?.bufferedReader()?.readText() ?: ""
                "Error: Screenshot capture failed. screencap exit=$exitCode, stderr=$err"
            }
        } catch (e: Exception) {
            Lumberjack.e(TAG, "Legacy screenshot failed", e)
            "Error: Screenshot failed — ${e.message}"
        }
    }

    companion object {
        private const val TAG = "AccessibilityService"

        @Volatile
        private var instance: LinkXAccessibilityService? = null

        fun getInstance(): LinkXAccessibilityService? = instance

        fun isConnected(): Boolean = instance != null
    }
}
