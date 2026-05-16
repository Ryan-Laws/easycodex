package com.easycodex.mobile

import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.reflect.KClass

class FileTypeIconTest {
    @Test
    fun mapsCommonCodeFileExtensionsToBadgeLabels() {
        assertEquals("TS", fileTypeBadgeSpec("agent-relay/src/session-orchestrator.ts").label)
        assertEquals("KT", fileTypeBadgeSpec("mobile/app/src/main/java/EasyCodexController.kt").label)
        assertEquals("JSON", fileTypeBadgeSpec("package.json").label)
        assertEquals("MD", fileTypeBadgeSpec("README.md").label)
    }

    @Test
    fun fallsBackToGenericFileBadgeForUnknownOrExtensionlessPaths() {
        assertEquals("FILE", fileTypeBadgeSpec("Dockerfile").label)
        assertEquals("FILE", fileTypeBadgeSpec("notes.customext").label)
    }

    @Test
    fun detectsMarkdownListItemStartingWithFileLink() {
        val item = firstListItem("- [session-orchestrator.ts](agent-relay/src/session-orchestrator.ts) (line 3340): patch_apply_begin")

        assertEquals("session-orchestrator.ts", markdownLeadingFileReference(item)?.path)
    }

    @Test
    fun detectsMarkdownListItemStartingWithBareFilePath() {
        val item = firstListItem("- mobile/app/src/main/java/com/easycodex/mobile/EasyCodexController.kt (line 2813): delta")

        assertEquals("mobile/app/src/main/java/com/easycodex/mobile/EasyCodexController.kt", markdownLeadingFileReference(item)?.path)
    }

    @Test
    fun ignoresNonFileLinksPlainBulletsAndTaskItems() {
        val normalLink = firstListItem("- [OpenAI](https://openai.com) official docs")
        val plainBullet = firstListItem("- 这是一个普通列表项")
        val taskItem = firstListItem("- [x] README.md")

        assertNull(markdownLeadingFileReference(normalLink))
        assertNull(markdownLeadingFileReference(plainBullet))
        assertNull(markdownLeadingFileReference(taskItem))
    }

    private fun firstListItem(markdown: String): ListItem {
        return parseMarkdownForMobile(markdown).firstDescendant(ListItem::class)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Node> Node.firstDescendant(type: KClass<T>): T {
        var child = firstChild
        while (child != null) {
            if (type.isInstance(child)) return child as T
            val nested = runCatching { child.firstDescendant(type) }.getOrNull()
            if (nested != null) return nested
            child = child.next
        }
        error("Expected descendant ${type.simpleName}")
    }
}
