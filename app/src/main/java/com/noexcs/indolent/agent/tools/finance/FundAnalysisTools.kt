package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FundPerformanceTool : AgentTool {
    override val name = "fund_performance"
    override val description = "基金综合业绩指标。返回: 各周期收益(近1周/1月/3月/6月/1年/2年/3年/今年以来), 最大回撤, 年化收益, 年化波动率, 夏普比率, 卡玛比率"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码(6位)", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_analysis")
            mod.callAttr("fund_performance",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required"
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("FundAnalysisTools", "Error in fund_performance", e)
            "Error: ${e.message}"
        }
    }
}

object FundVsBenchmarkTool : AgentTool {
    override val name = "fund_vs_benchmark"
    override val description = "基金 vs 基准对比。默认对比沪深300(000300)，也可对比中证500(000905)等。返回: 各周期超额收益, 相关性, 相对强弱(正值=跑赢)"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码(6位)", true),
        ToolParameter("benchmark", "string", "基准指数代码, 默认000300(沪深300), 可选000905(中证500)/000016(上证50)等", false, "000300")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_analysis")
            mod.callAttr("fund_vs_benchmark",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["benchmark"]?.toString() ?: "000300"
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("FundAnalysisTools", "Error in fund_vs_benchmark", e)
            "Error: ${e.message}"
        }
    }
}

val fund_analysis_tools = listOf(
    FundPerformanceTool,
    FundVsBenchmarkTool
)
