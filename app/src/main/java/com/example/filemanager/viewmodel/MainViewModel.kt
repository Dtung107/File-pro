package com.example.filemanager.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filemanager.archive.ArchiveManager
import com.example.filemanager.model.*
import com.example.filemanager.utils.FileOperations
import com.example.filemanager.utils.FileUtils
import com.example.filemanager.utils.SafManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class ScreenDestination {
    data object Home : ScreenDestination()
    data object Files : ScreenDestination()
    data object GameData : ScreenDestination()
    data object Analyzer : ScreenDestination()
    data object Settings : ScreenDestination()
    data class TextViewer(val file: File) : ScreenDestination()
    data class ApkInspector(val file: File) : ScreenDestination()
    data class SqliteViewer(val file: File) : ScreenDestination()
    data class HexViewer(val file: File) : ScreenDestination()
    data class ArchiveViewer(val file: File) : ScreenDestination()
    data class MediaViewer(val file: File, val type: FileType) : ScreenDestination()
    data object DualPane : ScreenDestination()
}

enum class DualPaneSplit {
    VERTICAL,   // Top / Bottom
    HORIZONTAL  // Left / Right
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("file_manager_pro_prefs", Context.MODE_PRIVATE)

    private val _currentScreen = MutableStateFlow<ScreenDestination>(ScreenDestination.Files)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    private val _navigationHistory = mutableListOf<ScreenDestination>()

    // Primary Pane Directory & Backstack
    private val _currentDirectory = MutableStateFlow<File>(Environment.getExternalStorageDirectory())
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    private val _directoryBackStack = mutableListOf<File>()

    private val _activeCategoryTitle = MutableStateFlow<String?>(null)
    val activeCategoryTitle: StateFlow<String?> = _activeCategoryTitle.asStateFlow()

    private val _fileItems = MutableStateFlow<List<FileItem>>(emptyList())
    val fileItems: StateFlow<List<FileItem>> = _fileItems.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Secondary Pane (Dual Pane / Split Screen)
    private val _isDualPaneMode = MutableStateFlow(false)
    val isDualPaneMode: StateFlow<Boolean> = _isDualPaneMode.asStateFlow()

    private val _dualPaneSplit = MutableStateFlow(DualPaneSplit.VERTICAL)
    val dualPaneSplit: StateFlow<DualPaneSplit> = _dualPaneSplit.asStateFlow()

    private val _secondaryDirectory = MutableStateFlow<File>(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            ?.takeIf { it.exists() && it.canRead() } ?: Environment.getExternalStorageDirectory()
    )
    val secondaryDirectory: StateFlow<File> = _secondaryDirectory.asStateFlow()

    private val _secondaryFileItems = MutableStateFlow<List<FileItem>>(emptyList())
    val secondaryFileItems: StateFlow<List<FileItem>> = _secondaryFileItems.asStateFlow()

    private val _secondaryBackStack = mutableListOf<File>()

    private val _secondaryLoading = MutableStateFlow(false)
    val secondaryLoading: StateFlow<Boolean> = _secondaryLoading.asStateFlow()

    private val _activePaneIndex = MutableStateFlow(0) // 0: Pane A (Primary), 1: Pane B (Secondary)
    val activePaneIndex: StateFlow<Int> = _activePaneIndex.asStateFlow()

    private val _storageVolumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val storageVolumes: StateFlow<List<StorageVolumeInfo>> = _storageVolumes.asStateFlow()

    private val _clipboard = MutableStateFlow<ClipboardState?>(null)
    val clipboard: StateFlow<ClipboardState?> = _clipboard.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<FileItem>>(emptySet())
    val selectedItems: StateFlow<Set<FileItem>> = _selectedItems.asStateFlow()

    private val _isMultiSelectMode = MutableStateFlow<Boolean>(false)
    val isMultiSelectMode: StateFlow<Boolean> = _isMultiSelectMode.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption(SortField.NAME, SortOrder.ASCENDING))
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _showHiddenFiles = MutableStateFlow(prefs.getBoolean("show_hidden_files", false))
    val showHiddenFiles: StateFlow<Boolean> = _showHiddenFiles.asStateFlow()

    private val _isGridView = MutableStateFlow(prefs.getBoolean("is_grid_view", false))
    val isGridView: StateFlow<Boolean> = _isGridView.asStateFlow()

    private val _favorites = MutableStateFlow<Set<String>>(prefs.getStringSet("favorites", emptySet()) ?: emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _recentFilePaths = MutableStateFlow<List<String>>(
        prefs.getString("recent_files", "")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    )
    val recentFilePaths: StateFlow<List<String>> = _recentFilePaths.asStateFlow()

    private val _operationProgress = MutableStateFlow(OperationProgress())
    val operationProgress: StateFlow<OperationProgress> = _operationProgress.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        refreshStorageVolumes()
        loadDirectory(_currentDirectory.value)
    }

    fun navigateTo(destination: ScreenDestination) {
        if (_currentScreen.value != destination) {
            _navigationHistory.add(_currentScreen.value)
            _currentScreen.value = destination
        }
    }

    /**
     * Proper Back navigation:
     * When inside Files (Internal Storage), retraces folder-by-folder in exact reverse order
     * rather than jumping straight out to Home!
     */
    fun navigateBack(): Boolean {
        // If in Files screen:
        if (_currentScreen.value is ScreenDestination.Files) {
            // Cancel search first if active
            if (_searchQuery.value.isNotBlank()) {
                _searchQuery.value = ""
                return true
            }

            // Exit multi-select mode if active
            if (_isMultiSelectMode.value) {
                _isMultiSelectMode.value = false
                _selectedItems.value = emptySet()
                return true
            }

            // In Dual Pane mode, if Pane B is active and has backstack, pop Pane B:
            if (_isDualPaneMode.value && _activePaneIndex.value == 1) {
                if (_secondaryBackStack.isNotEmpty()) {
                    val prev = _secondaryBackStack.removeAt(_secondaryBackStack.size - 1)
                    loadSecondaryDirectory(prev)
                    return true
                }
            }

            // If inside a Category list (e.g. Images, APK, etc.), return to normal folder view
            if (_activeCategoryTitle.value != null) {
                _activeCategoryTitle.value = null
                loadDirectory(_currentDirectory.value)
                return true
            }

            // Pop folder backstack in primary pane (retraces exact folder path entered!)
            if (_directoryBackStack.isNotEmpty()) {
                val previousDir = _directoryBackStack.removeAt(_directoryBackStack.size - 1)
                loadDirectory(previousDir)
                return true
            }

            // If backstack is empty, check if current directory is a child of root storage
            val parent = _currentDirectory.value.parentFile
            val internalRoot = Environment.getExternalStorageDirectory()
            if (parent != null && parent.canRead() && _currentDirectory.value.absolutePath != internalRoot.absolutePath) {
                loadDirectory(parent)
                return true
            }

            // At root of storage: pop navigation history if user navigated here from another screen
            if (_navigationHistory.isNotEmpty()) {
                _currentScreen.value = _navigationHistory.removeAt(_navigationHistory.size - 1)
                return true
            }
            return false
        }

        // For sub-screens (TextViewer, ApkInspector, HexViewer, Settings, etc.):
        if (_navigationHistory.isNotEmpty()) {
            _currentScreen.value = _navigationHistory.removeAt(_navigationHistory.size - 1)
            return true
        }

        if (_currentScreen.value !is ScreenDestination.Home) {
            _currentScreen.value = ScreenDestination.Home
            return true
        }

        return false
    }

    fun canNavigateBack(): Boolean {
        if (_currentScreen.value is ScreenDestination.Files) {
            if (_searchQuery.value.isNotBlank()) return true
            if (_isMultiSelectMode.value) return true
            if (_activeCategoryTitle.value != null) return true
            if (_directoryBackStack.isNotEmpty()) return true
            val parent = _currentDirectory.value.parentFile
            val internalRoot = Environment.getExternalStorageDirectory()
            if (parent != null && parent.canRead() && _currentDirectory.value.absolutePath != internalRoot.absolutePath) return true
            return _navigationHistory.isNotEmpty()
        }
        return _currentScreen.value !is ScreenDestination.Home
    }

    fun refreshStorageVolumes() {
        viewModelScope.launch {
            val volumes = withContext(Dispatchers.IO) {
                FileOperations.getStorageVolumes(getApplication())
            }
            _storageVolumes.value = volumes
        }
    }

    fun openStorageRoot(directory: File) {
        _directoryBackStack.clear()
        _activeCategoryTitle.value = null
        loadDirectory(directory)
        navigateTo(ScreenDestination.Files)
    }

    /**
     * Enters a directory and records the previous directory in backstack
     */
    fun enterDirectory(directory: File) {
        if (_currentDirectory.value.absolutePath != directory.absolutePath) {
            _directoryBackStack.add(_currentDirectory.value)
        }
        loadDirectory(directory)
    }

    fun enterSecondaryDirectory(directory: File) {
        if (_secondaryDirectory.value.absolutePath != directory.absolutePath) {
            _secondaryBackStack.add(_secondaryDirectory.value)
        }
        loadSecondaryDirectory(directory)
    }

    fun loadCategoryFiles(fileType: FileType, categoryTitle: String) {
        _isLoading.value = true
        _activeCategoryTitle.value = categoryTitle
        _selectedItems.value = emptySet()
        _isMultiSelectMode.value = false
        _directoryBackStack.clear()
        navigateTo(ScreenDestination.Files)

        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                val list = mutableListOf<FileItem>()
                val root = Environment.getExternalStorageDirectory()
                val candidateDirs = listOf(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    root
                ).filter { it != null && it.exists() && it.canRead() }

                val seenPaths = mutableSetOf<String>()
                fun scan(dir: File, depth: Int) {
                    if (depth > 3 || list.size >= 250) return
                    dir.listFiles()?.forEach { f ->
                        if (f.name.startsWith(".")) return@forEach
                        if (f.isDirectory) {
                            scan(f, depth + 1)
                        } else if (seenPaths.add(f.absolutePath)) {
                            if (FileUtils.detectFileType(f) == fileType) {
                                val ext = FileUtils.getExtension(f.name)
                                list.add(
                                    FileItem(
                                        name = f.name,
                                        path = f.absolutePath,
                                        size = f.length(),
                                        lastModified = f.lastModified(),
                                        isDirectory = false,
                                        isFavorite = _favorites.value.contains(f.absolutePath),
                                        fileType = fileType,
                                        mimeType = FileUtils.getMimeType(f.name),
                                        extension = ext,
                                        childCount = 0,
                                        isReadable = f.canRead(),
                                        isWritable = f.canWrite()
                                    )
                                )
                            }
                        }
                    }
                }

                for (dir in candidateDirs) {
                    scan(dir, 0)
                }
                sortFileList(list, _sortOption.value)
            }
            _fileItems.value = items
            _isLoading.value = false
        }
    }

    fun loadDirectory(directory: File) {
        _isLoading.value = true
        _activeCategoryTitle.value = null
        _selectedItems.value = emptySet()
        _isMultiSelectMode.value = false
        _currentDirectory.value = directory

        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                val list = mutableListOf<FileItem>()
                val files = directory.listFiles()
                if (files != null) {
                    val showHidden = _showHiddenFiles.value
                    for (f in files) {
                        if (!showHidden && f.name.startsWith(".")) continue
                        val isDir = f.isDirectory
                        val ext = FileUtils.getExtension(f.name)
                        val type = FileUtils.detectFileType(f)
                        val childCount = if (isDir) (f.listFiles()?.size ?: 0) else 0

                        list.add(
                            FileItem(
                                name = f.name,
                                path = f.absolutePath,
                                size = f.length(),
                                lastModified = f.lastModified(),
                                isDirectory = isDir,
                                isFavorite = _favorites.value.contains(f.absolutePath),
                                fileType = type,
                                mimeType = FileUtils.getMimeType(f.name),
                                extension = ext,
                                childCount = childCount,
                                isReadable = f.canRead(),
                                isWritable = f.canWrite()
                            )
                        )
                    }
                }
                sortFileList(list, _sortOption.value)
            }
            _fileItems.value = items
            _isLoading.value = false
        }
    }

    fun loadSecondaryDirectory(directory: File) {
        _secondaryLoading.value = true
        _secondaryDirectory.value = directory

        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                val list = mutableListOf<FileItem>()
                val files = directory.listFiles()
                if (files != null) {
                    val showHidden = _showHiddenFiles.value
                    for (f in files) {
                        if (!showHidden && f.name.startsWith(".")) continue
                        val isDir = f.isDirectory
                        val ext = FileUtils.getExtension(f.name)
                        val type = FileUtils.detectFileType(f)
                        val childCount = if (isDir) (f.listFiles()?.size ?: 0) else 0

                        list.add(
                            FileItem(
                                name = f.name,
                                path = f.absolutePath,
                                size = f.length(),
                                lastModified = f.lastModified(),
                                isDirectory = isDir,
                                isFavorite = _favorites.value.contains(f.absolutePath),
                                fileType = type,
                                mimeType = FileUtils.getMimeType(f.name),
                                extension = ext,
                                childCount = childCount,
                                isReadable = f.canRead(),
                                isWritable = f.canWrite()
                            )
                        )
                    }
                }
                sortFileList(list, _sortOption.value)
            }
            _secondaryFileItems.value = items
            _secondaryLoading.value = false
        }
    }

    // Dual Pane Helpers
    fun toggleDualPane(enable: Boolean? = null) {
        val newVal = enable ?: !_isDualPaneMode.value
        _isDualPaneMode.value = newVal
        if (newVal && _secondaryFileItems.value.isEmpty()) {
            loadSecondaryDirectory(_secondaryDirectory.value)
        }
    }

    fun toggleDualPaneSplit() {
        _dualPaneSplit.value = if (_dualPaneSplit.value == DualPaneSplit.VERTICAL) {
            DualPaneSplit.HORIZONTAL
        } else {
            DualPaneSplit.VERTICAL
        }
    }

    fun setActivePane(index: Int) {
        _activePaneIndex.value = index
    }

    fun transferFileBetweenPanes(fileItem: FileItem, targetDir: File, isMove: Boolean) {
        viewModelScope.launch {
            val src = File(fileItem.path)
            _isLoading.value = true
            _secondaryLoading.value = true
            val success = withContext(Dispatchers.IO) {
                try {
                    val res = if (isMove) {
                        FileOperations.moveFileOrDirectory(src, targetDir)
                    } else {
                        FileOperations.copyFileOrDirectory(src, targetDir)
                    }
                    res.isSuccess
                } catch (e: Exception) {
                    false
                }
            }
            if (success) {
                _statusMessage.value = if (isMove) "Đã di chuyển: ${fileItem.name}" else "Đã sao chép: ${fileItem.name}"
                loadDirectory(_currentDirectory.value)
                loadSecondaryDirectory(_secondaryDirectory.value)
            } else {
                _statusMessage.value = "Không thể chuyển tệp (kiểm tra quyền truy cập)"
                _isLoading.value = false
                _secondaryLoading.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        _fileItems.value = sortFileList(_fileItems.value, option)
        _secondaryFileItems.value = sortFileList(_secondaryFileItems.value, option)
    }

    private fun sortFileList(list: List<FileItem>, option: SortOption): List<FileItem> {
        val comparator = when (option.field) {
            SortField.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortField.DATE -> compareBy { it.lastModified }
            SortField.SIZE -> compareBy { it.size }
            SortField.TYPE -> compareBy { it.extension }
        }

        val sorted = if (option.order == SortOrder.ASCENDING) {
            list.sortedWith(comparator)
        } else {
            list.sortedWith(comparator.reversed())
        }

        // Directories always appear on top
        return sorted.sortedByDescending { it.isDirectory }
    }

    fun toggleViewMode() {
        val newMode = !_isGridView.value
        _isGridView.value = newMode
        prefs.edit().putBoolean("is_grid_view", newMode).apply()
    }

    fun toggleGridView() = toggleViewMode()

    fun toggleShowHiddenFiles() {
        val newShow = !_showHiddenFiles.value
        _showHiddenFiles.value = newShow
        prefs.edit().putBoolean("show_hidden_files", newShow).apply()
        loadDirectory(_currentDirectory.value)
        if (_isDualPaneMode.value) loadSecondaryDirectory(_secondaryDirectory.value)
    }

    fun toggleFavorite(fileItem: FileItem) {
        val newFavs = _favorites.value.toMutableSet()
        if (newFavs.contains(fileItem.path)) {
            newFavs.remove(fileItem.path)
        } else {
            newFavs.add(fileItem.path)
        }
        _favorites.value = newFavs
        prefs.edit().putStringSet("favorites", newFavs).apply()

        _fileItems.value = _fileItems.value.map {
            if (it.path == fileItem.path) it.copy(isFavorite = newFavs.contains(it.path)) else it
        }
        _secondaryFileItems.value = _secondaryFileItems.value.map {
            if (it.path == fileItem.path) it.copy(isFavorite = newFavs.contains(it.path)) else it
        }
    }

    fun addRecentFile(file: File) {
        val list = _recentFilePaths.value.toMutableList()
        list.remove(file.absolutePath)
        list.add(0, file.absolutePath)
        val trimmed = list.take(15)
        _recentFilePaths.value = trimmed
        prefs.edit().putString("recent_files", trimmed.joinToString(",")).apply()
    }

    fun toggleSelectItem(item: FileItem) {
        val set = _selectedItems.value.toMutableSet()
        if (set.contains(item)) {
            set.remove(item)
        } else {
            set.add(item)
        }
        _selectedItems.value = set
        _isMultiSelectMode.value = set.isNotEmpty()
    }

    fun selectAll() {
        _selectedItems.value = _fileItems.value.toSet()
        _isMultiSelectMode.value = true
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
        _isMultiSelectMode.value = false
    }

    fun setClipboard(action: ClipboardAction) {
        if (_selectedItems.value.isNotEmpty()) {
            _clipboard.value = ClipboardState(action = action, items = _selectedItems.value.toList())
            clearSelection()
            _statusMessage.value = "Đã lưu ${_clipboard.value?.items?.size} tệp vào khay nhớ tạm"
        }
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    fun pasteClipboard(targetDirectory: File = _currentDirectory.value) {
        val clip = _clipboard.value ?: return
        viewModelScope.launch {
            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = if (clip.action == ClipboardAction.COPY) "Đang sao chép..." else "Đang di chuyển...",
                totalFiles = clip.items.size
            )

            var successCount = 0
            withContext(Dispatchers.IO) {
                clip.items.forEachIndexed { index, item ->
                    _operationProgress.value = _operationProgress.value.copy(
                        currentFileIndex = index + 1,
                        currentFileName = item.name,
                        progress = if (clip.items.isNotEmpty()) (index + 1).toFloat() / clip.items.size.toFloat() else 1f
                    )
                    val src = File(item.path)
                    val res = if (clip.action == ClipboardAction.COPY) {
                        FileOperations.copyFileOrDirectory(src, targetDirectory)
                    } else {
                        FileOperations.moveFileOrDirectory(src, targetDirectory)
                    }
                    if (res.isSuccess) successCount++
                }
            }

            _operationProgress.value = OperationProgress(isRunning = false)
            if (successCount > 0) {
                _statusMessage.value = "Đã xử lý thành công $successCount tệp"
                if (clip.action == ClipboardAction.CUT) clearClipboard()
                loadDirectory(_currentDirectory.value)
                if (_isDualPaneMode.value) loadSecondaryDirectory(_secondaryDirectory.value)
            } else {
                _statusMessage.value = "Không thể xử lý tệp"
            }
        }
    }

    fun openFile(fileItem: FileItem) {
        val file = File(fileItem.path)
        if (!file.exists()) {
            _statusMessage.value = "Tệp không tồn tại"
            return
        }

        if (fileItem.isDirectory) {
            _activeCategoryTitle.value = null
            enterDirectory(file)
            navigateTo(ScreenDestination.Files)
            return
        }

        addRecentFile(file)

        when (fileItem.fileType) {
            FileType.APK -> navigateTo(ScreenDestination.ApkInspector(file))
            FileType.DATABASE -> navigateTo(ScreenDestination.SqliteViewer(file))
            FileType.ARCHIVE -> navigateTo(ScreenDestination.ArchiveViewer(file))
            FileType.TEXT_CODE -> navigateTo(ScreenDestination.TextViewer(file))
            FileType.IMAGE, FileType.AUDIO, FileType.VIDEO -> navigateTo(ScreenDestination.MediaViewer(file, fileItem.fileType))
            FileType.GAME_DATA -> {
                val ext = fileItem.extension
                when (ext) {
                    "obb", "zip" -> navigateTo(ScreenDestination.ArchiveViewer(file))
                    "db", "sqlite" -> navigateTo(ScreenDestination.SqliteViewer(file))
                    "json", "xml", "txt", "ini", "cfg", "txs", "ts", "css", "bat" -> navigateTo(ScreenDestination.TextViewer(file))
                    "dat" -> {
                        if (FileUtils.isLikelyTextFile(file)) {
                            navigateTo(ScreenDestination.TextViewer(file))
                        } else {
                            navigateTo(ScreenDestination.HexViewer(file))
                        }
                    }
                    else -> navigateTo(ScreenDestination.HexViewer(file))
                }
            }
            else -> {
                if (FileUtils.isLikelyTextFile(file)) {
                    navigateTo(ScreenDestination.TextViewer(file))
                } else {
                    navigateTo(ScreenDestination.HexViewer(file))
                }
            }
        }
    }

    fun createFolder(name: String, inSecondaryPane: Boolean = false) {
        val target = if (inSecondaryPane) _secondaryDirectory.value else _currentDirectory.value
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                FileOperations.createNewFolder(target, name)
            }
            if (res.isSuccess) {
                _statusMessage.value = "Đã tạo thư mục: $name"
                if (inSecondaryPane) loadSecondaryDirectory(target) else loadDirectory(target)
            } else {
                _statusMessage.value = res.exceptionOrNull()?.message ?: "Không thể tạo thư mục"
            }
        }
    }

    fun createFile(name: String, inSecondaryPane: Boolean = false) {
        val target = if (inSecondaryPane) _secondaryDirectory.value else _currentDirectory.value
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                FileOperations.createNewFile(target, name)
            }
            if (res.isSuccess) {
                _statusMessage.value = "Đã tạo tệp: $name"
                if (inSecondaryPane) loadSecondaryDirectory(target) else loadDirectory(target)
            } else {
                _statusMessage.value = res.exceptionOrNull()?.message ?: "Không thể tạo tệp"
            }
        }
    }

    fun renameFile(item: FileItem, newName: String) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                FileOperations.rename(File(item.path), newName)
            }
            if (res.isSuccess) {
                _statusMessage.value = "Đã đổi tên thành công"
                loadDirectory(_currentDirectory.value)
                if (_isDualPaneMode.value) loadSecondaryDirectory(_secondaryDirectory.value)
            } else {
                _statusMessage.value = res.exceptionOrNull()?.message ?: "Không thể đổi tên tệp"
            }
        }
    }

    fun deleteSelectedItems() {
        val items = _selectedItems.value.toList()
        if (items.isEmpty()) return

        viewModelScope.launch {
            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Đang xóa tệp...",
                totalFiles = items.size
            )

            var deletedCount = 0
            withContext(Dispatchers.IO) {
                items.forEachIndexed { index, item ->
                    _operationProgress.value = _operationProgress.value.copy(
                        currentFileIndex = index + 1,
                        currentFileName = item.name,
                        progress = if (items.isNotEmpty()) (index + 1).toFloat() / items.size.toFloat() else 1f
                    )
                    val res = FileOperations.deleteRecursively(File(item.path))
                    if (res.isSuccess && res.getOrDefault(false)) deletedCount++
                }
            }

            _operationProgress.value = OperationProgress(isRunning = false)
            clearSelection()
            _statusMessage.value = "Đã xóa $deletedCount/${items.size} tệp/thư mục"
            loadDirectory(_currentDirectory.value)
            if (_isDualPaneMode.value) loadSecondaryDirectory(_secondaryDirectory.value)
        }
    }

    fun deleteSingleFile(item: FileItem) {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                FileOperations.deleteRecursively(File(item.path))
            }
            if (res.isSuccess && res.getOrDefault(false)) {
                _statusMessage.value = "Đã xóa ${item.name}"
                loadDirectory(_currentDirectory.value)
                if (_isDualPaneMode.value) loadSecondaryDirectory(_secondaryDirectory.value)
            } else {
                _statusMessage.value = "Không thể xóa tệp"
            }
        }
    }

    fun createZipFromSelected(archiveName: String) {
        val items = _selectedItems.value.toList()
        if (items.isEmpty()) return

        val targetZip = File(_currentDirectory.value, if (archiveName.endsWith(".zip")) archiveName else "$archiveName.zip")
        viewModelScope.launch {
            _operationProgress.value = OperationProgress(
                isRunning = true,
                title = "Đang nén tệp...",
                totalFiles = items.size
            )

            val res = withContext(Dispatchers.IO) {
                ArchiveManager.createZip(
                    sourceFiles = items.map { File(it.path) },
                    destinationZip = targetZip,
                    onProgress = { progress, name ->
                        _operationProgress.value = _operationProgress.value.copy(
                            progress = progress,
                            currentFileName = name
                        )
                    }
                )
            }

            _operationProgress.value = OperationProgress(isRunning = false)
            clearSelection()
            if (res.isSuccess) {
                _statusMessage.value = "Đã tạo tệp nén: ${targetZip.name}"
                loadDirectory(_currentDirectory.value)
                if (_isDualPaneMode.value) loadSecondaryDirectory(_secondaryDirectory.value)
            } else {
                _statusMessage.value = "Lỗi khi nén: ${res.exceptionOrNull()?.message}"
            }
        }
    }

    fun handleSafUri(uri: Uri) {
        SafManager.takePersistableUriPermission(getApplication(), uri)
        _statusMessage.value = "Đã cấp quyền truy cập qua SAF"
        refreshStorageVolumes()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
