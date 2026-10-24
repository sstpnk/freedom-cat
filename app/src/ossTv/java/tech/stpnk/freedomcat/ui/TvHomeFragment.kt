package tech.stpnk.freedomcat.ui

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import tech.stpnk.freedomcat.GroupType
import tech.stpnk.freedomcat.R
import tech.stpnk.freedomcat.FreedomCatApp
import tech.stpnk.freedomcat.aidl.SpeedDisplayData
import tech.stpnk.freedomcat.aidl.TrafficData
import tech.stpnk.freedomcat.bg.BaseService
import tech.stpnk.freedomcat.database.DataStore
import tech.stpnk.freedomcat.database.GroupManager
import tech.stpnk.freedomcat.database.ProfileManager
import tech.stpnk.freedomcat.database.ProxyEntity
import tech.stpnk.freedomcat.database.ProxyGroup
import tech.stpnk.freedomcat.database.AppDatabase
import tech.stpnk.freedomcat.group.GroupUpdater
import tech.stpnk.freedomcat.ktx.Logs
import tech.stpnk.freedomcat.ktx.onMainDispatcher
import tech.stpnk.freedomcat.ktx.readableMessage
import tech.stpnk.freedomcat.ktx.runOnDefaultDispatcher
import tech.stpnk.freedomcat.ktx.runOnLifecycleDispatcher
import tech.stpnk.freedomcat.core.utils.toBytesString

class TvHomeFragment : Fragment(R.layout.layout_tv_home),
    MainActivity.StatusAwareFragment,
    ProfileManager.Listener,
    GroupManager.Listener {

    private data class TvProfile(val profile: ProxyEntity, val group: ProxyGroup)

    private lateinit var stateText: TextView
    private lateinit var selectedProfileName: TextView
    private lateinit var selectedProfileMeta: TextView
    private lateinit var selectedProfileTraffic: TextView
    private lateinit var speedText: TextView
    private lateinit var emptyText: TextView
    private lateinit var connectButton: MaterialButton
    private lateinit var profileList: RecyclerView
    private val profileAdapter = ProfileAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stateText = view.findViewById(R.id.state_text)
        selectedProfileName = view.findViewById(R.id.selected_profile_name)
        selectedProfileMeta = view.findViewById(R.id.selected_profile_meta)
        selectedProfileTraffic = view.findViewById(R.id.selected_profile_traffic)
        speedText = view.findViewById(R.id.speed_text)
        emptyText = view.findViewById(R.id.empty_text)
        connectButton = view.findViewById(R.id.connect_button)
        profileList = view.findViewById(R.id.profile_list)

        profileList.layoutManager = LinearLayoutManager(requireContext())
        profileList.adapter = profileAdapter

        connectButton.setOnClickListener {
            if (DataStore.selectedProxy == 0L) {
                mainActivity().snackbar(R.string.profile_empty).show()
            } else {
                mainActivity().toggleServiceFromUi()
            }
        }
        view.findViewById<MaterialButton>(R.id.profiles_button).setOnClickListener {
            mainActivity().displayFragment(TvImportFragment())
        }
        view.findViewById<MaterialButton>(R.id.settings_button).setOnClickListener {
            mainActivity().displayFragmentWithId(R.id.nav_settings)
        }
        view.findViewById<MaterialButton>(R.id.update_subscriptions_button).setOnClickListener {
            updateSubscriptions()
        }
        view.findViewById<MaterialButton>(R.id.connection_test_button).setOnClickListener {
            testConnection()
        }

        ProfileManager.addListener(this)
        GroupManager.addListener(this)

        onServiceStateChanged(DataStore.serviceState)
        reloadProfiles()
    }

    override fun onDestroyView() {
        GroupManager.removeListener(this)
        ProfileManager.removeListener(this)
        super.onDestroyView()
    }

    override fun onServiceStateChanged(state: BaseService.State) {
        if (!this::stateText.isInitialized) return

        stateText.setText(
            when (state) {
                BaseService.State.Connecting -> R.string.connecting
                BaseService.State.Connected -> R.string.vpn_connected
                BaseService.State.Stopping -> R.string.stopping
                else -> R.string.not_connected
            }
        )
        connectButton.setText(if (state.canStop) R.string.stop else R.string.connect)
        connectButton.isEnabled = state.canStop || state == BaseService.State.Stopped
    }

    override fun onSpeedUpdated(stats: SpeedDisplayData) {
        if (!this::speedText.isInitialized) return

        val tx = getString(R.string.speed, Formatter.formatFileSize(requireContext(), stats.txRateProxy))
        val rx = getString(R.string.speed, Formatter.formatFileSize(requireContext(), stats.rxRateProxy))
        speedText.text = "$tx ↑   $rx ↓"
    }

    override suspend fun onAdd(profile: ProxyEntity) {
        reloadProfilesOnMain()
    }

    override suspend fun onUpdated(data: TrafficData) {
        reloadProfilesOnMain()
    }

    override suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean) {
        reloadProfilesOnMain()
    }

    override suspend fun onRemoved(groupId: Long, profileId: Long) {
        reloadProfilesOnMain()
    }

    override suspend fun groupAdd(group: ProxyGroup) {
        reloadProfilesOnMain()
    }

    override suspend fun groupUpdated(group: ProxyGroup) {
        reloadProfilesOnMain()
    }

    override suspend fun groupRemoved(groupId: Long) {
        reloadProfilesOnMain()
    }

    override suspend fun groupUpdated(groupId: Long) {
        reloadProfilesOnMain()
    }

    private suspend fun reloadProfilesOnMain() {
        onMainDispatcher {
            if (view != null) reloadProfiles()
        }
    }

    private fun reloadProfiles() {
        runOnLifecycleDispatcher {
            val groups = AppDatabase.groupDao.allGroups()
            val profiles = groups.flatMap { group ->
                AppDatabase.proxyDao.getByGroup(group.id).map { TvProfile(it, group) }
            }

            onMainDispatcher {
                profileAdapter.setItems(profiles)
                emptyText.isVisible = profiles.isEmpty()
                profileList.isVisible = profiles.isNotEmpty()
                updateSelectedProfile(profiles)
            }
        }
    }

    private fun updateSelectedProfile(profiles: List<TvProfile>) {
        val selected = profiles.firstOrNull { it.profile.id == DataStore.selectedProxy }

        if (selected == null) {
            selectedProfileName.setText(R.string.tv_home_no_profile)
            selectedProfileMeta.text = ""
            selectedProfileTraffic.text = ""
            profileAdapter.selectedId = 0L
            profileAdapter.notifyDataSetChanged()
            return
        }

        selectedProfileName.text = selected.profile.displayName()
        selectedProfileMeta.text = "${selected.profile.displayType()} / ${selected.group.displayName()}"
        selectedProfileTraffic.text = formatTraffic(selected.profile)
        profileAdapter.selectedId = selected.profile.id
        profileAdapter.notifyDataSetChanged()
    }

    private fun selectProfile(item: TvProfile) {
        val previous = DataStore.selectedProxy
        DataStore.selectedProxy = item.profile.id
        DataStore.selectedGroup = item.profile.groupId
        updateSelectedProfile(profileAdapter.items)

        runOnDefaultDispatcher {
            ProfileManager.postUpdate(previous)
            ProfileManager.postUpdate(item.profile.id)
            if (DataStore.serviceState.canStop) {
                FreedomCatApp.reloadService()
            }
        }
    }

    private fun updateSubscriptions() {
        runOnDefaultDispatcher {
            val subscriptions = AppDatabase.groupDao.allGroups()
                .filter { it.type == GroupType.SUBSCRIPTION }

            onMainDispatcher {
                if (subscriptions.isEmpty()) {
                    mainActivity().snackbar(R.string.tv_home_no_subscriptions).show()
                } else {
                    subscriptions.forEach { GroupUpdater.startUpdate(it, true) }
                    mainActivity().snackbar(R.string.tv_home_subscription_update_started).show()
                }
            }
        }
    }

    private fun testConnection() {
        if (!DataStore.serviceState.connected) {
            mainActivity().snackbar(R.string.not_connected).show()
            return
        }

        runOnDefaultDispatcher {
            try {
                val elapsed = mainActivity().urlTest()
                onMainDispatcher {
                    mainActivity().snackbar(
                        getString(R.string.connection_test_available, elapsed)
                    ).show()
                }
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher {
                    mainActivity().snackbar(
                        getString(R.string.connection_test_error, e.readableMessage)
                    ).show()
                }
            }
        }
    }

    private fun formatTraffic(profile: ProxyEntity): String {
        return if (profile.rx + profile.tx == 0L) {
            getString(R.string.tv_home_status_ready)
        } else {
            getString(R.string.traffic, profile.tx.toBytesString(), profile.rx.toBytesString())
        }
    }

    private fun mainActivity(): MainActivity {
        return requireActivity() as MainActivity
    }

    private inner class ProfileAdapter : RecyclerView.Adapter<ProfileHolder>() {
        var items: List<TvProfile> = emptyList()
            private set
        var selectedId: Long = DataStore.selectedProxy

        fun setItems(newItems: List<TvProfile>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.layout_tv_profile_item, parent, false)
            return ProfileHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ProfileHolder, position: Int) {
            holder.bind(items[position])
        }
    }

    private inner class ProfileHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val card: MaterialCardView = view.findViewById(R.id.profile_card)
        private val name: TextView = view.findViewById(R.id.profile_name)
        private val meta: TextView = view.findViewById(R.id.profile_meta)
        private val status: TextView = view.findViewById(R.id.profile_status)

        fun bind(item: TvProfile) {
            val selected = item.profile.id == profileAdapter.selectedId
            name.text = item.profile.displayName()
            meta.text = "${item.profile.displayType()} / ${item.group.displayName()}"
            status.text = if (item.profile.status == 1) {
                getString(R.string.available, item.profile.ping)
            } else {
                formatTraffic(item.profile)
            }
            card.isChecked = selected
            card.setOnClickListener { selectProfile(item) }
            card.setOnFocusChangeListener { view, hasFocus ->
                val scale = if (hasFocus) 1.03f else 1f
                view.animate().scaleX(scale).scaleY(scale).setDuration(120L).start()
            }
        }
    }
}
