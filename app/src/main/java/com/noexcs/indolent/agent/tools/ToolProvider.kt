package com.noexcs.indolent.agent.tools

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.noexcs.indolent.agent.MemoryProvider
import com.noexcs.indolent.agent.termux.TermuxExecutor
import com.noexcs.indolent.agent.tools.common.CalendarTool
import com.noexcs.indolent.agent.tools.common.ClipboardTool
import com.noexcs.indolent.agent.tools.common.GetCurrentTimeTool
import com.noexcs.indolent.agent.tools.common.IntentTool
import com.noexcs.indolent.agent.tools.common.SubagentTool
import com.noexcs.indolent.agent.tools.common.UpdateMemoryTool
import com.noexcs.indolent.agent.tools.conditional.CreateConditionalTriggerTool
import com.noexcs.indolent.agent.tools.conditional.DeleteConditionalTriggerTool
import com.noexcs.indolent.agent.tools.conditional.EditConditionalTriggerTool
import com.noexcs.indolent.agent.tools.conditional.ListConditionalTriggerHistoryTool
import com.noexcs.indolent.agent.tools.conditional.ListConditionalTriggersTool
import com.noexcs.indolent.agent.tools.filesystem.DeleteFileTool
import com.noexcs.indolent.agent.tools.filesystem.GetStorageInfoTool
import com.noexcs.indolent.agent.tools.filesystem.ListFilesTool
import com.noexcs.indolent.agent.tools.filesystem.ReadFileTool
import com.noexcs.indolent.agent.tools.filesystem.WriteFileTool
import com.noexcs.indolent.agent.tools.finance.*
import com.noexcs.indolent.agent.tools.interact.AskUserTool
import com.noexcs.indolent.agent.tools.notification.CreateNotificationTool
import com.noexcs.indolent.agent.tools.notification.DismissNotificationTool
import com.noexcs.indolent.agent.tools.notification.ListActiveNotificationsTool
import com.noexcs.indolent.agent.tools.notification.ManageNotificationChannelTool
import com.noexcs.indolent.agent.tools.notification.OpenNotificationAccessSettingsTool
import com.noexcs.indolent.agent.tools.notification.QueryNotificationTool
import com.noexcs.indolent.agent.tools.notification.UpdateNotificationTool
import com.noexcs.indolent.agent.tools.scheduledTask.CreateScheduledTaskTool
import com.noexcs.indolent.agent.tools.scheduledTask.DeleteScheduledTaskTool
import com.noexcs.indolent.agent.tools.scheduledTask.EditScheduledTaskTool
import com.noexcs.indolent.agent.tools.scheduledTask.ListScheduledTasksTool
import com.noexcs.indolent.agent.tools.self.LogQueryTool
import com.noexcs.indolent.agent.tools.sensor.GetSensorDataTool
import com.noexcs.indolent.agent.tools.setting.AudioControlTool
import com.noexcs.indolent.agent.tools.setting.SystemSettingTool
import com.noexcs.indolent.agent.tools.systeminfo.BatteryInfoTool
import com.noexcs.indolent.agent.tools.systeminfo.CurrentScreenInfoTool
import com.noexcs.indolent.agent.tools.systeminfo.GetAppInfoTool
import com.noexcs.indolent.agent.tools.systeminfo.NetworkStatusTool
import com.noexcs.indolent.agent.tools.termux.TermuxDialogTool
import com.noexcs.indolent.agent.tools.termux.TermuxExecuteCommandTool
import com.noexcs.indolent.agent.tools.termux.TermuxReadFileTool
import com.noexcs.indolent.agent.tools.termux.TermuxWriteFileTool
import com.noexcs.indolent.data.SettingsManager

/**
 * Shared tool factory. All agent entry points use this to build the tool list,
 * ensuring consistency between foreground (ViewModel) and background (Workers).
 */
object ToolProvider {

    fun build(
        context: Context,
        settings: SettingsManager,
        memoryProvider: MemoryProvider,
        executor: TermuxExecutor? = null,
    ): List<AgentTool> {
        val appContext = context.applicationContext
        val hasTermux = ContextCompat.checkSelfPermission(
            appContext, "com.termux.permission.RUN_COMMAND"
        ) == PackageManager.PERMISSION_GRANTED

        val useTermuxTools = hasTermux && settings.termuxToolsEnabled && executor != null
        val useFundTools = settings.fundToolsEnabled
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

        if (useFundTools) {
            PythonInit.init(appContext)
        }

        val baseTools = buildList {
            if (useTermuxTools) {
                add(TermuxExecuteCommandTool(executor!!))
                add(TermuxReadFileTool(executor!!))
                add(TermuxWriteFileTool(executor!!))
                add(TermuxDialogTool(executor!!))
            }
            if (useCommonTools) {
                add(UpdateMemoryTool(memoryProvider))
                add(IntentTool(appContext))
                add(ClipboardTool(appContext))
                add(CalendarTool(appContext))
                add(GetCurrentTimeTool())
            }
            if (useSystemInfoTools) {
                add(GetAppInfoTool(appContext))
                add(BatteryInfoTool(appContext))
                add(NetworkStatusTool(appContext))
                add(CurrentScreenInfoTool(appContext))
            }
            if (useNotificationTools) {
                add(CreateNotificationTool(appContext))
                add(UpdateNotificationTool(appContext))
                add(DismissNotificationTool(appContext))
                add(QueryNotificationTool(appContext))
                add(ManageNotificationChannelTool(appContext))
                add(ListActiveNotificationsTool(appContext))
                add(OpenNotificationAccessSettingsTool(appContext))
            }
            if (useSensorTools) {
                add(GetSensorDataTool(appContext))
            }
            if (useSettingTools) {
                add(SystemSettingTool(appContext))
                add(AudioControlTool(appContext))
            }
            if (useInteractTools) {
                add(AskUserTool(appContext))
            }
            if (useSelfTools) {
                add(LogQueryTool())
            }
            if (useFilesystemTools) {
                add(ReadFileTool(appContext))
                add(WriteFileTool(appContext))
                add(ListFilesTool(appContext))
                add(DeleteFileTool(appContext))
                add(GetStorageInfoTool(appContext))
            }
            if (useScheduledTaskTools) {
                add(CreateScheduledTaskTool(appContext))
                add(ListScheduledTasksTool(appContext))
                add(EditScheduledTaskTool(appContext))
                add(DeleteScheduledTaskTool(appContext))
            }
            if (useConditionalTools) {
                add(CreateConditionalTriggerTool(appContext))
                add(ListConditionalTriggersTool(appContext))
                add(EditConditionalTriggerTool(appContext))
                add(DeleteConditionalTriggerTool(appContext))
                add(ListConditionalTriggerHistoryTool(appContext))
            }
            if (useFundTools) {
                addFundTools(this)
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

    private fun addFundTools(list: MutableList<AgentTool>) {
        with(list) {
            // Fund EM tools
            add(FundInfoIndexEmTool)
            add(FundOpenFundInfoEmTool)
            add(FundETFFundInfoEmTool)
            add(FundValueEstimationEmTool)
            add(FundValueEstimationEmRankTool)
            // Fund manager
            add(FundManagerEmTool)
            // Fund overview
            add(FundOverviewEmTool)
            // Fund portfolio
            add(FundPortfolioHoldEmTool)
            add(FundPortfolioBondHoldEmTool)
            add(FundPortfolioIndustryAllocationEmTool)
            add(FundPortfolioChangeEmTool)
            // Fund rank
            add(FundOpenFundRankEmTool)
            // Fund XQ
            add(FundIndividualBasicInfoXqTool)
            add(FundIndividualAchievementXqTool)
            add(FundIndividualAnalysisXqTool)
            add(FundIndividualProfitProbabilityXqTool)
            add(FundIndividualDetailInfoXqTool)
            add(FundIndividualDetailHoldXqTool)
            // Stock info
            add(StockIndividualInfoEmTool)
            add(StockIndividualSpotXqTool)
            // Stock history
            add(StockZhAHistTool)
            // Stock fund flow
            add(StockIndividualFundFlowTool)
            add(StockMarketFundFlowTool)
            add(StockSectorFundFlowRankTool)
            // Stock board
            add(StockBoardIndustrySpotEmTool)
            add(StockBoardConceptSpotEmTool)
            add(StockBoardIndustryNameEmTool)
            add(StockBoardConceptNameEmTool)
            // Stock concept detail
            add(StockBoardConceptHistEmTool)
            add(StockBoardConceptConsEmTool)
            // Stock fund flow (extended)
            add(StockSectorFundFlowSummaryTool)
            add(StockSectorFundFlowHistTool)
            add(StockConceptFundFlowHistTool)
            // Stock industry (Sina)
            add(StockSectorSpotTool)
            add(StockSectorDetailTool)
            // Stock intraday
            add(StockIntradayEmTool)
            add(StockIntradaySinaTool)
            // Stock technical indicators
            add(TrendIndicatorTool)
            add(OscillatorIndicatorTool)
            add(VolumeIndicatorTool)
            add(MomentumIndicatorTool)
            add(DirectionalIndicatorTool)
            add(EnergyIndicatorTool)
            // Fund analysis (portfolio decision support)
            add(FundPerformanceTool)
            add(FundVsBenchmarkTool)
            // Portfolio management
            add(PortfolioAddTool)
            add(PortfolioUpdateTool)
            add(PortfolioRemoveTool)
            add(PortfolioListTool)
            add(PortfolioSummaryTool)
            add(PortfolioAnalyzeAllTool)
        }
    }
}
