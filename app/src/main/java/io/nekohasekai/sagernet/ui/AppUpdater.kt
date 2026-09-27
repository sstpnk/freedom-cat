package io.nekohasekai.sagernet.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.filterIsInstance
import io.nekohasekai.sagernet.ktx.getStr
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import libcore.Libcore
import moe.matsuri.nb4a.utils.Util
import org.json.JSONObject
import java.io.File

class AppUpdater(
    private val activity: MainActivity,
    private val unknownSourcesSettings: ActivityResultLauncher<Intent>,
) {

    companion object {
        private const val RELEASES_API =
            "https://api.github.com/repos/sstpnk/freedom-cat/releases/latest"
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val TV_FLAVOR = "ossTv"
    }

    private var activeUpdate: UpdateInfo? = null

    fun checkOnStartup() {
        runOnDefaultDispatcher {
            runCatching {
                checkLatestRelease()
            }.onSuccess { update ->
                if (update != null) {
                    onMainDispatcher {
                        showUpdateDialog(update)
                    }
                }
            }
        }
    }

    fun installDownloadedUpdateIfAllowed() {
        val update = activeUpdate ?: return
        val apkFile = update.localFile
        if (apkFile.isFile) {
            installOrRequestPermission(update)
        }
    }

    private fun checkLatestRelease(): UpdateInfo? {
        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            trySocks5(DataStore.mixedPort)
        }

        try {
            val response = client.newRequest().apply {
                setURL(RELEASES_API)
            }.execute()

            val release = JSONObject(Util.getStringBox(response.contentString))
            val tagName = release.optString("tag_name").trim()
            val version = tagName.removePrefix("v")
            if (!isNewerVersion(version, BuildConfig.VERSION_NAME)) return null

            val assetName = assetName(version)
            val releaseAssets = release.getJSONArray("assets").filterIsInstance<JSONObject>()
            val asset = releaseAssets.find { it.getStr("name") == assetName }
                ?: error(activity.getString(R.string.update_asset_not_found, assetName))
            val downloadUrl = asset.getStr("browser_download_url")
                ?: error(activity.getString(R.string.update_asset_not_found, assetName))

            return UpdateInfo(
                version = version,
                assetName = assetName,
                downloadUrl = downloadUrl,
                localFile = File(updateCacheDir(), assetName),
            )
        } finally {
            client.close()
        }
    }

    private fun showUpdateDialog(update: UpdateInfo) {
        activeUpdate = update
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.update_dialog_title)
            .setMessage(
                activity.getString(
                    R.string.update_dialog_message,
                    BuildConfig.VERSION_NAME,
                    update.version,
                )
            )
            .setPositiveButton(R.string.download) { _, _ ->
                downloadAndInstall(update)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun downloadAndInstall(update: UpdateInfo) {
        activity.snackbar(activity.getString(R.string.update_download_started)).show()
        runOnDefaultDispatcher {
            runCatching {
                downloadApk(update)
            }.onSuccess {
                onMainDispatcher {
                    activity.snackbar(activity.getString(R.string.update_download_complete)).show()
                    installOrRequestPermission(update)
                }
            }.onFailure { error ->
                onMainDispatcher {
                    activity.alert(error.readableMessage).show()
                }
            }
        }
    }

    private fun downloadApk(update: UpdateInfo) {
        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            trySocks5(DataStore.mixedPort)
        }

        try {
            updateCacheDir().listFiles()
                ?.filter { it.isFile && it.name.endsWith(".apk") && it.name != update.assetName }
                ?.forEach { it.delete() }

            val tmpFile = File(update.localFile.parentFile, update.assetName + ".tmp")
            if (tmpFile.isFile) tmpFile.delete()

            val response = client.newRequest().apply {
                setURL(update.downloadUrl)
            }.execute()
            response.writeTo(tmpFile.canonicalPath)

            if (update.localFile.isFile) update.localFile.delete()
            check(tmpFile.renameTo(update.localFile)) {
                activity.getString(R.string.update_download_failed)
            }
        } finally {
            client.close()
        }
    }

    private fun installOrRequestPermission(update: UpdateInfo) {
        activeUpdate = update
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_install_permission_title)
                .setMessage(R.string.update_install_permission_message)
                .setPositiveButton(R.string.open_app_settings) { _, _ ->
                    unknownSourcesSettings.launch(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${BuildConfig.APPLICATION_ID}"),
                        )
                    )
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }

        val uri = FileProvider.getUriForFile(
            activity,
            BuildConfig.APPLICATION_ID + ".cache",
            update.localFile,
        )
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun updateCacheDir(): File {
        return File(activity.cacheDir, "updates").apply { mkdirs() }
    }

    private fun assetName(version: String): String {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { it in releaseAbis }
            ?: error(activity.getString(R.string.update_no_supported_abi))
        val tvPart = if (BuildConfig.FLAVOR == TV_FLAVOR) "-tv" else ""
        return "FreedomCat-$version$tvPart-$abi.apk"
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.versionParts()
        val localParts = local.versionParts()
        val maxSize = maxOf(remoteParts.size, localParts.size)
        for (index in 0 until maxSize) {
            val remotePart = remoteParts.getOrNull(index) ?: 0
            val localPart = localParts.getOrNull(index) ?: 0
            if (remotePart != localPart) return remotePart > localPart
        }
        return false
    }

    private fun String.versionParts(): List<Int> {
        return split('.', '-', '_')
            .mapNotNull { part -> part.takeWhile { it.isDigit() }.toIntOrNull() }
    }

    private data class UpdateInfo(
        val version: String,
        val assetName: String,
        val downloadUrl: String,
        val localFile: File,
    )

    private val releaseAbis = setOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
}
