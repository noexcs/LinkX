package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FundOverviewEmTool : AgentTool {
    override val name = "fund_overview_em"
    override val description = "基金档案-基本概况。返回: 基金全称, 简称, 类型, 成立日期/规模, 净资产规模, 基金管理人, 基金托管人, 基金经理, 费率, 业绩比较基准, 跟踪标的"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_overview_em")
            mod.callAttr("fund_overview_em",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required"
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundOverviewTools", "Error in fund_overview_em", e)
                "Error: ${e.message}"
            }
    }
}

val fund_overview_em_tools = listOf(FundOverviewEmTool)
