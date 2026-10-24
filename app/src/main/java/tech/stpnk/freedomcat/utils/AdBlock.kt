package tech.stpnk.freedomcat.utils

import libcore.Libcore
import tech.stpnk.freedomcat.FreedomCatApp
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

    private const val URL =
        "https://cdn.jsdelivr.net/gh/217heidai/adblockfilters@main/rules/adblocksingbox.srs"
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val MIN_SIZE = 1024L

    val file: File
        get() = File(FreedomCatApp.application.cacheDir.parentFile, "no_backup/$FILE_NAME")

    fun isReady() = file.length() > MIN_SIZE

    fun isStale() = !isReady() ||
            System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS

    fun update(): Boolean {
        val dst = file
        dst.parentFile?.mkdirs()
        val tmp = File(dst.parentFile, "$FILE_NAME.tmp")
        return try {
            Libcore.newHttpClient().newRequest().apply {
                setURL(URL)
                setUserAgent(USER_AGENT)
            }.execute().writeTo(tmp.absolutePath)
            if (tmp.length() <= MIN_SIZE) {
                tmp.delete()
                false
            } else {
                dst.delete()
                tmp.renameTo(dst)
            }
        } catch (e: Exception) {
            Logs.w("adblock: download failed", e)
            tmp.delete()
            false
        }
    }

}
