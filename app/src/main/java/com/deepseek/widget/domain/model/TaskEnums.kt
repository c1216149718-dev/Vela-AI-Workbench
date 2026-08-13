package com.deepseek.widget.domain.model

/** 任务状态机。 */
enum class TaskStatus {
    BACKLOG,
    PLANNED,
    IN_PROGRESS,
    DONE,
    CANCELLED;

    companion object {
        fun fromName(name: String): TaskStatus =
            entries.firstOrNull { it.name == name } ?: BACKLOG
    }
}

/** 任务优先级：0=无, 1=低, 2=中, 3=高。 */
enum class TaskPriority(val value: Int) {
    NONE(0),
    LOW(1),
    MEDIUM(2),
    HIGH(3);

    companion object {
        fun fromValue(value: Int): TaskPriority =
            entries.firstOrNull { it.value == value } ?: NONE
    }
}

/** 任务来源类型。 */
enum class TaskSourceType {
    MANUAL,
    HACKER_NEWS,
    GITHUB
}
