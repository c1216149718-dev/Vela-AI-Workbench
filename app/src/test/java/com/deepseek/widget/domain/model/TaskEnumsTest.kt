package com.deepseek.widget.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TaskEnumsTest {

    @Test
    fun taskStatusFromNameResolves() {
        assertEquals(TaskStatus.BACKLOG, TaskStatus.fromName("BACKLOG"))
        assertEquals(TaskStatus.IN_PROGRESS, TaskStatus.fromName("IN_PROGRESS"))
        assertEquals(TaskStatus.DONE, TaskStatus.fromName("DONE"))
    }

    @Test
    fun taskStatusFromUnknownNameDefaultsToBacklog() {
        assertEquals(TaskStatus.BACKLOG, TaskStatus.fromName("INVALID"))
    }

    @Test
    fun taskPriorityFromValueResolves() {
        assertEquals(TaskPriority.NONE, TaskPriority.fromValue(0))
        assertEquals(TaskPriority.LOW, TaskPriority.fromValue(1))
        assertEquals(TaskPriority.HIGH, TaskPriority.fromValue(3))
    }

    @Test
    fun taskPriorityFromUnknownValueDefaultsToNone() {
        assertEquals(TaskPriority.NONE, TaskPriority.fromValue(99))
    }

    @Test
    fun focusStatusFromNameResolves() {
        assertEquals(FocusStatus.RUNNING, FocusStatus.fromName("RUNNING"))
        assertEquals(FocusStatus.PAUSED, FocusStatus.fromName("PAUSED"))
        assertEquals(FocusStatus.COMPLETED, FocusStatus.fromName("COMPLETED"))
    }

    @Test
    fun focusStatusFromUnknownNameDefaultsToRunning() {
        assertEquals(FocusStatus.RUNNING, FocusStatus.fromName("NOPE"))
    }
}
