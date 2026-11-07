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
    object Home : ScreenDestination()
    object Files : ScreenDestination()
    object GameData : ScreenDestination()
    object Analyzer : ScreenDestination()
    object Settings : ScreenDestination()
    data class TextViewer(val file: File) : ScreenDestination()
    data class ApkInspector(val file: File) : ScreenDestination()
    data class SqliteViewer(val file: File) : ScreenDestination()
    data class HexViewer(val file: File) : ScreenDestination()
    data class ArchiveViewer(val file: File) : ScreenDestination()
    data class MediaViewer(val file: File, val type: FileType) : ScreenDestination()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("file_manager_pro_prefs", Context.MODE_PRIVATE)

    private val _currentScreen = MutableStateFlow<ScreenDestination>(ScreenDestination.Home)
    val currentScreen: StateFlow<ScreenDestination> = _currentScreen.asStateFlow()

    private val _navigationHistory = mutableListOf<ScreenDestination>()

    private val _currentDirectory = MutableStateFlow<File>(Environment.getExternalStorageDirectory())
    val currentDirectory: StateFlow<File> = _currentDirectory.asStateFlow()

    private val _activeCategoryTitle = MutableStateFlow<String?>(null)
    val activeCategoryTitle: StateFlow<String?> = _activeCategoryTitle.asStateFlow()

    private val _fileItems = MutableStateFlow<List<FileItem>>(emptyList())
    val fileItems: StateFlow<List<FileItem>> = _fileItems.asStateFlow()

    private val _isLoading = MutableStateFlow<Boolean>(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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

    fun navigateBack(): Boolean {
        if (_navigationHistory.isNotEmpty()) {
            _currentScreen.value = _navigationHistory.removeAt(_navigationHistory.size - 1)
            return true
        }

        // If inside category view in Files, return to Home
        if (_activeCategoryTitle.value != null) {
            _activeCategoryTitle.value = null
            _currentScreen.value = ScreenDestination.Home
            return true
        }

        // If inside Files screen and not at root storage, navigate up directory
        if (_currentScreen.value is ScreenDestination.Files) {
            val parent = _currentDirectory.value.parentFile
            val internalRoot = Environment.getExternalStorageDirectory()
            if (parent != null && parent.canRead() && _currentDirectory.value.absolutePath != internalRoot.absolutePath) {
                loadDirectory(parent)
                return true
            } else {
                _currentScreen.value = ScreenDestination.Home
                return true
            }
        }

        if (_currentScreen.value !is ScreenDestination.Home) {
            _currentScreen.value = ScreenDestination.Home
            return true
        }

        return false
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
        _activeCategoryTitle.value = null
        loadDirectory(directory)
        navigateTo(ScreenDestination.Files)
    }

    fun loadCategoryFiles(fileType: FileType, categoryTitle: String) {
        _isLoading.value = true
        _activeCategoryTitle.value = categoryTitle
        _selectedItems.value = emptySet()
        _isMultiSelectMode.value = false
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
                                size = if (isDir) 0L else f.length(),
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

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
        _fileItems.value = sortFileList(_fileItems.value, option)
    }

    private fun sortFileList(list: List<FileItem>, option: SortOption): List<FileItem> {
        val comparator = when (option.field) {
            SortField.NAME -> compareBy<FileItem> { it.name.lowercase() }
            SortField.SIZE -> compareBy { it.size }
            SortField.DATE -> compareBy { it.lastModified }
            SortField.TYPE -> compareBy { it.fileType.name }
        }

        val sorted = if (option.order == SortOrder.ASCENDING) {
            list.sortedWith(compareBy<FileItem> { !it.isDirectory }.then(comparator))
        } else {
            list.sortedWith(compareBy<FileItem> { !it.isDirectory }.thenDescending(comparator))
        }
        return sorted
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleMultiSelect(item: FileItem) {
        val current = _selectedItems.value.toMutableSet()
        if (current.contains(item)) {
            current.remove(item)
        } else {
            current.add(item)
        }
        _selectedItems.value = current
        _isMultiSelectMode.value = current.isNotEmpty()
    }

    fun selectAll() {
        _selectedItems.value = _fileItems.value.toSet()
        _isMultiSelectMode.value = true
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
        _isMultiSelectMode.value = false
    }

    fun toggleFavorite(fileItem: FileItem) {
        val favs = _favorites.value.toMutableSet()
        if (favs.contains(fileItem.path)) {
            favs.remove(fileItem.path)
        } else {
            favs.add(fileItem.path)
        }
        _favorites.value = favs
        prefs.edit().putStringSet("favorites", favs).apply()
        _fileItems.value = _fileItems.value.map {
            if (it.path == fileItem.path) it.copy(isFavorite = favs.contains(it.path)) else it
        }
    }

    fun addRecentFile(file: File) {
        val list = _recentFilePaths.value.toMutableList()
        list.remove(file.absolutePath)
        list.add(0, file.absolutePath)
        val trimmed = list.take(20)
        _recentFilePaths.value = trimmed
        prefs.edit().putString("recent_files", trimmed.joinToString(",")).apply()
    }

    fun toggleShowHiddenFiles() {
        val newValue = !_showHiddenFiles.value
        _showHiddenFiles.value = newValue
        prefs.edit().putBoolean("show_hidden_files", newValue).apply()
        loadDirectory(_currentDirectory.value)
    }

    fun toggleGridView() {
        val newValue = !_isGridView.value
        _isGridView.value = newValue
        prefs.edit().putBoolean("is_grid_view", newValue).apply()
    }

    fun openFile(fileItem: FileItem) {
        val file = File(fileItem.path)
        if (!file.exists()) {
            _statusMessage.value = "Tệp không tồn tại"
            return
        }

        if (fileItem.isDirectory) {
            _activeCategoryTitle.value = null
            loadDirectory(file)
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
                    "json", "xml", "txt", "ini", "cfg" -> navigateTo(ScreenDestination.TextViewer(file))
                    else -> navigateTo(ScreenDestination.HexViewer(file))
                }
            }
            FileType.BINARY -> navigateTo(ScreenDestination.HexViewer(file))
            else -> {
                val opened = FileUtils.openWithExternalApp(getApplication(), file)
                if (!opened) {
                    navigateTo(ScreenDestination.HexViewer(file))
                }
            }
        }
    }

    fun setClipboard(action: ClipboardAction, items: List<FileItem>) {
        _clipboard.value = ClipboardState(action, items)
        clearSelection()
        _statusMessage.value = "Đã đưa ${items.size} mục vào khay nhớ tạm"
    }

    fun clearClipboard() {
        _clipboard.value = null
    }

    fun pasteClipboard() {
        val clip = _clipboard.value ?: return
        val targetDir = _currentDirectory.value

        _operationProgress.value = OperationProgress(
            isRunning = true,
            title = if (clip.action == ClipboardAction.COPY) "Đang sao chép..." else "Đang di chuyển...",
            canCancel = false
        )

        viewModelScope.launch {
            var successCount = 0
            for (item in clip.items) {
                val src = File(item.path)
                val res = if (clip.action == ClipboardAction.COPY) {
                    FileOperations.copyFileOrDirectory(src, targetDir) { p, name ->
                        _operationProgress.value = _operationProgress.value.copy(
                            progress = p,
                            message = name
                        )
                    }
                } else {
                    FileOperations.moveFileOrDirectory(src, targetDir) { p, name ->
                        _operationProgress.value = _operationProgress.value.copy(
                            progress = p,
                            message = name
                        )
                    }
                }
                if (res.isSuccess) successCount++
            }

            _operationProgress.value = OperationProgress(isRunning = false)
            if (clip.action == ClipboardAction.CUT) {
                _clipboard.value = null
            }
            _statusMessage.value = "Đã dán thành công $successCount mục"
            loadDirectory(targetDir)
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            val res = FileOperations.createNewFolder(_currentDirectory.value, name)
            if (res.isSuccess) {
                _statusMessage.value = "Đã tạo thư mục: $name"
                loadDirectory(_currentDirectory.value)
            } else {
                _statusMessage.value = res.exceptionOrNull()?.message ?: "Lỗi tạo thư mục"
            }
        }
    }

    fun createFile(name: String) {
        viewModelScope.launch {
            val res = FileOperations.createNewFile(_currentDirectory.value, name)
            if (res.isSuccess) {
                _statusMessage.value = "Đã tạo tệp: $name"
                loadDirectory(_currentDirectory.value)
            } else {
                _statusMessage.value = res.exceptionOrNull()?.message ?: "Lỗi tạo tệp"
            }
        }
    }

    fun renameFile(item: FileItem, newName: String) {
        viewModelScope.launch {
            val res = FileOperations.rename(File(item.path), newName)
            if (res.isSuccess) {
                _statusMessage.value = "Đã đổi tên thành công"
                loadDirectory(_currentDirectory.value)
            } else {
                _statusMessage.value = res.exceptionOrNull()?.message ?: "Lỗi đổi tên"
            }
        }
    }

    fun deleteSelectedItems() {
        val items = _selectedItems.value.toList()
        if (items.isEmpty()) return

        _operationProgress.value = OperationProgress(
            isRunning = true,
            title = "Đang xóa...",
            canCancel = false
        )

        viewModelScope.launch {
            var count = 0
            for (item in items) {
                val f = File(item.path)
                val res = FileOperations.deleteRecursively(f)
                if (res.isSuccess && res.getOrDefault(false)) count++
            }
            _operationProgress.value = OperationProgress(isRunning = false)
            clearSelection()
            _statusMessage.value = "Đã xóa $count mục"
            loadDirectory(_currentDirectory.value)
        }
    }

    fun compressSelectedItemsToZip(zipName: String) {
        val items = _selectedItems.value.toList()
        if (items.isEmpty()) return

        val targetZip = File(_currentDirectory.value, if (zipName.endsWith(".zip")) zipName else "$zipName.zip")
        _operationProgress.value = OperationProgress(
            isRunning = true,
            title = "Đang nén tệp...",
            canCancel = false
        )

        viewModelScope.launch {
            val files = items.map { File(it.path) }
            val res = ArchiveManager.createZip(files, targetZip) { p, name ->
                _operationProgress.value = _operationProgress.value.copy(progress = p, message = name)
            }

            _operationProgress.value = OperationProgress(isRunning = false)
            clearSelection()
            if (res.isSuccess) {
                _statusMessage.value = "Đã tạo tệp nén: ${targetZip.name}"
                loadDirectory(_currentDirectory.value)
            } else {
                _statusMessage.value = res.exceptionOrNull()?.message ?: "Lỗi nén tệp"
            }
        }
    }

    fun handleSafUri(uri: Uri) {
        SafManager.takePersistableUriPermission(getApplication(), uri)
        _statusMessage.value = "Đã cấp quyền truy cập SAF: ${uri.lastPathSegment}"
        refreshStorageVolumes()
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }
}
