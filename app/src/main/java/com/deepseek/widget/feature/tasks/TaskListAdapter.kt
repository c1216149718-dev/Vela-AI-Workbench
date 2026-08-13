package com.deepseek.widget.feature.tasks

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deepseek.widget.databinding.ItemTaskBinding
import com.deepseek.widget.domain.model.Task
import com.deepseek.widget.domain.model.TaskPriority

class TaskListAdapter(
    private val onComplete: (Task) -> Unit,
    private val onRestore: (Task) -> Unit,
    private val onClick: (Task) -> Unit
) : ListAdapter<Task, TaskListAdapter.TaskViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(
        private val binding: ItemTaskBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.taskTitle.text = task.title
            binding.taskCheckbox.isChecked = task.status == com.deepseek.widget.domain.model.TaskStatus.DONE

            // 优先级标记
            val priorityText = when (task.priority) {
                TaskPriority.HIGH -> "高"
                TaskPriority.MEDIUM -> "中"
                TaskPriority.LOW -> "低"
                TaskPriority.NONE -> ""
            }
            if (priorityText.isNotEmpty()) {
                binding.taskPriority.text = priorityText
                binding.taskPriority.visibility = android.view.View.VISIBLE
            } else {
                binding.taskPriority.visibility = android.view.View.GONE
            }

            binding.taskCheckbox.setOnClickListener {
                if (task.status == com.deepseek.widget.domain.model.TaskStatus.DONE) {
                    onRestore(task)
                } else {
                    onComplete(task)
                }
            }
            binding.root.setOnClickListener { onClick(task) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Task>() {
            override fun areItemsTheSame(a: Task, b: Task) = a.id == b.id
            override fun areContentsTheSame(a: Task, b: Task) =
                a.title == b.title && a.status == b.status && a.priority == b.priority &&
                    a.plannedDate == b.plannedDate && a.sortOrder == b.sortOrder
        }
    }
}
