package tech.stpnk.freedomcat.ui

import android.net.Uri
import android.os.Bundle
import android.view.View
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import tech.stpnk.freedomcat.GroupType
import tech.stpnk.freedomcat.R
import tech.stpnk.freedomcat.SagerNet
import tech.stpnk.freedomcat.database.DataStore
import tech.stpnk.freedomcat.database.GroupManager
import tech.stpnk.freedomcat.database.ProfileManager
import tech.stpnk.freedomcat.database.ProxyGroup
import tech.stpnk.freedomcat.database.SubscriptionBean
import tech.stpnk.freedomcat.fmt.KryoConverters
import tech.stpnk.freedomcat.group.GroupUpdater
import tech.stpnk.freedomcat.group.RawUpdater
import tech.stpnk.freedomcat.ktx.Logs
import tech.stpnk.freedomcat.ktx.SubscriptionFoundException
import tech.stpnk.freedomcat.ktx.onMainDispatcher
import tech.stpnk.freedomcat.ktx.readableMessage
import tech.stpnk.freedomcat.ktx.runOnDefaultDispatcher
import tech.stpnk.freedomcat.core.utils.Util
import java.net.URLDecoder

class TvImportFragment : androidx.fragment.app.Fragment(R.layout.layout_tv_import) {

    private lateinit var inputLayout: TextInputLayout
    private lateinit var linkInput: TextInputEditText

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        inputLayout = view.findViewById(R.id.link_input_layout)
        linkInput = view.findViewById(R.id.link_input)

        view.findViewById<MaterialButton>(R.id.import_button).setOnClickListener {
            importText(linkInput.text?.toString().orEmpty())
        }
        view.findViewById<MaterialButton>(R.id.clipboard_button).setOnClickListener {
            val text = SagerNet.getClipboardText()
            if (text.isBlank()) {
                mainActivity().snackbar(R.string.clipboard_empty).show()
            } else {
                linkInput.setText(text)
                linkInput.setSelection(text.length)
            }
        }
        view.findViewById<MaterialButton>(R.id.full_list_button).setOnClickListener {
            mainActivity().displayConfigurationList()
        }

        linkInput.requestFocus()
    }

    private fun importText(rawText: String) {
        val text = rawText.trim()
        inputLayout.error = null

        if (text.isBlank()) {
            inputLayout.error = getString(R.string.tv_import_empty)
            return
        }

        runOnDefaultDispatcher {
            try {
                val profiles = RawUpdater.parseRaw(text)
                if (profiles.isNullOrEmpty()) {
                    onMainDispatcher {
                        inputLayout.error = getString(R.string.no_proxies_found)
                    }
                    return@runOnDefaultDispatcher
                }

                val targetGroupId = DataStore.selectedGroupForImport()
                profiles.forEach { ProfileManager.createProfile(targetGroupId, it) }

                onMainDispatcher {
                    mainActivity().snackbar(
                        resources.getQuantityString(R.plurals.added, profiles.size, profiles.size)
                    ).show()
                    mainActivity().displayFragment(TvHomeFragment())
                }
            } catch (e: SubscriptionFoundException) {
                importSubscription(e.link)
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher {
                    inputLayout.error = e.readableMessage
                }
            }
        }
    }

    private fun importSubscription(link: String) {
        runOnDefaultDispatcher {
            try {
                val group = parseSubscription(link)
                GroupManager.createGroup(group)
                GroupUpdater.startUpdate(group, true)

                onMainDispatcher {
                    mainActivity().snackbar(R.string.tv_import_subscription_added).show()
                    mainActivity().displayFragment(TvHomeFragment())
                }
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher {
                    inputLayout.error = e.readableMessage
                }
            }
        }
    }

    private fun parseSubscription(link: String): ProxyGroup {
        val uri = Uri.parse(link)
        val url = uri.getQueryParameter("url")

        if (!url.isNullOrBlank()) {
            return ProxyGroup(type = GroupType.SUBSCRIPTION).apply {
                subscription = SubscriptionBean().apply {
                    this.link = url
                }
                name = uri.getQueryParameter("name")
                    ?: runCatching { URLDecoder.decode(Uri.parse(url).host.orEmpty(), "UTF-8") }
                        .getOrNull()
                    ?: "Subscription #${System.currentTimeMillis()}"
            }
        }

        val data = uri.encodedQuery.takeIf { !it.isNullOrBlank() }
            ?: error(getString(R.string.no_proxies_found))

        return KryoConverters.deserialize(
            ProxyGroup().apply { export = true },
            Util.zlibDecompress(Util.b64Decode(data))
        ).apply {
            export = false
            name = name.takeIf { !it.isNullOrBlank() }
                ?: "Subscription #${System.currentTimeMillis()}"
        }
    }

    private fun mainActivity(): MainActivity {
        return requireActivity() as MainActivity
    }
}
