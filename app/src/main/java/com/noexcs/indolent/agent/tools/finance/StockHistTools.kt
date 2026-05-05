package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StockZhAHistTool : AgentTool {
    override val name = "stock_zh_a_hist"
    override val description = "A股历史行情。返回: 日期, 开盘, 收盘, 最高, 最低, 成交量, 成交额, 振幅, 涨跌幅, 涨跌额, 换手率。东方财富失败自动回退腾讯证券"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "6位股票代码", true),
        ToolParameter("period", "string", "K线周期: daily/weekly/monthly", false, "daily"),
        ToolParameter("start_date", "string", "开始日期 YYYYMMDD", false, "20240101"),
        ToolParameter("end_date", "string", "结束日期 YYYYMMDD", false, "20500101"),
        ToolParameter("adjust", "string", "复权: qfq(前复权)/hfq(后复权)/\"\"(不复权)", false, "qfq"),
        ToolParameter("limit", "integer", "最新多少条", false, "60")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_hist")
            mod.callAttr("stock_zh_a_hist",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["period"]?.toString() ?: "daily",
                args["start_date"]?.toString() ?: "20240101",
                args["end_date"]?.toString() ?: "20500101",
                args["adjust"]?.toString() ?: "qfq",
                (args["limit"]?.toString())?.toIntOrNull() ?: 60
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockHistTools", "Error in stock_zh_a_hist", e)
            "Error: ${e.message}"
        }
    }
}

val stock_hist_tools = listOf(StockZhAHistTool)
