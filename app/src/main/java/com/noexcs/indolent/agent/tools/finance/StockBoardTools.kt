package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object StockBoardIndustrySpotEmTool : AgentTool {
    override val name = "stock_board_industry_spot_em"
    override val description = "东方财富行业板块行情。返回板块内个股: 代码, 名称, 最新价, 涨跌幅, 成交量, 成交额, 振幅, 换手率, 市盈率等"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "行业板块名称, 如: 小金属/半导体/银行/白酒等", false, "小金属"),
        ToolParameter("limit", "integer", "返回前几只个股", false, "30")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_board")
            mod.callAttr("stock_board_industry_spot_em",
                args["symbol"]?.toString() ?: "小金属",
                (args["limit"]?.toString())?.toIntOrNull() ?: 30
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockBoardTools", "Error in stock_board_industry_spot_em", e)
            "Error: ${e.message}"
        }
    }
}

object StockBoardConceptSpotEmTool : AgentTool {
    override val name = "stock_board_concept_spot_em"
    override val description = "东方财富概念板块行情。返回概念板块内个股: 代码, 名称, 最新价, 涨跌幅, 成交量, 成交额, 振幅, 换手率, 市盈率等"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "概念板块名称, 如: 可燃冰/AI智能体/低空经济/人形机器人等", false, "可燃冰"),
        ToolParameter("limit", "integer", "返回前几只个股", false, "30")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_board")
            mod.callAttr("stock_board_concept_spot_em",
                args["symbol"]?.toString() ?: "可燃冰",
                (args["limit"]?.toString())?.toIntOrNull() ?: 30
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockBoardTools", "Error in stock_board_concept_spot_em", e)
            "Error: ${e.message}"
        }
    }
}

object StockBoardIndustryNameEmTool : AgentTool {
    override val name = "stock_board_industry_name_em"
    override val description = "东方财富行业板块名称列表。返回所有行业板块名称，供 stock_board_industry_spot_em 查询使用"
    override val parameters = emptyList<ToolParameter>()
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_board")
            mod.callAttr("stock_board_industry_name_em").toString()
        } catch (e: Exception) {
            Lumberjack.e("StockBoardTools", "Error in stock_board_industry_name_em", e)
            "Error: ${e.message}"
        }
    }
}

object StockBoardConceptNameEmTool : AgentTool {
    override val name = "stock_board_concept_name_em"
    override val description = "东方财富概念板块名称列表。返回所有概念板块名称，供 stock_board_concept_spot_em 查询使用"
    override val parameters = emptyList<ToolParameter>()
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_board")
            mod.callAttr("stock_board_concept_name_em").toString()
        } catch (e: Exception) {
            Lumberjack.e("StockBoardTools", "Error in stock_board_concept_name_em", e)
            "Error: ${e.message}"
        }
    }
}

val stock_board_tools = listOf(
    StockBoardIndustrySpotEmTool,
    StockBoardConceptSpotEmTool,
    StockBoardIndustryNameEmTool,
    StockBoardConceptNameEmTool
)
