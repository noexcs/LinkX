package com.noexcs.indolent.agent.tools

import com.noexcs.indolent.R

enum class ToolGroup {
    TERMUX, FUND, COMMON, CONDITIONAL, FILESYSTEM, INTERACT,
    NOTIFICATION, SCHEDULED_TASK, SELF, SENSOR, SETTING, SYSTEM_INFO, MCP, PYTHON
}

data class ToolInfo(
    val name: String,
    val displayNameResId: Int,
    val group: ToolGroup
)

object ToolRegistry {
    val allTools: List<ToolInfo> = listOf(
        // Termux
        ToolInfo("execute_command", R.string.tool_execute_command, ToolGroup.TERMUX),

        // Common (SubagentTool excluded — gated by API config)
        ToolInfo("update_memory", R.string.tool_update_memory, ToolGroup.COMMON),
        ToolInfo("send_intent", R.string.tool_send_intent, ToolGroup.COMMON),
        ToolInfo("clipboard", R.string.tool_clipboard, ToolGroup.COMMON),
        ToolInfo("calendar", R.string.tool_calendar, ToolGroup.COMMON),
        ToolInfo("get_current_time", R.string.tool_get_current_time, ToolGroup.COMMON),
        ToolInfo("http_request", R.string.tool_http_request, ToolGroup.COMMON),

        // Conditional
        ToolInfo("create_conditional_trigger", R.string.tool_create_conditional_trigger, ToolGroup.CONDITIONAL),
        ToolInfo("list_conditional_triggers", R.string.tool_list_conditional_triggers, ToolGroup.CONDITIONAL),
        ToolInfo("edit_conditional_trigger", R.string.tool_edit_conditional_trigger, ToolGroup.CONDITIONAL),
        ToolInfo("delete_conditional_trigger", R.string.tool_delete_conditional_trigger, ToolGroup.CONDITIONAL),
        ToolInfo("list_conditional_trigger_history", R.string.tool_list_conditional_trigger_history, ToolGroup.CONDITIONAL),

        // Filesystem
        ToolInfo("fs_read", R.string.tool_fs_read, ToolGroup.FILESYSTEM),
        ToolInfo("fs_write", R.string.tool_fs_write, ToolGroup.FILESYSTEM),
        ToolInfo("fs_list", R.string.tool_fs_list, ToolGroup.FILESYSTEM),
        ToolInfo("fs_delete", R.string.tool_fs_delete, ToolGroup.FILESYSTEM),
        ToolInfo("fs_find", R.string.tool_fs_find, ToolGroup.FILESYSTEM),
        ToolInfo("fs_storage_info", R.string.tool_fs_storage_info, ToolGroup.FILESYSTEM),

        // Interact
        ToolInfo("ask_user", R.string.tool_ask_user, ToolGroup.INTERACT),
        ToolInfo("display_content", R.string.tool_display_content, ToolGroup.INTERACT),

        // Notification
        ToolInfo("create_notification", R.string.tool_create_notification, ToolGroup.NOTIFICATION),
        ToolInfo("update_notification", R.string.tool_update_notification, ToolGroup.NOTIFICATION),
        ToolInfo("dismiss_notification", R.string.tool_dismiss_notification, ToolGroup.NOTIFICATION),
        ToolInfo("query_notification", R.string.tool_query_notification, ToolGroup.NOTIFICATION),
        ToolInfo("manage_notification_channel", R.string.tool_manage_notification_channel, ToolGroup.NOTIFICATION),
        ToolInfo("list_active_notifications", R.string.tool_list_active_notifications, ToolGroup.NOTIFICATION),
        ToolInfo("open_notification_access_settings", R.string.tool_open_notification_access_settings, ToolGroup.NOTIFICATION),

        // ScheduledTask
        ToolInfo("create_scheduled_task", R.string.tool_create_scheduled_task, ToolGroup.SCHEDULED_TASK),
        ToolInfo("list_scheduled_tasks", R.string.tool_list_scheduled_tasks, ToolGroup.SCHEDULED_TASK),
        ToolInfo("edit_scheduled_task", R.string.tool_edit_scheduled_task, ToolGroup.SCHEDULED_TASK),
        ToolInfo("delete_scheduled_task", R.string.tool_delete_scheduled_task, ToolGroup.SCHEDULED_TASK),

        // Self
        ToolInfo("query_logs", R.string.tool_query_logs, ToolGroup.SELF),

        // Sensor
        ToolInfo("get_sensor_data", R.string.tool_get_sensor_data, ToolGroup.SENSOR),

        // Setting
        ToolInfo("audio_control", R.string.tool_audio_control, ToolGroup.SETTING),
        ToolInfo("system_setting", R.string.tool_system_setting, ToolGroup.SETTING),
        ToolInfo("app_setting", R.string.tool_app_setting, ToolGroup.SETTING),
        ToolInfo("set_color_theme", R.string.tool_set_color_theme, ToolGroup.SETTING),

        // SystemInfo
        ToolInfo("get_app_info", R.string.tool_get_app_info, ToolGroup.SYSTEM_INFO),
        ToolInfo("get_battery_info", R.string.tool_get_battery_info, ToolGroup.SYSTEM_INFO),
        ToolInfo("get_network_status", R.string.tool_get_network_status, ToolGroup.SYSTEM_INFO),
        ToolInfo("get_current_screen", R.string.tool_get_current_screen, ToolGroup.SYSTEM_INFO),
        ToolInfo("query_app_activities", R.string.tool_query_app_activities, ToolGroup.SYSTEM_INFO),
        ToolInfo("get_cpu_info", R.string.tool_get_cpu_info, ToolGroup.SYSTEM_INFO),
        ToolInfo("get_memory_info", R.string.tool_get_memory_info, ToolGroup.SYSTEM_INFO),
        ToolInfo("get_process_info", R.string.tool_get_process_info, ToolGroup.SYSTEM_INFO),

        // Fund — EM tools
        ToolInfo("fund_info_index_em", R.string.tool_fund_info_index_em, ToolGroup.FUND),
        ToolInfo("fund_open_fund_info_em", R.string.tool_fund_open_fund_info_em, ToolGroup.FUND),
        ToolInfo("fund_etf_fund_info_em", R.string.tool_fund_etf_fund_info_em, ToolGroup.FUND),
        ToolInfo("fund_value_estimate_em", R.string.tool_fund_value_estimate_em, ToolGroup.FUND),
        ToolInfo("fund_value_estimation_em_rank", R.string.tool_fund_value_estimation_em_rank, ToolGroup.FUND),
        // Fund — manager
        ToolInfo("fund_manager_em", R.string.tool_fund_manager_em, ToolGroup.FUND),
        // Fund — overview
        ToolInfo("fund_overview_em", R.string.tool_fund_overview_em, ToolGroup.FUND),
        // Fund — portfolio
        ToolInfo("fund_portfolio_hold_em", R.string.tool_fund_portfolio_hold_em, ToolGroup.FUND),
        ToolInfo("fund_portfolio_bond_hold_em", R.string.tool_fund_portfolio_bond_hold_em, ToolGroup.FUND),
        ToolInfo("fund_portfolio_industry_allocation_em", R.string.tool_fund_portfolio_industry_allocation_em, ToolGroup.FUND),
        ToolInfo("fund_portfolio_change_em", R.string.tool_fund_portfolio_change_em, ToolGroup.FUND),
        // Fund — rank
        ToolInfo("fund_open_fund_rank_em", R.string.tool_fund_open_fund_rank_em, ToolGroup.FUND),
        // Fund — XQ
        ToolInfo("fund_individual_basic_info_xq", R.string.tool_fund_individual_basic_info_xq, ToolGroup.FUND),
        ToolInfo("fund_individual_achievement_xq", R.string.tool_fund_individual_achievement_xq, ToolGroup.FUND),
        ToolInfo("fund_individual_analysis_xq", R.string.tool_fund_individual_analysis_xq, ToolGroup.FUND),
        ToolInfo("fund_individual_profit_probability_xq", R.string.tool_fund_individual_profit_probability_xq, ToolGroup.FUND),
        ToolInfo("fund_individual_detail_info_xq", R.string.tool_fund_individual_detail_info_xq, ToolGroup.FUND),
        ToolInfo("fund_individual_detail_hold_xq", R.string.tool_fund_individual_detail_hold_xq, ToolGroup.FUND),
        // Fund — stock info
        ToolInfo("stock_individual_info_em", R.string.tool_stock_individual_info_em, ToolGroup.FUND),
        ToolInfo("stock_individual_spot_xq", R.string.tool_stock_individual_spot_xq, ToolGroup.FUND),
        // Fund — stock history
        ToolInfo("stock_zh_a_hist", R.string.tool_stock_zh_a_hist, ToolGroup.FUND),
        // Fund — stock fund flow
        ToolInfo("stock_individual_fund_flow", R.string.tool_stock_individual_fund_flow, ToolGroup.FUND),
        ToolInfo("stock_market_fund_flow", R.string.tool_stock_market_fund_flow, ToolGroup.FUND),
        ToolInfo("stock_sector_fund_flow_rank", R.string.tool_stock_sector_fund_flow_rank, ToolGroup.FUND),
        ToolInfo("stock_sector_fund_flow_summary", R.string.tool_stock_sector_fund_flow_summary, ToolGroup.FUND),
        ToolInfo("stock_sector_fund_flow_hist", R.string.tool_stock_sector_fund_flow_hist, ToolGroup.FUND),
        ToolInfo("stock_concept_fund_flow_hist", R.string.tool_stock_concept_fund_flow_hist, ToolGroup.FUND),
        // Fund — stock board
        ToolInfo("stock_board_industry_spot_em", R.string.tool_stock_board_industry_spot_em, ToolGroup.FUND),
        ToolInfo("stock_board_concept_spot_em", R.string.tool_stock_board_concept_spot_em, ToolGroup.FUND),
        ToolInfo("stock_board_industry_name_em", R.string.tool_stock_board_industry_name_em, ToolGroup.FUND),
        ToolInfo("stock_board_concept_name_em", R.string.tool_stock_board_concept_name_em, ToolGroup.FUND),
        // Fund — stock concept detail
        ToolInfo("stock_board_concept_hist_em", R.string.tool_stock_board_concept_hist_em, ToolGroup.FUND),
        ToolInfo("stock_board_concept_cons_em", R.string.tool_stock_board_concept_cons_em, ToolGroup.FUND),
        // Fund — stock industry (Sina)
        ToolInfo("stock_sector_spot", R.string.tool_stock_sector_spot, ToolGroup.FUND),
        ToolInfo("stock_sector_detail", R.string.tool_stock_sector_detail, ToolGroup.FUND),
        // Fund — stock intraday
        ToolInfo("stock_intraday_em", R.string.tool_stock_intraday_em, ToolGroup.FUND),
        ToolInfo("stock_intraday_sina", R.string.tool_stock_intraday_sina, ToolGroup.FUND),
        // Fund — technical indicators
        ToolInfo("trend_indicator", R.string.tool_trend_indicator, ToolGroup.FUND),
        ToolInfo("oscillator_indicator", R.string.tool_oscillator_indicator, ToolGroup.FUND),
        ToolInfo("volume_indicator", R.string.tool_volume_indicator, ToolGroup.FUND),
        ToolInfo("momentum_indicator", R.string.tool_momentum_indicator, ToolGroup.FUND),
        ToolInfo("directional_indicator", R.string.tool_directional_indicator, ToolGroup.FUND),
        ToolInfo("energy_indicator", R.string.tool_energy_indicator, ToolGroup.FUND),
        // Fund — analysis
        ToolInfo("fund_performance", R.string.tool_fund_performance, ToolGroup.FUND),
        ToolInfo("fund_vs_benchmark", R.string.tool_fund_vs_benchmark, ToolGroup.FUND),
        // Fund — portfolio management
        ToolInfo("portfolio_add", R.string.tool_portfolio_add, ToolGroup.FUND),
        ToolInfo("portfolio_update", R.string.tool_portfolio_update, ToolGroup.FUND),
        ToolInfo("portfolio_remove", R.string.tool_portfolio_remove, ToolGroup.FUND),
        ToolInfo("portfolio_list", R.string.tool_portfolio_list, ToolGroup.FUND),
        ToolInfo("portfolio_summary", R.string.tool_portfolio_summary, ToolGroup.FUND),
        ToolInfo("portfolio_analyze_all", R.string.tool_portfolio_analyze_all, ToolGroup.FUND),

        // Python
        ToolInfo("execute_python", R.string.tool_execute_python, ToolGroup.PYTHON),
    )

    fun toolsForGroup(group: ToolGroup): List<ToolInfo> = allTools.filter { it.group == group }
}
