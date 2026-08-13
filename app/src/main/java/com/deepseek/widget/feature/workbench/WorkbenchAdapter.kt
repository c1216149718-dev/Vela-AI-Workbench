package com.deepseek.widget.feature.workbench

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deepseek.widget.R
import com.deepseek.widget.data.AccountCache
import com.deepseek.widget.domain.model.DailyReview
import com.deepseek.widget.domain.model.FocusSession
import com.deepseek.widget.domain.model.FocusStatus
import com.deepseek.widget.domain.model.Task
import com.deepseek.widget.domain.model.TaskStatus
import com.google.android.material.button.MaterialButton
import kotlin.math.ceil

sealed class WorkbenchItem {
    data class Header(val dateText: String, val greeting: String) : WorkbenchItem()
    data class QuickAdd(val placeholder: String) : WorkbenchItem()
    data class SectionHeader(val title: String, val count: String? = null) : WorkbenchItem()
    data class TodayTask(val task: Task) : WorkbenchItem()
    data object SeeAll : WorkbenchItem()
    data class Focus(val session: FocusSession?) : WorkbenchItem()
    data class AiResources(val deepSeek: AccountCache, val apiKeyFun: AccountCache) : WorkbenchItem()
    data class Review(
        val review: DailyReview?,
        val completedTasks: Int,
        val focusMinutes: Int
    ) : WorkbenchItem()
    data object Modules : WorkbenchItem()
}

class WorkbenchAdapter(
    private val onQuickAdd: (String) -> Unit,
    private val onTaskToggle: (Task) -> Unit,
    private val onTaskClick: (Task) -> Unit,
    private val onSeeAll: () -> Unit,
    private val onFocusClick: () -> Unit,
    private val onDeepSeekClick: () -> Unit,
    private val onApiKeyFunClick: () -> Unit,
    private val onReviewSave: (String) -> Unit,
    private val onModuleClick: (WorkbenchModule) -> Unit
) : ListAdapter<WorkbenchItem, RecyclerView.ViewHolder>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = when (val item = getItem(position)) {
        is WorkbenchItem.Header -> 1L
        is WorkbenchItem.QuickAdd -> 2L
        is WorkbenchItem.SectionHeader -> 100L + item.title.hashCode()
        is WorkbenchItem.TodayTask -> 1_000L + item.task.id
        WorkbenchItem.SeeAll -> 3L
        is WorkbenchItem.Focus -> 4L
        is WorkbenchItem.AiResources -> 5L
        is WorkbenchItem.Review -> 6L
        WorkbenchItem.Modules -> 7L
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is WorkbenchItem.Header -> TYPE_HEADER
        is WorkbenchItem.QuickAdd -> TYPE_QUICK_ADD
        is WorkbenchItem.SectionHeader -> TYPE_SECTION_HEADER
        is WorkbenchItem.TodayTask -> TYPE_TASK
        WorkbenchItem.SeeAll -> TYPE_SEE_ALL
        is WorkbenchItem.Focus -> TYPE_FOCUS
        is WorkbenchItem.AiResources -> TYPE_AI
        is WorkbenchItem.Review -> TYPE_REVIEW
        WorkbenchItem.Modules -> TYPE_MODULES
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(inflater.inflate(R.layout.item_workbench_header, parent, false))
            TYPE_QUICK_ADD -> QuickAddViewHolder(inflater.inflate(R.layout.item_workbench_quick_add, parent, false))
            TYPE_SECTION_HEADER -> SectionHeaderViewHolder(inflater.inflate(R.layout.item_workbench_section_header, parent, false))
            TYPE_TASK -> TaskViewHolder(inflater.inflate(R.layout.item_workbench_task, parent, false))
            TYPE_SEE_ALL -> SeeAllViewHolder(inflater.inflate(R.layout.item_workbench_section_header, parent, false))
            TYPE_FOCUS -> FocusViewHolder(inflater.inflate(R.layout.item_workbench_focus, parent, false))
            TYPE_AI -> AiResourcesViewHolder(inflater.inflate(R.layout.item_workbench_ai_resources, parent, false))
            TYPE_REVIEW -> ReviewViewHolder(inflater.inflate(R.layout.item_workbench_review, parent, false))
            TYPE_MODULES -> ModulesViewHolder(inflater.inflate(R.layout.item_workbench_modules, parent, false))
            else -> error("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is WorkbenchItem.Header -> (holder as HeaderViewHolder).bind(item)
            is WorkbenchItem.QuickAdd -> (holder as QuickAddViewHolder).bind(item)
            is WorkbenchItem.SectionHeader -> (holder as SectionHeaderViewHolder).bind(item)
            is WorkbenchItem.TodayTask -> (holder as TaskViewHolder).bind(item.task)
            WorkbenchItem.SeeAll -> (holder as SeeAllViewHolder).bind()
            is WorkbenchItem.Focus -> (holder as FocusViewHolder).bind(item.session)
            is WorkbenchItem.AiResources -> (holder as AiResourcesViewHolder).bind(item)
            is WorkbenchItem.Review -> (holder as ReviewViewHolder).bind(item)
            WorkbenchItem.Modules -> (holder as ModulesViewHolder).bind()
        }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateText: TextView = view.findViewById(R.id.wb_date)
        private val greeting: TextView = view.findViewById(R.id.wb_greeting)
        fun bind(item: WorkbenchItem.Header) {
            dateText.text = item.dateText
            greeting.text = item.greeting
        }
    }

    inner class QuickAddViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val input: EditText = view.findViewById(R.id.wb_quick_input)
        private val button: View = view.findViewById(R.id.wb_quick_btn)

        init {
            button.setOnClickListener { submit() }
            input.setOnEditorActionListener { _, _, _ -> submit() }
        }

        private fun submit(): Boolean {
            val text = input.text?.toString().orEmpty().trim()
            if (text.isEmpty()) return false
            onQuickAdd(text)
            input.text?.clear()
            return true
        }

        fun bind(item: WorkbenchItem.QuickAdd) {
            input.hint = item.placeholder
        }
    }

    class SectionHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.wb_section_title)
        private val count: TextView = view.findViewById(R.id.wb_section_count)
        fun bind(item: WorkbenchItem.SectionHeader) {
            title.text = item.title
            count.text = item.count.orEmpty()
        }
    }

    inner class SeeAllViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.wb_section_title)
        private val count: TextView = view.findViewById(R.id.wb_section_count)

        init {
            itemView.setOnClickListener { onSeeAll() }
        }

        fun bind() {
            title.text = itemView.context.getString(R.string.workbench_see_all)
            title.setTextColor(ContextCompat.getColor(itemView.context, R.color.deepseek_blue))
            count.text = "›"
        }
    }

    inner class TaskViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val checkbox: CheckBox = view.findViewById(R.id.wb_task_checkbox)
        private val title: TextView = view.findViewById(R.id.wb_task_title)

        fun bind(task: Task) {
            val done = task.status == TaskStatus.DONE
            title.text = task.title
            title.paintFlags = if (done) {
                title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            checkbox.setOnCheckedChangeListener(null)
            checkbox.isChecked = done
            checkbox.setOnCheckedChangeListener { _, _ -> onTaskToggle(task) }
            itemView.setOnClickListener { onTaskClick(task) }
        }
    }

    inner class FocusViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val state: TextView = view.findViewById(R.id.wb_focus_state)
        private val detail: TextView = view.findViewById(R.id.wb_focus_detail)
        private val action: TextView = view.findViewById(R.id.wb_focus_action)

        init {
            itemView.setOnClickListener { onFocusClick() }
        }

        fun bind(session: FocusSession?) {
            if (session == null) {
                state.text = itemView.context.getString(R.string.workbench_no_active_focus)
                detail.text = itemView.context.getString(R.string.workbench_focus_ready)
                action.text = itemView.context.getString(R.string.workbench_start_focus)
            } else {
                state.text = itemView.context.getString(
                    if (session.status == FocusStatus.PAUSED) R.string.focus_status_paused
                    else R.string.focus_status_running
                )
                val remaining = ceil(session.remainingMillis(System.currentTimeMillis()) / 60_000.0)
                    .toInt().coerceAtLeast(0)
                detail.text = itemView.context.getString(
                    R.string.workbench_focus_remaining,
                    remaining,
                    session.plannedMinutes
                )
                action.text = itemView.context.getString(R.string.action_view)
            }
        }
    }

    inner class AiResourcesViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val deepSeekBalance: TextView = view.findViewById(R.id.wb_deepseek_balance)
        private val deepSeekStatus: TextView = view.findViewById(R.id.wb_deepseek_status)
        private val apiKeyFunBalance: TextView = view.findViewById(R.id.wb_apikey_balance)
        private val apiKeyFunStatus: TextView = view.findViewById(R.id.wb_apikey_status)
        private val deepSeekRow: View = view.findViewById(R.id.wb_deepseek_row)
        private val apiKeyFunRow: View = view.findViewById(R.id.wb_apikey_row)

        init {
            deepSeekRow.setOnClickListener { onDeepSeekClick() }
            apiKeyFunRow.setOnClickListener { onApiKeyFunClick() }
        }

        fun bind(item: WorkbenchItem.AiResources) {
            bindAccount(item.deepSeek, deepSeekBalance, deepSeekStatus)
            bindAccount(item.apiKeyFun, apiKeyFunBalance, apiKeyFunStatus)
        }

        private fun bindAccount(cache: AccountCache, balance: TextView, status: TextView) {
            val configured = cache.lastUpdated > 0L
            balance.text = if (configured) {
                "${currencySymbol(cache.currency)}${cache.totalBalance.ifBlank { "--" }}"
            } else {
                itemView.context.getString(R.string.placeholder_value)
            }
            status.text = when {
                cache.errorMessage.isNotBlank() -> itemView.context.getString(R.string.status_error)
                !configured -> itemView.context.getString(R.string.not_configured)
                cache.isAvailable -> itemView.context.getString(R.string.status_available)
                else -> itemView.context.getString(R.string.status_unavailable)
            }
        }

        private fun currencySymbol(currency: String): String = when (currency.uppercase()) {
            "CNY", "RMB" -> "¥"
            "EUR" -> "€"
            else -> "$"
        }
    }

    inner class ReviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val summary: TextView = view.findViewById(R.id.wb_review_summary)
        private val note: EditText = view.findViewById(R.id.wb_review_note)
        private val save: MaterialButton = view.findViewById(R.id.wb_review_save)

        init {
            save.setOnClickListener { onReviewSave(note.text?.toString().orEmpty().trim()) }
        }

        fun bind(item: WorkbenchItem.Review) {
            summary.text = itemView.context.getString(
                R.string.workbench_review_summary,
                item.completedTasks,
                item.focusMinutes
            )
            if (!note.hasFocus() && note.text?.toString() != item.review?.note.orEmpty()) {
                note.setText(item.review?.note.orEmpty())
            }
            save.text = itemView.context.getString(
                if (item.review == null) R.string.workbench_review_placeholder else R.string.action_save
            )
        }
    }

    inner class ModulesViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind() {
            val container: LinearLayout = itemView.findViewById(R.id.wb_modules_container)
            container.removeAllViews()
            WorkbenchModuleRegistry.enabledModules.forEachIndexed { index, module ->
                val moduleView = LayoutInflater.from(itemView.context)
                    .inflate(R.layout.item_workbench_module_card, container, false)
                val icon: ImageView = moduleView.findViewById(R.id.module_icon)
                val title: TextView = moduleView.findViewById(R.id.module_title)
                val description: TextView = moduleView.findViewById(R.id.module_desc)
                icon.setImageResource(module.iconRes)
                icon.setColorFilter(ContextCompat.getColor(itemView.context, module.accentColorRes))
                title.setText(module.titleRes)
                description.setText(module.descriptionRes)
                if (index > 0) {
                    (moduleView.layoutParams as LinearLayout.LayoutParams).marginStart =
                        (8 * itemView.resources.displayMetrics.density).toInt()
                }
                moduleView.setOnClickListener { onModuleClick(module) }
                container.addView(moduleView)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WorkbenchItem>() {
            override fun areItemsTheSame(oldItem: WorkbenchItem, newItem: WorkbenchItem): Boolean =
                itemKey(oldItem) == itemKey(newItem)

            override fun areContentsTheSame(oldItem: WorkbenchItem, newItem: WorkbenchItem): Boolean =
                oldItem == newItem

            private fun itemKey(item: WorkbenchItem): String = when (item) {
                is WorkbenchItem.Header -> "header"
                is WorkbenchItem.QuickAdd -> "quick_add"
                is WorkbenchItem.SectionHeader -> "section:${item.title}"
                is WorkbenchItem.TodayTask -> "task:${item.task.id}"
                WorkbenchItem.SeeAll -> "see_all"
                is WorkbenchItem.Focus -> "focus"
                is WorkbenchItem.AiResources -> "ai"
                is WorkbenchItem.Review -> "review"
                WorkbenchItem.Modules -> "modules"
            }
        }

        private const val TYPE_HEADER = 0
        private const val TYPE_QUICK_ADD = 1
        private const val TYPE_SECTION_HEADER = 2
        private const val TYPE_TASK = 3
        private const val TYPE_SEE_ALL = 4
        private const val TYPE_FOCUS = 5
        private const val TYPE_AI = 6
        private const val TYPE_REVIEW = 7
        private const val TYPE_MODULES = 8
    }
}
