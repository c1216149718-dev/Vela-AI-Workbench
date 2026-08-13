package com.deepseek.widget.feature.workbench

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import com.deepseek.widget.R

enum class WorkbenchModuleId { TASKS, FOCUS, REPORTS, INBOX }

data class WorkbenchModule(
    val id: WorkbenchModuleId,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val iconRes: Int,
    @param:ColorRes val accentColorRes: Int,
    @param:IdRes val destinationId: Int,
    val sortOrder: Int
)

object WorkbenchModuleRegistry {

    /** 阶段 2 只启用任务和专注；报告和信息收件箱在阶段 3/4 启用。 */
    val modules: List<WorkbenchModule> = listOf(
        WorkbenchModule(
            id = WorkbenchModuleId.TASKS,
            titleRes = R.string.module_tasks,
            descriptionRes = R.string.module_tasks_desc,
            iconRes = R.drawable.ic_module_todo,
            accentColorRes = R.color.deepseek_blue,
            destinationId = R.id.taskListFragment,
            sortOrder = 0
        ),
        WorkbenchModule(
            id = WorkbenchModuleId.FOCUS,
            titleRes = R.string.module_focus,
            descriptionRes = R.string.module_focus_desc,
            iconRes = R.drawable.ic_module_pomodoro,
            accentColorRes = R.color.accent_green,
            destinationId = R.id.focusFragment,
            sortOrder = 1
        ),
        WorkbenchModule(
            id = WorkbenchModuleId.REPORTS,
            titleRes = R.string.module_reports,
            descriptionRes = R.string.module_reports_desc,
            iconRes = R.drawable.ic_module_plan,
            accentColorRes = R.color.apikey_amber,
            destinationId = R.id.workbenchFragment,
            sortOrder = 2
        ),
        WorkbenchModule(
            id = WorkbenchModuleId.INBOX,
            titleRes = R.string.module_inbox,
            descriptionRes = R.string.module_inbox_desc,
            iconRes = R.drawable.ic_module_news,
            accentColorRes = R.color.module_violet,
            destinationId = R.id.workbenchFragment,
            sortOrder = 3
        )
    )

    /** 阶段 2 启用的模块。 */
    val enabledModules: List<WorkbenchModule> = modules.filter { it.sortOrder < 2 }
}
