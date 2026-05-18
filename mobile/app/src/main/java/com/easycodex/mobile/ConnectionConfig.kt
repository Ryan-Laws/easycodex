package com.easycodex.mobile

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class EasyCodexConnectionConfig(
    val relayUrl: String,
    val apiKey: String,
)

const val PREF_RELAY_HOST_PROFILES = "relay_host_profiles"
const val PREF_ACTIVE_RELAY_HOST_ID = "active_relay_host_id"

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

fun relayHostIdFor(relayUrl: String): String {
    val uri = runCatching { URI(relayUrl.trim()) }.getOrNull()
    val host = uri?.host?.lowercase(Locale.ROOT).orEmpty().ifBlank { relayUrl.trim().lowercase(Locale.ROOT) }
    val port = uri?.port?.takeIf { it > 0 }?.toString().orEmpty()
    val scheme = uri?.scheme?.lowercase(Locale.ROOT).orEmpty()
    return listOf(scheme, host, port).filter { it.isNotBlank() }.joinToString("_")
        .replace(Regex("[^a-z0-9_.-]"), "_")
        .ifBlank { "relay_host" }
}

fun relayHostNameFor(relayUrl: String): String {
    val uri = runCatching { URI(relayUrl.trim()) }.getOrNull()
    val host = uri?.host?.takeIf { it.isNotBlank() } ?: return "EasyCodex Relay"
    return listOfNotNull(host, uri.port.takeIf { it > 0 }?.toString()).joinToString(":")
}

fun parseRelayHostProfiles(raw: String?): List<RelayHostProfile> {
    if (raw.isNullOrBlank()) return emptyList()
    val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val relayUrl = json.optString("relayUrl").trim()
            val apiKey = json.optString("apiKey").trim()
            if (relayUrl.isBlank() || apiKey.isBlank()) continue
            add(
                RelayHostProfile(
                    id = json.optString("id").ifBlank { relayHostIdFor(relayUrl) },
                    name = json.optString("name").ifBlank { relayHostNameFor(relayUrl) },
                    relayUrl = relayUrl,
                    apiKey = apiKey,
                    hostname = json.optString("hostname"),
                    platform = json.optString("platform"),
                    workspaceRoot = json.optString("workspaceRoot"),
                    lastSeen = json.optLong("lastSeen", 0L),
                    warnings = json.optJSONArray("warnings")?.let { warnings ->
                        buildList {
                            for (warningIndex in 0 until warnings.length()) {
                                warnings.optString(warningIndex).takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                    } ?: emptyList(),
                ),
            )
        }
    }.distinctBy { it.id }
}

fun serializeRelayHostProfiles(profiles: List<RelayHostProfile>): String {
    val array = JSONArray()
    profiles.distinctBy { it.id }.forEach { profile ->
        array.put(
            JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("relayUrl", profile.relayUrl)
                .put("apiKey", profile.apiKey)
                .put("hostname", profile.hostname)
                .put("platform", profile.platform)
                .put("workspaceRoot", profile.workspaceRoot)
                .put("lastSeen", profile.lastSeen)
                .put("warnings", JSONArray(profile.warnings)),
        )
    }
    return array.toString()
}

fun upsertRelayHostProfile(profiles: List<RelayHostProfile>, profile: RelayHostProfile): List<RelayHostProfile> {
    val next = profiles.filterNot { it.id == profile.id }.toMutableList()
    next.add(0, profile)
    return next
}

fun profileFromConnectionConfig(config: EasyCodexConnectionConfig): RelayHostProfile {
    return RelayHostProfile(
        id = relayHostIdFor(config.relayUrl),
        name = relayHostNameFor(config.relayUrl),
        relayUrl = config.relayUrl,
        apiKey = config.apiKey,
    )
}

fun saveRelayHostProfile(
    prefs: android.content.SharedPreferences,
    profile: RelayHostProfile,
    makeActive: Boolean = true,
) {
    val profiles = parseRelayHostProfiles(prefs.getString(PREF_RELAY_HOST_PROFILES, "[]"))
    val editor = prefs.edit()
        .putString(PREF_RELAY_HOST_PROFILES, serializeRelayHostProfiles(upsertRelayHostProfile(profiles, profile)))
    if (makeActive) {
        editor
            .putString(PREF_ACTIVE_RELAY_HOST_ID, profile.id)
            .putString(PREF_RELAY_URL, profile.relayUrl)
            .putString(PREF_API_KEY, profile.apiKey)
    }
    editor.apply()
}

fun validateRelayEndpoint(value: String, strings: AppStrings): String? {
    return validateRelayEndpoint(value, strings.invalidRelayUrl)
}

fun validateRelayEndpoint(value: String, invalidRelayUrl: String): String? {
    val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return invalidRelayUrl
    val scheme = uri.scheme?.lowercase(Locale.ROOT).orEmpty()
    val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
    if (host.isBlank()) return invalidRelayUrl
    if (scheme == "wss") return null
    if (scheme != "ws") return "Relay 地址必须使用 ws:// 或 wss://"
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
