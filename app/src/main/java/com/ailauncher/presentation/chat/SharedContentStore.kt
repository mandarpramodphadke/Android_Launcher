package com.ailauncher.presentation.chat

import com.ailauncher.data.share.SharedPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Hands content shared to the launcher (MainActivity) over to the assistant screen. In memory only. */
@Singleton
class SharedContentStore @Inject constructor() {
    private val _pending = MutableStateFlow<SharedPayload?>(null)
    val pending: StateFlow<SharedPayload?> = _pending.asStateFlow()

    fun offer(payload: SharedPayload) {
        _pending.value = payload
    }

    fun clear() {
        _pending.value = null
    }
}
