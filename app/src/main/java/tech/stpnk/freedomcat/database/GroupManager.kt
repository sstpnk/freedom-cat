package tech.stpnk.freedomcat.database

import tech.stpnk.freedomcat.GroupType
import tech.stpnk.freedomcat.bg.SubscriptionUpdater
import tech.stpnk.freedomcat.ktx.applyDefaultValues

object GroupManager {

    interface Listener {
        suspend fun groupAdd(group: ProxyGroup)
        suspend fun groupUpdated(group: ProxyGroup)

        suspend fun groupRemoved(groupId: Long)
        suspend fun groupUpdated(groupId: Long)
    }

    interface Interface {
        suspend fun confirm(message: String): Boolean
        suspend fun alert(message: String)
        suspend fun onUpdateSuccess(
            group: ProxyGroup,
            changed: Int,
            added: List<String>,
            updated: Map<String, String>,
            deleted: List<String>,
            duplicate: List<String>,
            byUser: Boolean
        )

        suspend fun onUpdateFailure(group: ProxyGroup, message: String)
    }

    private val listeners = ArrayList<Listener>()
    var userInterface: Interface? = null

    suspend fun iterator(what: suspend Listener.() -> Unit) {
        synchronized(listeners) {
            listeners.toList()
        }.forEach { listener ->
            what(listener)
        }
    }

    fun addListener(listener: Listener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: Listener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    suspend fun clearGroup(groupId: Long) {
        DataStore.selectedProxy = 0L
        AppDatabase.proxyDao.deleteAll(groupId)
        iterator { groupUpdated(groupId) }
    }

    fun rearrange(groupId: Long) {
        val entities = AppDatabase.proxyDao.getByGroup(groupId)
        for (index in entities.indices) {
            entities[index].userOrder = (index + 1).toLong()
        }
        AppDatabase.proxyDao.updateProxy(entities)
    }

    suspend fun postUpdate(group: ProxyGroup) {
        iterator { groupUpdated(group) }
    }

    suspend fun postUpdate(groupId: Long) {
        postUpdate(AppDatabase.groupDao.getById(groupId) ?: return)
    }

    suspend fun postReload(groupId: Long) {
        iterator { groupUpdated(groupId) }
    }

    suspend fun createGroup(group: ProxyGroup): ProxyGroup {
        group.userOrder = AppDatabase.groupDao.nextOrder() ?: 1
        group.id = AppDatabase.groupDao.createGroup(group.applyDefaultValues())
        iterator { groupAdd(group) }
        if (group.type == GroupType.SUBSCRIPTION) {
            SubscriptionUpdater.reconfigureUpdater()
        }
        return group
    }

    suspend fun updateGroup(group: ProxyGroup) {
        AppDatabase.groupDao.updateGroup(group)
        iterator { groupUpdated(group) }
        if (group.type == GroupType.SUBSCRIPTION) {
            SubscriptionUpdater.reconfigureUpdater()
        }
    }

    suspend fun updateAllGroupOrder(order: Int) {
        val groups = AppDatabase.groupDao.allGroups().filter { it.order != order }
        if (groups.isEmpty()) return
        groups.forEach { it.order = order }
        AppDatabase.groupDao.updateGroup(groups)
        iterator { groupUpdated(0L) }
    }

    suspend fun deleteGroup(groupId: Long) {
        val selectedProfile = AppDatabase.proxyDao.getById(DataStore.selectedProxy)
        if (selectedProfile?.groupId == groupId) {
            DataStore.selectedProxy = 0L
        }
        AppDatabase.groupDao.deleteById(groupId)
        AppDatabase.proxyDao.deleteByGroup(groupId)
        if (DataStore.selectedGroup == groupId) {
            DataStore.selectedGroup = DataStore.currentGroupId()
        }
        iterator { groupRemoved(groupId) }
        SubscriptionUpdater.reconfigureUpdater()
    }

    suspend fun deleteGroup(group: List<ProxyGroup>) {
        val deletedGroupIds = group.map { it.id }.toLongArray()
        val selectedProfile = AppDatabase.proxyDao.getById(DataStore.selectedProxy)
        if (selectedProfile != null && selectedProfile.groupId in deletedGroupIds) {
            DataStore.selectedProxy = 0L
        }
        AppDatabase.groupDao.deleteGroup(group)
        AppDatabase.proxyDao.deleteByGroup(deletedGroupIds)
        if (DataStore.selectedGroup in deletedGroupIds) {
            DataStore.selectedGroup = DataStore.currentGroupId()
        }
        for (proxyGroup in group) iterator { groupRemoved(proxyGroup.id) }
        SubscriptionUpdater.reconfigureUpdater()
    }

}
