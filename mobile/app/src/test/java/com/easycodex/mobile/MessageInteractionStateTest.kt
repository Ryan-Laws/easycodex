package com.easycodex.mobile

import org.json.JSONObject
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

    @Test
    fun spacedEasyCodexNameFallsBackToTaskPreview() {
        assertEquals(
            "有报错程序运行不起来 请你",
            displayTaskNameForMobile("easy codex", "有报错程序运行不起来 请你"),
        )
    }

    @Test
    fun parsesScreenshotArtifactSourceFromMarkdownImage() {
        val artifact = parseStructuredArtifactDisplay(
            "screenshot",
            "![Home screen](C:\\Projects\\easycodex\\.easycodex-attachments\\shot.png)",
        )

        assertEquals("截图", artifact.label)
        assertEquals("Home screen", artifact.title)
        assertEquals("C:\\Projects\\easycodex\\.easycodex-attachments\\shot.png", artifact.source)
    }

    @Test
    fun parsesFailedTestResultArtifact() {
        val artifact = parseStructuredArtifactDisplay(
            "test_result",
            "command: gradle test\nstatus: failed\nsource: build/reports/tests/index.html",
        )

        assertEquals("测试结果", artifact.label)
        assertEquals("gradle test", artifact.title)
        assertEquals("failed", artifact.status)
        assertEquals("build/reports/tests/index.html", artifact.source)
        assertEquals(StructuredArtifactStatus.Failed, artifact.statusKind)
    }

    @Test
    fun parsesPluginActivityAsCollapsedDetailSummary() {
        val artifact = parseStructuredArtifactDisplay(
            "plugin_activity",
            "tool: browser\nstatus: running\nOpened local preview",
        )

        assertEquals("插件/技能", artifact.label)
        assertEquals("browser", artifact.title)
        assertEquals("running", artifact.status)
        assertEquals("Opened local preview", artifact.summary)
        assertEquals(StructuredArtifactStatus.Running, artifact.statusKind)
    }

    @Test
    fun notificationActionsCoverApprovalFromShade() {
        val actions = agentNotificationActionSpecs(AgentAlertKind.Confirmation, canApprove = true)

        assertEquals(
            listOf(
                AgentNotificationActionKind.Approval,
                AgentNotificationActionKind.Approval,
                AgentNotificationActionKind.Dismiss,
            ),
            actions.map { it.kind },
        )
        assertEquals(listOf("批准", "拒绝", "稍后"), actions.map { it.title })
        assertEquals(listOf(true, false), actions.take(2).map { it.approved })
    }

    @Test
    fun notificationConfirmationWithoutRequestIdDoesNotShowApprovalButtons() {
        val actions = agentNotificationActionSpecs(AgentAlertKind.Confirmation)

        assertEquals(listOf(AgentNotificationActionKind.QuickReply, AgentNotificationActionKind.Dismiss), actions.map { it.kind })
        assertEquals(listOf("回复", "稍后"), actions.map { it.title })
    }

    @Test
    fun notificationActionsKeepMobileFollowUpShortcuts() {
        val completed = agentNotificationActionSpecs(AgentAlertKind.Completed)
        val question = agentNotificationActionSpecs(AgentAlertKind.Question)
        val error = agentNotificationActionSpecs(AgentAlertKind.Error)

        assertEquals(listOf("继续", "追问", "稍后"), completed.map { it.title })
        assertEquals("请继续下一步", completed.first().presetText)
        assertEquals(listOf("回答", "稍后"), question.map { it.title })
        assertEquals(listOf("分析错误", "稍后"), error.map { it.title })
        assertEquals("请分析刚才的错误并给出下一步", error.first().presetText)
    }

    @Test
    fun parsesGitStatusRenameObjectsAndRestorableFiles() {
        val status = parseGitStatusSummary(
            JSONObject(
                """
                {
                  "branch": "feature/mobile",
                  "isClean": false,
                  "modified": ["src/app.ts"],
                  "renamed": [{"from": "src/old.ts", "to": "src/new.ts"}],
                  "notAdded": ["scratch.txt"],
                  "restorableFiles": ["src/app.ts", "src/new.ts"]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(listOf("src/app.ts", "src/new.ts", "scratch.txt"), status.files)
        assertEquals(listOf("src/app.ts", "src/new.ts"), status.restorableFiles)
    }

    @Test
    fun parsesHostHealthMetadataAndWarnings() {
        val health = parseHostHealthState(
            JSONObject(
                """
                {
                  "status": "ok",
                  "workspaceRoot": "C:\\repo",
                  "uptimeMs": 1200,
                  "connectedClients": 2,
                  "system": {"hostname": "devbox", "platform": "win32"},
                  "runtime": {"providerMode": "official"},
                  "warnings": [
                    {"message": "电脑可能休眠", "recommendation": "保持主机唤醒"}
                  ]
                }
                """.trimIndent(),
            ),
            checkedAt = 99L,
        )

        assertTrue(health.online)
        assertEquals("devbox", health.hostname)
        assertEquals("win32", health.platform)
        assertEquals("C:\\repo", health.workspaceRoot)
        assertEquals("official", health.runtimeMode)
        assertEquals(listOf("电脑可能休眠 保持主机唤醒"), health.warnings)
        assertEquals(99L, health.checkedAt)
    }

    @Test
    fun artifactOpenSourceDoesNotExposeRelayKeyForDesktopLocalPaths() {
        val source = artifactOpenSource(
            "C:\\repo\\.easycodex-attachments\\shot.png",
        )

        assertEquals("", source)
    }

    @Test
    fun artifactOpenSourceLeavesPlainUrlsUnchanged() {
        assertEquals(
            "https://example.com/report.html",
            artifactOpenSource("https://example.com/report.html"),
        )
    }
}
