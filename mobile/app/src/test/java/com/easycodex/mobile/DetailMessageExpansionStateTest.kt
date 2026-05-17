package com.easycodex.mobile

import org.junit.Assert.assertFalse
import org.junit.Test

class DetailMessageExpansionStateTest {
    @Test
    fun completedDetailGroupDefaultsCollapsed() {
        val messages = listOf(
            AgentMessage("agent", "command", "gradle test", 1L, itemId = "cmd_1", streaming = false),
            AgentMessage("agent", "command_output", "status: completed", 2L, itemId = "out_1", streaming = false),
        )

        assertFalse(detailGroupDefaultExpanded(messages))
    }

    @Test
    fun runningDetailGroupDefaultsCollapsed() {
        val messages = listOf(
            AgentMessage("agent", "command", "gradle test", 1L, itemId = "cmd_1", streaming = true),
            AgentMessage("agent", "command_output", "status: running", 2L, itemId = "out_1", streaming = false),
        )

        assertFalse(detailGroupDefaultExpanded(messages))
    }

    @Test
    fun completedSingleDetailDefaultsCollapsed() {
        val message = AgentMessage("agent", "file_change", "status: completed", 1L, itemId = "diff_1", streaming = false)

        assertFalse(detailMessageDefaultExpanded(message))
    }

    @Test
    fun runningSingleDetailDefaultsCollapsed() {
        val message = AgentMessage("agent", "command", "gradle test", 1L, itemId = "cmd_1", streaming = true)

        assertFalse(detailMessageDefaultExpanded(message))
    }

    @Test
    fun subAgentSingleDetailDefaultsCollapsed() {
        val message = AgentMessage("agent", "sub_agent", "子代理正在工作", 1L, itemId = "sub_1", streaming = true)

        assertFalse(detailMessageDefaultExpanded(message))
    }
}
