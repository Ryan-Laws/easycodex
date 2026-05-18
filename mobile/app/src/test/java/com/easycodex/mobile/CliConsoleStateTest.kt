package com.easycodex.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class CliConsoleStateTest {
    @Test
    fun activeWindowFallsBackWhenWindowListIsEmpty() {
        val state = CliConsoleState(activeWindowId = "cli_missing", windows = emptyList())

        assertEquals("cli_missing", state.activeWindow.id)
        assertEquals("CLI 1", state.activeWindow.title)
    }
}
