package com.easycodex.mobile

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class EasyCodexConnectionConfig(
    val relayUrl: String,
    val apiKey: String,
)

fun parseEasyCodexConnectionUri(raw: String?): EasyCodexConnectionConfig? {
    val uri = runCatching { URI(raw?.trim().orEmpty()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
    val isDeepLink = scheme == "easycodex" && uri.host.equals("connect", ignoreCase = true)
    val isHttpConnect = (scheme == "http" || scheme == "https") &&
        (uri.path.equals("/c", ignoreCase = true) || uri.path.equals("/connect", ignoreCase = true))

    if (!isDeepLink && !isHttpConnect) return null

    val query = parseQueryParameters(uri.rawQuery)
    val relayUrl = firstQueryParameter(query, "relayUrl", "webSocketUrl", "wsUrl", "url")
        ?: inferRelayUrlFromHttpConnectUri(uri, scheme)
        ?: return null
    val apiKey = firstQueryParameter(query, "apiKey", "key", "k") ?: return null
    if (!relayUrl.startsWith("ws://", ignoreCase = true) && !relayUrl.startsWith("wss://", ignoreCase = true)) {
        return null
    }
    if (validateRelayEndpoint(relayUrl, "") != null) return null

    return EasyCodexConnectionConfig(relayUrl = relayUrl, apiKey = apiKey)
}

fun validateRelayEndpoint(value: String, strings: AppStrings): String? {
    return validateRelayEndpoint(value, strings.invalidRelayUrl)
}

fun validateRelayEndpoint(value: String, invalidRelayUrl: String): String? {
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return invalidRelayUrl
    val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
    if (scheme == "wss") return null
    if (scheme != "ws") return "Relay 地址必须使用 ws:// 或 wss://"
    val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
    val privateOrLocal = host == "localhost" ||
        host == "::1" ||
        host == "10.0.2.2" ||
        isPrivateOrLoopbackIpv4(host)
    return if (privateOrLocal) null else "出于安全考虑，ws:// 只允许连接 localhost、模拟器或局域网地址；公网地址请使用 wss://。"
}

private fun isPrivateOrLoopbackIpv4(host: String): Boolean {
    val parts = host.split(".")
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    return octets[0] == 10 ||
        octets[0] == 127 ||
        (octets[0] == 192 && octets[1] == 168) ||
        (octets[0] == 172 && octets[1] in 16..31)
}

private fun inferRelayUrlFromHttpConnectUri(uri: URI, scheme: String): String? {
    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
    val relayScheme = when (scheme) {
        "https" -> "wss"
        "http" -> "ws"
        else -> return null
    }
    val port = if (uri.port > 0) ":${uri.port}" else ""
    return "$relayScheme://$host$port"
}

private fun parseQueryParameters(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    val values = linkedMapOf<String, String>()
    rawQuery.split("&").forEach { part ->
        val separator = part.indexOf("=")
        if (separator < 0) return@forEach
        val key = decodeQueryValue(part.substring(0, separator)).takeIf { it.isNotBlank() } ?: return@forEach
        val value = decodeQueryValue(part.substring(separator + 1)).trim()
        values.putIfAbsent(key, value)
    }
    return values
}

private fun decodeQueryValue(value: String): String {
    return runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
}

private fun firstQueryParameter(query: Map<String, String>, vararg names: String): String? {
    for (name in names) {
        query[name]?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}
