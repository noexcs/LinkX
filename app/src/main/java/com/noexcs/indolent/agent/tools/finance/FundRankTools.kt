package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FundOpenFundRankEmTool : AgentTool {
    override val name = "fund_open_fund_rank_em"
    override val description = "开放式基金排行榜。symbol: 全部/股票型/混合型/债券型/指数型/QDII/FOF。返回: 基金代码, 简称, 单位净值, 日增长率-成立来等"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金类型", false, "全部"),
        ToolParameter("sort_by", "string", "排序字段: 日增长率/近1周/近1月/近3月/近6月/近1年/近2年/近3年/今年来/成立来", false, "近1月"),
        ToolParameter("sort_ascending", "boolean", "是否升序", false, "false"),
        ToolParameter("limit", "integer", "返回数量", false, "20")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_rank_em")
            fun bool(s: String?) = s?.lowercase() == "true"
            mod.callAttr("fund_open_fund_rank_em",
                args["symbol"]?.toString() ?: "全部",
                args["sort_by"]?.toString() ?: "近1月",
                bool(args["sort_ascending"]?.toString()),
                (args["limit"]?.toString())?.toIntOrNull() ?: 20
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundRankTools", "Error in fund_open_fund_rank_em", e)
                "Error: ${e.message}"
            }
    }
}

val fund_rank_em_tools = listOf(FundOpenFundRankEmTool)
