package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StockIntradayEmTool : AgentTool {
    override val name = "stock_intraday_em"
    override val description = "东方财富日内分时数据。返回当日分时: 时间, 价格, 成交量, 成交额, 均价"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "股票代码(6位数字)", false, "603777")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_intraday")
            mod.callAttr("stock_intraday_em",
                args["symbol"]?.toString() ?: "603777"
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockIntradayTools", "Error in stock_intraday_em", e)
            "Error: ${e.message}"
        }
    }
}

object StockIntradaySinaTool : AgentTool {
    override val name = "stock_intraday_sina"
    override val description = "新浪财经日内分时数据。symbol需带前缀(如sz000001), date格式YYYYMMDD。返回: ticktime, price, volume, prev_price, kind"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "股票代码(带前缀, 如sz000001或sh600000)", true),
        ToolParameter("date", "string", "日期 YYYYMMDD", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_intraday")
            mod.callAttr("stock_intraday_sina",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["date"]?.toString() ?: return@withContext "Error: date required"
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockIntradayTools", "Error in stock_intraday_sina", e)
            "Error: ${e.message}"
        }
    }
}

val stock_intraday_tools = listOf(
    StockIntradayEmTool,
    StockIntradaySinaTool
)
