package com.easycodex.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectionConfigTest {
    @Test
    fun parsesDeepLinkConnectionConfig() {
        val config = parseEasyCodexConnectionUri(
            "easycodex://connect?relayUrl=ws%3A%2F%2F10.0.2.2%3A3001&apiKey=secret-key",
        )

        assertEquals("ws://10.0.2.2:3001", config?.relayUrl)
        assertEquals("secret-key", config?.apiKey)
    }

    @Test
    fun infersRelayUrlFromHttpsConnectUrl() {
        val config = parseEasyCodexConnectionUri("https://relay.example.com/connect?k=abc")

        assertEquals("wss://relay.example.com", config?.relayUrl)
        assertEquals("abc", config?.apiKey)
    }

    @Test
    fun rejectsNonWebSocketRelayUrl() {
        val config = parseEasyCodexConnectionUri("easycodex://connect?relayUrl=https%3A%2F%2Fexample.com&apiKey=abc")

        assertNull(config)
    }

    @Test
    fun rejectsPublicCleartextRelayEndpointFromImportedConnection() {
        val config = parseEasyCodexConnectionUri("easycodex://connect?relayUrl=ws%3A%2F%2Fexample.com%3A3001&apiKey=abc")

        assertNull(config)
    }

    @Test
    fun allowsPrivateCleartextRelayEndpointFromImportedConnection() {
        val config = parseEasyCodexConnectionUri("easycodex://connect?relayUrl=ws%3A%2F%2F192.168.1.20%3A3001&apiKey=abc")

        assertEquals("ws://192.168.1.20:3001", config?.relayUrl)
        assertEquals("abc", config?.apiKey)
    }

    @Test
    fun allowsSecurePublicRelayEndpointFromImportedConnection() {
        val config = parseEasyCodexConnectionUri("easycodex://connect?relayUrl=wss%3A%2F%2Frelay.example.com&apiKey=abc")

        assertEquals("wss://relay.example.com", config?.relayUrl)
        assertEquals("abc", config?.apiKey)
    }

    @Test
    fun rejectsSecureRelayEndpointWithoutHost() {
        val error = validateRelayEndpoint("wss:///connect", "中继地址格式不正确")

        assertEquals("中继地址格式不正确", error)
    }

    @Test
    fun rejectsPublicCleartextRelayEndpoint() {
        val error = validateRelayEndpoint("ws://example.com:3001", "中继地址格式不正确")

        assertEquals("出于安全考虑，ws:// 只允许连接 localhost、模拟器或局域网地址；公网地址请使用 wss://。", error)
    }

    @Test
    fun allowsPrivateCleartextRelayEndpoint() {
        assertNull(validateRelayEndpoint("ws://192.168.1.20:3001", "中继地址格式不正确"))
    }

    @Test
    fun rejectsDomainThatOnlyLooksLikePrivateIpv4() {
        val error = validateRelayEndpoint("ws://10.attacker.example:3001", "中继地址格式不正确")

        assertEquals("出于安全考虑，ws:// 只允许连接 localhost、模拟器或局域网地址；公网地址请使用 wss://。", error)
    }

    @Test
    fun rejectsInvalidPrivateIpv4Octets() {
        val error = validateRelayEndpoint("ws://192.168.999.10:3001", "中继地址格式不正确")

        assertEquals("中继地址格式不正确", error)
    }

    @Test
    fun relayHostProfilesDeriveIdentityAndUpsert() {
        val first = RelayHostProfile(
            id = relayHostIdFor("ws://192.168.1.20:3001"),
            name = "devbox",
            relayUrl = "ws://192.168.1.20:3001",
            apiKey = "one",
            hostname = "devbox",
            platform = "win32",
            workspaceRoot = "C:\\repo",
            lastSeen = 42L,
            warnings = listOf("keep host awake"),
        )
        val second = profileFromConnectionConfig(EasyCodexConnectionConfig("wss://relay.example.com", "two"))

        assertEquals("ws_192.168.1.20_3001", first.id)
        assertEquals("relay.example.com", second.name)
        assertEquals(listOf(second, first), upsertRelayHostProfile(listOf(first), second))
    }

    @Test
    fun relayHostProfilesSerializeParseAndDropInvalidEntries() {
        val first = RelayHostProfile(
            id = "relay-one",
            name = "devbox",
            relayUrl = "ws://192.168.1.20:3001",
            apiKey = "one",
            hostname = "devbox",
            platform = "win32",
            workspaceRoot = "C:\\repo",
            lastSeen = 42L,
            warnings = listOf("keep host awake"),
        )
        val duplicate = first.copy(name = "duplicate")
        val second = RelayHostProfile(
            id = "relay-two",
            name = "prod",
            relayUrl = "wss://relay.example.com",
            apiKey = "two",
        )

        val serialized = serializeRelayHostProfiles(listOf(first, duplicate, second))
        val parsed = parseRelayHostProfiles(
            serialized.dropLast(1) + ",{\"id\":\"bad\",\"relayUrl\":\"\",\"apiKey\":\"\"}]",
        )

        assertEquals(listOf(first, second), parsed)
    }

    @Test
    fun artifactMessageTypesNormalizeForMobileCards() {
        assertEquals("screenshot", normalizedAgentMessageType("agent", "screenshot", "source: shot.png"))
        assertEquals("test_result", normalizedAgentMessageType("agent", "testResult", "status: failed"))
        assertEquals("plugin_activity", normalizedAgentMessageType("agent", "pluginActivity", "tool: browser"))
    }
}
