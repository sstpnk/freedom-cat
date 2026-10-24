package tech.stpnk.freedomcat.bg.proto

import android.os.SystemClock
import tech.stpnk.freedomcat.SagerNet
import tech.stpnk.freedomcat.bg.AbstractInstance
import tech.stpnk.freedomcat.bg.GuardedProcessPool
import tech.stpnk.freedomcat.database.DataStore
import tech.stpnk.freedomcat.database.ProxyEntity
import tech.stpnk.freedomcat.fmt.ConfigBuildResult
import tech.stpnk.freedomcat.fmt.buildConfig
import tech.stpnk.freedomcat.fmt.hysteria.HysteriaBean
import tech.stpnk.freedomcat.fmt.hysteria.buildHysteria1Config
import tech.stpnk.freedomcat.fmt.mieru.MieruBean
import tech.stpnk.freedomcat.fmt.mieru.buildMieruConfig
import tech.stpnk.freedomcat.fmt.naive.NaiveBean
import tech.stpnk.freedomcat.fmt.naive.buildNaiveConfig
import tech.stpnk.freedomcat.fmt.trojan_go.TrojanGoBean
import tech.stpnk.freedomcat.fmt.trojan_go.buildTrojanGoConfig
import tech.stpnk.freedomcat.ktx.*
import tech.stpnk.freedomcat.plugin.PluginManager
import kotlinx.coroutines.*
import libcore.BoxInstance
import libcore.Libcore
import tech.stpnk.freedomcat.core.net.LocalResolverImpl
import java.io.File

abstract class BoxInstance(
    val profile: ProxyEntity
) : AbstractInstance {

    lateinit var config: ConfigBuildResult
    lateinit var box: BoxInstance

    val pluginPath = hashMapOf<String, PluginManager.InitResult>()
    val pluginConfigs = hashMapOf<Int, Pair<Int, String>>()
    val externalInstances = hashMapOf<Int, AbstractInstance>()
    open lateinit var processes: GuardedProcessPool
    private var cacheFiles = ArrayList<File>()
    fun isInitialized(): Boolean {
        return ::config.isInitialized && ::box.isInitialized
    }

    protected fun initPlugin(name: String): PluginManager.InitResult {
        return pluginPath.getOrPut(name) { PluginManager.init(name)!! }
    }

    protected open fun buildConfig() {
        config = buildConfig(profile)
    }

    protected open suspend fun loadConfig() {
        box = Libcore.newSingBoxInstance(config.config, LocalResolverImpl)
    }

    open suspend fun init() {
        buildConfig()
        for ((chain) in config.externalIndex) {
            chain.entries.forEachIndexed { index, (port, profile) ->
                when (val bean = profile.requireBean()) {
                    is TrojanGoBean -> {
                        initPlugin("trojan-go-plugin")
                        pluginConfigs[port] = profile.type to bean.buildTrojanGoConfig(port)
                    }

                    is MieruBean -> {
                        initPlugin("mieru-plugin")
                        pluginConfigs[port] = profile.type to bean.buildMieruConfig(port)
                    }

                    is NaiveBean -> {
                        initPlugin("naive-plugin")
                        pluginConfigs[port] = profile.type to bean.buildNaiveConfig(port)
                    }

                    is HysteriaBean -> {
                        initPlugin("hysteria-plugin")
                        pluginConfigs[port] = profile.type to bean.buildHysteria1Config(port) {
                            File(
                                app.cacheDir, "hysteria_" + SystemClock.elapsedRealtime() + ".ca"
                            ).apply {
                                parentFile?.mkdirs()
                                cacheFiles.add(this)
                            }
                        }
                    }
                }
            }
        }
        loadConfig()
    }

    override fun launch() {
        // TODO move, this is not box
        val cacheDir = File(SagerNet.application.cacheDir, "tmpcfg")
        cacheDir.mkdirs()

        for ((chain) in config.externalIndex) {
            chain.entries.forEachIndexed { index, (port, profile) ->
                val bean = profile.requireBean()
                val needChain = index != chain.size - 1
                val (profileType, config) = pluginConfigs[port] ?: (0 to "")

                when {
                    externalInstances.containsKey(port) -> {
                        externalInstances[port]!!.launch()
                    }

                    bean is TrojanGoBean -> {
                        val configFile = File(
                            cacheDir, "trojan_go_" + SystemClock.elapsedRealtime() + ".json"
                        )
                        configFile.parentFile?.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val commands = mutableListOf(
                            initPlugin("trojan-go-plugin").path, "-config", configFile.absolutePath
                        )

                        processes.start(commands)
                    }

                    bean is MieruBean -> {
                        val configFile = File(
                            cacheDir, "mieru_" + SystemClock.elapsedRealtime() + ".json"
                        )

                        configFile.parentFile?.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val envMap = mutableMapOf<String, String>()
                        envMap["MIERU_CONFIG_JSON_FILE"] = configFile.absolutePath
                        envMap["MIERU_PROTECT_PATH"] = "protect_path"

                        val commands = mutableListOf(
                            initPlugin("mieru-plugin").path, "run",
                        )

                        processes.start(commands, envMap)
                    }

                    bean is NaiveBean -> {
                        val configFile = File(
                            cacheDir, "naive_" + SystemClock.elapsedRealtime() + ".json"
                        )

                        configFile.parentFile?.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val envMap = mutableMapOf<String, String>()

                        if (bean.certificates.isNotBlank()) {
                            val certFile = File(
                                cacheDir, "naive_" + SystemClock.elapsedRealtime() + ".crt"
                            )

                            certFile.parentFile?.mkdirs()
                            certFile.writeText(bean.certificates)
                            cacheFiles.add(certFile)

                            envMap["SSL_CERT_FILE"] = certFile.absolutePath
                        }

                        val commands = mutableListOf(
                            initPlugin("naive-plugin").path, configFile.absolutePath
                        )

                        processes.start(commands, envMap)
                    }

                    bean is HysteriaBean -> {
                        val configFile = File(
                            cacheDir, "hysteria_" + SystemClock.elapsedRealtime() + ".json"
                        )

                        configFile.parentFile?.mkdirs()
                        configFile.writeText(config)
                        cacheFiles.add(configFile)

                        val commands = mutableListOf(
                            initPlugin("hysteria-plugin").path,
                            "--no-check",
                            "--config",
                            configFile.absolutePath,
                            "--log-level",
                            if (DataStore.logLevel > 0) "trace" else "warn",
                            "client"
                        )

                        if (bean.protocol == HysteriaBean.PROTOCOL_FAKETCP) {
                            commands.addAll(0, listOf("su", "-c"))
                        }

                        processes.start(commands)
                    }
                }
            }
        }

        box.start()
    }

    @Suppress("EXPERIMENTAL_API_USAGE")
    override fun close() {
        for (instance in externalInstances.values) {
            runCatching {
                instance.close()
            }
        }

        cacheFiles.removeAll { it.delete(); true }

        if (::processes.isInitialized) processes.close(GlobalScope + Dispatchers.IO)

        if (::box.isInitialized) {
            box.close()
        }
    }

}