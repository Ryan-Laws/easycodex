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

        assertEquals("出于安全考虑，ws:// 只允许连接 localhost、模拟器或局域网地址；公网地址请使用 wss://。", error)
    }
}
