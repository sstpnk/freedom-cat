package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.preference.PreferenceDataStore
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.proto.UrlTest
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.databinding.LayoutGroupItemBinding
import io.nekohasekai.sagernet.databinding.LayoutProgressListBinding
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.toUniversalLink
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.getColour
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnLifecycleDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ktx.scrollTo
import io.nekohasekai.sagernet.ktx.showAllowingStateLoss
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.ktx.tryToShow
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.ui.profile.ChainSettingsActivity
import io.nekohasekai.sagernet.ui.profile.HttpSettingsActivity
import io.nekohasekai.sagernet.ui.profile.HysteriaSettingsActivity
import io.nekohasekai.sagernet.ui.profile.MieruSettingsActivity
import io.nekohasekai.sagernet.ui.profile.NaiveSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SSHSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowsocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TrojanGoSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TrojanSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TuicSettingsActivity
import io.nekohasekai.sagernet.ui.profile.VMessSettingsActivity
import io.nekohasekai.sagernet.ui.profile.WireGuardSettingsActivity
import io.nekohasekai.sagernet.widget.QRCodeDialog
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.matsuri.nb4a.Protocols
import moe.matsuri.nb4a.Protocols.getProtocolColor
import moe.matsuri.nb4a.proxy.anytls.AnyTLSSettingsActivity
import moe.matsuri.nb4a.proxy.config.ConfigSettingActivity
import moe.matsuri.nb4a.proxy.shadowtls.ShadowTLSSettingsActivity
import moe.matsuri.nb4a.ui.ConnectionTestNotification
import moe.matsuri.nb4a.utils.Util
import moe.matsuri.nb4a.utils.toBytesString
import okhttp3.internal.closeQuietly
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream

class ConfigurationFragment @JvmOverloads constructor(
    val select: Boolean = false, val selectedItem: ProxyEntity? = null, val titleRes: Int = 0
) : ToolbarFragment(R.layout.layout_group_list),
    PopupMenu.OnMenuItemClickListener,
    Toolbar.OnMenuItemClickListener,
    SearchView.OnQueryTextListener,
    OnPreferenceDataStoreChangeListener {

    interface SelectCallback {
        fun returnProfile(profileId: Long)
    }

    lateinit var adapter: GroupListAdapter
    lateinit var configurationListView: RecyclerView

    val alwaysShowAddress by lazy { DataStore.alwaysShowAddress }

    override fun onQueryTextChange(query: String): Boolean {
        adapter.filter(query)
        return false
    }

    override fun onQueryTextSubmit(query: String): Boolean = false

    @SuppressLint("DetachAndAttachSameFragment")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            parentFragmentManager.beginTransaction()
                .setReorderingAllowed(false)
                .detach(this)
                .attach(this)
                .commit()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!select) {
            toolbar.inflateMenu(R.menu.add_profile_menu)
            toolbar.setOnMenuItemClickListener(this)
        } else {
            toolbar.setTitle(titleRes)
            toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
            toolbar.setNavigationOnClickListener {
                requireActivity().finish()
            }
        }

        val searchView = toolbar.findViewById<SearchView>(R.id.action_search)
        if (searchView != null) {
            searchView.setOnQueryTextListener(this)
            searchView.maxWidth = Int.MAX_VALUE

            searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    cancelSearch(searchView)
                }
            }
        }

        configurationListView = view.findViewById(R.id.configuration_list)
        configurationListView.layoutManager = FixedLinearLayoutManager(configurationListView)
        adapter = GroupListAdapter()
        ProfileManager.addListener(adapter)
        GroupManager.addListener(adapter)
        configurationListView.adapter = adapter
        configurationListView.setItemViewCacheSize(20)

        if (!select) {

            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START
            ) {
                override fun getSwipeDirs(
                    recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
                ): Int {
                    val item = adapter.getItemAt(viewHolder.bindingAdapterPosition) ?: return 0
                    if (item is Item.GroupItem) {
                        if (item.group.ungrouped || item.group.id in GroupUpdater.updating) {
                            return 0
                        }
                    }
                    return super.getSwipeDirs(recyclerView, viewHolder)
                }

                override fun getDragDirs(
                    recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder
                ): Int {
                    val item = adapter.getItemAt(viewHolder.bindingAdapterPosition) ?: return 0
                    if (item !is Item.GroupItem) return 0
                    if (item.group.ungrouped || item.group.id in GroupUpdater.updating) {
                        return 0
                    }
                    return super.getDragDirs(recyclerView, viewHolder)
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    val index = viewHolder.bindingAdapterPosition
                    val item = adapter.getItemAt(index) ?: return
                    when (item) {
                        is Item.GroupItem -> {
                            adapter.removeGroup(index)
                            undoManager.remove(index to item.group)
                        }

                        is Item.ProfileItem -> {
                            adapter.removeProfile(index)
                            undoManager.remove(index to item.profile)
                        }
                    }
                }

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder,
                ): Boolean {
                    adapter.moveGroup(
                        viewHolder.bindingAdapterPosition, target.bindingAdapterPosition
                    )
                    return true
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) {
                    super.clearView(recyclerView, viewHolder)
                    adapter.commitMoveGroup()
                }
            }).attachToRecyclerView(configurationListView)

        }

        toolbar.setOnClickListener {
            adapter.scrollToSelected()

            if (adapter.selectedProfileIndex() == -1) {
                configurationListView.scrollTo(0)
            }
        }

        DataStore.profileCacheStore.registerChangeListener(this)
    }

    override fun onResume() {
        super.onResume()
        if (!select) checkOrderMenu()
    }

    private fun checkOrderMenu() {
        val group = DataStore.currentGroup()

        val menu = toolbar.menu
        val origin = menu.findItem(R.id.action_order_origin)
        val byName = menu.findItem(R.id.action_order_by_name)
        val byDelay = menu.findItem(R.id.action_order_by_delay)
        if (origin == null || byName == null || byDelay == null) return

        when (group.order) {
            GroupOrder.ORIGIN -> {
                origin.isChecked = true
            }

            GroupOrder.BY_NAME -> {
                byName.isChecked = true
            }

            GroupOrder.BY_DELAY -> {
                byDelay.isChecked = true
            }
        }

        fun updateTo(order: Int) {
            if (adapter.groupList.isNotEmpty() && adapter.groupList.all { it.order == order }) return
            runOnDefaultDispatcher {
                GroupManager.updateAllGroupOrder(order)
            }
        }

        origin.setOnMenuItemClickListener {
            it.isChecked = true
            updateTo(GroupOrder.ORIGIN)
            true
        }
        byName.setOnMenuItemClickListener {
            it.isChecked = true
            updateTo(GroupOrder.BY_NAME)
            true
        }
        byDelay.setOnMenuItemClickListener {
            it.isChecked = true
            updateTo(GroupOrder.BY_DELAY)
            true
        }
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        runOnMainDispatcher {
            // editingGroup
            if (key == Key.PROFILE_GROUP) {
                val targetId = DataStore.editingGroup
                if (targetId > 0 && targetId != DataStore.selectedGroup) {
                    DataStore.selectedGroup = targetId
                    adapter.expand(targetId)
                }
            }
        }
    }

    override fun onDestroy() {
        DataStore.profileCacheStore.unregisterChangeListener(this)

        if (::adapter.isInitialized) {
            GroupManager.removeListener(adapter)
            ProfileManager.removeListener(adapter)
        }

        super.onDestroy()

        undoManager.flush()
    }

    override fun onKeyDown(ketCode: Int, event: KeyEvent): Boolean {
        configurationListView.apply {
            if (!hasFocus()) requestFocus()
        }
        return super.onKeyDown(ketCode, event)
    }

    private val importFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
            if (file != null) runOnDefaultDispatcher {
                try {
                    val fileName =
                        requireContext().contentResolver.query(file, null, null, null, null)
                            ?.use { cursor ->
                                cursor.moveToFirst()
                                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                                    .let(cursor::getString)
                            }
                    val proxies = mutableListOf<AbstractBean>()
                    if (fileName != null && fileName.endsWith(".zip")) {
                        // try parse wireguard zip
                        val zip =
                            ZipInputStream(requireContext().contentResolver.openInputStream(file)!!)
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.isDirectory) continue
                            val fileText = zip.bufferedReader().readText()
                            RawUpdater.parseRaw(fileText, entry.name)
                                ?.let { pl -> proxies.addAll(pl) }
                            zip.closeEntry()
                        }
                        zip.closeQuietly()
                    } else {
                        val fileText =
                            requireContext().contentResolver.openInputStream(file)!!.use {
                                it.bufferedReader().readText()
                            }
                        RawUpdater.parseRaw(fileText, fileName ?: "")
                            ?.let { pl -> proxies.addAll(pl) }
                    }
                    if (proxies.isEmpty()) onMainDispatcher {
                        snackbar(getString(R.string.no_proxies_found_in_file)).show()
                    } else import(proxies)
                } catch (e: SubscriptionFoundException) {
                    (requireActivity() as MainActivity).importSubscription(e.link.toUri())
                } catch (e: Exception) {
                    Logs.w(e)
                    onMainDispatcher {
                        snackbar(e.readableMessage).show()
                    }
                }
            }
        }

    suspend fun import(proxies: List<AbstractBean>) {
        val targetId = DataStore.selectedGroupForImport()
        for (proxy in proxies) {
            ProfileManager.createProfile(targetId, proxy)
        }
        onMainDispatcher {
            DataStore.editingGroup = targetId
            snackbar(
                requireContext().resources.getQuantityString(
                    R.plurals.added, proxies.size, proxies.size
                )
            ).show()
        }

    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_scan_qr_code -> {
                startActivity(Intent(context, ScannerActivity::class.java))
            }

            R.id.action_import_clipboard -> {
                val text = SagerNet.getClipboardText()
                if (text.isBlank()) {
                    snackbar(getString(R.string.clipboard_empty)).show()
                } else runOnDefaultDispatcher {
                    try {
                        val proxies = RawUpdater.parseRaw(text)
                        if (proxies.isNullOrEmpty()) onMainDispatcher {
                            snackbar(getString(R.string.no_proxies_found_in_clipboard)).show()
                        } else import(proxies)
                    } catch (e: SubscriptionFoundException) {
                        (requireActivity() as MainActivity).importSubscription(e.link.toUri())
                    } catch (e: Exception) {
                        Logs.w(e)

                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }
                }
            }

            R.id.action_import_file -> {
                startFilesForResult(importFile, "*/*")
            }

            R.id.action_new_group -> {
                startActivity(Intent(requireActivity(), GroupSettingsActivity::class.java))
            }

            R.id.action_new_socks -> {
                startActivity(Intent(requireActivity(), SocksSettingsActivity::class.java))
            }

            R.id.action_new_http -> {
                startActivity(Intent(requireActivity(), HttpSettingsActivity::class.java))
            }

            R.id.action_new_ss -> {
                startActivity(Intent(requireActivity(), ShadowsocksSettingsActivity::class.java))
            }

            R.id.action_new_vmess -> {
                startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java))
            }

            R.id.action_new_vless -> {
                startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java).apply {
                    putExtra("vless", true)
                })
            }

            R.id.action_new_trojan -> {
                startActivity(Intent(requireActivity(), TrojanSettingsActivity::class.java))
            }

            R.id.action_new_trojan_go -> {
                startActivity(Intent(requireActivity(), TrojanGoSettingsActivity::class.java))
            }

            R.id.action_new_mieru -> {
                startActivity(Intent(requireActivity(), MieruSettingsActivity::class.java))
            }

            R.id.action_new_naive -> {
                startActivity(Intent(requireActivity(), NaiveSettingsActivity::class.java))
            }

            R.id.action_new_hysteria -> {
                startActivity(Intent(requireActivity(), HysteriaSettingsActivity::class.java))
            }

            R.id.action_new_tuic -> {
                startActivity(Intent(requireActivity(), TuicSettingsActivity::class.java))
            }

            R.id.action_new_ssh -> {
                startActivity(Intent(requireActivity(), SSHSettingsActivity::class.java))
            }

            R.id.action_new_wg -> {
                startActivity(Intent(requireActivity(), WireGuardSettingsActivity::class.java))
            }

            R.id.action_new_awg -> {
                startActivity(
                    Intent(requireActivity(), WireGuardSettingsActivity::class.java)
                        .putExtra(WireGuardSettingsActivity.EXTRA_AMNEZIA, true)
                )
            }

            R.id.action_new_shadowtls -> {
                startActivity(Intent(requireActivity(), ShadowTLSSettingsActivity::class.java))
            }

            R.id.action_new_anytls -> {
                startActivity(Intent(requireActivity(), AnyTLSSettingsActivity::class.java))
            }

            R.id.action_new_config -> {
                startActivity(Intent(requireActivity(), ConfigSettingActivity::class.java))
            }

            R.id.action_new_chain -> {
                startActivity(Intent(requireActivity(), ChainSettingsActivity::class.java))
            }

            R.id.action_update_subscription -> {
                val group = DataStore.currentGroup()
                if (group.type != GroupType.SUBSCRIPTION) {
                    snackbar(R.string.group_not_subscription).show()
                    Logs.e("onMenuItemClick: Group(${group.displayName()}) is not subscription")
                } else {
                    runOnLifecycleDispatcher {
                        GroupUpdater.startUpdate(group, true)
                    }
                }
            }

            R.id.action_clear_traffic_statistics -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    if (profiles.isNotEmpty()) for (profile in profiles) {
                        if (profile.tx != 0L || profile.rx != 0L) {
                            profile.tx = 0
                            profile.rx = 0
                            toClear.add(profile)
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        ProfileManager.updateProfile(toClear)
                    }
                }
            }

            R.id.action_connection_test_clear_results -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    if (profiles.isNotEmpty()) for (profile in profiles) {
                        if (profile.status != 0) {
                            profile.status = 0
                            profile.ping = 0
                            profile.error = null
                            toClear.add(profile)
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        ProfileManager.updateProfile(toClear)
                    }
                }
            }

            R.id.action_connection_test_delete_unavailable -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    if (profiles.isNotEmpty()) for (profile in profiles) {
                        if (profile.status != 0 && profile.status != 1) {
                            toClear.add(profile)
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        onMainDispatcher {
                            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                                .setMessage(R.string.delete_confirm_prompt)
                                .setPositiveButton(R.string.yes) { _, _ ->
                                    for (profile in toClear) {
                                        adapter.removeProfileAt(profile.id)
                                    }
                                    runOnDefaultDispatcher {
                                        for (profile in toClear) {
                                            ProfileManager.deleteProfile2(
                                                profile.groupId, profile.id
                                            )
                                        }
                                    }
                                }
                                .setNegativeButton(R.string.no, null)
                                .show()
                        }
                    }
                }
            }

            R.id.action_remove_duplicate -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    val uniqueProxies = LinkedHashSet<Protocols.Deduplication>()
                    for (pf in profiles) {
                        val proxy = Protocols.Deduplication(pf.requireBean(), pf.displayType())
                        if (!uniqueProxies.add(proxy)) {
                            toClear += pf
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        onMainDispatcher {
                            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                                .setMessage(
                                    getString(R.string.delete_confirm_prompt) + "\n" +
                                            toClear.mapIndexedNotNull { index, proxyEntity ->
                                                if (index < 20) {
                                                    proxyEntity.displayName()
                                                } else if (index == 20) {
                                                    "......"
                                                } else {
                                                    null
                                                }
                                            }.joinToString("\n")
                                )
                                .setPositiveButton(R.string.yes) { _, _ ->
                                    for (profile in toClear) {
                                        adapter.removeProfileAt(profile.id)
                                    }
                                    runOnDefaultDispatcher {
                                        for (profile in toClear) {
                                            ProfileManager.deleteProfile2(
                                                profile.groupId, profile.id
                                            )
                                        }
                                    }
                                }
                                .setNegativeButton(R.string.no, null)
                                .show()
                        }
                    }
                }
            }

            R.id.action_connection_tcp_ping -> {
                pingTest(false)
            }

            R.id.action_connection_url_test -> {
                urlTest()
            }
        }
        return true
    }

    inner class TestDialog {
        val binding = LayoutProgressListBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(requireContext()).setView(binding.root)
            .setPositiveButton(R.string.minimize) { _, _ ->
                minimize()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                cancel()
            }
            .setCancelable(false)

        lateinit var cancel: () -> Unit
        lateinit var minimize: () -> Unit

        val dialogStatus = AtomicInteger(0) // 1: hidden 2: cancelled
        var notification: ConnectionTestNotification? = null

        val results: MutableSet<ProxyEntity> = ConcurrentHashMap.newKeySet()
        var proxyN = 0
        val finishedN = AtomicInteger(0)

        fun update(profile: ProxyEntity) {
            if (dialogStatus.get() != 2) {
                results.add(profile)
            }
            runOnMainDispatcher {
                val context = context ?: return@runOnMainDispatcher
                val progress = finishedN.addAndGet(1)
                val status = dialogStatus.get()
                notification?.updateNotification(
                    progress,
                    proxyN,
                    progress >= proxyN || status == 2
                )
                if (status >= 1) return@runOnMainDispatcher
                if (!isAdded) return@runOnMainDispatcher

                // refresh dialog

                var profileStatusText: String? = null
                var profileStatusColor = 0

                when (profile.status) {
                    -1 -> {
                        profileStatusText = profile.error
                        profileStatusColor = context.getColorAttr(android.R.attr.textColorSecondary)
                    }

                    0 -> {
                        profileStatusText = getString(R.string.connection_test_testing)
                        profileStatusColor = context.getColorAttr(android.R.attr.textColorSecondary)
                    }

                    1 -> {
                        profileStatusText = getString(R.string.available, profile.ping)
                        profileStatusColor = context.getColour(R.color.material_green_500)
                    }

                    2 -> {
                        profileStatusText = profile.error
                        profileStatusColor = context.getColour(R.color.material_red_500)
                    }

                    3 -> {
                        val err = profile.error ?: ""
                        val msg = Protocols.genFriendlyMsg(err)
                        profileStatusText = if (msg != err) msg else getString(R.string.unavailable)
                        profileStatusColor = context.getColour(R.color.material_red_500)
                    }
                }

                val text = SpannableStringBuilder().apply {
                    append("\n" + profile.displayName())
                    append("\n")
                    append(
                        profile.displayType(),
                        ForegroundColorSpan(context.getProtocolColor(profile.type)),
                        SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    append(" ")
                    append(
                        profileStatusText,
                        ForegroundColorSpan(profileStatusColor),
                        SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    append("\n")
                }

                binding.nowTesting.text = text
                binding.progress.text = "$progress / $proxyN"
            }
        }

    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("EXPERIMENTAL_API_USAGE")
    fun pingTest(icmpPing: Boolean) {
        if (DataStore.runningTest) return else DataStore.runningTest = true
        val test = TestDialog()
        val dialog = test.builder.show()
        val testJobs = mutableListOf<Job>()
        val group = DataStore.currentGroup()

        val mainJob = runOnDefaultDispatcher {
            val profilesList = SagerDatabase.proxyDao.getByGroup(group.id).filter {
                if (icmpPing) {
                    if (it.requireBean().canICMPing()) {
                        return@filter true
                    }
                } else {
                    if (it.requireBean().canTCPing()) {
                        return@filter true
                    }
                }
                return@filter false
            }
            test.proxyN = profilesList.size
            val profiles = ConcurrentLinkedQueue(profilesList)
            repeat(DataStore.connectionTestConcurrent) {
                testJobs.add(launch(Dispatchers.IO) {
                    while (isActive) {
                        val profile = profiles.poll() ?: break

                        profile.status = 0
                        var address = profile.requireBean().serverAddress
                        if (!address.isIpAddress()) {
                            try {
                                SagerNet.underlyingNetwork!!.getAllByName(address).apply {
                                    if (isNotEmpty()) {
                                        address = this[0].hostAddress
                                    }
                                }
                            } catch (ignored: UnknownHostException) {
                            }
                        }
                        if (!isActive) break
                        if (!address.isIpAddress()) {
                            profile.status = 2
                            profile.error = app.getString(R.string.connection_test_domain_not_found)
                            test.update(profile)
                            continue
                        }
                        try {
                            if (icmpPing) {
                                // removed
                            } else {
                                val socket =
                                    SagerNet.underlyingNetwork?.socketFactory?.createSocket()
                                        ?: Socket()
                                try {
                                    socket.soTimeout = 3000
                                    socket.bind(InetSocketAddress(0))
                                    val start = SystemClock.elapsedRealtime()
                                    socket.connect(
                                        InetSocketAddress(
                                            address, profile.requireBean().serverPort
                                        ), 3000
                                    )
                                    if (!isActive) break
                                    profile.status = 1
                                    profile.ping = (SystemClock.elapsedRealtime() - start).toInt()
                                    test.update(profile)
                                } finally {
                                    socket.closeQuietly()
                                }
                            }
                        } catch (e: Exception) {
                            if (!isActive) break
                            val message = e.readableMessage

                            if (icmpPing) {
                                profile.status = 2
                                profile.error = getString(R.string.connection_test_unreachable)
                            } else {
                                profile.status = 2
                                when {
                                    !message.contains("failed:") -> profile.error =
                                        getString(R.string.connection_test_timeout)

                                    else -> when {
                                        message.contains("ECONNREFUSED") -> {
                                            profile.error =
                                                getString(R.string.connection_test_refused)
                                        }

                                        message.contains("ENETUNREACH") -> {
                                            profile.error =
                                                getString(R.string.connection_test_unreachable)
                                        }

                                        else -> {
                                            profile.status = 3
                                            profile.error = message
                                        }
                                    }
                                }
                            }
                            test.update(profile)
                        }
                    }
                })
            }

            testJobs.joinAll()

            runOnMainDispatcher {
                test.cancel()
            }
        }
        test.cancel = {
            test.dialogStatus.set(2)
            dialog.dismiss()
            runOnDefaultDispatcher {
                mainJob.cancel()
                testJobs.forEach { it.cancel() }
                test.results.forEach {
                    try {
                        ProfileManager.updateProfile(it)
                    } catch (e: Exception) {
                        Logs.w(e)
                    }
                }
                GroupManager.postReload(DataStore.currentGroupId())
                DataStore.runningTest = false
            }
        }
        test.minimize = {
            test.dialogStatus.set(1)
            test.notification = ConnectionTestNotification(
                dialog.context,
                "[${group.displayName()}] ${getString(R.string.connection_test)}"
            )
            dialog.hide()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun urlTest() {
        if (DataStore.runningTest) return else DataStore.runningTest = true
        val test = TestDialog()
        val dialog = test.builder.show()
        val testJobs = mutableListOf<Job>()
        val group = DataStore.currentGroup()

        val mainJob = runOnDefaultDispatcher {
            val profilesList = SagerDatabase.proxyDao.getByGroup(group.id)
            test.proxyN = profilesList.size
            val profiles = ConcurrentLinkedQueue(profilesList)
            repeat(DataStore.connectionTestConcurrent) {
                testJobs.add(launch(Dispatchers.IO) {
                    val urlTest = UrlTest() // note: this is NOT in bg process
                    while (isActive) {
                        val profile = profiles.poll() ?: break
                        profile.status = 0

                        try {
                            val result = urlTest.doTest(profile)
                            profile.status = 1
                            profile.ping = result
                        } catch (e: PluginManager.PluginNotFoundException) {
                            profile.status = 2
                            profile.error = e.readableMessage
                        } catch (e: Exception) {
                            profile.status = 3
                            profile.error = e.readableMessage
                        }

                        test.update(profile)
                    }
                })
            }

            testJobs.joinAll()

            runOnMainDispatcher {
                test.cancel()
            }
        }
        test.cancel = {
            test.dialogStatus.set(2)
            dialog.dismiss()
            runOnDefaultDispatcher {
                mainJob.cancel()
                testJobs.forEach { it.cancel() }
                test.results.forEach {
                    try {
                        ProfileManager.updateProfile(it)
                    } catch (e: Exception) {
                        Logs.w(e)
                    }
                }
                GroupManager.postReload(DataStore.currentGroupId())
                DataStore.runningTest = false
            }
        }
        test.minimize = {
            test.dialogStatus.set(1)
            test.notification = ConnectionTestNotification(
                dialog.context,
                "[${group.displayName()}] ${getString(R.string.connection_test)}"
            )
            dialog.hide()
        }
    }

    private val undoManager by lazy { UndoSnackbarManager<Any>(activity as MainActivity, adapter) }

    private fun sortedProfiles(group: ProxyGroup): MutableList<ProxyEntity> {
        var newProfiles = SagerDatabase.proxyDao.getByGroup(group.id).toMutableList()
        when (group.order) {
            GroupOrder.BY_NAME -> {
                newProfiles = newProfiles.sortedBy { it.displayName() }.toMutableList()
            }

            GroupOrder.BY_DELAY -> {
                newProfiles = newProfiles.sortedBy {
                    if (it.status == 1) it.ping else 114514
                }.toMutableList()
            }
        }
        return newProfiles
    }

    sealed class Item {
        class GroupItem(val group: ProxyGroup) : Item()
        class ProfileItem(val profile: ProxyEntity) : Item()

        companion object {
            const val TYPE_GROUP = 0
            const val TYPE_PROFILE = 1
        }
    }

    inner class GroupListAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        ProfileManager.Listener,
        GroupManager.Listener,
        UndoSnackbarManager.Interface<Any> {

        val groupList = ArrayList<ProxyGroup>()
        val profilesMap = HashMap<Long, MutableList<ProxyEntity>>()
        val expanded = LinkedHashSet<Long>()
        val items = ArrayList<Item>()
        var filterQuery = ""
        var forceExpanded = false

        init {
            setHasStableIds(true)
            runOnDefaultDispatcher {
                reload()
            }
        }

        fun getItemAt(index: Int): Item? {
            return if (index in 0 until items.size) items[index] else null
        }

        override fun getItemId(position: Int): Long {
            // group.id and profile.id come from different Room tables and may collide;
            // stable IDs must be unique, so groups map to even and profiles to odd IDs.
            return when (val item = items[position]) {
                is Item.GroupItem -> item.group.id * 2
                is Item.ProfileItem -> item.profile.id * 2 + 1
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is Item.GroupItem -> Item.TYPE_GROUP
                is Item.ProfileItem -> Item.TYPE_PROFILE
            }
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == Item.TYPE_GROUP) {
                GroupHolder(LayoutGroupItemBinding.inflate(layoutInflater, parent, false))
            } else {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.layout_profile, parent, false)
                val lp = view.layoutParams as? ViewGroup.MarginLayoutParams
                    ?: RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                lp.marginStart = dp2px(16)
                view.layoutParams = lp
                ConfigurationHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            try {
                when (val item = items[position]) {
                    is Item.GroupItem -> (holder as GroupHolder).bind(item.group)
                    is Item.ProfileItem -> (holder as ConfigurationHolder).bind(item.profile)
                }
            } catch (ignored: NullPointerException) { // when group deleted
            }
        }

        private suspend fun reload() {
            var newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
            if (newGroupList.isEmpty()) {
                SagerDatabase.groupDao.createGroup(ProxyGroup(ungrouped = true))
                newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
            }
            newGroupList.find { it.ungrouped }?.let {
                if (SagerDatabase.proxyDao.countByGroup(it.id) == 0L) {
                    newGroupList.remove(it)
                }
            }

            if (select) {
                forceExpanded = true
            } else if (!forceExpanded && expanded.isEmpty()) {
                // expand the selected group at startup
                newGroupList.firstOrNull { it.id == DataStore.selectedGroup }?.let {
                    expanded.add(it.id)
                } ?: newGroupList.firstOrNull()?.let { expanded.add(it.id) }
            }

            val newProfilesMap = HashMap<Long, MutableList<ProxyEntity>>()
            for (group in newGroupList) {
                newProfilesMap[group.id] = sortedProfiles(group)
            }

            val showAll = filterQuery.isNotEmpty() || forceExpanded
            val newItems = ArrayList<Item>()
            for (group in newGroupList) {
                newItems.add(Item.GroupItem(group))
                if (showAll || expanded.contains(group.id)) {
                    val profiles = newProfilesMap[group.id] ?: continue
                    if (filterQuery.isEmpty()) {
                        for (p in profiles) newItems.add(Item.ProfileItem(p))
                    } else {
                        val lower = filterQuery
                        for (p in profiles) {
                            if (p.displayName().lowercase().contains(lower) ||
                                p.displayType().lowercase().contains(lower) ||
                                p.displayAddress().lowercase().contains(lower)
                            ) {
                                newItems.add(Item.ProfileItem(p))
                            }
                        }
                    }
                }
            }

            onMainDispatcher {
                groupList.clear()
                groupList.addAll(newGroupList)
                profilesMap.clear()
                profilesMap.putAll(newProfilesMap)
                items.clear()
                items.addAll(newItems)
                notifyDataSetChanged()
            }
        }

        private fun rebuild() {
            val showAll = filterQuery.isNotEmpty() || forceExpanded
            items.clear()
            for (group in groupList) {
                items.add(Item.GroupItem(group))
                if (showAll || expanded.contains(group.id)) {
                    val profiles = profilesMap[group.id] ?: continue
                    if (filterQuery.isEmpty()) {
                        for (p in profiles) items.add(Item.ProfileItem(p))
                    } else {
                        val lower = filterQuery
                        for (p in profiles) {
                            if (p.displayName().lowercase().contains(lower) ||
                                p.displayType().lowercase().contains(lower) ||
                                p.displayAddress().lowercase().contains(lower)
                            ) {
                                items.add(Item.ProfileItem(p))
                            }
                        }
                    }
                }
            }
            notifyDataSetChanged()
        }

        fun filter(query: String) {
            filterQuery = query.trim().lowercase()
            rebuild()
        }

        fun expand(groupId: Long) {
            expanded.add(groupId)
            DataStore.selectedGroup = groupId
            rebuild()
        }

        fun toggle(groupId: Long) {
            if (forceExpanded) return
            if (!expanded.remove(groupId)) {
                expanded.add(groupId)
            }
            DataStore.selectedGroup = groupId
            rebuild()
        }

        fun selectedProfileIndex(): Int {
            val selected = selectedItem?.id ?: DataStore.selectedProxy
            return items.indexOfFirst { it is Item.ProfileItem && it.profile.id == selected }
        }

        fun scrollToSelected() {
            val index = selectedProfileIndex()
            if (index != -1) {
                val layoutManager = configurationListView.layoutManager as LinearLayoutManager
                val first = layoutManager.findFirstVisibleItemPosition()
                val last = layoutManager.findLastVisibleItemPosition()
                if (index !in first..last) {
                    configurationListView.scrollTo(index, true)
                }
            }
        }

        fun removeGroup(index: Int) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }

        fun removeProfile(index: Int) {
            items.removeAt(index)
            notifyItemRemoved(index)
        }

        fun removeProfileAt(profileId: Long) {
            val index = items.indexOfFirst { it is Item.ProfileItem && it.profile.id == profileId }
            if (index == -1) return
            items.removeAt(index)
            notifyItemRemoved(index)
        }

        private val updatedGroups = HashSet<ProxyGroup>()

        fun moveGroup(from: Int, to: Int) {
            val fromGroup = (items[from] as? Item.GroupItem)?.group ?: return
            val toGroup = (items[to] as? Item.GroupItem)?.group ?: return
            val fromIndex = groupList.indexOf(fromGroup)
            val toIndex = groupList.indexOf(toGroup)
            if (fromIndex == -1 || toIndex == -1) return

            var previousOrder = fromGroup.userOrder
            val (step, range) = if (fromIndex < toIndex) Pair(1, fromIndex until toIndex) else Pair(
                -1, toIndex + 1 downTo fromIndex
            )
            for (i in range) {
                val next = groupList[i + step]
                val order = next.userOrder
                next.userOrder = previousOrder
                previousOrder = order
                groupList[i] = next
                updatedGroups.add(next)
            }
            fromGroup.userOrder = previousOrder
            groupList[toIndex] = fromGroup
            updatedGroups.add(fromGroup)
            rebuild()
        }

        fun commitMoveGroup() = runOnDefaultDispatcher {
            updatedGroups.forEach { SagerDatabase.groupDao.updateGroup(it) }
            updatedGroups.clear()
        }

        override fun undo(actions: List<Pair<Int, Any>>) {
            runOnDefaultDispatcher {
                reload()
            }
        }

        override fun commit(actions: List<Pair<Int, Any>>) {
            val groups = actions.filter { it.second is ProxyGroup }.map { it.second as ProxyGroup }
            val profiles = actions.filter { it.second is ProxyEntity }.map { it.second as ProxyEntity }
            if (groups.isNotEmpty()) {
                runOnDefaultDispatcher {
                    GroupManager.deleteGroup(groups)
                    reload()
                }
            }
            if (profiles.isNotEmpty()) {
                runOnDefaultDispatcher {
                    for (entity in profiles) {
                        ProfileManager.deleteProfile(entity.groupId, entity.id)
                    }
                    reload()
                }
            }
        }

        override suspend fun onAdd(profile: ProxyEntity) {
            reload()
        }

        override suspend fun onUpdated(data: TrafficData) {
            val index = items.indexOfFirst { it is Item.ProfileItem && it.profile.id == data.id }
            if (index == -1) return
            val holder = configurationListView.findViewHolderForAdapterPosition(index)
                as? ConfigurationHolder ?: return
            onMainDispatcher {
                holder.bind(holder.entity, data)
            }
        }

        override suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean) {
            val index = items.indexOfFirst { it is Item.ProfileItem && it.profile.id == profile.id }
            if (index == -1) {
                reload()
                return
            }
            profilesMap[profile.groupId]?.let { list ->
                val pIndex = list.indexOfFirst { it.id == profile.id }
                if (pIndex != -1) list[pIndex] = profile
            }
            configurationListView.post {
                items[index] = Item.ProfileItem(profile)
                notifyItemChanged(index)
            }
        }

        override suspend fun onRemoved(groupId: Long, profileId: Long) {
            reload()
        }

        override suspend fun groupAdd(group: ProxyGroup) {
            reload()
        }

        override suspend fun groupRemoved(groupId: Long) {
            expanded.remove(groupId)
            reload()
        }

        override suspend fun groupUpdated(group: ProxyGroup) {
            reload()
        }

        override suspend fun groupUpdated(groupId: Long) {
            reload()
        }

    }

    inner class GroupHolder(binding: LayoutGroupItemBinding) :
        RecyclerView.ViewHolder(binding.root),
        PopupMenu.OnMenuItemClickListener {

        lateinit var proxyGroup: ProxyGroup
        val groupName = binding.groupName
        val groupStatus = binding.groupStatus
        val groupTraffic = binding.groupTraffic
        val groupUser = binding.groupUser
        val groupExpand = binding.groupExpand
        val editButton = binding.edit
        val optionsButton = binding.options
        val updateButton = binding.groupUpdate
        val subscriptionUpdateProgress = binding.subscriptionUpdateProgress

        override fun onMenuItemClick(item: MenuItem): Boolean {

            fun export(link: String) {
                val success = SagerNet.trySetPrimaryClip(link)
                (activity as MainActivity).snackbar(
                    if (success) R.string.action_export_msg else R.string.action_export_err
                ).show()
            }

            when (item.itemId) {
                R.id.action_group_edit -> {
                    startActivity(Intent(requireActivity(), GroupSettingsActivity::class.java).apply {
                        putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, proxyGroup.id)
                    })
                }

                R.id.action_group_delete -> {
                    MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                        .setMessage(R.string.delete_group_prompt)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            runOnDefaultDispatcher {
                                GroupManager.deleteGroup(proxyGroup.id)
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }

                R.id.action_universal_qr -> {
                    QRCodeDialog(
                        proxyGroup.toUniversalLink(), proxyGroup.displayName()
                    ).showAllowingStateLoss(parentFragmentManager)
                }

                R.id.action_universal_clipboard -> {
                    export(proxyGroup.toUniversalLink())
                }

                R.id.action_export_clipboard -> {
                    runOnDefaultDispatcher {
                        val profiles = SagerDatabase.proxyDao.getByGroup(proxyGroup.id)
                        val links = profiles.joinToString("\n") { it.toStdLink(compact = true) }
                        onMainDispatcher {
                            SagerNet.trySetPrimaryClip(links)
                            snackbar(getString(R.string.copy_toast_msg)).show()
                        }
                    }
                }

                R.id.action_export_file -> {
                    startFilesForResult(exportProfiles, "profiles_${proxyGroup.displayName()}.txt")
                }

                R.id.action_clear -> {
                    MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                        .setMessage(R.string.clear_profiles_message)
                        .setPositiveButton(R.string.yes) { _, _ ->
                            runOnDefaultDispatcher {
                                GroupManager.clearGroup(proxyGroup.id)
                            }
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
            }

            return true
        }

        fun bind(group: ProxyGroup) {
            proxyGroup = group

            itemView.setOnClickListener {
                adapter.toggle(group.id)
            }

            groupExpand.animate().rotation(if (adapter.expanded.contains(group.id)) 0f else -90f)
                .setDuration(200).start()

            editButton.isGone = proxyGroup.ungrouped
            updateButton.isVisible = proxyGroup.type == GroupType.SUBSCRIPTION
            groupName.text = proxyGroup.displayName()

            editButton.setOnClickListener {
                startActivity(Intent(it.context, GroupSettingsActivity::class.java).apply {
                    putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, group.id)
                })
            }

            updateButton.setOnClickListener {
                GroupUpdater.startUpdate(proxyGroup, true)
            }

            optionsButton.setOnClickListener {
                selectedGroup = proxyGroup

                val popup = PopupMenu(requireContext(), it)
                popup.menuInflater.inflate(R.menu.group_action_menu, popup.menu)

                if (proxyGroup.type != GroupType.SUBSCRIPTION) {
                    popup.menu.removeItem(R.id.action_share_subscription)
                }
                if (proxyGroup.ungrouped) {
                    popup.menu.removeItem(R.id.action_group_edit)
                    popup.menu.removeItem(R.id.action_group_delete)
                }
                if (proxyGroup.id in GroupUpdater.updating) {
                    popup.menu.removeItem(R.id.action_group_edit)
                    popup.menu.removeItem(R.id.action_group_delete)
                    popup.menu.removeItem(R.id.action_clear)
                    popup.menu.removeItem(R.id.action_export)
                }
                popup.setOnMenuItemClickListener(this)
                popup.show()
            }

            if (proxyGroup.id in GroupUpdater.updating) {
                (groupName.parent as LinearLayout).apply {
                    setPadding(paddingLeft, dp2px(11), paddingRight, paddingBottom)
                }

                subscriptionUpdateProgress.isVisible = true

                if (!GroupUpdater.progress.containsKey(proxyGroup.id)) {
                    subscriptionUpdateProgress.isIndeterminate = true
                } else {
                    subscriptionUpdateProgress.isIndeterminate = false
                    GroupUpdater.progress[proxyGroup.id]?.let {
                        subscriptionUpdateProgress.max = it.max
                        subscriptionUpdateProgress.progress = it.progress
                    }
                }

                updateButton.isVisible = false
                editButton.isGone = true
            } else {
                (groupName.parent as LinearLayout).apply {
                    setPadding(paddingLeft, dp2px(15), paddingRight, paddingBottom)
                }

                subscriptionUpdateProgress.isVisible = false
                updateButton.isVisible = proxyGroup.type == GroupType.SUBSCRIPTION
                editButton.isGone = proxyGroup.ungrouped
            }

            val subscription = proxyGroup.subscription
            if (subscription != null && subscription.bytesUsed > 0L) { // SIP008 & Open Online Config
                groupTraffic.isVisible = true
                groupTraffic.text = if (subscription.bytesRemaining > 0L) {
                    app.getString(
                        R.string.subscription_traffic, Formatter.formatFileSize(
                            app, subscription.bytesUsed
                        ), Formatter.formatFileSize(
                            app, subscription.bytesRemaining
                        )
                    )
                } else {
                    app.getString(
                        R.string.subscription_used, Formatter.formatFileSize(
                            app, subscription.bytesUsed
                        )
                    )
                }
                groupStatus.setPadding(0)
            } else if (subscription != null && !subscription.subscriptionUserinfo.isNullOrBlank()) { // Raw
                var text = ""

                fun get(regex: String): String? {
                    return regex.toRegex().findAll(subscription.subscriptionUserinfo).mapNotNull {
                        if (it.groupValues.size > 1) it.groupValues[1] else null
                    }.firstOrNull()
                }

                try {
                    var used: Long = 0
                    get("upload=([0-9]+)")?.apply {
                        used += toLong()
                    }
                    get("download=([0-9]+)")?.apply {
                        used += toLong()
                    }
                    val total = get("total=([0-9]+)")?.toLong() ?: 0
                    val remain = total - used
                    if (used > 0 || total > 0) {
                        text += if (remain > 0) {
                            getString(
                                R.string.subscription_traffic,
                                used.toBytesString(),
                                remain.toBytesString()
                            )
                        } else {
                            getString(R.string.subscription_used, used.toBytesString())
                        }
                    }
                    get("expire=([0-9]+)")?.apply {
                        text += "\n"
                        text += getString(
                            R.string.subscription_expire,
                            Util.timeStamp2Text(this.toLong() * 1000)
                        )
                    }
                } catch (_: NumberFormatException) {
                    // ignore
                }

                if (text.isNotEmpty()) {
                    groupTraffic.isVisible = true
                    groupTraffic.text = text
                    groupStatus.setPadding(0)
                }
            } else {
                groupTraffic.isVisible = false
                groupStatus.setPadding(0, 0, 0, dp2px(4))
            }

            groupUser.text = subscription?.username ?: ""

            runOnDefaultDispatcher {
                val size = SagerDatabase.proxyDao.countByGroup(group.id)
                onMainDispatcher {
                    @Suppress("DEPRECATION") when (group.type) {
                        GroupType.BASIC -> {
                            if (size == 0L) {
                                groupStatus.setText(R.string.group_status_empty)
                            } else {
                                groupStatus.text = getString(R.string.group_status_proxies, size)
                            }
                        }

                        GroupType.SUBSCRIPTION -> {
                            groupStatus.text = if (size == 0L) {
                                getString(R.string.group_status_empty_subscription)
                            } else {
                                val date = Date(group.subscription!!.lastUpdated * 1000L)
                                getString(
                                    R.string.group_status_proxies_subscription,
                                    size,
                                    "${date.month + 1} - ${date.date}"
                                )
                            }

                        }
                    }
                }

            }

        }
    }

    private lateinit var selectedGroup: ProxyGroup

    private val exportProfiles =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) { data ->
            if (data != null) {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(selectedGroup.id)
                    val links = profiles.joinToString("\n") { it.toStdLink(compact = true) }
                    try {
                        (requireActivity() as MainActivity).contentResolver.openOutputStream(
                            data
                        )!!.bufferedWriter().use {
                            it.write(links)
                        }
                        onMainDispatcher {
                            snackbar(getString(R.string.action_export_msg)).show()
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }

                }
            }
        }

    inner class ConfigurationHolder(val view: View) : RecyclerView.ViewHolder(view),
        PopupMenu.OnMenuItemClickListener {

        lateinit var entity: ProxyEntity

        val profileName: TextView = view.findViewById(R.id.profile_name)
        val profileType: TextView = view.findViewById(R.id.profile_type)
        val profileAddress: TextView = view.findViewById(R.id.profile_address)
        val profileStatus: TextView = view.findViewById(R.id.profile_status)

        val trafficText: TextView = view.findViewById(R.id.traffic_text)
        val selectedView: LinearLayout = view.findViewById(R.id.selected_view)
        val editButton: ImageView = view.findViewById(R.id.edit)
        val shareLayout: LinearLayout = view.findViewById(R.id.share)
        val shareLayer: LinearLayout = view.findViewById(R.id.share_layer)
        val shareButton: ImageView = view.findViewById(R.id.shareIcon)
        val removeButton: ImageView = view.findViewById(R.id.remove)

        fun bind(proxyEntity: ProxyEntity, trafficData: TrafficData? = null) {
            val pf = this@ConfigurationFragment

            entity = proxyEntity

            if (select) {
                view.setOnClickListener {
                    (requireActivity() as SelectCallback).returnProfile(proxyEntity.id)
                }
            } else {
                view.setOnClickListener {
                    runOnDefaultDispatcher {
                        var update: Boolean
                        var lastSelected: Long
                        profileAccess.withLock {
                            update = DataStore.selectedProxy != proxyEntity.id
                            lastSelected = DataStore.selectedProxy
                            DataStore.selectedProxy = proxyEntity.id
                            DataStore.selectedGroup = proxyEntity.groupId
                            onMainDispatcher {
                                selectedView.visibility = View.VISIBLE
                            }
                        }

                        if (update) {
                            ProfileManager.postUpdate(lastSelected)
                            if (DataStore.serviceState.canStop && reloadAccess.tryLock()) {
                                SagerNet.reloadService()
                                reloadAccess.unlock()
                            }
                        } else if (SagerNet.isTv) {
                            if (DataStore.serviceState.started) {
                                SagerNet.stopService()
                            } else {
                                SagerNet.startService()
                            }
                        }
                    }

                }
            }

            profileName.text = proxyEntity.displayName()
            profileType.text = proxyEntity.displayType()
            profileType.setTextColor(requireContext().getProtocolColor(proxyEntity.type))

            var rx = proxyEntity.rx
            var tx = proxyEntity.tx
            if (trafficData != null) {
                // use new data
                tx = trafficData.tx
                rx = trafficData.rx
            }

            val showTraffic = rx + tx != 0L
            trafficText.isVisible = showTraffic
            if (showTraffic) {
                trafficText.text = view.context.getString(
                    R.string.traffic,
                    Formatter.formatFileSize(view.context, tx),
                    Formatter.formatFileSize(view.context, rx)
                )
            }

            var address = proxyEntity.displayAddress()
            if (showTraffic && address.length >= 30) {
                address = address.substring(0, 27) + "..."
            }

            if (proxyEntity.requireBean().name.isBlank() || !pf.alwaysShowAddress) {
                address = ""
            }

            profileAddress.text = address
            (trafficText.parent as View).isGone =
                (!showTraffic || proxyEntity.status <= 0) && address.isBlank()

            if (proxyEntity.status <= 0) {
                if (showTraffic) {
                    profileStatus.text = trafficText.text
                    profileStatus.setTextColor(
                        requireContext().getColorAttr(android.R.attr.textColorSecondary)
                    )
                    trafficText.text = ""
                } else {
                    profileStatus.text = ""
                }
            } else if (proxyEntity.status == 1) {
                profileStatus.text = getString(R.string.available, proxyEntity.ping)
                profileStatus.setTextColor(requireContext().getColour(R.color.material_green_500))
            } else {
                profileStatus.setTextColor(requireContext().getColour(R.color.material_red_500))
                if (proxyEntity.status == 2) {
                    profileStatus.text = proxyEntity.error
                }
            }

            if (proxyEntity.status == 3) {
                val err = proxyEntity.error ?: "<?>"
                val msg = Protocols.genFriendlyMsg(err)
                profileStatus.text = if (msg != err) msg else getString(R.string.unavailable)
                profileStatus.setOnClickListener {
                    alert(err).tryToShow()
                }
            } else {
                profileStatus.setOnClickListener(null)
            }

            editButton.setOnClickListener {
                it.context.startActivity(
                    proxyEntity.settingIntent(
                        it.context, proxyGroupType() == GroupType.SUBSCRIPTION
                    )
                )
            }
            removeButton.setOnClickListener {
                val index = adapter.items.indexOfFirst { item ->
                    item is Item.ProfileItem && item.profile.id == proxyEntity.id
                }
                if (index == -1) return@setOnClickListener
                adapter.removeProfile(index)
                undoManager.remove(index to proxyEntity)
            }

            val selectOrChain = select || proxyEntity.type == ProxyEntity.TYPE_CHAIN
            shareLayout.isGone = selectOrChain
            editButton.isGone = select
            removeButton.isGone = select

            proxyEntity.nekoBean?.apply {
                shareLayout.isGone = true
            }

            runOnDefaultDispatcher {
                val selected = (selectedItem?.id ?: DataStore.selectedProxy) == proxyEntity.id
                val started =
                    selected && DataStore.serviceState.started && DataStore.currentProfile == proxyEntity.id
                onMainDispatcher {
                    editButton.isEnabled = !started
                    removeButton.isEnabled = !started
                    selectedView.visibility = if (selected) View.VISIBLE else View.INVISIBLE
                }

                fun showShare(anchor: View) {
                    val popup = PopupMenu(requireContext(), anchor)
                    popup.menuInflater.inflate(R.menu.profile_share_menu, popup.menu)

                    when {
                        !proxyEntity.haveStandardLink() -> {
                            popup.menu.findItem(R.id.action_group_qr).subMenu?.removeItem(R.id.action_standard_qr)
                            popup.menu.findItem(R.id.action_group_clipboard).subMenu?.removeItem(
                                R.id.action_standard_clipboard
                            )
                        }

                        !proxyEntity.haveLink() -> {
                            popup.menu.removeItem(R.id.action_group_qr)
                            popup.menu.removeItem(R.id.action_group_clipboard)
                        }
                    }

                    if (proxyEntity.nekoBean != null) {
                        popup.menu.removeItem(R.id.action_group_configuration)
                    }

                    popup.setOnMenuItemClickListener(this@ConfigurationHolder)
                    popup.show()
                }

                if (!(select || proxyEntity.type == ProxyEntity.TYPE_CHAIN)) {
                    onMainDispatcher {
                        shareLayer.setBackgroundColor(Color.TRANSPARENT)
                        shareButton.setImageResource(R.drawable.ic_social_share)
                        shareButton.setColorFilter(Color.GRAY)
                        shareButton.isVisible = true

                        shareLayout.setOnClickListener {
                            showShare(it)
                        }
                    }
                }
            }

        }

        fun proxyGroupType(): Int {
            return SagerDatabase.groupDao.getById(entity.groupId)?.type ?: GroupType.BASIC
        }

        var currentName = ""
        fun showCode(link: String) {
            QRCodeDialog(link, currentName).showAllowingStateLoss(parentFragmentManager)
        }

        fun export(link: String) {
            val success = SagerNet.trySetPrimaryClip(link)
            (activity as MainActivity).snackbar(if (success) R.string.action_export_msg else R.string.action_export_err)
                .show()
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            try {
                currentName = entity.displayName()!!
                when (item.itemId) {
                    R.id.action_standard_qr -> showCode(entity.toStdLink())
                    R.id.action_standard_clipboard -> export(entity.toStdLink())
                    R.id.action_universal_qr -> showCode(entity.requireBean().toUniversalLink())
                    R.id.action_universal_clipboard -> export(
                        entity.requireBean().toUniversalLink()
                    )

                    R.id.action_config_export_clipboard -> export(entity.exportConfig().first)
                    R.id.action_config_export_file -> {
                        val cfg = entity.exportConfig()
                        DataStore.serverConfig = cfg.first
                        startFilesForResult(
                            this@ConfigurationFragment.exportConfig, cfg.second
                        )
                    }
                }
            } catch (e: Exception) {
                Logs.w(e)
                (activity as MainActivity).snackbar(e.readableMessage).show()
                return true
            }
            return true
        }
    }

    val profileAccess = Mutex()
    val reloadAccess = Mutex()

    private val exportConfig =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) { data ->
            if (data != null) {
                runOnDefaultDispatcher {
                    try {
                        (requireActivity() as MainActivity).contentResolver.openOutputStream(data)!!
                            .bufferedWriter()
                            .use {
                                it.write(DataStore.serverConfig)
                            }
                        onMainDispatcher {
                            snackbar(getString(R.string.action_export_msg)).show()
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }

                }
            }
        }

    private fun cancelSearch(searchView: SearchView) {
        searchView.onActionViewCollapsed()
        searchView.clearFocus()
    }

}
