package com.noexcs.indolent.agent.tools

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.noexcs.indolent.agent.MemoryProvider
import com.noexcs.indolent.agent.termux.TermuxExecutor
import com.noexcs.indolent.agent.tools.common.CalendarTool
import com.noexcs.indolent.agent.tools.common.ClipboardTool
import com.noexcs.indolent.agent.tools.common.GetCurrentTimeTool
import com.noexcs.indolent.agent.tools.common.HttpRequestTool
import com.noexcs.indolent.agent.tools.common.IntentTool
import com.noexcs.indolent.agent.tools.common.SubagentTool
import com.noexcs.indolent.agent.tools.common.UpdateMemoryTool
import com.noexcs.indolent.agent.tools.conditional.CreateConditionalTriggerTool
import com.noexcs.indolent.agent.tools.conditional.DeleteConditionalTriggerTool
import com.noexcs.indolent.agent.tools.conditional.EditConditionalTriggerTool
import com.noexcs.indolent.agent.tools.conditional.ListConditionalTriggerHistoryTool
import com.noexcs.indolent.agent.tools.conditional.ListConditionalTriggersTool
import com.noexcs.indolent.agent.tools.filesystem.DeleteFileTool
import com.noexcs.indolent.agent.tools.filesystem.FindFilesTool
import com.noexcs.indolent.agent.tools.filesystem.GetStorageInfoTool
import com.noexcs.indolent.agent.tools.filesystem.ListFilesTool
import com.noexcs.indolent.agent.tools.filesystem.ReadFileTool
import com.noexcs.indolent.agent.tools.filesystem.WriteFileTool
import com.noexcs.indolent.agent.tools.finance.*
import com.noexcs.indolent.agent.tools.interact.AskUserTool
import com.noexcs.indolent.agent.tools.interact.ContentDisplayManager
import com.noexcs.indolent.agent.tools.interact.DisplayContentTool
import com.noexcs.indolent.agent.tools.notification.CreateNotificationTool
import com.noexcs.indolent.agent.tools.notification.DismissNotificationTool
import com.noexcs.indolent.agent.tools.notification.ListActiveNotificationsTool
import com.noexcs.indolent.agent.tools.notification.ManageNotificationChannelTool
import com.noexcs.indolent.agent.tools.notification.OpenNotificationAccessSettingsTool
import com.noexcs.indolent.agent.tools.notification.QueryNotificationTool
import com.noexcs.indolent.agent.tools.notification.UpdateNotificationTool
import com.noexcs.indolent.agent.tools.python.PythonExecuteTool
import com.noexcs.indolent.agent.tools.scheduledTask.CreateScheduledTaskTool
import com.noexcs.indolent.agent.tools.scheduledTask.DeleteScheduledTaskTool
import com.noexcs.indolent.agent.tools.scheduledTask.EditScheduledTaskTool
import com.noexcs.indolent.agent.tools.scheduledTask.ListScheduledTasksTool
import com.noexcs.indolent.agent.tools.self.LogQueryTool
import com.noexcs.indolent.agent.tools.sensor.GetSensorDataTool
import com.noexcs.indolent.agent.tools.setting.AppSettingTool
import com.noexcs.indolent.agent.tools.setting.AudioControlTool
import com.noexcs.indolent.agent.tools.setting.SystemSettingTool
import com.noexcs.indolent.agent.tools.systeminfo.BatteryInfoTool
import com.noexcs.indolent.agent.tools.systeminfo.CpuInfoTool
import com.noexcs.indolent.agent.tools.systeminfo.CurrentScreenInfoTool
import com.noexcs.indolent.agent.tools.systeminfo.GetAppInfoTool
import com.noexcs.indolent.agent.tools.systeminfo.MemoryInfoTool
import com.noexcs.indolent.agent.tools.systeminfo.NetworkStatusTool
import com.noexcs.indolent.agent.tools.systeminfo.ProcessInfoTool
import com.noexcs.indolent.agent.tools.systeminfo.QueryAppActivitiesTool
import com.noexcs.indolent.agent.tools.termux.TermuxExecuteCommandTool
import com.noexcs.indolent.agent.mcp.McpClientManager
import com.noexcs.indolent.data.SettingsManager

/**
 * Shared tool factory. All agent entry points use this to build the tool list,
 * ensuring consistency between foreground (ViewModel) and background (Workers).
 */
object ToolProvider {

    suspend fun build(
        context: Context,
        settings: SettingsManager,
        memoryProvider: MemoryProvider,
        contentDisplayManager: ContentDisplayManager? = null,
    ): List<AgentTool> {
        val appContext = context.applicationContext
        val hasTermux = ContextCompat.checkSelfPermission(
            appContext, "com.termux.permission.RUN_COMMAND"
        ) == PackageManager.PERMISSION_GRANTED
        val executor = if (hasTermux && settings.termuxToolsEnabled) TermuxExecutor(appContext) else null

        val useTermuxTools = hasTermux && settings.termuxToolsEnabled && executor != null
        val useFundTools = settings.fundToolsEnabled
        val usePythonTools = settings.pythonToolsEnabled
        val useCommonTools = settings.commonToolsEnabled
        val useConditionalTools = settings.conditionalToolsEnabled
        val useFilesystemTools = settings.filesystemToolsEnabled
        val useInteractTools = settings.interactToolsEnabled
        val useNotificationTools = settings.notificationToolsEnabled
        val useScheduledTaskTools = settings.scheduledTaskToolsEnabled
        val useSelfTools = settings.selfToolsEnabled
        val useSensorTools = settings.sensorToolsEnabled
        val useSettingTools = settings.settingToolsEnabled
        val useSystemInfoTools = settings.systemInfoToolsEnabled

        if (useFundTools || usePythonTools) {
            PythonInit.init(appContext)
        }

        val isToolEnabled: (String) -> Boolean = { name -> settings.isToolEnabled(name) }

        val baseTools = buildList {
            if (useTermuxTools) {
                if (isToolEnabled("execute_command")) add(TermuxExecuteCommandTool(executor))
            }
            if (usePythonTools) {
                if (isToolEnabled("execute_python")) add(PythonExecuteTool())
            }
            if (useCommonTools) {
                if (isToolEnabled("update_memory")) add(UpdateMemoryTool(memoryProvider))
                if (isToolEnabled("send_intent")) add(IntentTool(appContext))
                if (isToolEnabled("clipboard")) add(ClipboardTool(appContext))
                if (isToolEnabled("calendar")) add(CalendarTool(appContext))
                if (isToolEnabled("get_current_time")) add(GetCurrentTimeTool())
                if (isToolEnabled("http_request")) add(HttpRequestTool())
            }
            if (useSystemInfoTools) {
                if (isToolEnabled("get_app_info")) add(GetAppInfoTool(appContext))
                if (isToolEnabled("get_battery_info")) add(BatteryInfoTool(appContext))
                if (isToolEnabled("get_network_status")) add(NetworkStatusTool(appContext))
                if (isToolEnabled("get_current_screen")) add(CurrentScreenInfoTool(appContext))
                if (isToolEnabled("query_app_activities")) add(QueryAppActivitiesTool(appContext))
                if (isToolEnabled("get_cpu_info")) add(CpuInfoTool(appContext))
                if (isToolEnabled("get_memory_info")) add(MemoryInfoTool(appContext))
                if (isToolEnabled("get_process_info")) add(ProcessInfoTool(appContext))
            }
            if (useNotificationTools) {
                if (isToolEnabled("create_notification")) add(CreateNotificationTool(appContext))
                if (isToolEnabled("update_notification")) add(UpdateNotificationTool(appContext))
                if (isToolEnabled("dismiss_notification")) add(DismissNotificationTool(appContext))
                if (isToolEnabled("query_notification")) add(QueryNotificationTool(appContext))
                if (isToolEnabled("manage_notification_channel")) add(ManageNotificationChannelTool(appContext))
                if (isToolEnabled("list_active_notifications")) add(ListActiveNotificationsTool(appContext))
                if (isToolEnabled("open_notification_access_settings")) add(OpenNotificationAccessSettingsTool(appContext))
            }
            if (useSensorTools) {
                if (isToolEnabled("get_sensor_data")) add(GetSensorDataTool(appContext))
            }
            if (useSettingTools) {
                if (isToolEnabled("system_setting")) add(SystemSettingTool(appContext))
                if (isToolEnabled("audio_control")) add(AudioControlTool(appContext))
                if (isToolEnabled("app_setting")) add(AppSettingTool(settings))
            }
            if (useInteractTools) {
                if (isToolEnabled("ask_user")) add(AskUserTool(appContext))
                if (isToolEnabled("display_content")) add(DisplayContentTool(appContext, contentDisplayManager))
            }
            if (useSelfTools) {
                if (isToolEnabled("query_logs")) add(LogQueryTool())
            }
            if (useFilesystemTools) {
                if (isToolEnabled("fs_read")) add(ReadFileTool(appContext))
                if (isToolEnabled("fs_write")) add(WriteFileTool(appContext))
                if (isToolEnabled("fs_list")) add(ListFilesTool(appContext))
                if (isToolEnabled("fs_delete")) add(DeleteFileTool(appContext))
                if (isToolEnabled("fs_find")) add(FindFilesTool(appContext))
                if (isToolEnabled("fs_storage_info")) add(GetStorageInfoTool(appContext))
            }
            if (useScheduledTaskTools) {
                if (isToolEnabled("create_scheduled_task")) add(CreateScheduledTaskTool(appContext))
                if (isToolEnabled("list_scheduled_tasks")) add(ListScheduledTasksTool(appContext))
                if (isToolEnabled("edit_scheduled_task")) add(EditScheduledTaskTool(appContext))
                if (isToolEnabled("delete_scheduled_task")) add(DeleteScheduledTaskTool(appContext))
            }
            if (useConditionalTools) {
                if (isToolEnabled("create_conditional_trigger")) add(CreateConditionalTriggerTool(appContext))
                if (isToolEnabled("list_conditional_triggers")) add(ListConditionalTriggersTool(appContext))
                if (isToolEnabled("edit_conditional_trigger")) add(EditConditionalTriggerTool(appContext))
                if (isToolEnabled("delete_conditional_trigger")) add(DeleteConditionalTriggerTool(appContext))
                if (isToolEnabled("list_conditional_trigger_history")) add(ListConditionalTriggerHistoryTool(appContext))
            }
            if (useFundTools) {
                addFundTools(this, isToolEnabled)
            }
            if (settings.mcpToolsEnabled) {
                addAll(McpClientManager.getTools(settings))
            }
        }

        return if (useCommonTools && settings.baseUrl != null && settings.apiKey != null && settings.model != null) {
            val subagent = SubagentTool()
            subagent.init(
                settings.baseUrl!!,
                settings.apiKey!!,
                settings.model!!,
                baseTools,
                thinkingEnabled = settings.thinkingEnabled,
                reasoningEffort = settings.reasoningEffort,
            )
            baseTools + subagent
        } else {
            baseTools
        }
    }

    private fun addFundTools(list: MutableList<AgentTool>, isToolEnabled: (String) -> Boolean) {
        with(list) {
            fun addIf(name: String, tool: AgentTool) {
                if (isToolEnabled(name)) add(tool)
            }
            // Fund EM tools
            addIf("fund_info_index_em", FundInfoIndexEmTool)
            addIf("fund_open_fund_info_em", FundOpenFundInfoEmTool)
            addIf("fund_etf_fund_info_em", FundETFFundInfoEmTool)
            addIf("fund_value_estimate_em", FundValueEstimationEmTool)
            addIf("fund_value_estimation_em_rank", FundValueEstimationEmRankTool)
            // Fund manager
            addIf("fund_manager_em", FundManagerEmTool)
            // Fund overview
            addIf("fund_overview_em", FundOverviewEmTool)
            // Fund portfolio
            addIf("fund_portfolio_hold_em", FundPortfolioHoldEmTool)
            addIf("fund_portfolio_bond_hold_em", FundPortfolioBondHoldEmTool)
            addIf("fund_portfolio_industry_allocation_em", FundPortfolioIndustryAllocationEmTool)
            addIf("fund_portfolio_change_em", FundPortfolioChangeEmTool)
            // Fund rank
            addIf("fund_open_fund_rank_em", FundOpenFundRankEmTool)
            // Fund XQ
            addIf("fund_individual_basic_info_xq", FundIndividualBasicInfoXqTool)
            addIf("fund_individual_achievement_xq", FundIndividualAchievementXqTool)
            addIf("fund_individual_analysis_xq", FundIndividualAnalysisXqTool)
            addIf("fund_individual_profit_probability_xq", FundIndividualProfitProbabilityXqTool)
            addIf("fund_individual_detail_info_xq", FundIndividualDetailInfoXqTool)
            addIf("fund_individual_detail_hold_xq", FundIndividualDetailHoldXqTool)
            // Stock info
            addIf("stock_individual_info_em", StockIndividualInfoEmTool)
            addIf("stock_individual_spot_xq", StockIndividualSpotXqTool)
            // Stock history
            addIf("stock_zh_a_hist", StockZhAHistTool)
            // Stock fund flow
            addIf("stock_individual_fund_flow", StockIndividualFundFlowTool)
            addIf("stock_market_fund_flow", StockMarketFundFlowTool)
            addIf("stock_sector_fund_flow_rank", StockSectorFundFlowRankTool)
            // Stock board
            addIf("stock_board_industry_spot_em", StockBoardIndustrySpotEmTool)
            addIf("stock_board_concept_spot_em", StockBoardConceptSpotEmTool)
            addIf("stock_board_industry_name_em", StockBoardIndustryNameEmTool)
            addIf("stock_board_concept_name_em", StockBoardConceptNameEmTool)
            // Stock concept detail
            addIf("stock_board_concept_hist_em", StockBoardConceptHistEmTool)
            addIf("stock_board_concept_cons_em", StockBoardConceptConsEmTool)
            // Stock fund flow (extended)
            addIf("stock_sector_fund_flow_summary", StockSectorFundFlowSummaryTool)
            addIf("stock_sector_fund_flow_hist", StockSectorFundFlowHistTool)
            addIf("stock_concept_fund_flow_hist", StockConceptFundFlowHistTool)
            // Stock industry (Sina)
            addIf("stock_sector_spot", StockSectorSpotTool)
            addIf("stock_sector_detail", StockSectorDetailTool)
            // Stock intraday
            addIf("stock_intraday_em", StockIntradayEmTool)
            addIf("stock_intraday_sina", StockIntradaySinaTool)
            // Stock technical indicators
            addIf("trend_indicator", TrendIndicatorTool)
            addIf("oscillator_indicator", OscillatorIndicatorTool)
            addIf("volume_indicator", VolumeIndicatorTool)
            addIf("momentum_indicator", MomentumIndicatorTool)
            addIf("directional_indicator", DirectionalIndicatorTool)
            addIf("energy_indicator", EnergyIndicatorTool)
            // Fund analysis (portfolio decision support)
            addIf("fund_performance", FundPerformanceTool)
            addIf("fund_vs_benchmark", FundVsBenchmarkTool)
            // Portfolio management
            addIf("portfolio_add", PortfolioAddTool)
            addIf("portfolio_update", PortfolioUpdateTool)
            addIf("portfolio_remove", PortfolioRemoveTool)
            addIf("portfolio_list", PortfolioListTool)
            addIf("portfolio_summary", PortfolioSummaryTool)
            addIf("portfolio_analyze_all", PortfolioAnalyzeAllTool)
        }
    }
}
