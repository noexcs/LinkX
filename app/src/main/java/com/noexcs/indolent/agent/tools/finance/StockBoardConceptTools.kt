package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StockBoardConceptHistEmTool : AgentTool {
    override val name = "stock_board_concept_hist_em"
    override val description = "东方财富概念板块历史行情K线。返回: 日期, 开盘, 收盘, 最高, 最低, 成交量, 成交额, 涨跌幅等"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "概念板块名称, 如: 融资融券/AI智能体/低空经济等", true),
        ToolParameter("period", "string", "K线周期: daily/weekly/monthly", false, "daily"),
        ToolParameter("start_date", "string", "开始日期 YYYYMMDD", false, "20240101"),
        ToolParameter("end_date", "string", "结束日期 YYYYMMDD", false, "20500101"),
        ToolParameter("adjust", "string", "复权: qfq(前复权)/hfq(后复权)/\"\"(不复权)", false, "qfq"),
        ToolParameter("limit", "integer", "最新多少条", false, "60")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_board_concept")
            mod.callAttr("stock_board_concept_hist_em",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["period"]?.toString() ?: "daily",
                args["start_date"]?.toString() ?: "20240101",
                args["end_date"]?.toString() ?: "20500101",
                args["adjust"]?.toString() ?: "qfq",
                (args["limit"]?.toString())?.toIntOrNull() ?: 60
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockBoardConceptTools", "Error in stock_board_concept_hist_em", e)
            "Error: ${e.message}"
        }
    }
}

object StockBoardConceptConsEmTool : AgentTool {
    override val name = "stock_board_concept_cons_em"
    override val description = "东方财富概念板块成分股。返回概念板块包含的个股: 代码, 名称, 最新价, 涨跌幅, 成交量, 成交额, 流通市值, 总市值等"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "概念板块名称, 如: 融资融券/AI智能体/人形机器人等", true),
        ToolParameter("limit", "integer", "返回前几只个股", false, "50")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_board_concept")
            mod.callAttr("stock_board_concept_cons_em",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                (args["limit"]?.toString())?.toIntOrNull() ?: 50
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockBoardConceptTools", "Error in stock_board_concept_cons_em", e)
            "Error: ${e.message}"
        }
    }
}

val stock_board_concept_tools = listOf(
    StockBoardConceptHistEmTool,
    StockBoardConceptConsEmTool
)
