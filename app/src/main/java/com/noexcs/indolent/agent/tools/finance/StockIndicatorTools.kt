package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TrendIndicatorTool : AgentTool {
    override val name = "trend_indicator"
    override val description = "趋势指标: MACD(DIF/DEA/MACD柱), DMA(DDD/AMA), TRIX, BOLL(中轨/上轨/下轨), SAR(抛物线), MIKE(WR/MR/SR/WS/MS/SS)。自动获取历史行情并计算"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "6位股票代码", true),
        ToolParameter("start_date", "string", "开始日期 YYYYMMDD", false, "20250101"),
        ToolParameter("end_date", "string", "结束日期 YYYYMMDD", false, "20500101"),
        ToolParameter("limit", "integer", "返回最新几条", false, "5")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_indicator")
            mod.callAttr("trend_indicator",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["start_date"]?.toString() ?: "20250101",
                args["end_date"]?.toString() ?: "20500101",
                (args["limit"]?.toString())?.toIntOrNull() ?: 5
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockIndicatorTools", "Error in trend_indicator", e)
            "Error: ${e.message}"
        }
    }
}

object OscillatorIndicatorTool : AgentTool {
    override val name = "oscillator_indicator"
    override val description = "振荡指标: KDJ(K/D/J), RSI6/12/24, WR6/10/14(威廉), ROC(变动率), BIAS6/12/24(乖离率), CCI(顺势)。自动获取历史行情并计算"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "6位股票代码", true),
        ToolParameter("start_date", "string", "开始日期 YYYYMMDD", false, "20250101"),
        ToolParameter("end_date", "string", "结束日期 YYYYMMDD", false, "20500101"),
        ToolParameter("limit", "integer", "返回最新几条", false, "5")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_indicator")
            mod.callAttr("oscillator_indicator",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["start_date"]?.toString() ?: "20250101",
                args["end_date"]?.toString() ?: "20500101",
                (args["limit"]?.toString())?.toIntOrNull() ?: 5
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockIndicatorTools", "Error in oscillator_indicator", e)
            "Error: ${e.message}"
        }
    }
}

object VolumeIndicatorTool : AgentTool {
    override val name = "volume_indicator"
    override val description = "成交量指标: OBV(能量潮), VR(成交量变异率)。自动获取历史行情并计算"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "6位股票代码", true),
        ToolParameter("start_date", "string", "开始日期 YYYYMMDD", false, "20250101"),
        ToolParameter("end_date", "string", "结束日期 YYYYMMDD", false, "20500101"),
        ToolParameter("limit", "integer", "返回最新几条", false, "5")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_indicator")
            mod.callAttr("volume_indicator",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["start_date"]?.toString() ?: "20250101",
                args["end_date"]?.toString() ?: "20500101",
                (args["limit"]?.toString())?.toIntOrNull() ?: 5
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockIndicatorTools", "Error in volume_indicator", e)
            "Error: ${e.message}"
        }
    }
}

object MomentumIndicatorTool : AgentTool {
    override val name = "momentum_indicator"
    override val description = "动量指标: PSY(心理线), PSYMA, MTM(动量), MTMMA。自动获取历史行情并计算"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "6位股票代码", true),
        ToolParameter("start_date", "string", "开始日期 YYYYMMDD", false, "20250101"),
        ToolParameter("end_date", "string", "结束日期 YYYYMMDD", false, "20500101"),
        ToolParameter("limit", "integer", "返回最新几条", false, "5")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_indicator")
            mod.callAttr("momentum_indicator",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["start_date"]?.toString() ?: "20250101",
                args["end_date"]?.toString() ?: "20500101",
                (args["limit"]?.toString())?.toIntOrNull() ?: 5
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockIndicatorTools", "Error in momentum_indicator", e)
            "Error: ${e.message}"
        }
    }
}

object DirectionalIndicatorTool : AgentTool {
    override val name = "directional_indicator"
    override val description = "方向指标: DMI (PDI/MDI/ADX/ADXR)。自动获取历史行情并计算"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "6位股票代码", true),
        ToolParameter("start_date", "string", "开始日期 YYYYMMDD", false, "20250101"),
        ToolParameter("end_date", "string", "结束日期 YYYYMMDD", false, "20500101"),
        ToolParameter("limit", "integer", "返回最新几条", false, "5")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_indicator")
            mod.callAttr("directional_indicator",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["start_date"]?.toString() ?: "20250101",
                args["end_date"]?.toString() ?: "20500101",
                (args["limit"]?.toString())?.toIntOrNull() ?: 5
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockIndicatorTools", "Error in directional_indicator", e)
            "Error: ${e.message}"
        }
    }
}

object EnergyIndicatorTool : AgentTool {
    override val name = "energy_indicator"
    override val description = "能量指标: CR, ARBR(AR/BR人气意愿)。自动获取历史行情并计算"
    override val parameters = listOf(
        ToolParameter("symbol", "string", "6位股票代码", true),
        ToolParameter("start_date", "string", "开始日期 YYYYMMDD", false, "20250101"),
        ToolParameter("end_date", "string", "结束日期 YYYYMMDD", false, "20500101"),
        ToolParameter("limit", "integer", "返回最新几条", false, "5")
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("stock_indicator")
            mod.callAttr("energy_indicator",
                args["symbol"]?.toString() ?: return@withContext "Error: symbol required",
                args["start_date"]?.toString() ?: "20250101",
                args["end_date"]?.toString() ?: "20500101",
                (args["limit"]?.toString())?.toIntOrNull() ?: 5
            ).toString()
        } catch (e: Exception) {
            Lumberjack.e("StockIndicatorTools", "Error in energy_indicator", e)
            "Error: ${e.message}"
        }
    }
}

val stock_indicator_tools = listOf(
    TrendIndicatorTool,
    OscillatorIndicatorTool,
    VolumeIndicatorTool,
    MomentumIndicatorTool,
    DirectionalIndicatorTool,
    EnergyIndicatorTool
)
