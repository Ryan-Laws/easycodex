package com.easycodex.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownTableParsingTest {
    @Test
    fun parsesCodexMarkdownTable() {
        val lines = """
            回看刚才的压力情况，核心结论是：

            | 并发 | 时长 | 总体情况 | p95 | 错误 |
            |---:|---:|---|---:|---:|
            | 50 | 30s | 很稳 | 129-155ms | 0% |
            | 200 | 30s | 仍然稳 | 469-564ms | 0% |
            | 500 | 20s | 没崩，但变慢 | 1161-1287ms | 0% |

            这说明：用户后台单机 dev 环境大概在 200 并发以内体验比较好。
        """.trimIndent().lines()

        val table = markdownTableAt(lines, 2)

        assertNotNull(table)
        assertEquals(listOf("并发", "时长", "总体情况", "p95", "错误"), table!!.first.headers)
        assertEquals(3, table.first.rows.size)
        assertEquals(listOf("500", "20s", "没崩，但变慢", "1161-1287ms", "0%"), table.first.rows.last())
        assertEquals(7, table.second)
    }

    @Test
    fun normalizesRowsToHeaderColumnCount() {
        val lines = listOf(
            "| A | B | C |",
            "|---|---|---|",
            "| 1 | 2 |",
            "| 3 | 4 | 5 | extra |",
        )

        val table = markdownTableAt(lines, 0)

        assertNotNull(table)
        assertEquals(listOf("1", "2", ""), table!!.first.rows.first())
        assertEquals(listOf("3", "4", "5"), table.first.rows.last())
    }

    @Test
    fun rejectsPlainPipeTextWithoutSeparator() {
        val lines = listOf(
            "| this | is | just text |",
            "| not | a | separator |",
        )

        assertNull(markdownTableAt(lines, 0))
    }
}
