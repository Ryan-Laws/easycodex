package com.easycodex.mobile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class AttachmentLimitsTest {
    @Test
    fun acceptsStreamAtLimit() {
        val bytes = ByteArray(8) { it.toByte() }

        assertArrayEquals(bytes, readAttachmentBytesWithinLimit(ByteArrayInputStream(bytes), maxBytes = 8))
    }

    @Test
    fun rejectsStreamAboveLimit() {
        val bytes = ByteArray(9) { it.toByte() }

        assertNull(readAttachmentBytesWithinLimit(ByteArrayInputStream(bytes), maxBytes = 8))
    }

    @Test
    fun detectsKnownSizeAboveLimit() {
        assertTrue(isAttachmentSizeOverLimit(9, maxBytes = 8))
        assertFalse(isAttachmentSizeOverLimit(8, maxBytes = 8))
        assertFalse(isAttachmentSizeOverLimit(null, maxBytes = 8))
    }
}
