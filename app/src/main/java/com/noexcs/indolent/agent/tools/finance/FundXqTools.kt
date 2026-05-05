package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FundIndividualBasicInfoXqTool : AgentTool {
    override val name = "fund_individual_basic_info_xq"
    override val description = "雪球基金-基金详情。返回: 基金名称, 基金全称, 成立时间, 最新规模, 基金公司, 基金经理, 托管银行, 基金类型, 评级, 投资策略, 投资目标, 业绩比较基准"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_xq")
            mod.callAttr("fund_individual_basic_info_xq",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required"
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundXqTools", "Error in fund_individual_basic_info_xq", e)
                "Error: ${e.message}"
            }
    }
}

object FundIndividualAchievementXqTool : AgentTool {
    override val name = "fund_individual_achievement_xq"
    override val description = "雪球基金-基金业绩。返回: 业绩类型, 周期, 本产品区间收益, 本产品最大回撤, 周期收益同类排名"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_xq")
            mod.callAttr("fund_individual_achievement_xq",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required"
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundXqTools", "Error in fund_individual_achievement_xq", e)
                "Error: ${e.message}"
            }
    }
}

object FundIndividualAnalysisXqTool : AgentTool {
    override val name = "fund_individual_analysis_xq"
    override val description = "雪球基金-数据分析。返回: 较同类风险收益比, 较同类抗风险波动, 年化波动率, 年化夏普比率, 最大回撤"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_xq")
            mod.callAttr("fund_individual_analysis_xq",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required"
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundXqTools", "Error in fund_individual_analysis_xq", e)
                "Error: ${e.message}"
            }
    }
}

object FundIndividualProfitProbabilityXqTool : AgentTool {
    override val name = "fund_individual_profit_probability_xq"
    override val description = "雪球基金-盈利概率。历史任意时点买入，持有满 X 年，盈利概率 Y%"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_xq")
            mod.callAttr("fund_individual_profit_probability_xq",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required"
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundXqTools", "Error in fund_individual_profit_probability_xq", e)
                "Error: ${e.message}"
            }
    }
}

object FundIndividualDetailInfoXqTool : AgentTool {
    override val name = "fund_individual_detail_info_xq"
    override val description = "雪球基金-交易规则。返回: 买入规则, 卖出规则, 管理费, 托管费, 销售服务费"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_xq")
            mod.callAttr("fund_individual_detail_info_xq",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required"
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundXqTools", "Error in fund_individual_detail_info_xq", e)
                "Error: ${e.message}"
            }
    }
}

object FundIndividualDetailHoldXqTool : AgentTool {
    override val name = "fund_individual_detail_hold_xq"
    override val description = "雪球基金-持仓。返回资产类型占比: 股票, 债券, 现金, 其他"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "基金代码", true),
        ToolParameter("date", "string", "日期 YYYYMMDD", false, "20231231")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_xq")
            mod.callAttr("fund_individual_detail_hold_xq",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["date"]?.toString() ?: "20231231"
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundXqTools", "Error in fund_individual_detail_hold_xq", e)
                "Error: ${e.message}"
            }
    }
}

val fund_xq_tools = listOf(
    FundIndividualBasicInfoXqTool,
    FundIndividualAchievementXqTool,
    FundIndividualAnalysisXqTool,
    FundIndividualProfitProbabilityXqTool,
    FundIndividualDetailInfoXqTool,
    FundIndividualDetailHoldXqTool
)
