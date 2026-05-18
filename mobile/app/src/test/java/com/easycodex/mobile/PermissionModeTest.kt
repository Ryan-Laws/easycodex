package com.easycodex.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionModeTest {
    @Test
    fun defaultsToDefaultReview() {
        assertEquals(DEFAULT_PERMISSION_MODE, normalizePermissionMode(null))
        assertEquals(DEFAULT_PERMISSION_MODE, normalizePermissionMode(""))
        assertEquals(DEFAULT_PERMISSION_MODE, normalizePermissionMode("unknown"))
    }

    @Test
    fun acceptsOfficialEasyCodexModes() {
        assertEquals(PERMISSION_MODE_DEFAULT_REVIEW, normalizePermissionMode("default-review"))
        assertEquals(PERMISSION_MODE_AUTO_REVIEW, normalizePermissionMode("auto-review"))
        assertEquals(PERMISSION_MODE_FULL_ACCESS, normalizePermissionMode("full-access"))
    }

    @Test
    fun acceptsLegacyFullControlAlias() {
        assertEquals(PERMISSION_MODE_FULL_ACCESS, normalizePermissionMode("full-control"))
        assertEquals(PERMISSION_MODE_FULL_ACCESS, normalizePermissionMode("full_control"))
    }

    @Test
    fun infersLegacyRuntimeFullAccessWhenExplicitModeIsMissing() {
        assertEquals(PERMISSION_MODE_FULL_ACCESS, permissionModeFromRuntimeFields(null, "never", null))
        assertEquals(PERMISSION_MODE_FULL_ACCESS, permissionModeFromRuntimeFields(null, null, "danger-full-access"))
        assertEquals(PERMISSION_MODE_DEFAULT_REVIEW, permissionModeFromRuntimeFields(null, null, null))
    }

    @Test
    fun explicitPermissionModeWinsOverLegacyRuntimeFields() {
        assertEquals(
            PERMISSION_MODE_DEFAULT_REVIEW,
            permissionModeFromRuntimeFields("default-review", "never", "danger-full-access"),
        )
    }
}
