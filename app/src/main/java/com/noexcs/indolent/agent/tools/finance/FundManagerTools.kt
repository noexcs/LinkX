package com.noexcs.indolent.agent.tools.finance

import com.chaquo.python.Python
import com.noexcs.indolent.agent.tools.AgentTool
import com.noexcs.indolent.agent.tools.ToolParameter
import com.noexcs.indolent.logging.Lumberjack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FundManagerEmTool : AgentTool {
    override val name = "fund_manager_em"
    override val description = "基金经理信息。返回: 姓名, 所属公司, 现任基金代码, 现任基金, 累计从业时间, 现任基金资产总规模, 现任基金最佳回报"
    override val parameters = listOf(
        ToolParameter("name", "string", "基金经理姓名", true)
    )
    override suspend fun execute(args: Map<String, Any?>): String = withContext(Dispatchers.IO) {
        try {
            PythonInit.ensureStarted()
            val mod = Python.getInstance().getModule("fund_manager")
            mod.callAttr("fund_manager_em",
                args["name"]?.toString() ?: return@withContext "Error: name required"
            ).toString()
        } catch (e: Exception) {
                Lumberjack.e("FundManagerTools", "Error in fund_manager_em", e)
                "Error: ${e.message}"
            }
    }
}

val fund_manager_tools = listOf(FundManagerEmTool)
