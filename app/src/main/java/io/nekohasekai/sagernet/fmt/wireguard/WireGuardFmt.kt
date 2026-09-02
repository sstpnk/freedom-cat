package io.nekohasekai.sagernet.fmt.wireguard

import android.net.Uri
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.wrapIPV6Host
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.Util
import moe.matsuri.nb4a.utils.listByLineOrComma
import org.ini4j.Ini
import org.ini4j.Profile
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.net.URI
import java.net.URLDecoder
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Inflater

private val AMNEZIA_SCHEMES = setOf("awg", "amneziawg", "amnezia")

private data class WireGuardEndpoint(val address: String, val port: Int)

fun genReserved(anyStr: String): String {
    try {
        val list = anyStr.listByLineOrComma()
        val ba = ByteArray(3)
        if (list.size == 3) {
            list.forEachIndexed { index, s ->
                val i = s
                    .replace("[", "")
                    .replace("]", "")
                    .replace(" ", "")
                    .toIntOrNull() ?: return anyStr
                ba[index] = i.toByte()
            }
            return Util.b64EncodeOneLine(ba)
        } else {
            return anyStr
        }
    } catch (e: Exception) {
        return anyStr
    }
}

fun buildSingBoxOutboundWireguardBean(bean: WireGuardBean): SingBoxOptions.Outbound_WireGuardOptions {
    return SingBoxOptions.Outbound_WireGuardOptions().apply {
        type = "wireguard"
        server = bean.serverAddress
        server_port = bean.serverPort
        local_address = bean.localAddress.listByLineOrComma()
        private_key = bean.privateKey
        peer_public_key = bean.peerPublicKey
        pre_shared_key = bean.peerPreSharedKey
        mtu = bean.mtu
        if (bean.reserved.isNotBlank()) reserved = genReserved(bean.reserved)
    }
}

fun buildSingBoxEndpointAwgBean(bean: WireGuardBean): SingBoxOptions.Endpoint_AwgOptions {
    return SingBoxOptions.Endpoint_AwgOptions().apply {
        type = "awg"
        private_key = bean.privateKey
        address = bean.localAddress.listByLineOrComma()
        mtu = bean.mtu
        jc = bean.jc
        jmin = bean.jmin
        jmax = bean.jmax
        s1 = bean.s1
        s2 = bean.s2
        s3 = bean.s3
        s4 = bean.s4
        h1 = bean.h1
        h2 = bean.h2
        h3 = bean.h3
        h4 = bean.h4
        i1 = bean.i1
        i2 = bean.i2
        i3 = bean.i3
        i4 = bean.i4
        i5 = bean.i5
        header_protection_key = bean.headerProtectionKey
        content_padding_addition = bean.contentPaddingAddition
        rekey_after_time = bean.rekeyAfterTime
        rekey_timeout = bean.rekeyTimeout
        reject_after_time = bean.rejectAfterTime
        keepalive_timeout = bean.keepaliveTimeout
        max_handshake_attempts = bean.maxHandshakeAttempts
        random_trailers = bean.randomTrailers
        disable_cookies = bean.disableCookies
        peers = listOf(SingBoxOptions.AwgPeerOptions().apply {
            address = bean.serverAddress
            port = bean.serverPort
            public_key = bean.peerPublicKey
            pre_shared_key = bean.peerPreSharedKey
            allowed_ips = listOf("0.0.0.0/0", "::/0")
            persistent_keepalive_interval = bean.persistentKeepalive
        })
    }
}

private fun parseEndpoint(endpoint: String?): WireGuardEndpoint? {
    val value = endpoint?.trim()?.substringAfterLast("@") ?: return null
    if (value.isBlank() || !value.contains(":")) return null

    val (address, portString) = if (value.startsWith("[")) {
        val end = value.indexOf(']')
        if (end <= 0 || end + 1 >= value.length || value[end + 1] != ':') return null
        value.substring(1, end) to value.substring(end + 2)
    } else {
        value.substringBeforeLast(":") to value.substringAfterLast(":")
    }
    val port = portString.toIntOrNull() ?: return null
    if (address.isBlank() || port <= 0) return null
    return WireGuardEndpoint(percentDecode(address), port)
}

fun parseWireGuardConfig(conf: String): List<WireGuardBean> {
    val ini = Ini(StringReader(conf))
    val iface = ini["Interface"] ?: error("Missing 'Interface' selection")
    val bean = WireGuardBean().applyDefaultValues()
    val localAddresses = iface.getAll("Address")
    if (localAddresses.isNullOrEmpty()) error("Empty address in 'Interface' selection")
    bean.localAddress = localAddresses.flatMap { it.split(",") }.joinToString("\n") { it.trim() }
    bean.privateKey = iface["PrivateKey"] ?: ""
    bean.mtu = iface["MTU"]?.toIntOrNull()
    bean.enableAmnezia = iface["enable_amnezia"]?.toBooleanStrictOrNull() ?: false
    bean.jc = iface["Jc"]?.toIntOrNull()
    bean.jmin = iface["Jmin"]?.toIntOrNull()
    bean.jmax = iface["Jmax"]?.toIntOrNull()
    bean.s1 = iface["S1"]?.toIntOrNull()
    bean.s2 = iface["S2"]?.toIntOrNull()
    bean.s3 = iface["S3"]?.toIntOrNull()
    bean.s4 = iface["S4"]?.toIntOrNull()
    bean.h1 = iface["H1"]
    bean.h2 = iface["H2"]
    bean.h3 = iface["H3"]
    bean.h4 = iface["H4"]
    bean.i1 = iface["I1"]
    bean.i2 = iface["I2"]
    bean.i3 = iface["I3"]
    bean.i4 = iface["I4"]
    bean.i5 = iface["I5"]
    bean.headerProtectionKey = iface.getParam("HeaderProtectionKey", "header_protection_key")
    bean.contentPaddingAddition = iface.getParam("ContentPaddingAddition", "content_padding_addition")
    bean.rekeyAfterTime = iface.getParam("RekeyAfterTime", "rekey_after_time")
    bean.rekeyTimeout = iface.getParam("RekeyTimeout", "rekey_timeout")
    bean.rejectAfterTime = iface.getParam("RejectAfterTime", "reject_after_time")
    bean.keepaliveTimeout = iface.getParam("KeepaliveTimeout", "keepalive_timeout")
    bean.maxHandshakeAttempts = iface.getParam("MaxHandshakeAttempts", "max_handshake_attempts")
    bean.randomTrailers = iface.getBoolParam("RandomTrailers", "random_trailers")
    bean.disableCookies = iface.getBoolParam("DisableCookies", "disable_cookies")
    bean.applyDefaultValues()
    bean.enableAmnezia = bean.enableAmnezia == true || bean.hasAmneziaOptions()

    val peers = ini.getAll("Peer")
    if (peers.isNullOrEmpty()) error("Missing 'Peer' selections")
    val beans = mutableListOf<WireGuardBean>()
    for (peer in peers) {
        val endpoint = parseEndpoint(peer["Endpoint"]) ?: continue
        val peerBean = bean.clone()
        peerBean.serverAddress = endpoint.address
        peerBean.serverPort = endpoint.port
        peerBean.peerPublicKey = peer["PublicKey"] ?: continue
        peerBean.peerPreSharedKey = peer["PresharedKey"]
        peerBean.persistentKeepalive =
            peer.getParam("PersistentKeepalive", "persistent_keepalive_interval") ?: ""
        beans.add(peerBean.applyDefaultValues())
    }
    if (beans.isEmpty()) error("Empty available peer list")
    return beans
}

fun parseWireGuardLinks(link: String): List<WireGuardBean> {
    val schemeEnd = link.indexOf("://")
    if (schemeEnd <= 0) error("Invalid link")
    val scheme = link.substring(0, schemeEnd).lowercase()
    return if (scheme == "vpn") {
        parseAmneziaVpnLink(link)
    } else {
        listOf(parseWireGuardUriLink(link, scheme))
    }
}

fun parseWireGuardLink(link: String): AbstractBean {
    val bean = parseWireGuardLinks(link).firstOrNull() ?: error("Invalid link")
    bean.applyDefaultValues()
    return bean
}

private fun parseWireGuardUriLink(link: String, scheme: String): WireGuardBean {
    val body = link.substringAfter("://")
    val url = URI("wg://$body")
    val query = parseQuery(url.rawQuery)
    val endpoint = parseEndpoint(url.rawAuthority) ?: error("Invalid link")
    val bean = WireGuardBean().apply {
        serverAddress = endpoint.address
        serverPort = endpoint.port
        name = url.rawFragment?.let(::percentDecode) ?: ""
        privateKey = query.getParam("private_key") ?: ""
        peerPublicKey = query.getParam("peer_public_key", "public_key") ?: ""
        peerPreSharedKey = query.getParam("pre_shared_key", "preshared_key") ?: ""
        reserved = query.getParam("reserved")?.replace("-", "") ?: ""
        localAddress = query.getParam("local_address", "address")?.replace("-", "\n") ?: ""
        mtu = query.getParam("mtu")?.toIntOrNull()
        enableAmnezia =
            scheme in AMNEZIA_SCHEMES || query.getParam("enable_amnezia")?.equals("true", true) == true
        jc = query.getIntParam("jc", "junk_packet_count") ?: 0
        jmin = query.getIntParam("jmin", "junk_packet_min_size") ?: 0
        jmax = query.getIntParam("jmax", "junk_packet_max_size") ?: 0
        s1 = query.getIntParam("s1", "init_packet_junk_size") ?: 0
        s2 = query.getIntParam("s2", "response_packet_junk_size") ?: 0
        s3 = query.getIntParam("s3", "cookie_reply_junk_size") ?: 0
        s4 = query.getIntParam("s4", "transport_packet_junk_size") ?: 0
        h1 = query.getParam("h1", "init_packet_magic_header") ?: ""
        h2 = query.getParam("h2", "response_packet_magic_header") ?: ""
        h3 = query.getParam(
            "h3",
            "cookie_reply_magic_header",
            "underload_packet_magic_header"
        ) ?: ""
        h4 = query.getParam("h4", "transport_packet_magic_header") ?: ""
        i1 = query.getParam("i1") ?: ""
        i2 = query.getParam("i2") ?: ""
        i3 = query.getParam("i3") ?: ""
        i4 = query.getParam("i4") ?: ""
        i5 = query.getParam("i5") ?: ""
        headerProtectionKey = query.getParam("header_protection_key", "headerprotectionkey") ?: ""
        contentPaddingAddition = query.getParam("content_padding_addition", "contentpaddingaddition") ?: ""
        rekeyAfterTime = query.getParam("rekey_after_time", "rekeyaftertime") ?: ""
        rekeyTimeout = query.getParam("rekey_timeout", "rekeytimeout") ?: ""
        rejectAfterTime = query.getParam("reject_after_time", "rejectaftertime") ?: ""
        keepaliveTimeout = query.getParam("keepalive_timeout", "keepalivetimeout") ?: ""
        maxHandshakeAttempts = query.getParam("max_handshake_attempts", "maxhandshakeattempts") ?: ""
        randomTrailers = query.getBoolParam("random_trailers", "randomtrailers")
        disableCookies = query.getBoolParam("disable_cookies", "disablecookies")
        persistentKeepalive = query.getParam(
            "persistent_keepalive",
            "persistent_keepalive_interval",
            "persistentkeepalive"
        ) ?: ""
    }
    bean.applyDefaultValues()
    if (bean.enableAmnezia != true && bean.hasAmneziaOptions()) {
        bean.enableAmnezia = true
    }
    return bean
}

private fun parseAmneziaVpnLink(link: String): List<WireGuardBean> {
    val decoded = decodeAmneziaVpnPayload(link)
    val trimmed = decoded.trim()
    if (trimmed.startsWith("[Interface]", ignoreCase = true)) {
        return parseWireGuardConfig(trimmed).onEach { it.enableAmnezia = true }
    }

    val root = JSONObject(trimmed)
    val displayName = listOf(
        root.optString("displayName"),
        root.optString("description"),
        root.optString("name")
    ).firstOrNull { it.isNotBlank() } ?: ""

    val containers = root.optJSONArray("containers") ?: error("Missing Amnezia containers")
    for (i in containers.length() - 1 downTo 0) {
        val container = containers.optJSONObject(i) ?: continue
        val awg = container.optJSONObject("awg")
            ?: container.optJSONObject("amnezia-awg")
            ?: container.optJSONObject("amneziaAwg")
            ?: continue
        val lastConfig = awg.optJSONObjectOrString("last_config") ?: continue
        val config = lastConfig.optString("config")
        if (config.isBlank()) continue

        val beans = parseWireGuardConfig(config)
        val mtu = lastConfig.opt("mtu")?.toString()?.toIntOrNull()
        val persistentKeepalive = lastConfig.optString("persistent_keep_alive")
        for (bean in beans) {
            if (bean.name.isBlank()) bean.name = displayName
            if (mtu != null && mtu > 0) bean.mtu = mtu
            if (bean.persistentKeepalive.isBlank() && persistentKeepalive.isNotBlank()) {
                bean.persistentKeepalive = persistentKeepalive
            }
            bean.enableAmnezia = true
            bean.applyDefaultValues()
        }
        if (beans.isNotEmpty()) return beans
    }

    error("No AmneziaWG config found")
}

private fun decodeAmneziaVpnPayload(link: String): String {
    val payload = link.substringAfter("://").trim().trimStart('/')
    val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
    val decoded = Base64.getUrlDecoder().decode(padded)
    val uncompressed = qUncompress(decoded) ?: decoded
    return uncompressed.toString(Charsets.UTF_8)
}

private fun qUncompress(input: ByteArray): ByteArray? {
    if (input.size <= 4) return null
    val inflater = Inflater()
    return try {
        inflater.setInput(input, 4, input.size - 4)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            if (count == 0) {
                if (inflater.needsInput() || inflater.needsDictionary()) break
            } else {
                output.write(buffer, 0, count)
            }
        }
        output.toByteArray().takeIf { inflater.finished() && it.isNotEmpty() }
    } catch (_: DataFormatException) {
        null
    } finally {
        inflater.end()
    }
}

private fun parseQuery(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    val result = LinkedHashMap<String, String>()
    rawQuery.split("&").forEach { part ->
        if (part.isBlank()) return@forEach
        val key = part.substringBefore("=")
        val value = if (part.contains("=")) part.substringAfter("=") else ""
        result[percentDecode(key).lowercase()] = percentDecode(value)
    }
    return result
}

private fun percentDecode(value: String): String {
    return URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name())
}

private fun Map<String, String>.getParam(vararg keys: String): String? {
    for (key in keys) {
        val value = this[key.lowercase()]
        if (value != null) return value
    }
    return null
}

private fun Map<String, String>.getIntParam(vararg keys: String): Int? {
    return getParam(*keys)?.toIntOrNull()
}

private fun Map<String, String>.getBoolParam(vararg keys: String): Boolean {
    val value = getParam(*keys)?.trim()?.lowercase() ?: return false
    return value == "true" || value == "1" || value == "on" || value == "yes"
}

private fun JSONObject.optJSONObjectOrString(name: String): JSONObject? {
    val value = opt(name) ?: return null
    return when (value) {
        is JSONObject -> value
        is String -> runCatching { JSONObject(value) }.getOrNull()
        else -> null
    }
}

private fun WireGuardBean.hasAmneziaOptions(): Boolean {
    return (jc ?: 0) > 0 ||
            (jmin ?: 0) > 0 ||
            (jmax ?: 0) > 0 ||
            (s1 ?: 0) > 0 ||
            (s2 ?: 0) > 0 ||
            (s3 ?: 0) > 0 ||
            (s4 ?: 0) > 0 ||
            !h1.isNullOrBlank() ||
            !h2.isNullOrBlank() ||
            !h3.isNullOrBlank() ||
            !h4.isNullOrBlank() ||
            !i1.isNullOrBlank() ||
            !i2.isNullOrBlank() ||
            !i3.isNullOrBlank() ||
            !i4.isNullOrBlank() ||
            !i5.isNullOrBlank() ||
            !headerProtectionKey.isNullOrBlank() ||
            !contentPaddingAddition.isNullOrBlank() ||
            !rekeyAfterTime.isNullOrBlank() ||
            !rekeyTimeout.isNullOrBlank() ||
            !rejectAfterTime.isNullOrBlank() ||
            !keepaliveTimeout.isNullOrBlank() ||
            !maxHandshakeAttempts.isNullOrBlank() ||
            randomTrailers == true ||
            disableCookies == true
}

fun WireGuardBean.toUri(): String {
    val builder = Uri.Builder()
        .scheme(if (enableAmnezia == true) "awg" else "wg")
        .encodedAuthority("${serverAddress.wrapIPV6Host()}:$serverPort")
        .appendQueryParameter("private_key", privateKey)
        .appendQueryParameter("peer_public_key", peerPublicKey)
        .appendQueryParameter("pre_shared_key", peerPreSharedKey)
        .appendQueryParameter("reserved", reserved.replace(",", "-"))
        .appendQueryParameter("local_address", localAddress.replace("\n", "-"))
        .appendQueryParameter("mtu", mtu?.toString() ?: "")
    if (enableAmnezia == true) {
        builder
            .appendQueryParameter("enable_amnezia", "true")
            .appendQueryParameter("junk_packet_count", jc?.toString() ?: "0")
            .appendQueryParameter("junk_packet_min_size", jmin?.toString() ?: "0")
            .appendQueryParameter("junk_packet_max_size", jmax?.toString() ?: "0")
            .appendQueryParameter("s1", s1?.toString() ?: "0")
            .appendQueryParameter("s2", s2?.toString() ?: "0")
            .appendQueryParameter("s3", s3?.toString() ?: "0")
            .appendQueryParameter("s4", s4?.toString() ?: "0")
            .appendQueryParameter("h1", h1 ?: "")
            .appendQueryParameter("h2", h2 ?: "")
            .appendQueryParameter("h3", h3 ?: "")
            .appendQueryParameter("h4", h4 ?: "")
            .appendQueryParameter("i1", i1 ?: "")
            .appendQueryParameter("i2", i2 ?: "")
            .appendQueryParameter("i3", i3 ?: "")
            .appendQueryParameter("i4", i4 ?: "")
            .appendQueryParameter("i5", i5 ?: "")
            .appendQueryParameter("header_protection_key", headerProtectionKey ?: "")
            .appendQueryParameter("content_padding_addition", contentPaddingAddition ?: "")
            .appendQueryParameter("rekey_after_time", rekeyAfterTime ?: "")
            .appendQueryParameter("rekey_timeout", rekeyTimeout ?: "")
            .appendQueryParameter("reject_after_time", rejectAfterTime ?: "")
            .appendQueryParameter("keepalive_timeout", keepaliveTimeout ?: "")
            .appendQueryParameter("max_handshake_attempts", maxHandshakeAttempts ?: "")
            .appendQueryParameter("random_trailers", randomTrailers?.toString() ?: "false")
            .appendQueryParameter("disable_cookies", disableCookies?.toString() ?: "false")
            .appendQueryParameter("persistent_keepalive", persistentKeepalive ?: "")
    }
    return builder.build().toString()
}

private fun Profile.Section.getParam(vararg keys: String): String? {
    for (key in keys) {
        val value = this[key]
        if (!value.isNullOrBlank()) return value
    }
    return null
}

private fun Profile.Section.getBoolParam(vararg keys: String): Boolean {
    val value = getParam(*keys)?.trim()?.lowercase() ?: return false
    return value == "true" || value == "1" || value == "on" || value == "yes"
}
