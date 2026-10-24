package tech.stpnk.freedomcat.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import tech.stpnk.freedomcat.database.DataStore
import java.security.MessageDigest
import java.util.Locale

/**
 * Stable per-device identifier that subscription panels expect in the `x-hwid`
 * header. Panels bound to hardware IDs count each value as one device slot, so
 * it has to survive restarts and updates: it is computed once and then stored.
 */
object Hwid {

    @SuppressLint("HardwareIds")
    fun get(context: Context): String {
        DataStore.subscriptionHwid?.takeIf { it.isNotBlank() }?.let { return it }
        val source = listOf(
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty(),
            Build.MANUFACTURER,
            Build.MODEL,
            Build.BRAND,
            Build.DEVICE,
            Build.PRODUCT,
            Build.BOARD,
            Build.HARDWARE,
        ).joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        val hex = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val hwid = "%s-%s-%s-%s-%s".format(
            hex.substring(0, 8),
            hex.substring(8, 12),
            hex.substring(12, 16),
            hex.substring(16, 20),
            hex.substring(20, 32),
        ).uppercase(Locale.ROOT)
        DataStore.subscriptionHwid = hwid
        return hwid
    }

}
