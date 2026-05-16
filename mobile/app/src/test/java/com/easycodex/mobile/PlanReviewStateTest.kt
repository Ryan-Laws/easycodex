package com.easycodex.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanReviewStateTest {
    @Test
    fun streamingPlanIsNotActionable() {
        val message = AgentMessage("agent", "plan", completeProposedPlan(), 1L, itemId = "plan_1", streaming = true)

        assertFalse(isActionablePlanMessage(message))
    }

    @Test
    fun emptyAndOmittedPlansAreNotComplete() {
        assertFalse(isCompletePlanText(""))
        assertFalse(isCompletePlanText("详细内容已省略。"))
        assertFalse(isCompletePlanText("计划内容为空。"))
    }

    @Test
    fun completeProposedPlanIsComplete() {
        assertTrue(isCompletePlanText(completeProposedPlan()))
    }

    @Test
    fun proposedPlanDisplayTextStripsWrapperTags() {
        val text = planDisplayText(completeProposedPlan())

        assertFalse(text.contains("<proposed_plan>"))
        assertFalse(text.contains("</proposed_plan>"))
        assertTrue(text.contains("**Summary**"))
    }

    @Test
    fun agentMessageWithCompleteProposedPlanIsActionablePlan() {
        val message = AgentMessage("agent", "agent", completeProposedPlan(), 1L, itemId = "message_1")

        assertTrue(isActionablePlanMessage(message))
        assertTrue(normalizedAgentMessageType(message.role, message.type, message.text) == "plan")
    }

    @Test
    fun structuredPlanStepsAreComplete() {
        val text = """
            我会按下面步骤处理。

            - [ ] 定位移动端计划弹窗状态
            - [ ] 修复稍后之后的执行入口
        """.trimIndent()

        assertTrue(isCompletePlanText(text))
    }

    @Test
    fun planStartRequestAnswersUseDesktopStartWording() {
        val request = AgentUserInputRequest(
            id = "request_1",
            agentId = "agent_1",
            title = "是否开始",
            detail = "是否要开始这个计划？",
            questions = listOf(
                AgentUserInputQuestion(
                    id = "start",
                    header = "开始",
                    question = "是否开始任务？",
                    isOther = false,
                    isSecret = false,
                    options = listOf(
                        AgentUserInputOption("请开始任务", "开始执行计划"),
                        AgentUserInputOption("暂不开始", "稍后再说"),
                    ),
                ),
            ),
            timestamp = 1L,
        )

        assertTrue(planStartAnswersForRequest(request) == mapOf("start" to "请开始任务"))
    }

    @Test
    fun partialProposedPlanIsNotComplete() {
        assertFalse(isCompletePlanText("<proposed_plan>\n还在生成中"))
    }

    private fun completeProposedPlan(): String {
        return """
            <proposed_plan>
            **Summary**
            - 等计划完整后再展示执行入口。
            </proposed_plan>
        """.trimIndent()
    }
}
