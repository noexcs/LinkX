package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StockIndividualInfoEmTool : AgentTool {
    override val name = "stock_individual_info_em"
    override val description = "个股基本信息。返回: 最新价, 股票代码, 股票简称, 总股本, 流通股, 总市值, 流通市值, 行业, 上市时间"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "6位股票代码", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_info")
            mod.callAttr("stock_individual_info_em",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required"
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockInfoTools", "Error in stock_individual_info_em", e)
            "Error: ${e.message}"
        }
    }
}

object StockIndividualSpotXqTool : AgentTool {
    override val name = "stock_individual_spot_xq"
    override val description = "雪球个股行情。返回30+字段: 代码, 名称, 现价, 涨跌幅, 成交量, 成交额, 总市值, 流通值, 换手率, 52周最高最低等。自动补全SH/SZ/BJ前缀"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "6位数字代码或SH/SZ+代码", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_info")
            mod.callAttr("stock_individual_spot_xq",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required"
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockInfoTools", "Error in stock_individual_spot_xq", e)
            "Error: ${e.message}"
        }
    }
}

val stock_info_tools = listOf(
    StockIndividualInfoEmTool,
    StockIndividualSpotXqTool
)
