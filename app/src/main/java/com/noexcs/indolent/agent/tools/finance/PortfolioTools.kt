package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PortfolioAddTool : AgentTool {
    override val name = "portfolio_add"
    override val description = "添加基金持仓。code=基金代码(必填), name=名称(不填则自动获取), cost_nav=买入时单位净值, shares=持有份额, buy_date=购买日期YYYY-MM-DD, notes=备注(如定投/一次性)"
    override val parameters = listOf(
        ToolParameter("code", "string", "基金代码(6位)", true),
        ToolParameter("name", "string", "基金名称, 可不填自动获取", false, ""),
        ToolParameter("cost_nav", "number", "买入时单位净值", false, "1.0"),
        ToolParameter("shares", "number", "持有份额", false, "0.0"),
        ToolParameter("buy_date", "string", "购买日期 YYYY-MM-DD", false, ""),
        ToolParameter("notes", "string", "备注: 定投/一次性/目标仓位等", false, "")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("portfolio")
            mod.callAttr("portfolio_add",
                args["code"]?.toString() ?: return@withContext "Error: code required",
                args["name"]?.toString() ?: "",
                (args["cost_nav"]?.toString())?.toDoubleOrNull() ?: 1.0,
                (args["shares"]?.toString())?.toDoubleOrNull() ?: 0.0,
                args["buy_date"]?.toString() ?: "",
                args["notes"]?.toString() ?: ""
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("PortfolioTools", "Error in portfolio_add", e)
            "Error: ${e.message}"
        }
    }
}

object PortfolioUpdateTool : AgentTool {
    override val name = "portfolio_update"
    override val description = "更新持仓。只需传要改的字段，不传则保持原值。可更新: code, name, cost_nav, shares, buy_date, notes"
    override val parameters = listOf(
        ToolParameter("id", "integer", "持仓ID(从portfolio_list获取)", true),
        ToolParameter("code", "string", "基金代码", false),
        ToolParameter("name", "string", "基金名称", false),
        ToolParameter("cost_nav", "number", "买入净值", false),
        ToolParameter("shares", "number", "持有份额", false),
        ToolParameter("buy_date", "string", "购买日期", false),
        ToolParameter("notes", "string", "备注", false)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("portfolio")
            mod.callAttr(
                "portfolio_update",
                (args["id"]?.toString())?.toIntOrNull() ?: return@withContext "Error: id required",
                args["code"]?.toString(),
                args["name"]?.toString(),
                (args["cost_nav"]?.toString())?.toDoubleOrNull(),
                (args["shares"]?.toString())?.toDoubleOrNull(),
                args["buy_date"]?.toString(),
                args["notes"]?.toString()
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("PortfolioTools", "Error in portfolio_update", e)
            "Error: ${e.message}"
        }
    }
}

object PortfolioRemoveTool : AgentTool {
    override val name = "portfolio_remove"
    override val description = "删除指定持仓"
    override val parameters = listOf(
        ToolParameter("id", "integer", "持仓ID", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("portfolio")
            mod.callAttr("portfolio_remove",
                (args["id"]?.toString())?.toIntOrNull() ?: return@withContext "Error: id required"
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("PortfolioTools", "Error in portfolio_remove", e)
            "Error: ${e.message}"
        }
    }
}

object PortfolioListTool : AgentTool {
    override val name = "portfolio_list"
    override val description = "列出全部持仓。返回每支基金的id/代码/名称/成本净值/份额/购买日期/备注，供AI分析使用"
    override val parameters = emptyList<ToolParameter>()
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("portfolio")
            mod.callAttr("portfolio_list").toString()
        } catch (e: Exception) {
            Lumberjack.e("PortfolioTools", "Error in portfolio_list", e)
            "Error: ${e.message}"
        }
    }
}

object PortfolioSummaryTool : AgentTool {
    override val name = "portfolio_summary"
    override val description = "持仓汇总分析。自动获取每支基金最新净值，计算: 当前市值/成本/盈亏金额/收益率/持仓占比/组合总盈亏。AI可直接用此数据给出持仓建议"
    override val parameters = emptyList<ToolParameter>()
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("portfolio")
            mod.callAttr("portfolio_summary").toString()
        } catch (e: Exception) {
            Lumberjack.e("PortfolioTools", "Error in portfolio_summary", e)
            "Error: ${e.message}"
        }
    }
}

object PortfolioAnalyzeAllTool : AgentTool {
    override val name = "portfolio_analyze_all"
    override val description = "对全部持仓逐一获取买卖信号+业绩指标。对每支基金自动调用fund_decision_signal和fund_performance，供AI全面评估整个组合"
    override val parameters = emptyList<ToolParameter>()
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("portfolio")
            mod.callAttr("portfolio_analyze_all").toString()
        } catch (e: Exception) {
            Lumberjack.e("PortfolioTools", "Error in portfolio_analyze_all", e)
            "Error: ${e.message}"
        }
    }
}

val portfolio_tools = listOf(
    PortfolioAddTool,
    PortfolioUpdateTool,
    PortfolioRemoveTool,
    PortfolioListTool,
    PortfolioSummaryTool,
    PortfolioAnalyzeAllTool
)
