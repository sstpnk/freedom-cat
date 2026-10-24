package tech.stpnk.freedomcat.utils

import libcore.Libcore
import tech.stpnk.freedomcat.FreedomCatApp
import tech.stpnk.freedomcat.database.DataStore
import tech.stpnk.freedomcat.ktx.Logs
import tech.stpnk.freedomcat.ktx.USER_AGENT
import java.io.File

/**
 * Fetches the ad-blocking rule set into the sing-box working directory.
 * sing-box refuses to start when a remote rule set cannot be downloaded, so the
 * app downloads the list itself and the config only points at the local file.
 */
object AdBlock {

    const val FILE_NAME = "adblock.srs"

    // The CDN is unreachable from some mobile networks, so fall back to a raw
    // mirror; both are retried through the tunnel when it is already running.
    private val URLS = listOf(
        "https://cdn.jsdelivr.net/gh/217heidai/adblockfilters@main/rules/adblocksingbox.srs",
        "https://raw.githubusercontent.com/217heidai/adblockfilters/main/rules/adblocksingbox.srs",
    )

    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val MIN_SIZE = 100_000L

    val file: File
        get() = File(FreedomCatApp.application.cacheDir.parentFile, "no_backup/$FILE_NAME")

    fun isReady() = file.length() > MIN_SIZE

    fun isStale() = !isReady() ||
            System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS

    fun update(): Boolean {
        for (viaTunnel in booleanArrayOf(false, true)) {
            if (viaTunnel && !DataStore.serviceState.started) continue
            for (url in URLS) {
                if (download(url, viaTunnel)) return true
            }
        }
        return false
    }

    private fun download(url: String, viaTunnel: Boolean): Boolean {
        val dst = file
        dst.parentFile?.mkdirs()
        val tmp = File(dst.parentFile, "$FILE_NAME.tmp")
        return try {
            Libcore.newHttpClient().apply {
                if (viaTunnel) trySocks5(DataStore.mixedPort)
            }.newRequest().apply {
                setURL(url)
                setUserAgent(USER_AGENT)
            }.execute().writeTo(tmp.absolutePath)

            val valid = tmp.length() > MIN_SIZE && isRuleSet(tmp)
            if (valid) {
                dst.delete()
                tmp.renameTo(dst)
            } else {
                tmp.delete()
            }
            valid
        } catch (e: Exception) {
            Logs.w("adblock: $url failed", e)
            tmp.delete()
            false
        }
    }

    // SRS files start with "SRS"; anything else is a proxy error page.
    private fun isRuleSet(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val magic = ByteArray(3)
            input.read(magic) == 3 && String(magic) == "SRS"
        }
    }.getOrDefault(false)

}
