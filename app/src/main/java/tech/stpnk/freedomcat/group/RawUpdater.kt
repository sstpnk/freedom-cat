package tech.stpnk.freedomcat.group

import android.annotation.SuppressLint
import android.os.Build
import tech.stpnk.freedomcat.BuildConfig
import tech.stpnk.freedomcat.R
import tech.stpnk.freedomcat.database.*
import tech.stpnk.freedomcat.fmt.AbstractBean
import tech.stpnk.freedomcat.fmt.http.HttpBean
import tech.stpnk.freedomcat.fmt.hysteria.HysteriaBean
import tech.stpnk.freedomcat.fmt.hysteria.parseHysteria1Json
import tech.stpnk.freedomcat.fmt.shadowsocks.ShadowsocksBean
import tech.stpnk.freedomcat.fmt.shadowsocks.parseShadowsocks
import tech.stpnk.freedomcat.fmt.socks.SOCKSBean
import tech.stpnk.freedomcat.fmt.trojan.TrojanBean
import tech.stpnk.freedomcat.fmt.trojan_go.parseTrojanGo
import tech.stpnk.freedomcat.fmt.tuic.TuicBean
import tech.stpnk.freedomcat.fmt.v2ray.StandardV2RayBean
import tech.stpnk.freedomcat.fmt.v2ray.VMessBean
import tech.stpnk.freedomcat.fmt.v2ray.isTLS
import tech.stpnk.freedomcat.fmt.v2ray.setTLS
import tech.stpnk.freedomcat.fmt.wireguard.WireGuardBean
import tech.stpnk.freedomcat.fmt.wireguard.normalizeIpPrefixes
import tech.stpnk.freedomcat.fmt.wireguard.parseWireGuardConfig
import tech.stpnk.freedomcat.ktx.*
import tech.stpnk.freedomcat.utils.Hwid
import libcore.HTTPResponse
import libcore.Libcore
import tech.stpnk.freedomcat.core.Protocols
import tech.stpnk.freedomcat.core.proxy.anytls.AnyTLSBean
import tech.stpnk.freedomcat.core.proxy.config.ConfigBean
import tech.stpnk.freedomcat.core.utils.Util
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.yaml.snakeyaml.TypeDescription
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.error.YAMLException
import androidx.core.net.toUri

@Suppress("EXPERIMENTAL_API_USAGE")
object RawUpdater : GroupUpdater() {

    @SuppressLint("Recycle")
    override suspend fun doUpdate(
        proxyGroup: ProxyGroup,
        subscription: SubscriptionBean,
        userInterface: GroupManager.Interface?,
        byUser: Boolean
    ) {

        val link = subscription.link
        var proxies: List<AbstractBean>
        if (link.startsWith("content://")) {
            val contentText = app.contentResolver.openInputStream(link.toUri())
                ?.bufferedReader()
                ?.readText()

            proxies = contentText?.let { parseRaw(contentText) }
                ?: error(app.getString(R.string.no_proxies_found_in_subscription))
        } else {

            val response = Libcore.newHttpClient().apply {
                trySocks5(DataStore.mixedPort)
                tryH3Direct()
                when (DataStore.appTLSVersion) {
                    "1.3" -> restrictedTLS()
                }
            }.newRequest().apply {
                if (DataStore.allowInsecureOnRequest) {
                    allowInsecure()
                }
                setURL(subscription.link)
                setUserAgent(subscription.customUserAgent.takeIf { it.isNotBlank() } ?: USER_AGENT)
                // Panels that bind a subscription to devices answer without these headers
                // with a placeholder "app not supported" list instead of real servers.
                val hwid = Hwid.get(app)
                setHeader("x-hwid", hwid)
                setHeader("X-Device-ID", hwid)
                setHeader("x-device-os", "Android")
                setHeader("x-ver-os", Build.VERSION.RELEASE)
                setHeader("x-device-model", Build.MODEL)
                setHeader("x-app-version", BuildConfig.VERSION_NAME)
                setHeader("x-client", "FreedomCat")
            }.execute()
            checkSubscriptionDenied(response)
            proxies = parseRaw(Util.getStringBox(response.contentString))
                ?: error(app.getString(R.string.no_proxies_found))
            checkPlaceholder(proxies)

            subscription.subscriptionUserinfo =
                Util.getStringBox(response.getHeader("Subscription-Userinfo"))

            // 修改默认名字
            if (proxyGroup.name?.startsWith("Subscription #") == true) {
                var remoteName = Util.getStringBox(response.getHeader("content-disposition"))
                if (remoteName.isNotBlank()) {
                    remoteName = Util.decodeFilename(remoteName)
                    if (remoteName.isNotBlank()) {
                        proxyGroup.name = remoteName
                    }
                }
            }
        }

        val proxiesMap = LinkedHashMap<String, AbstractBean>()
        for (proxy in proxies) {
            var index = 0
            var name = proxy.displayName()
            while (proxiesMap.containsKey(name)) {
                println("Exists name: $name")
                index++
                name = name.replace(" (${index - 1})", "")
                name = "$name ($index)"
                proxy.name = name
            }
            proxiesMap[proxy.displayName()] = proxy
        }
        proxies = proxiesMap.values.toList()

        if (subscription.forceResolve) forceResolve(proxies, proxyGroup.id)

        val exists = SagerDatabase.proxyDao.getByGroup(proxyGroup.id)
        val duplicate = ArrayList<String>()
        if (subscription.deduplication) {
            Logs.d("Before deduplication: ${proxies.size}")
            val uniqueProxies = LinkedHashSet<Protocols.Deduplication>()
            val uniqueNames = HashMap<Protocols.Deduplication, String>()
            for (_proxy in proxies) {
                val proxy = Protocols.Deduplication(_proxy, _proxy.javaClass.toString())
                if (!uniqueProxies.add(proxy)) {
                    val index = uniqueProxies.indexOf(proxy)
                    if (uniqueNames.containsKey(proxy)) {
                        val name = uniqueNames[proxy]!!.replace(" ($index)", "")
                        if (name.isNotBlank()) {
                            duplicate.add("$name ($index)")
                            uniqueNames[proxy] = ""
                        }
                    }
                    duplicate.add(_proxy.displayName() + " ($index)")
                } else {
                    uniqueNames[proxy] = _proxy.displayName()
                }
            }
            uniqueProxies.retainAll(uniqueNames.keys)
            proxies = uniqueProxies.toList().map { it.bean }
        }

        Logs.d("New profiles: ${proxies.size}")

        val nameMap = proxies.associateBy { bean ->
            bean.displayName()
        }

        Logs.d("Unique profiles: ${nameMap.size}")

        val toDelete = ArrayList<ProxyEntity>()
        val toReplace = exists.mapNotNull { entity ->
            val name = entity.displayName()
            if (nameMap.contains(name)) name to entity else let {
                toDelete.add(entity)
                null
            }
        }.toMap()

        Logs.d("toDelete profiles: ${toDelete.size}")
        Logs.d("toReplace profiles: ${toReplace.size}")

        val toUpdate = ArrayList<ProxyEntity>()
        val added = mutableListOf<String>()
        val updated = mutableMapOf<String, String>()
        val deleted = toDelete.map { it.displayName() }

        var userOrder = 1L
        var changed = toDelete.size
        for ((name, bean) in nameMap.entries) {
            if (toReplace.contains(name)) {
                val entity = toReplace[name]!!
                val existsBean = entity.requireBean()
                // 更新订阅，保留自定义覆写设置
                bean.customOutboundJson = existsBean.customOutboundJson
                bean.customConfigJson = existsBean.customConfigJson
                when {
                    existsBean != bean -> {
                        changed++
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        updated[entity.displayName()] = name

                        Logs.d("Updated profile: $name")
                    }

                    entity.userOrder != userOrder -> {
                        entity.putBean(bean)
                        toUpdate.add(entity)
                        entity.userOrder = userOrder

                        Logs.d("Reordered profile: $name")
                    }

                    else -> {
                        Logs.d("Ignored profile: $name")
                    }
                }
            } else {
                changed++
                SagerDatabase.proxyDao.addProxy(
                    ProxyEntity(
                        groupId = proxyGroup.id, userOrder = userOrder
                    ).apply {
                        putBean(bean)
                    })
                added.add(name)
                Logs.d("Inserted profile: $name")
            }
            userOrder++
        }

        SagerDatabase.proxyDao.updateProxy(toUpdate).also {
            Logs.d("Updated profiles: $it")
        }

        SagerDatabase.proxyDao.deleteProxy(toDelete).also {
            Logs.d("Deleted profiles: $it")
        }

        val existCount = SagerDatabase.proxyDao.countByGroup(proxyGroup.id).toInt()

        if (existCount != proxies.size) {
            Logs.e("Exist profiles: $existCount, new profiles: ${proxies.size}")
        }

        subscription.lastUpdated = (System.currentTimeMillis() / 1000).toInt()
        SagerDatabase.groupDao.updateGroup(proxyGroup)
        finishUpdate(proxyGroup)

        userInterface?.onUpdateSuccess(
            proxyGroup, changed, added, updated, deleted, duplicate, byUser
        )
    }

    private fun checkSubscriptionDenied(response: HTTPResponse) {
        if (Util.getStringBox(response.getHeader("x-hwid-not-supported")).isNotBlank()) {
            error(app.getString(R.string.subscription_hwid_unsupported))
        }
        if (Util.getStringBox(response.getHeader("x-hwid-max-devices-reached")).isNotBlank()) {
            error(app.getString(R.string.subscription_hwid_limit_reached))
        }
    }

    // Such panels answer a rejected client with 200 and one fake profile per reason.
    private fun checkPlaceholder(proxies: List<AbstractBean>) {
        if (proxies.isEmpty() || proxies.any { it.serverAddress != "0.0.0.0" }) return
        error(
            app.getString(
                R.string.subscription_placeholder,
                proxies.joinToString(" / ") { it.displayName() },
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun parseRaw(text: String, fileName: String = ""): List<AbstractBean>? {

        val proxies = mutableListOf<AbstractBean>()

        if (text.contains("proxies:")) {

            // clash & meta

            try {

                val yaml = Yaml().apply {
                    addTypeDescription(TypeDescription(String::class.java, "str"))
                }.loadAs(text, Map::class.java)

                val globalClientFingerprint = yaml["global-client-fingerprint"]?.toString() ?: ""

                for (proxy in (yaml["proxies"] as? (List<Map<String, Any?>>) ?: error(
                    app.getString(R.string.no_proxies_found_in_file)
                ))) {
                    // Note: YAML numbers parsed as "Long"

                    when (proxy["type"] as String) {
                        "socks5" -> {
                            proxies.add(SOCKSBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                username = proxy["username"]?.toString()
                                password = proxy["password"]?.toString()
                                name = proxy["name"]?.toString()
                            })
                        }

                        "http" -> {
                            proxies.add(HttpBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                username = proxy["username"]?.toString()
                                password = proxy["password"]?.toString()
                                setTLS(proxy["tls"]?.toString() == "true")
                                sni = proxy["sni"]?.toString()
                                name = proxy["name"]?.toString()
                                allowInsecure = proxy["skip-cert-verify"]?.toString() == "true"
                            })
                        }

                        "ss" -> {
                            val ssPlugin = mutableListOf<String>()
                            if (proxy.contains("plugin")) {
                                val opts = proxy["plugin-opts"] as Map<String, Any?>
                                when (proxy["plugin"]) {
                                    "obfs" -> {
                                        ssPlugin.apply {
                                            add("obfs-local")
                                            add("obfs=" + (opts["mode"]?.toString() ?: ""))
                                            add("obfs-host=" + (opts["host"]?.toString() ?: ""))
                                        }
                                    }

                                    "v2ray-plugin" -> {
                                        ssPlugin.apply {
                                            add("v2ray-plugin")
                                            add("mode=" + (opts["mode"]?.toString() ?: ""))
                                            if (opts["mode"]?.toString() == "true") add("tls")
                                            add("host=" + (opts["host"]?.toString() ?: ""))
                                            add("path=" + (opts["path"]?.toString() ?: ""))
                                            if (opts["mux"]?.toString() == "true") add("mux=8")
                                        }
                                    }
                                }
                            }
                            proxies.add(ShadowsocksBean().apply {
                                serverAddress = proxy["server"] as String
                                serverPort = proxy["port"].toString().toInt()
                                password = proxy["password"]?.toString()
                                method = clashCipher(proxy["cipher"] as String)
                                plugin = ssPlugin.joinToString(";")
                                name = proxy["name"]?.toString()
                            })
                        }

                        "vmess", "vless", "trojan" -> {
                            val bean = when (proxy["type"] as String) {
                                "vmess" -> VMessBean()
                                "vless" -> VMessBean().apply {
                                    alterId = -1 // make it VLESS
                                    packetEncoding = 2 // clash meta default XUDP
                                }

                                "trojan" -> TrojanBean().apply {
                                    security = "tls"
                                }

                                else -> error("impossible")
                            }

                            bean.serverAddress = proxy["server"]?.toString() ?: continue
                            bean.serverPort = proxy["port"]?.toString()?.toIntOrNull() ?: continue

                            for (opt in proxy) {
                                when (opt.key) {
                                    "name" -> bean.name = opt.value?.toString()
                                    "password" -> if (bean is TrojanBean) bean.password =
                                        opt.value?.toString()

                                    "uuid" -> if (bean is VMessBean) bean.uuid =
                                        opt.value?.toString()

                                    "alterId" -> if (bean is VMessBean && !bean.isVLESS) bean.alterId =
                                        opt.value?.toString()?.toIntOrNull()

                                    "cipher" -> if (bean is VMessBean && !bean.isVLESS) bean.encryption =
                                        (opt.value as? String)

                                    "flow" -> if (bean is VMessBean && bean.isVLESS) {
                                        (opt.value as? String)?.let {
                                            if (it.contains("xtls-rprx-vision")) {
                                                bean.encryption = "xtls-rprx-vision"
                                            }
                                        }
                                    }

                                    "packet-encoding" -> if (bean is VMessBean) {
                                        bean.packetEncoding = when ((opt.value as? String)) {
                                            "packetaddr" -> 1
                                            "xudp" -> 2
                                            else -> 0
                                        }
                                    }

                                    "tls" -> if (bean is VMessBean) {
                                        bean.security =
                                            if (opt.value as? Boolean == true) "tls" else ""
                                    }

                                    "servername", "sni" -> bean.sni = opt.value?.toString()

                                    "alpn" -> bean.alpn =
                                        (opt.value as? List<Any>)?.joinToString("\n")

                                    "skip-cert-verify" -> bean.allowInsecure =
                                        opt.value as? Boolean == true

                                    "client-fingerprint" -> bean.utlsFingerprint =
                                        opt.value as String

                                    "reality-opts" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (realityOpt in it) {
                                            bean.security = "tls"

                                            when (realityOpt.key) {
                                                "public-key" -> bean.realityPubKey =
                                                    realityOpt.value?.toString()

                                                "short-id" -> bean.realityShortId =
                                                    realityOpt.value?.toString()
                                            }
                                        }
                                    }

                                    "network" -> {
                                        when (opt.value) {
                                            "h2", "http" -> bean.type = "http"
                                            "ws", "grpc" -> bean.type = opt.value as String
                                        }
                                    }

                                    "ws-opts" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (wsOpt in it) {
                                            when (wsOpt.key) {
                                                "headers" -> (wsOpt.value as? Map<Any, Any?>)?.forEach { (key, value) ->
                                                    when (key.toString().lowercase()) {
                                                        "host" -> {
                                                            bean.host = value?.toString()
                                                        }
                                                    }
                                                }

                                                "path" -> {
                                                    bean.path = wsOpt.value?.toString()
                                                }

                                                "max-early-data" -> {
                                                    bean.wsMaxEarlyData =
                                                        wsOpt.value?.toString()?.toIntOrNull()
                                                }

                                                "early-data-header-name" -> {
                                                    bean.earlyDataHeaderName =
                                                        wsOpt.value?.toString()
                                                }

                                                "v2ray-http-upgrade" -> {
                                                    if (wsOpt.value as? Boolean == true) {
                                                        bean.type = "httpupgrade"
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    "h2-opts" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (h2Opt in it) {
                                            when (h2Opt.key) {
                                                "host" -> bean.host =
                                                    (h2Opt.value as? List<Any>)?.joinToString("\n")

                                                "path" -> bean.path = h2Opt.value?.toString()
                                            }
                                        }
                                    }

                                    "http-opts" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (httpOpt in it) {
                                            when (httpOpt.key) {
                                                "path" -> bean.path =
                                                    (httpOpt.value as? List<Any>)?.joinToString("\n")

                                                "headers" -> {
                                                    (httpOpt.value as? Map<Any, List<Any>>)?.forEach { (key, value) ->
                                                        when (key.toString().lowercase()) {
                                                            "host" -> {
                                                                bean.host = value.joinToString("\n")
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    "grpc-opts" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (grpcOpt in it) {
                                            when (grpcOpt.key) {
                                                "grpc-service-name" -> bean.path =
                                                    grpcOpt.value?.toString()
                                            }
                                        }
                                    }

                                    "smux" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (smuxOpt in it) {
                                            when (smuxOpt.key) {
                                                "enabled" -> bean.enableMux =
                                                    smuxOpt.value.toString() == "true"

                                                "max-streams" -> bean.muxConcurrency =
                                                    smuxOpt.value.toString().toInt()

                                                "padding" -> bean.muxPadding =
                                                    smuxOpt.value.toString() == "true"
                                            }
                                        }
                                    }

                                    "ech-opts" -> (opt.value as? Map<String, Any?>)?.also {
                                        for (echOpt in it) {
                                            when (echOpt.key) {
                                                "enable" -> bean.enableECH =
                                                    echOpt.value.toString() == "true"
                                            }
                                        }
                                    }
                                }
                            }
                            proxies.add(bean)
                        }

                        "anytls" -> {
                            val bean = AnyTLSBean()
                            for (opt in proxy) {
                                if (opt.value == null) continue
                                when (opt.key.replace("_", "-")) {
                                    "name" -> bean.name = opt.value.toString()
                                    "server" -> bean.serverAddress = opt.value as String
                                    "port" -> bean.serverPort = opt.value.toString().toInt()
                                    "password" -> bean.password = opt.value.toString()
                                    "client-fingerprint" -> bean.utlsFingerprint =
                                        opt.value as String

                                    "sni" -> bean.sni = opt.value.toString()
                                    "skip-cert-verify" -> bean.allowInsecure =
                                        opt.value.toString() == "true"

                                    "alpn" -> {
                                        val alpn = (opt.value as? (List<String>))
                                        bean.alpn = alpn?.joinToString("\n")
                                    }
                                }
                            }
                            proxies.add(bean)
                        }

                        "hysteria" -> {
                            val bean = HysteriaBean()
                            bean.protocolVersion = 1
                            var hopPorts = ""
                            for (opt in proxy) {
                                if (opt.value == null) continue
                                when (opt.key.replace("_", "-")) {
                                    "name" -> bean.name = opt.value.toString()
                                    "server" -> bean.serverAddress = opt.value as String
                                    "port" -> bean.serverPorts = opt.value.toString()
                                    "ports" -> hopPorts = opt.value.toString()

                                    "obfs" -> bean.obfuscation = opt.value.toString()

                                    "auth-str" -> {
                                        bean.authPayloadType = HysteriaBean.TYPE_STRING
                                        bean.authPayload = opt.value.toString()
                                    }

                                    "sni" -> bean.sni = opt.value.toString()

                                    "skip-cert-verify" -> bean.allowInsecure =
                                        opt.value.toString() == "true"

                                    "up" -> bean.uploadMbps =
                                        opt.value.toString().substringBefore(" ").toIntOrNull()
                                            ?: 100

                                    "down" -> bean.downloadMbps =
                                        opt.value.toString().substringBefore(" ").toIntOrNull()
                                            ?: 100

                                    "recv-window-conn" -> bean.connectionReceiveWindow =
                                        opt.value.toString().toIntOrNull() ?: 0

                                    "recv-window" -> bean.streamReceiveWindow =
                                        opt.value.toString().toIntOrNull() ?: 0

                                    "disable-mtu-discovery" -> bean.disableMtuDiscovery =
                                        opt.value.toString() == "true" || opt.value.toString() == "1"

                                    "alpn" -> {
                                        val alpn = (opt.value as? (List<String>))
                                        bean.alpn = alpn?.joinToString("\n") ?: "h3"
                                    }
                                }
                            }
                            if (hopPorts.isNotBlank()) {
                                bean.serverPorts = hopPorts
                            }
                            proxies.add(bean)
                        }

                        "hysteria2" -> {
                            val bean = HysteriaBean()
                            bean.protocolVersion = 2
                            var hopPorts = ""
                            for (opt in proxy) {
                                if (opt.value == null) continue
                                when (opt.key.replace("_", "-")) {
                                    "name" -> bean.name = opt.value.toString()
                                    "server" -> bean.serverAddress = opt.value as String
                                    "port" -> bean.serverPorts = opt.value.toString()
                                    "ports" -> hopPorts = opt.value.toString()

                                    "obfs-password" -> bean.obfuscation = opt.value.toString()

                                    "password" -> bean.authPayload = opt.value.toString()

                                    "sni" -> bean.sni = opt.value.toString()

                                    "skip-cert-verify" -> bean.allowInsecure =
                                        opt.value.toString() == "true"

                                    "up" -> bean.uploadMbps =
                                        opt.value.toString().substringBefore(" ").toIntOrNull() ?: 0

                                    "down" -> bean.downloadMbps =
                                        opt.value.toString().substringBefore(" ").toIntOrNull() ?: 0
                                }
                            }
                            if (hopPorts.isNotBlank()) {
                                bean.serverPorts = hopPorts
                            }
                            proxies.add(bean)
                        }

                        "tuic" -> {
                            val bean = TuicBean()
                            var ip = ""
                            for (opt in proxy) {
                                if (opt.value == null) continue
                                when (opt.key.replace("_", "-")) {
                                    "name" -> bean.name = opt.value.toString()
                                    "server" -> bean.serverAddress = opt.value.toString()
                                    "ip" -> ip = opt.value.toString()
                                    "port" -> bean.serverPort = opt.value.toString().toInt()

                                    "token" -> {
                                        bean.protocolVersion = 4
                                        bean.token = opt.value.toString()
                                    }

                                    "uuid" -> bean.uuid = opt.value.toString()

                                    "password" -> bean.token = opt.value.toString()

                                    "skip-cert-verify" -> bean.allowInsecure =
                                        opt.value.toString() == "true"

                                    "disable-sni" -> bean.disableSNI =
                                        opt.value.toString() == "true"

                                    "reduce-rtt" -> bean.reduceRTT =
                                        opt.value.toString() == "true"

                                    "sni" -> bean.sni = opt.value.toString()

                                    "alpn" -> {
                                        val alpn = (opt.value as? (List<String>))
                                        bean.alpn = alpn?.joinToString("\n")
                                    }

                                    "congestion-controller" -> bean.congestionController =
                                        opt.value.toString()

                                    "udp-relay-mode" -> bean.udpRelayMode = opt.value.toString()

                                }
                            }
                            if (ip.isNotBlank()) {
                                bean.serverAddress = ip
                                if (bean.sni.isNullOrBlank() && !bean.serverAddress.isNullOrBlank() && !bean.serverAddress.isIpAddress()) {
                                    bean.sni = bean.serverAddress
                                }
                            }
                            proxies.add(bean)
                        }
                    }
                }

                // Fix ent
                proxies.forEach {
                    it.initializeDefaultValues()
                    if (it is StandardV2RayBean) {
                        // 1. SNI
                        if (it.isTLS() && it.sni.isNullOrBlank() && !it.host.isNullOrBlank() && !it.host.isIpAddress()) {
                            it.sni = it.host
                        }
                        // 2. globalClientFingerprint
                        if (!it.realityPubKey.isNullOrBlank() && it.utlsFingerprint.isNullOrBlank()) {
                            it.utlsFingerprint = globalClientFingerprint
                            if (it.utlsFingerprint.isNullOrBlank()) it.utlsFingerprint = "chrome"
                        }
                    }
                }
                return proxies
            } catch (e: YAMLException) {
                Logs.w(e)
            }
        } else if (text.contains("[Interface]")) {
            // wireguard
            try {
                proxies.addAll(parseWireGuard(text).map {
                    if (fileName.isNotBlank()) it.name = fileName.removeSuffix(".conf")
                    it
                })
                return proxies
            } catch (e: Exception) {
                Logs.w(e)
            }
        }

        try {
            val json = JSONTokener(text).nextValue()
            return parseJSON(json)
        } catch (ignored: Exception) {
        }

        try {
            return parseProxies(text.decodeBase64UrlSafe()).takeIf { it.isNotEmpty() }
                ?: error("Not found")
        } catch (e: Exception) {
            Logs.w(e)
        }

        try {
            return parseProxies(text).takeIf { it.isNotEmpty() } ?: error("Not found")
        } catch (e: SubscriptionFoundException) {
            throw e
        } catch (ignored: Exception) {
        }

        return null
    }

    fun clashCipher(cipher: String): String {
        return when (cipher) {
            "dummy" -> "none"
            else -> cipher
        }
    }

    fun parseWireGuard(conf: String): List<WireGuardBean> {
        return parseWireGuardConfig(conf)
    }

    fun parseJSON(json: Any): List<AbstractBean> {
        val proxies = ArrayList<AbstractBean>()

        if (json is JSONObject) {
            when {
                json.has("server") && (json.has("up") || json.has("up_mbps")) -> {
                    return listOf(json.parseHysteria1Json())
                }

                json.has("method") -> {
                    return listOf(json.parseShadowsocks())
                }

                json.has("remote_addr") -> {
                    return listOf(json.parseTrojanGo())
                }

                json.has("outbounds") -> {
                    return json.getJSONArray("outbounds")
                        .filterIsInstance<JSONObject>()
                        .mapNotNull {
                            val ty = it.getStr("type")
                            if (ty == null || ty == "" ||
                                ty == "dns" || ty == "block" || ty == "direct" || ty == "selector" || ty == "urltest"
                            ) {
                                null
                            } else {
                                it
                            }
                        }.map {
                            ConfigBean().apply {
                                applyDefaultValues()
                                type = 1
                                config = it.toStringPretty()
                                name = it.getStr("tag")
                            }
                        }
                }

                json.has("endpoints") -> {
                    return json.getJSONArray("endpoints")
                        .filterIsInstance<JSONObject>()
                        .mapNotNull { endpoint ->
                            val peers = endpoint.optJSONArray("peers") ?: return@mapNotNull null
                            val peer = peers.optJSONObject(0) ?: return@mapNotNull null
                            val endpointAddress = peer.optString("endpoint", "")
                            val serverAddress = peer.optString("address")
                                .ifBlank { endpointAddress.substringBeforeLast(":") }
                                .removeSurrounding("[", "]")
                            val serverPort = if (peer.has("port")) {
                                peer.optInt("port", 0)
                            } else {
                                endpointAddress.substringAfterLast(":").toIntOrNull() ?: 0
                            }
                            if (serverAddress.isBlank() || serverPort == 0) {
                                return@mapNotNull null
                            }
                            WireGuardBean().apply {
                                name = endpoint.optString("tag", "")
                                    .ifBlank { "AmneziaWG" }
                                this.serverAddress = serverAddress
                                this.serverPort = serverPort
                                privateKey = endpoint.optString("private_key", "")
                                peerPublicKey = peer.optString("public_key", "")
                                peerPreSharedKey = peer.optString("pre_shared_key", "")
                                val addressArr = endpoint.optJSONArray("address")
                                localAddress = normalizeIpPrefixes(
                                    if (addressArr != null) {
                                        (0 until addressArr.length()).map { addressArr.getString(it) }
                                    } else {
                                        listOf(endpoint.optString("address", ""))
                                    },
                                    keepInvalid = true,
                                ).joinToString("\n")
                                val allowedIpsValue =
                                    peer.opt("allowed_ips") ?: endpoint.opt("allowed_ips")
                                allowedIPs = when (allowedIpsValue) {
                                    is JSONArray -> (0 until allowedIpsValue.length())
                                        .joinToString("\n") { allowedIpsValue.getString(it) }

                                    is String -> allowedIpsValue
                                    else -> ""
                                }
                                mtu = endpoint.optInt("mtu", 1420)
                                enableAmnezia = true
                                jc = endpoint.optInt("jc", 0)
                                jmin = endpoint.optInt("jmin", 0)
                                jmax = endpoint.optInt("jmax", 0)
                                s1 = endpoint.optInt("s1", 0)
                                s2 = endpoint.optInt("s2", 0)
                                s3 = endpoint.optInt("s3", 0)
                                s4 = endpoint.optInt("s4", 0)
                                h1 = endpoint.optString("h1", "")
                                h2 = endpoint.optString("h2", "")
                                h3 = endpoint.optString("h3", "")
                                h4 = endpoint.optString("h4", "")
                                i1 = endpoint.optString("i1", "")
                                i2 = endpoint.optString("i2", "")
                                i3 = endpoint.optString("i3", "")
                                i4 = endpoint.optString("i4", "")
                                i5 = endpoint.optString("i5", "")
                                headerProtectionKey = endpoint.optString("header_protection_key", "")
                                contentPaddingAddition = endpoint.optString("content_padding_addition", "")
                                rekeyAfterTime = endpoint.optString("rekey_after_time", "")
                                rekeyTimeout = endpoint.optString("rekey_timeout", "")
                                rejectAfterTime = endpoint.optString("reject_after_time", "")
                                keepaliveTimeout = endpoint.optString("keepalive_timeout", "")
                                maxHandshakeAttempts = endpoint.optString("max_handshake_attempts", "")
                                randomTrailers = endpoint.optFlag("random_trailers")
                                disableCookies = endpoint.optFlag("disable_cookies")
                                persistentKeepalive = peer.optString(
                                    "persistent_keepalive",
                                    peer.optString("persistent_keepalive_interval", ""),
                                )
                            }.applyDefaultValues()
                        }
                }

                json.has("server") && json.has("server_port") -> {
                    return listOf(ConfigBean().applyDefaultValues().apply {
                        type = 1
                        config = json.toStringPretty()
                    })
                }
            }
        } else {
            json as JSONArray
            json.forEach { _, it ->
                if (isJsonObjectValid(it)) {
                    proxies.addAll(parseJSON(it))
                }
            }
        }

        proxies.forEach { it.initializeDefaultValues() }
        return proxies
    }

    /** Reads a JSON boolean that generated configs may encode as "on"/"off". */
    private fun JSONObject.optFlag(name: String): Boolean {
        return when (val value = opt(name)) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.equals("true", true) || value.equals("on", true) ||
                    value.equals("yes", true) || value == "1"

            else -> false
        }
    }

}
