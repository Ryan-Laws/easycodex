package com.easycodex.mobile

import org.commonmark.ext.gfm.strikethrough.Strikethrough
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.task.list.items.TaskListItemMarker
import org.commonmark.node.BlockQuote
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class MarkdownCommonMarkParsingTest {
    @Test
    fun parsesCommonCodexMarkdownBlocksAndInlineExtensions() {
        val document = parseMarkdownForMobile(
            """
            # 标题

            > 引用 **重点**

            1. 第一项
            2. 第二项

            - [x] 已完成
            - [ ] 未完成

            | 并发 | p95 |
            |---:|---:|
            | 200 | 469-564ms |

            [OpenAI](https://openai.com) https://example.com ~~删除~~

            ```kotlin
            println("ok")
            ```
            """.trimIndent(),
        )

        assertTrue(document.anyDescendant(BlockQuote::class))
        assertTrue(document.anyDescendant(OrderedList::class))
        assertTrue(document.anyDescendant(TaskListItemMarker::class))
        assertTrue(document.anyDescendant(TableBlock::class))
        assertTrue(document.anyDescendant(Link::class))
        assertTrue(document.anyDescendant(Strikethrough::class))
        assertTrue(document.anyDescendant(FencedCodeBlock::class))
    }

    private fun Node.anyDescendant(type: KClass<out Node>): Boolean {
        var child = firstChild
        while (child != null) {
            if (type.isInstance(child) || child.anyDescendant(type)) return true
            child = child.next
        }
        return false
    }
}
