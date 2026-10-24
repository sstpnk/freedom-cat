package tech.stpnk.freedomcat.core.plugin

import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.net.Uri
import android.os.Build
import android.widget.Toast
import tech.stpnk.freedomcat.FreedomCatApp
import tech.stpnk.freedomcat.plugin.PluginManager.loadString
import tech.stpnk.freedomcat.utils.PackageCache

object Plugins {
    const val AUTHORITIES_PREFIX_SEKAI_EXE = "tech.stpnk.freedomcat.plugin."
    const val AUTHORITIES_PREFIX_NEKO_EXE = "moe.matsuri.exe."

    const val ACTION_NATIVE_PLUGIN = "tech.stpnk.freedomcat.plugin.ACTION_NATIVE_PLUGIN"

    const val METADATA_KEY_ID = "tech.stpnk.freedomcat.plugin.id"
    const val METADATA_KEY_EXECUTABLE_PATH = "tech.stpnk.freedomcat.plugin.executable_path"

    fun isExe(pkg: PackageInfo): Boolean {
        if (pkg.providers?.isEmpty() == true) return false
        val provider = pkg.providers?.get(0) ?: return false
        val auth = provider.authority ?: return false
        return auth.startsWith(AUTHORITIES_PREFIX_SEKAI_EXE)
                || auth.startsWith(AUTHORITIES_PREFIX_NEKO_EXE)
    }

    fun preferExePrefix(): String {
        return AUTHORITIES_PREFIX_NEKO_EXE
    }

    fun isUsingMatsuriExe(pluginId: String): Boolean {
        getPlugin(pluginId)?.apply {
            if (authority.startsWith(AUTHORITIES_PREFIX_NEKO_EXE)) {
                return true
            }
        }
        return false;
    }

    fun getPlugin(pluginId: String): ProviderInfo? {
        if (pluginId.isBlank()) return null
        getPluginExternal(pluginId)?.let { return it }
        // internal so
        return ProviderInfo().apply { authority = AUTHORITIES_PREFIX_NEKO_EXE }
    }

    fun getPluginExternal(pluginId: String): ProviderInfo? {
        if (pluginId.isBlank()) return null

        // try queryIntentContentProviders
        var providers = getExtPluginOld(pluginId)

        // try PackageCache
        if (providers.isEmpty()) providers = getExtPluginNew(pluginId)

        // not found
        if (providers.isEmpty()) return null

        if (providers.size > 1) {
            val prefer = providers.filter {
                it.authority.startsWith(preferExePrefix())
            }
            if (prefer.size == 1) providers = prefer
        }

        if (providers.size > 1) {
            val message =
                "Conflicting plugins found from: ${providers.joinToString { it.packageName }}"
            Toast.makeText(FreedomCatApp.application, message, Toast.LENGTH_LONG).show()
        }

        return providers[0]
    }

    private fun getExtPluginNew(pluginId: String): List<ProviderInfo> {
        PackageCache.awaitLoadSync()
        val pkgs = PackageCache.installedPluginPackages
            .map { it.value }
            .filter { it.providers?.get(0)?.loadString(METADATA_KEY_ID) == pluginId }
        return pkgs.mapNotNull { it.providers?.get(0) }
    }

    private fun buildUri(id: String, auth: String) = Uri.Builder()
        .scheme("plugin")
        .authority(auth)
        .path("/$id")
        .build()

    private fun getExtPluginOld(pluginId: String): List<ProviderInfo> {
        var flags = PackageManager.GET_META_DATA
        if (Build.VERSION.SDK_INT >= 24) {
            flags =
                flags or PackageManager.MATCH_DIRECT_BOOT_UNAWARE or PackageManager.MATCH_DIRECT_BOOT_AWARE
        }
        val list1 = FreedomCatApp.application.packageManager.queryIntentContentProviders(
            Intent(ACTION_NATIVE_PLUGIN, buildUri(pluginId, "tech.stpnk.freedomcat")), flags
        )
        val list2 = FreedomCatApp.application.packageManager.queryIntentContentProviders(
            Intent(ACTION_NATIVE_PLUGIN, buildUri(pluginId, "moe.matsuri.lite")), flags
        )
        return (list1 + list2).mapNotNull {
            it.providerInfo
        }.filter { it.exported }
    }
}
