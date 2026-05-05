package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StockSectorSpotTool : AgentTool {
    override val name = "stock_sector_spot"
    override val description = "新浪行业板块行情。indicator: 新浪行业/启明星行业/概念/地域/行业。返回: 板块名, 公司家数, 平均价格, 涨跌幅, 总成交量, 总成交额, 领涨个股等"
    override val parameters = listOf(
        ToolParameter("indicator", "string", "板块类型: 新浪行业/启明星行业/概念/地域/行业", false, "概念"),
        ToolParameter("sort_by", "string", "排序字段: 涨跌幅/涨跌额/总成交量/总成交额/平均价格", false, "涨跌幅"),
        ToolParameter("sort_ascending", "boolean", "是否升序", false, "false"),
        ToolParameter("limit", "integer", "返回前几名", false, "20")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_industry")
            fun bool(s: String?) = s?.lowercase() == "true"
            mod.callAttr("stock_sector_spot",
                args["indicator"]?.toString() ?: "概念",
                args["sort_by"]?.toString() ?: "涨跌幅",
                bool(args["sort_ascending"]?.toString()),
                (args["limit"]?.toString())?.toIntOrNull() ?: 20
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockIndustryTools", "Error in stock_sector_spot", e)
            "Error: ${e.message}"
        }
    }
}

object StockSectorDetailTool : AgentTool {
    override val name = "stock_sector_detail"
    override val description = "新浪行业板块详情。sector取自stock_sector_spot结果的label列。返回板块内个股: 代码, 名称, 最新价, 涨跌幅, 成交量, 成交额, 市盈率, 市净率, 总市值等"
    override val parameters = listOf(
        ToolParameter("sector", "string", "板块标签(如gn_HITdc), 来自stock_sector_spot结果中的label列", true),
        ToolParameter("sort_by", "string", "排序字段: changepercent/trade/amount/pricechange/volume/per/pb/mktcap/turnoverratio等", false, "changepercent"),
        ToolParameter("sort_ascending", "boolean", "是否升序", false, "false"),
        ToolParameter("limit", "integer", "返回前几只个股", false, "20")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_industry")
            fun bool(s: String?) = s?.lowercase() == "true"
            mod.callAttr("stock_sector_detail",
                args["sector"]?.toString() ?: return@withContext "Error: sector required",
                args["sort_by"]?.toString() ?: "changepercent",
                bool(args["sort_ascending"]?.toString()),
                (args["limit"]?.toString())?.toIntOrNull() ?: 20
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockIndustryTools", "Error in stock_sector_detail", e)
            "Error: ${e.message}"
        }
    }
}

val stock_industry_tools = listOf(
    StockSectorSpotTool,
    StockSectorDetailTool
)
