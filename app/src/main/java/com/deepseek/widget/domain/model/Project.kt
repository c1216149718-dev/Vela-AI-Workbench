package com.deepseek.widget.domain.model

/** 项目（任务分组）。默认项目 id=1 名为"收件箱"。 */
data class Project(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
