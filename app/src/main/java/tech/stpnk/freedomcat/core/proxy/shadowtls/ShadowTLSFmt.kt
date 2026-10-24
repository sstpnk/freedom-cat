package tech.stpnk.freedomcat.core.proxy.shadowtls

import tech.stpnk.freedomcat.fmt.v2ray.buildSingBoxOutboundTLS
import tech.stpnk.freedomcat.core.SingBoxOptions

fun buildSingBoxOutboundShadowTLSBean(bean: ShadowTLSBean): SingBoxOptions.Outbound_ShadowTLSOptions {
    return SingBoxOptions.Outbound_ShadowTLSOptions().apply {
        type = "shadowtls"
        server = bean.serverAddress
        server_port = bean.serverPort
        version = bean.version
        password = bean.password
        tls = buildSingBoxOutboundTLS(bean)
    }
}
