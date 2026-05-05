package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StockIndividualFundFlowTool : AgentTool {
    override val name = "stock_individual_fund_flow"
    override val description = "个股资金流向。返回: 日期, 主力净流入, 超大单净流入, 大单净流入, 中单净流入, 小单净流入"
    override val parameters = listOf(
        ToolParameter("stock", "string", "6位股票代码", true),
        ToolParameter("market", "string", "市场: sh/sz/bj", false, "sh"),
        ToolParameter("limit", "integer", "返回最近多少条", false, "20")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_fund_flow")
            mod.callAttr("stock_individual_fund_flow",
                args["stock"]?.toString() ?: return@withContext "Error: stock required",
                args["market"]?.toString() ?: "sh",
                (args["limit"]?.toString())?.toIntOrNull() ?: 20
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockFundFlowTools", "Error in stock_individual_fund_flow", e)
            "Error: ${e.message}"
        }
    }
}

object StockMarketFundFlowTool : AgentTool {
    override val name = "stock_market_fund_flow"
    override val description = "大盘资金流向。返回: 日期, 上证主力净流入, 深证主力净流入, 创业板主力净流入"
    override val parameters = listOf(
        ToolParameter("limit", "integer", "返回最近多少条", false, "20")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_fund_flow")
            mod.callAttr("stock_market_fund_flow",
                (args["limit"]?.toString())?.toIntOrNull() ?: 20
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockFundFlowTools", "Error in stock_market_fund_flow", e)
            "Error: ${e.message}"
        }
    }
}

object StockSectorFundFlowRankTool : AgentTool {
    override val name = "stock_sector_fund_flow_rank"
    override val description = "板块资金流向排名。indicator: 今日/5日/10日, sector_type: 行业资金流/概念资金流/地域资金流"
    override val parameters = listOf(
        ToolParameter("indicator", "string", "周期: 今日/5日/10日", false, "今日"),
        ToolParameter("sector_type", "string", "板块类型: 行业资金流/概念资金流/地域资金流", false, "行业资金流"),
        ToolParameter("limit", "integer", "返回前几名", false, "20")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_fund_flow")
            mod.callAttr("stock_sector_fund_flow_rank",
                args["indicator"]?.toString() ?: "今日",
                args["sector_type"]?.toString() ?: "行业资金流",
                (args["limit"]?.toString())?.toIntOrNull() ?: 20
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockFundFlowTools", "Error in stock_sector_fund_flow_rank", e)
            "Error: ${e.message}"
        }
    }
}

object StockSectorFundFlowSummaryTool : AgentTool {
    override val name = "stock_sector_fund_flow_summary"
    override val description = "行业板块个股资金流向。返回指定行业内个股的资金流向详情"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "行业板块名称, 如: 银行/半导体/白酒等", true),
        ToolParameter("indicator", "string", "周期: 今日/5日/10日", false, "今日"),
        ToolParameter("limit", "integer", "返回前几只个股", false, "20")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_fund_flow")
            mod.callAttr("stock_sector_fund_flow_summary",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["indicator"]?.toString() ?: "今日",
                (args["limit"]?.toString())?.toIntOrNull() ?: 20
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockFundFlowTools", "Error in stock_sector_fund_flow_summary", e)
            "Error: ${e.message}"
        }
    }
}

object StockSectorFundFlowHistTool : AgentTool {
    override val name = "stock_sector_fund_flow_hist"
    override val description = "行业板块历史资金流向走势。返回指定行业板块的资金流向历史数据"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "行业板块名称", true),
        ToolParameter("limit", "integer", "返回最近多少条", false, "30")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_fund_flow")
            mod.callAttr("stock_sector_fund_flow_hist",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                (args["limit"]?.toString())?.toIntOrNull() ?: 30
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockFundFlowTools", "Error in stock_sector_fund_flow_hist", e)
            "Error: ${e.message}"
        }
    }
}

object StockConceptFundFlowHistTool : AgentTool {
    override val name = "stock_concept_fund_flow_hist"
    override val description = "概念板块历史资金流向走势。返回指定概念板块的资金流向历史数据"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "概念板块名称, 如: AI智能体/低空经济等", true),
        ToolParameter("limit", "integer", "返回最近多少条", false, "30")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_fund_flow")
            mod.callAttr("stock_concept_fund_flow_hist",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                (args["limit"]?.toString())?.toIntOrNull() ?: 30
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockFundFlowTools", "Error in stock_concept_fund_flow_hist", e)
            "Error: ${e.message}"
        }
    }
}

val stock_fund_flow_tools = listOf(
    StockIndividualFundFlowTool,
    StockMarketFundFlowTool,
    StockSectorFundFlowRankTool,
    StockSectorFundFlowSummaryTool,
    StockSectorFundFlowHistTool,
    StockConceptFundFlowHistTool
)
