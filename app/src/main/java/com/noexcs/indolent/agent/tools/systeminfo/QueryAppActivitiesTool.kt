package com.noexcs.indolent.agent.tools.systeminfo

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QueryAppActivitiesTool(context: Context) : AgentTool {
    private val appContext = context.applicationContext

    override val name = "query_app_activities"
    override val description = """
        Query an installed app's launchable activities and their deep link configurations,
        or fuzzy-search across all apps by keyword.

        Modes:
        - packageName only → full activity details for that app
        - query only → search all apps for activities matching the keyword
        - both → filter the named app's activities by keyword

        Each result includes: class name, launcher status, intent actions, URI schemes,
        hosts, path patterns (with match type), MIME types, and required permissions.
    """.trimIndent()

    override val parameters = listOf(
        ToolParameter(
            name = "packageName",
            type = "string",
            required = false,
            description = "Package name of the app to query (e.g. com.android.chrome)"
        ),
        ToolParameter(
            name = "query",
            type = "string",
            required = false,
            description = "Fuzzy search keyword matching activity class name, action, scheme,"
                + " authority, path pattern, or MIME type across all apps. Max 20 results."
        ),
    )

    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        val packageName = (args["packageName"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        val query = (args["query"] as? String)?.trim()?.takeIf { it.isNotBlank() }

        if (packageName == null && query == null) {
            return@withContext "Error: provide at least one of 'packageName' or 'query'."
        }

        val pm = appContext.packageManager

        when {
            query != null -> fuzzySearchAcrossApps(pm, query, packageName)
            else -> querySinglePackage(pm, packageName!!)
        }
    }

    // ─── Single-package query ─────────────────────────────────

    private fun querySinglePackage(pm: PackageManager, packageName: String): String {
        val exportedActivities = getExportedActivities(pm, packageName)

        if (exportedActivities == null) {
            return "Error: Package '$packageName' is not installed."
        }
        if (exportedActivities.isEmpty()) {
            return "No exported activities found for '$packageName'."
        }

        val activityMap = linkedMapOf<String, ActivityData>()
        for (ai in exportedActivities) {
            activityMap[ai.name] = ActivityData(
                shortName = ai.name.substringAfterLast('.'),
                fullName = ai.name,
                permission = resolvePermission(ai),
            )
        }

        // Phase 1: probe implicit intents to discover filters for as many activities as possible
        // Use GET_RESOLVED_FILTER so ResolveInfo.filter contains the FULL manifest filter
        val launcherSet = mutableSetOf<String>()
        val probeIntents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            Intent(Intent.ACTION_VIEW),
            Intent(Intent.ACTION_SEND).setType("text/plain"),
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("http://x")),
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://x")),
        )

        for (probe in probeIntents) {
            val results: List<ResolveInfo> = try {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(probe, FI_GET_RESOLVED_FILTER)
            } catch (e: Exception) {
                Lumberjack.e(TAG, "Probe ${probe.action} failed: ${e.message}", e)
                emptyList()
            }
            for (ri in results) {
                if (ri.activityInfo.packageName != packageName) continue
                if (!ri.activityInfo.exported) continue
                val name = ri.activityInfo.name

                // Mark launcher
                ri.filter?.let { f ->
                    if (f.hasAction(Intent.ACTION_MAIN) &&
                        f.hasCategory(Intent.CATEGORY_LAUNCHER)) {
                        launcherSet.add(name)
                    }
                }

                val entry = activityMap[name] ?: continue
                ri.filter?.let { entry.mergeFilter(it) }
            }
        }

        // Phase 2: for activities still without filter, try explicit resolution
        for ((name, entry) in activityMap) {
            if (entry.filter != null) continue
            try {
                @Suppress("DEPRECATION")
                val results = pm.queryIntentActivities(
                    Intent().setClassName(packageName, name),
                    FI_GET_RESOLVED_FILTER,
                )
                val ri = results.firstOrNull()
                ri?.filter?.let { entry.mergeFilter(it) }
            } catch (e: Exception) {
                Lumberjack.e(TAG, "Explicit resolve failed for $packageName/$name: ${e.message}", e)
            }
        }

        return formatActivityList(packageName, activityMap, launcherSet)
    }

    // ─── Fuzzy search across all apps ─────────────────────────

    private fun fuzzySearchAcrossApps(
        pm: PackageManager,
        query: String,
        targetPackage: String?,
    ): String {
        val lowerQuery = query.lowercase()
        val maxResults = 20
        val results = mutableListOf<SearchResult>()

        val installed = pm.getInstalledApplications(0)
        for (appInfo in installed) {
            if (targetPackage != null && appInfo.packageName != targetPackage) continue
            if (results.size >= maxResults && targetPackage == null) break

            val appLabel = appInfo.loadLabel(pm).toString()
            val exportedActivities = getExportedActivities(pm, appInfo.packageName) ?: continue

            for (ai in exportedActivities) {
                if (results.size >= maxResults) break

                val className = ai.name
                val shortName = className.substringAfterLast('.')
                val matches = mutableListOf<String>()

                // Quick class-name match (no filter resolution needed)
                if (className.lowercase().contains(lowerQuery) ||
                    shortName.lowercase().contains(lowerQuery)) {
                    matches.add("className")
                    addResultIfNew(results, appInfo.packageName, appLabel, shortName, className, matches)
                    continue
                }

                // Resolve filter and match against all dimensions
                var filter: IntentFilter? = null
                try {
                    @Suppress("DEPRECATION")
                    val ris = pm.queryIntentActivities(
                        Intent().setClassName(appInfo.packageName, className),
                        FI_GET_RESOLVED_FILTER,
                    )
                    filter = ris.firstOrNull()?.filter
                } catch (e: Exception) {
                    Lumberjack.e(TAG, "Filter resolve failed ${appInfo.packageName}/$className: ${e.message}", e)
                }

                if (filter == null) continue

                // Match all filter dimensions
                for (i in 0 until filter.countActions()) {
                    val a = filter.getAction(i)
                    if (a.lowercase().contains(lowerQuery)) matches.add("action:$a")
                }
                for (i in 0 until filter.countDataSchemes()) {
                    val s = filter.getDataScheme(i)
                    if (s.lowercase().contains(lowerQuery)) matches.add("scheme:$s")
                }
                for (i in 0 until filter.countDataAuthorities()) {
                    val host = filter.getDataAuthority(i).host
                    if (host.lowercase().contains(lowerQuery)) matches.add("host:$host")
                }
                for (i in 0 until filter.countDataPaths()) {
                    val path = filter.getDataPath(i).path
                    if (path.lowercase().contains(lowerQuery)) matches.add("path:$path")
                }
                for (i in 0 until filter.countDataTypes()) {
                    val t = filter.getDataType(i)
                    if (t.lowercase().contains(lowerQuery)) matches.add("mime:$t")
                }

                if (matches.isNotEmpty()) {
                    addResultIfNew(results, appInfo.packageName, appLabel, shortName, className, matches)
                }
            }
        }

        if (results.isEmpty()) {
            return "No exported activities found matching \"$query\"."
        }

        val sorted = results.sortedByDescending { r ->
            var score = 0
            r.matches.forEach {
                score += when {
                    it == "className" -> 100
                    it.startsWith("action:") -> 50
                    it.startsWith("scheme:") -> 30
                    it.startsWith("host:") -> 30
                    it.startsWith("path:") -> 25
                    it.startsWith("mime:") -> 20
                    else -> 10
                }
            }
            score
        }

        return buildString {
            appendLine("Search results for \"$query\" (${sorted.size} found):")
            appendLine("=".repeat(56))
            sorted.forEachIndexed { i, r ->
                appendLine()
                appendLine("[${i + 1}] ${r.packageName} / ${r.appLabel}")
                appendLine("    Activity: ${r.shortName}")
                appendLine("    Class: ${r.fullName}")
                appendLine("    Matched: ${r.matches.joinToString(", ")}")
            }
        }
    }

    private fun addResultIfNew(
        list: MutableList<SearchResult>,
        pkg: String, label: String, short: String, full: String,
        matches: List<String>,
    ) {
        if (list.none { it.packageName == pkg && it.fullName == full }) {
            list.add(SearchResult(pkg, label, short, full, matches))
        }
    }

    // ─── Formatting ───────────────────────────────────────────

    private fun formatActivityList(
        packageName: String,
        activityMap: Map<String, ActivityData>,
        launcherSet: Set<String>,
    ): String = buildString {
        appendLine("Activities for $packageName (${activityMap.size} exported)")
        appendLine("=".repeat(56))

        activityMap.values.forEachIndexed { index, act ->
            if (index > 0) appendLine()

            val isLauncher = launcherSet.contains(act.fullName)
            val tagStr = if (isLauncher) " [Launcher]" else ""

            appendLine("Activity: ${act.shortName}$tagStr")
            appendLine("  Class: ${act.fullName}")
            appendLine("  Permission: ${act.permission}")

            val f = act.filter

            if (f == null) {
                appendLine("  (No intent filter data available)")
                return@forEachIndexed
            }

            if (f.countActions() > 0) {
                appendLine("  Actions:")
                for (i in 0 until f.countActions()) {
                    appendLine("    - ${f.getAction(i)}")
                }
            }
            if (f.countCategories() > 0) {
                appendLine("  Categories:")
                for (i in 0 until f.countCategories()) {
                    appendLine("    - ${f.getCategory(i)}")
                }
            }
            if (f.countDataSchemes() > 0) {
                val schemes = (0 until f.countDataSchemes()).map { f.getDataScheme(it) }
                appendLine("  Schemes: ${schemes.joinToString(", ")}")
            }
            if (f.countDataAuthorities() > 0) {
                appendLine("  Authorities:")
                for (i in 0 until f.countDataAuthorities()) {
                    val auth = f.getDataAuthority(i)
                    val portStr = if (auth.port >= 0) ":${auth.port}" else ""
                    appendLine("    - ${auth.host}$portStr")
                }
            }
            if (f.countDataPaths() > 0) {
                appendLine("  Paths:")
                for (i in 0 until f.countDataPaths()) {
                    val pm = f.getDataPath(i)
                    val typeLabel = when (pm.type) {
                        android.os.PatternMatcher.PATTERN_LITERAL -> "(精确)"
                        android.os.PatternMatcher.PATTERN_PREFIX -> "(前缀)"
                        android.os.PatternMatcher.PATTERN_SIMPLE_GLOB -> "(通配)"
                        android.os.PatternMatcher.PATTERN_ADVANCED_GLOB -> "(正则)"
                        else -> ""
                    }
                    appendLine("    - ${pm.path} $typeLabel")
                }
            }
            if (f.countDataTypes() > 0) {
                appendLine("  MIME Types:")
                for (i in 0 until f.countDataTypes()) {
                    appendLine("    - ${f.getDataType(i)}")
                }
            }
            if (f.countDataSchemes() > 0 && f.countDataAuthorities() > 0) {
                val scheme = f.getDataScheme(0)
                val host = f.getDataAuthority(0).host
                val path = if (f.countDataPaths() > 0) {
                    f.getDataPath(0).path.removeSuffix(".*").removeSuffix(".")
                } else ""
                appendLine("  Example: $scheme://$host$path")
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────

    private fun getExportedActivities(
        pm: PackageManager,
        packageName: String,
    ): List<ActivityInfo>? {
        val pkgInfo = try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        } catch (e: Exception) {
            Lumberjack.e(TAG, "getPackageInfo failed for $packageName: ${e.message}", e)
            return null
        }
        return pkgInfo.activities?.filter { it.exported } ?: emptyList()
    }

    private fun resolvePermission(ai: ActivityInfo): String {
        ai.permission?.let { return it }
        ai.applicationInfo?.permission?.let { return "${it} (inherited from Application)" }
        return "(无需权限)"
    }

    // ─── Data ─────────────────────────────────────────────────

    private class ActivityData(
        val shortName: String,
        val fullName: String,
        val permission: String,
    ) {
        var filter: IntentFilter? = null

        fun mergeFilter(other: IntentFilter) {
            try {
                if (filter == null) {
                    filter = IntentFilter()
                    copyInto(filter!!, other)
                } else {
                    mergeExisting(filter!!, other)
                }
            } catch (e: Exception) {
                Lumberjack.e(TAG, "mergeFilter failed for $fullName: ${e.message}", e)
            }
        }

        private fun mergeExisting(existing: IntentFilter, other: IntentFilter) {
            val existingAuthKeys = (0 until existing.countDataAuthorities())
                .map { i -> val a = existing.getDataAuthority(i); "${a.host}:${a.port}" }
                .toMutableSet()
            val existingPathKeys = (0 until existing.countDataPaths())
                .map { i -> val p = existing.getDataPath(i); "${p.path}:${p.type}" }
                .toMutableSet()

            for (i in 0 until other.countActions()) {
                val a = other.getAction(i)
                if (!existing.hasAction(a)) existing.addAction(a)
            }
            for (i in 0 until other.countCategories()) {
                val c = other.getCategory(i)
                if (!existing.hasCategory(c)) existing.addCategory(c)
            }
            for (i in 0 until other.countDataSchemes()) {
                val s = other.getDataScheme(i)
                if (!existing.hasDataScheme(s)) existing.addDataScheme(s)
            }
            for (i in 0 until other.countDataAuthorities()) {
                val auth = other.getDataAuthority(i)
                val key = "${auth.host}:${auth.port}"
                if (key !in existingAuthKeys) {
                    existingAuthKeys.add(key)
                    existing.addDataAuthority(auth.host,
                        if (auth.port >= 0) auth.port.toString() else null)
                }
            }
            for (i in 0 until other.countDataPaths()) {
                val pm = other.getDataPath(i)
                val key = "${pm.path}:${pm.type}"
                if (key !in existingPathKeys) {
                    existingPathKeys.add(key)
                    existing.addDataPath(pm.path, pm.type)
                }
            }
            for (i in 0 until other.countDataTypes()) {
                val dt = other.getDataType(i)
                if (!existing.hasDataType(dt)) existing.addDataType(dt)
            }
        }

        private fun copyInto(dst: IntentFilter, src: IntentFilter) {
            for (i in 0 until src.countActions()) dst.addAction(src.getAction(i))
            for (i in 0 until src.countCategories()) dst.addCategory(src.getCategory(i))
            for (i in 0 until src.countDataSchemes()) dst.addDataScheme(src.getDataScheme(i))
            for (i in 0 until src.countDataAuthorities()) {
                val auth = src.getDataAuthority(i)
                dst.addDataAuthority(auth.host,
                    if (auth.port >= 0) auth.port.toString() else null)
            }
            for (i in 0 until src.countDataPaths()) {
                val pm = src.getDataPath(i)
                dst.addDataPath(pm.path, pm.type)
            }
            for (i in 0 until src.countDataTypes()) dst.addDataType(src.getDataType(i))
        }
    }

    private data class SearchResult(
        val packageName: String,
        val appLabel: String,
        val shortName: String,
        val fullName: String,
        val matches: List<String>,
    )

    companion object {
        private const val TAG = "QueryAppActivitiesTool"
        private const val FI_GET_RESOLVED_FILTER = PackageManager.GET_RESOLVED_FILTER
    }
}
