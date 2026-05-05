package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FundInfoIndexEmTool : AgentTool {
    override val name = "fund_info_index_em"
    override val description = "基金列表-指数型。返回: 基金代码, 基金名称, 单位净值, 日增长率, 近1周-成立来, 跟踪标的等。symbol可选: 全部/沪深指数/行业主题/大盘指数/中盘指数/小盘指数/股票指数/债券指数; indicator可选: 全部/被动指数型/增强指数型"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "跟踪标的", false, "全部"),
        ToolParameter("indicator", "string", "跟踪方式", false, "全部"),
        ToolParameter("sort_by", "string", "排序字段: 单位净值/日增长率/近1周/近1月/近3月/近6月/近1年/近2年/近3年/今年来/成立来", false, "近1月"),
        ToolParameter("sort_ascending", "boolean", "是否升序", false, "false"),
        ToolParameter("limit", "integer", "返回数量", false, "20")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_em")
            fun bool(s: String?) = s?.lowercase() == "true"
            mod.callAttr("fund_info_index_em",
                args["symbol"]?.toString() ?: "全部",
                args["indicator"]?.toString() ?: "全部",
                args["sort_by"]?.toString() ?: "近1月",
                bool(args["sort_ascending"]?.toString()),
                (args["limit"]?.toString())?.toIntOrNull() ?: 20
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundEmTools", "Error in fund_info_index_em", e)
                "Error: ${e.message}"
            }
    }
}

object FundOpenFundInfoEmTool : AgentTool {
    override val name = "fund_open_fund_info_em"
    override val description = "开放式基金历史数据。indicator可选: 单位净值走势/累计净值走势/累计收益率走势/同类排名走势/同类排名百分比/分红送配详情/拆分详情"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码", true),
        ToolParameter("indicator", "string", "历史数据项", false, "单位净值走势"),
        ToolParameter("limit", "integer", "最新多少天数据", false, "30")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_em")
            mod.callAttr("fund_open_fund_info_em",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["indicator"]?.toString() ?: "单位净值走势",
                (args["limit"]?.toString())?.toIntOrNull() ?: 30
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundEmTools", "Error in fund_open_fund_info_em", e)
                "Error: ${e.message}"
            }
    }
}

object FundETFFundInfoEmTool : AgentTool {
    override val name = "fund_etf_fund_info_em"
    override val description = "场内ETF基金历史净值明细。返回: 净值日期, 单位净值, 累计净值, 日增长率, 申购状态, 赎回状态"
    override val parameters = listOf(
        ToolParameter("fund", "string", "ETF基金代码", true),
        ToolParameter("start_date", "string", "开始日期 YYYYMMDD", true),
        ToolParameter("end_date", "string", "结束日期 YYYYMMDD", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_em")
            mod.callAttr("fund_etf_fund_info_em",
                args["fund"]?.toString() ?: return@withContext "Error: fund required",
                args["start_date"]?.toString() ?: return@withContext "Error: start_date required",
                args["end_date"]?.toString() ?: return@withContext "Error: end_date required"
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundEmTools", "Error in fund_etf_fund_info_em", e)
                "Error: ${e.message}"
            }
    }
}

object FundValueEstimationEmTool : AgentTool {
    override val name = "fund_value_estimate_em"
    override val description = "基金今日净值估算。返回单只基金的: 估算值, 估算增长率, 单位净值, 日增长率, 估算偏差"
    override val parameters = listOf(
        ToolParameter("fund", "string", "基金代码", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_em")
            mod.callAttr("fund_value_estimation_em",
                args["fund"]?.toString() ?: return@withContext "Error: fund required"
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundEmTools", "Error in fund_value_estimation_em", e)
                "Error: ${e.message}"
            }
    }
}

object FundValueEstimationEmRankTool : AgentTool {
    override val name = "fund_value_estimation_em_rank"
    override val description = "基金今日估算增长率排名。按估算增长率降序排列"
    override val parameters = listOf(
        ToolParameter("limit", "integer", "返回数量", false, "15")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_em")
            mod.callAttr("fund_value_estimation_em_rank",
                (args["limit"]?.toString())?.toIntOrNull() ?: 15
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundEmTools", "Error in fund_value_estimation_em_rank", e)
                "Error: ${e.message}"
            }
    }
}

val fund_em_tools = listOf(
    FundInfoIndexEmTool,
    FundOpenFundInfoEmTool,
    FundETFFundInfoEmTool,
    FundValueEstimationEmTool,
    FundValueEstimationEmRankTool
)
