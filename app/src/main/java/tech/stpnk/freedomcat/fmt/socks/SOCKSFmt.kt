package tech.stpnk.freedomcat.fmt.socks

import tech.stpnk.freedomcat.ktx.decodeBase64UrlSafe
import tech.stpnk.freedomcat.ktx.toLink
import tech.stpnk.freedomcat.ktx.unUrlSafe
import tech.stpnk.freedomcat.ktx.urlSafe
import tech.stpnk.freedomcat.core.SingBoxOptions
import tech.stpnk.freedomcat.core.utils.NGUtil
import tech.stpnk.freedomcat.core.utils.Util
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun parseSOCKS(link: String): SOCKSBean {
    val url = ("http://" + link.substringAfter("://")).toHttpUrlOrNull()
        ?: error("Not supported: $link")

    return SOCKSBean().apply {
        protocol = when {
            link.startsWith("socks4://") -> SOCKSBean.PROTOCOL_SOCKS4
            link.startsWith("socks4a://") -> SOCKSBean.PROTOCOL_SOCKS4A
            else -> SOCKSBean.PROTOCOL_SOCKS5
        }
        name = url.fragment
        serverAddress = url.host
        serverPort = url.port
        username = url.username
        password = url.password
        // v2rayN fmt
        if (password.isNullOrBlank() && !username.isNullOrBlank()) {
            try {
                val n = username.decodeBase64UrlSafe()
                username = n.substringBefore(":")
                password = n.substringAfter(":")
            } catch (_: Exception) {
            }
        }
    }
}

fun SOCKSBean.toUri(): String {

    val builder = HttpUrl.Builder().scheme("http").host(serverAddress).port(serverPort)
    if (!username.isNullOrBlank()) builder.username(username)
    if (!password.isNullOrBlank()) builder.password(password)
    if (!name.isNullOrBlank()) builder.encodedFragment(name.urlSafe())
    return builder.toLink("socks${protocolVersion()}")

}

fun SOCKSBean.toV2rayN(): String {

    var link = ""
    if (username.isNotBlank()) {
        link += username.urlSafe() + ":" + password.urlSafe() + "@"
    }
    link += "$serverAddress:$serverPort"
    link = "socks://" + NGUtil.encode(link)
    if (name.isNotBlank()) {
        link += "#" + name.urlSafe()
    }

    return link

}

fun buildSingBoxOutboundSocksBean(bean: SOCKSBean): SingBoxOptions.Outbound_SocksOptions {
    return SingBoxOptions.Outbound_SocksOptions().apply {
        type = "socks"
        server = bean.serverAddress
        server_port = bean.serverPort
        username = bean.username
        password = bean.password
        version = bean.protocolVersionName()
    }
}
