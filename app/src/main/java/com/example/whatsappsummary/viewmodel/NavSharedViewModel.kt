package com.example.whatsappsummary.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * Estado compartido entre [com.example.whatsappsummary.ui.ChatsListFragment] y
 * [com.example.whatsappsummary.ui.DashboardFragment] dentro del mismo ViewPager.
 *
 * Sustituye los Intent extras que antes usaban MainActivity y DashboardActivity
 * para comunicarse (FILTER_PACKAGE, CLEAR_FILTERS).
 */
class NavSharedViewModel : ViewModel() {
    /** Solicitud de la página: -1 = sin solicitud, 0 = dashboard (home), 1 = chats. */
    val pageRequest = MutableLiveData<Int>(-1)

    data class ChatFilterRequest(
        val packageName: String?,
        val appName: String?,
        val clearFilters: Boolean = false
    )

    /** Petición desde Dashboard → Chats para aplicar filtro por app. */
    val chatFilterRequest = MutableLiveData<ChatFilterRequest?>(null)

    fun requestShowChats(packageName: String?, appName: String?) {
        chatFilterRequest.value = ChatFilterRequest(packageName, appName, clearFilters = false)
        pageRequest.value = 1
    }

    fun requestShowChatsClearFilters() {
        chatFilterRequest.value = ChatFilterRequest(null, null, clearFilters = true)
        pageRequest.value = 1
    }

    fun consumeChatFilter(): ChatFilterRequest? {
        val r = chatFilterRequest.value
        chatFilterRequest.value = null
        return r
    }

    fun consumePageRequest(): Int {
        val r = pageRequest.value ?: -1
        pageRequest.value = -1
        return r
    }
}
