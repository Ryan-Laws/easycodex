package com.easycodex.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageInteractionStateTest {
    @Test
    fun busyAgentFollowUpStaysOnCurrentAgent() {
        val working = Agent(
            id = "agent_1",
            name = "Task",
            model = "gpt-5.5",
            cwd = ".",
            status = "working",
        )

        assertTrue(shouldQueueFollowUpOnCurrentAgent(working))
    }

    @Test
    fun idleAgentFollowUpDoesNotNeedQueueMarker() {
        val ready = Agent(
            id = "agent_1",
            name = "Task",
            model = "gpt-5.5",
            cwd = ".",
            status = "ready",
        )

        assertFalse(shouldQueueFollowUpOnCurrentAgent(ready))
    }

    @Test
    fun formatsStructuredUserInputQuestions() {
        assertEquals(
            "目标：要优先修复哪个链路？\n验证：是否跑完整测试？",
            formatUserInputQuestionText(
                listOf(
                    "目标" to "要优先修复哪个链路？",
                    "验证" to "是否跑完整测试？",
                ),
            ),
        )
    }

    @Test
    fun ignoresBlankUserInputQuestionParts() {
        assertEquals("请输入你的回答。", formatUserInputQuestionText(listOf("" to "请输入你的回答。")))
    }
}
