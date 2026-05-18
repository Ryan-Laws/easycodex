package com.easycodex.mobile

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateAssetSelectionTest {
    @Test
    fun trustsOnlyEasyCodexGitHubReleaseAssetUrls() {
        assertTrue(
            isTrustedApkDownloadUrl("https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.6/EasyCodex.Mobile.0.1.6.apk"),
        )
        assertFalse(
            isTrustedApkDownloadUrl("http://github.com/Ryan-Laws/easycodex/releases/download/v0.1.6/EasyCodex.Mobile.0.1.6.apk"),
        )
        assertFalse(
            isTrustedApkDownloadUrl("https://example.com/Ryan-Laws/easycodex/releases/download/v0.1.6/EasyCodex.Mobile.0.1.6.apk"),
        )
        assertFalse(
            isTrustedApkDownloadUrl("https://github.com/other/easycodex/releases/download/v0.1.6/EasyCodex.Mobile.0.1.6.apk"),
        )
    }

    @Test
    fun apkAssetSelectionPrefersExactVersionAndIgnoresUntrustedAssets() {
        val assets = JSONArray(
            """
            [
              {
                "name": "EasyCodex.Mobile.0.1.5.apk",
                "browser_download_url": "https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.5/EasyCodex.Mobile.0.1.5.apk"
              },
              {
                "name": "EasyCodex.Mobile.0.1.6.apk",
                "browser_download_url": "https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.6/EasyCodex.Mobile.0.1.6.apk"
              },
              {
                "name": "EasyCodex.Mobile.0.1.6.apk",
                "browser_download_url": "https://example.com/EasyCodex.Mobile.0.1.6.apk"
              }
            ]
            """.trimIndent(),
        )

        assertEquals(
            "https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.6/EasyCodex.Mobile.0.1.6.apk",
            selectApkAssetUrl(assets, "0.1.6"),
        )
    }

    @Test
    fun apkAssetSelectionFallsBackToFirstTrustedMobileApk() {
        val assets = JSONArray(
            """
            [
              {
                "name": "EasyCodex.Relay.Portable.0.1.6.exe",
                "browser_download_url": "https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.6/EasyCodex.Relay.Portable.0.1.6.exe"
              },
              {
                "name": "EasyCodex.Mobile.0.1.6-beta.1.apk",
                "browser_download_url": "https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.6-beta.1/EasyCodex.Mobile.0.1.6-beta.1.apk"
              }
            ]
            """.trimIndent(),
        )

        assertEquals(
            "https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.6-beta.1/EasyCodex.Mobile.0.1.6-beta.1.apk",
            selectApkAssetUrl(assets, "0.1.6"),
        )
    }

    @Test
    fun apkAssetSelectionRejectsSidecarsRelayAssetsAndUntrustedUrls() {
        val assets = JSONArray(
            """
            [
              {
                "name": "EasyCodex.Mobile.0.1.6.apk.sha256",
                "browser_download_url": "https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.6/EasyCodex.Mobile.0.1.6.apk.sha256"
              },
              {
                "name": "EasyCodex.Relay.Portable.0.1.6.exe",
                "browser_download_url": "https://github.com/Ryan-Laws/easycodex/releases/download/v0.1.6/EasyCodex.Relay.Portable.0.1.6.exe"
              },
              {
                "name": "EasyCodex.Mobile.0.1.6.apk",
                "browser_download_url": "https://downloads.example.com/EasyCodex.Mobile.0.1.6.apk"
              }
            ]
            """.trimIndent(),
        )

        assertEquals("", selectApkAssetUrl(assets, "0.1.6"))
    }
}
