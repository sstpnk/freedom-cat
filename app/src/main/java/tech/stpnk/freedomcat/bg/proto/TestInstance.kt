package tech.stpnk.freedomcat.bg.proto

import tech.stpnk.freedomcat.BuildConfig
import tech.stpnk.freedomcat.bg.GuardedProcessPool
import tech.stpnk.freedomcat.database.ProxyEntity
import tech.stpnk.freedomcat.fmt.buildConfig
import tech.stpnk.freedomcat.ktx.Logs
import tech.stpnk.freedomcat.ktx.runOnDefaultDispatcher
import tech.stpnk.freedomcat.ktx.tryResume
import tech.stpnk.freedomcat.ktx.tryResumeWithException
import kotlinx.coroutines.delay
import libcore.Libcore
import tech.stpnk.freedomcat.core.net.LocalResolverImpl
import kotlin.coroutines.suspendCoroutine

class TestInstance(profile: ProxyEntity, val link: String, private val timeout: Int) :
    BoxInstance(profile) {

    suspend fun doTest(): Int {
        return suspendCoroutine { c ->
            processes = GuardedProcessPool {
                Logs.w(it)
                c.tryResumeWithException(it)
            }
            runOnDefaultDispatcher {
                use {
                    try {
                        init()
                        launch()
                        if (processes.processCount > 0) {
                            // wait for plugin start
                            delay(500)
                        }
                        c.tryResume(Libcore.urlTest(box, link, timeout))
                    } catch (e: Exception) {
                        c.tryResumeWithException(e)
                    }
                }
            }
        }
    }

    override fun buildConfig() {
        config = buildConfig(profile, true)
    }

    override suspend fun loadConfig() {
        // don't call destroyAllJsi here
        if (BuildConfig.DEBUG) Logs.d(config.config)
        box = Libcore.newSingBoxInstance(config.config, LocalResolverImpl)
    }

}
