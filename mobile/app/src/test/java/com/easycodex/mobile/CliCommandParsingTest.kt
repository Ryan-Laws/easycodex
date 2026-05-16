package com.easycodex.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CliCommandParsingTest {
    @Test
    fun keepsQuotedWindowsPathsWithSpaces() {
        val tokens = splitCliInput("/image \"C:\\Users\\liuch\\Desktop\\easy codex\\shot.png\" describe it")

        assertEquals(
            listOf("/image", "C:\\Users\\liuch\\Desktop\\easy codex\\shot.png", "describe", "it"),
            tokens,
        )
    }

    @Test
    fun parsesImageAndAddDirWithSpaces() {
        val draft = parseCliCommand(
            CliConsoleWindow(
                id = "cli_test",
                input = "/json /image \"C:\\Users\\liuch\\Desktop\\easy codex\\shot.png\" /add-dir \"C:\\Users\\liuch\\Desktop\\other repo\" summarize",
            ),
        )

        assertEquals("exec", draft.mode)
        assertTrue(draft.jsonOutput)
        assertEquals(listOf("C:\\Users\\liuch\\Desktop\\easy codex\\shot.png"), draft.images)
        assertEquals(listOf("C:\\Users\\liuch\\Desktop\\other repo"), draft.addDirs)
        assertEquals("summarize", draft.prompt)
    }

    @Test
    fun parsesReviewBaseAndPrompt() {
        val draft = parseCliCommand(CliConsoleWindow(id = "cli_test", input = "/review --base main focus on regressions"))

        assertEquals("review", draft.mode)
        assertEquals("base:main", draft.reviewTarget)
        assertEquals("focus on regressions", draft.prompt)
    }

    @Test
    fun parsesResumeSessionAndPrompt() {
        val draft = parseCliCommand(CliConsoleWindow(id = "cli_test", input = "/resume 123e4567 continue from here"))

        assertEquals("resume", draft.mode)
        assertEquals("123e4567", draft.sessionId)
        assertEquals("continue from here", draft.prompt)
    }
}
