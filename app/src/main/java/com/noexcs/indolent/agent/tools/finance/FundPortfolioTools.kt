package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FundPortfolioHoldEmTool : AgentTool {
    override val name = "fund_portfolio_hold_em"
    override val description = "基金股票持仓。返回: 股票代码, 股票名称, 占净值比例, 持股数, 持股市值"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码", true),
        ToolParameter("date", "string", "年份 YYYY", true),
        ToolParameter("limit", "integer", "持仓前多少股票", false, "15")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_portfolio_em")
            mod.callAttr("fund_portfolio_hold_em",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["date"]?.toString() ?: return@withContext "Error: date required",
                (args["limit"]?.toString())?.toIntOrNull() ?: 15
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundPortfolioTools", "Error in fund_portfolio_hold_em", e)
                "Error: ${e.message}"
            }
    }
}

object FundPortfolioBondHoldEmTool : AgentTool {
    override val name = "fund_portfolio_bond_hold_em"
    override val description = "基金债券持仓。返回: 债券代码, 债券名称, 占净值比例, 持仓市值"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码", true),
        ToolParameter("date", "string", "年份 YYYY", true),
        ToolParameter("limit", "integer", "持仓前多少债券", false, "15")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_portfolio_em")
            mod.callAttr("fund_portfolio_bond_hold_em",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["date"]?.toString() ?: return@withContext "Error: date required",
                (args["limit"]?.toString())?.toIntOrNull() ?: 15
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundPortfolioTools", "Error in fund_portfolio_bond_hold_em", e)
                "Error: ${e.message}"
            }
    }
}

object FundPortfolioIndustryAllocationEmTool : AgentTool {
    override val name = "fund_portfolio_industry_allocation_em"
    override val description = "基金行业配置。返回最新季度的行业分布: 行业名称, 占净值比例"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码", true),
        ToolParameter("date", "string", "年份 YYYY", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_portfolio_em")
            mod.callAttr("fund_portfolio_industry_allocation_em",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["date"]?.toString() ?: return@withContext "Error: date required"
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundPortfolioTools", "Error in fund_portfolio_industry_allocation_em", e)
                "Error: ${e.message}"
            }
    }
}

object FundPortfolioChangeEmTool : AgentTool {
    override val name = "fund_portfolio_change_em"
    override val description = "基金投资组合重大变动。indicator: 累计买入/累计卖出。返回: 股票代码, 股票名称, 本期累计买入/卖出金额, 占期初基金资产净值比例"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码", true),
        ToolParameter("indicator", "string", "累计买入 或 累计卖出", true),
        ToolParameter("date", "string", "年份 YYYY", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_portfolio_em")
            mod.callAttr("fund_portfolio_change_em",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["indicator"]?.toString() ?: return@withContext "Error: indicator required",
                args["date"]?.toString() ?: return@withContext "Error: date required"
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundPortfolioTools", "Error in fund_portfolio_change_em", e)
                "Error: ${e.message}"
            }
    }
}

val fund_portfolio_em_tools = listOf(
    FundPortfolioHoldEmTool,
    FundPortfolioBondHoldEmTool,
    FundPortfolioIndustryAllocationEmTool,
    FundPortfolioChangeEmTool
)
