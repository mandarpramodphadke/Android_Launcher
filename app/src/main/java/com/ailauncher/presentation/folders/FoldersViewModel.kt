package com.ailauncher.presentation.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.FolderAccent
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.DockRepository
import com.ailauncher.domain.repository.FolderRepository
import com.ailauncher.domain.repository.HomeLayoutRepository
import com.ailauncher.presentation.components.HomeGridMetrics
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale
import javax.inject.Inject

/** Create, edit, open and place custom folders (shared by the home screen, drawer and assistant). */
@HiltViewModel
class FoldersViewModel @Inject constructor(
    private val folderRepository: FolderRepository,
    private val dockRepository: DockRepository,
    private val homeLayout: HomeLayoutRepository,
    appRepository: AppRepository,
) : ViewModel() {

    val folders: StateFlow<List<CustomFolder>> =
        folderRepository.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Visible apps sorted by name, for the folder editor's checklist. */
    val apps: StateFlow<List<AppInfo>> = appRepository.apps.map { all ->
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        all.filter { !it.isHidden }.distinctBy { it.packageName }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** [placeOnHome]: a new folder also goes onto the current home page (created from the home menu). */
    fun save(
        existing: CustomFolder?,
        name: String,
        description: String?,
        accent: FolderAccent,
        packageNames: List<String>,
        placeOnHome: Boolean = false,
        onSaved: (Long) -> Unit = {},
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = if (existing == null) {
                folderRepository.create(trimmed, description, accent, packageNames).also { id ->
                    if (placeOnHome && homeLayout.addFolder(id, HomeGridMetrics.currentPage, HomeGridMetrics.spec) == null) {
                        _messages.send("Created $trimmed. The home screen is full, so it's in the drawer's Folders tab")
                    }
                }
            } else {
                folderRepository.update(existing.id, trimmed, description, accent)
                folderRepository.setApps(existing.id, packageNames)
                existing.id
            }
            onSaved(id)
        }
    }

    fun removeApp(folder: CustomFolder, packageName: String) {
        viewModelScope.launch { folderRepository.setApps(folder.id, folder.packageNames - packageName) }
    }

    fun delete(folder: CustomFolder) {
        viewModelScope.launch {
            folderRepository.delete(folder.id)
            _messages.send("Deleted ${folder.name} (its apps stay installed)")
        }
    }

    fun toggleDock(folder: CustomFolder) {
        viewModelScope.launch {
            when {
                folder.isInDock -> dockRepository.unpinFolder(folder.id)
                dockRepository.pinFolder(folder.id) -> _messages.send("Pinned ${folder.name} to the dock")
                else -> _messages.send("The dock is full (${DockRepository.MAX_SIZE} slots)")
            }
        }
    }

    fun addToHome(folder: CustomFolder) {
        viewModelScope.launch {
            val position = homeLayout.addFolder(folder.id, HomeGridMetrics.currentPage, HomeGridMetrics.spec)
            _messages.send(if (position != null) "Added ${folder.name} to page ${position.page + 1}" else "The home screen is full")
        }
    }
}
