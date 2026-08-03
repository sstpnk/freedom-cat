package io.nekohasekai.sagernet.fmt.wireguard

import android.net.Uri
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.ktx.applyDefaultValues
import io.nekohasekai.sagernet.ktx.wrapIPV6Host
import moe.matsuri.nb4a.SingBoxOptions
import moe.matsuri.nb4a.utils.Util
import moe.matsuri.nb4a.utils.listByLineOrComma

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
        peers = listOf(SingBoxOptions.AwgPeerOptions().apply {
            address = bean.serverAddress
            port = bean.serverPort
            public_key = bean.peerPublicKey
            pre_shared_key = bean.peerPreSharedKey
            allowed_ips = listOf("0.0.0.0/0", "::/0")
        })
    }
}

fun parseWireGuardLink(link: String): AbstractBean {
    val url = Uri.parse(link.replace("awg://", "wg://").replace("amneziawg://", "wg://"))
    val bean = WireGuardBean().apply {
        serverAddress = url.host ?: error("Invalid link")
        serverPort = url.port ?: error("Invalid link")
        privateKey = url.getQueryParameter("private_key") ?: ""
        peerPublicKey = url.getQueryParameter("peer_public_key") ?: ""
        peerPreSharedKey = url.getQueryParameter("pre_shared_key") ?: ""
        reserved = url.getQueryParameter("reserved")?.replace("-", "") ?: ""
        localAddress = url.getQueryParameter("local_address")?.replace("-", "\n") ?: ""
        mtu = url.getQueryParameter("mtu")?.toIntOrNull()
        enableAmnezia = url.getQueryParameter("enable_amnezia") == "true" ||
            url.getQueryParameter("junk_packet_count") != null ||
            url.getQueryParameter("s1") != null
        jc = url.getQueryParameter("junk_packet_count")?.toIntOrNull() ?: 0
        jmin = url.getQueryParameter("junk_packet_min_size")?.toIntOrNull() ?: 0
        jmax = url.getQueryParameter("junk_packet_max_size")?.toIntOrNull() ?: 0
        s1 = url.getQueryParameter("s1")?.toIntOrNull() ?: 0
        s2 = url.getQueryParameter("s2")?.toIntOrNull() ?: 0
        s3 = url.getQueryParameter("s3")?.toIntOrNull() ?: 0
        s4 = url.getQueryParameter("s4")?.toIntOrNull() ?: 0
        h1 = url.getQueryParameter("h1") ?: ""
        h2 = url.getQueryParameter("h2") ?: ""
        h3 = url.getQueryParameter("h3") ?: ""
        h4 = url.getQueryParameter("h4") ?: ""
        i1 = url.getQueryParameter("i1") ?: ""
        i2 = url.getQueryParameter("i2") ?: ""
        i3 = url.getQueryParameter("i3") ?: ""
        i4 = url.getQueryParameter("i4") ?: ""
        i5 = url.getQueryParameter("i5") ?: ""
    }
    bean.applyDefaultValues()
    return bean
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
    }
    return builder.build().toString()
}
