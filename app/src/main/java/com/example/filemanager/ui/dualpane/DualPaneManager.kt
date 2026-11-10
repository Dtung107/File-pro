package com.example.filemanager.ui.dualpane

import android.content.Context
import android.os.Environment
import androidx.compose.ui.geometry.Offset
import com.example.filemanager.model.FileItem
import com.example.filemanager.model.SortField
import com.example.filemanager.model.ViewMode
import com.example.filemanager.utils.FileOperations
import com.example.filemanager.utils.FileUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DualPaneManager(private val context: Context, private val scope: CoroutineScope) {

    private val internalDir = Environment.getExternalStorageDirectory()
    private val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        ?.takeIf { it.exists() && it.canRead() } ?: internalDir

    private val _leftPane = MutableStateFlow(
        PaneState(
            id = 0,
            currentDirectory = internalDir,
            storageTitle = "Bộ nhớ trong"
        )
    )
    val leftPane: StateFlow<PaneState> = _leftPane.asStateFlow()

    private val _rightPane = MutableStateFlow(
        PaneState(
            id = 1,
            currentDirectory = downloadsDir,
            storageTitle = if (downloadsDir != internalDir) "Tải xuống" else "Bộ nhớ trong"
        )
    )
    val rightPane: StateFlow<PaneState> = _rightPane.asStateFlow()

    private val _splitRatio = MutableStateFlow(0.5f) // 0.2f .. 0.8f
    val splitRatio: StateFlow<Float> = _splitRatio.asStateFlow()

    private val _splitOrientation = MutableStateFlow(SplitOrientation.HORIZONTAL)
    val splitOrientation: StateFlow<SplitOrientation> = _splitOrientation.asStateFlow()

    private val _dragSession = MutableStateFlow(DragSession())
    val dragSession: StateFlow<DragSession> = _dragSession.asStateFlow()

    private val _transferProgress = MutableStateFlow(FileTransferProgress())
    val transferProgress: StateFlow<FileTransferProgress> = _transferProgress.asStateFlow()

    private var transferJob: Job? = null

    init {
        loadDirectory(0, internalDir)
        loadDirectory(1, downloadsDir)
    }

    fun setSplitRatio(ratio: Float) {
        _splitRatio.value = ratio.coerceIn(0.2f, 0.8f)
    }

    fun setSplitOrientation(orientation: SplitOrientation) {
        _splitOrientation.value = orientation
    }

    fun toggleSplitOrientation() {
        _splitOrientation.value = if (_splitOrientation.value == SplitOrientation.HORIZONTAL) {
            SplitOrientation.VERTICAL
        } else {
            SplitOrientation.HORIZONTAL
        }
    }

    fun swapPanes() {
        val left = _leftPane.value
        val right = _rightPane.value

        _leftPane.value = right.copy(id = 0)
        _rightPane.value = left.copy(id = 1)
    }

    fun loadDirectory(paneId: Int, dir: File) {
        val targetFlow = if (paneId == 0) _leftPane else _rightPane
        val current = targetFlow.value

        scope.launch {
            targetFlow.value = current.copy(isLoading = true)
            val filesList = withContext(Dispatchers.IO) {
                try {
                    val rawFiles = dir.listFiles() ?: emptyArray()
                    rawFiles.map { FileUtils.fileToFileItem(it) }
                } catch (e: Exception) {
                    emptyList()
                }
            }

            val newBackStack = if (dir != current.currentDirectory && dir.exists()) {
                current.backStack + listOf(current.currentDirectory)
            } else {
                current.backStack
            }

            targetFlow.value = current.copy(
                currentDirectory = dir,
                backStack = newBackStack,
                files = filesList,
                selectedFiles = emptySet(),
                isMultiSelect = false,
                isLoading = false
            )
        }
    }

    fun navigateBack(paneId: Int): Boolean {
        val targetFlow = if (paneId == 0) _leftPane else _rightPane
        val current = targetFlow.value

        if (current.backStack.isNotEmpty()) {
            val prevDir = current.backStack.last()
            val newStack = current.backStack.dropLast(1)
            scope.launch {
                targetFlow.value = current.copy(isLoading = true)
                val filesList = withContext(Dispatchers.IO) {
                    val raw = prevDir.listFiles() ?: emptyArray()
                    raw.map { FileUtils.fileToFileItem(it) }
                }
                targetFlow.value = current.copy(
                    currentDirectory = prevDir,
                    backStack = newStack,
                    files = filesList,
                    selectedFiles = emptySet(),
                    isMultiSelect = false,
                    isLoading = false
                )
            }
            return true
        }

        // Try navigating to parent file
        val parent = current.currentDirectory.parentFile
        if (parent != null && parent.exists() && parent.canRead()) {
            loadDirectory(paneId, parent)
            return true
        }
        return false
    }

    fun refreshPane(paneId: Int) {
        val target = if (paneId == 0) _leftPane.value else _rightPane.value
        loadDirectory(paneId, target.currentDirectory)
    }

    fun refreshBoth() {
        refreshPane(0)
        refreshPane(1)
    }

    fun toggleSelect(paneId: Int, item: FileItem) {
        val targetFlow = if (paneId == 0) _leftPane else _rightPane
        val current = targetFlow.value
        val newSelection = current.selectedFiles.toMutableSet()
        if (newSelection.contains(item)) {
            newSelection.remove(item)
        } else {
            newSelection.add(item)
        }
        targetFlow.value = current.copy(
            selectedFiles = newSelection,
            isMultiSelect = newSelection.isNotEmpty()
        )
    }

    fun selectAll(paneId: Int) {
        val targetFlow = if (paneId == 0) _leftPane else _rightPane
        val current = targetFlow.value
        targetFlow.value = current.copy(
            selectedFiles = current.displayedFiles.toSet(),
            isMultiSelect = true
        )
    }

    fun clearSelection(paneId: Int) {
        val targetFlow = if (paneId == 0) _leftPane else _rightPane
        val current = targetFlow.value
        targetFlow.value = current.copy(
            selectedFiles = emptySet(),
            isMultiSelect = false
        )
    }

    fun setSort(paneId: Int, sortField: SortField) {
        val targetFlow = if (paneId == 0) _leftPane else _rightPane
        val current = targetFlow.value
        val newAsc = if (current.sortField == sortField) !current.sortAscending else true
        targetFlow.value = current.copy(sortField = sortField, sortAscending = newAsc)
    }

    fun setSearch(paneId: Int, query: String) {
        val targetFlow = if (paneId == 0) _leftPane else _rightPane
        val current = targetFlow.value
        targetFlow.value = current.copy(searchQuery = query)
    }

    fun toggleSearch(paneId: Int) {
        val targetFlow = if (paneId == 0) _leftPane else _rightPane
        val current = targetFlow.value
        val newState = !current.isSearchActive
        targetFlow.value = current.copy(
            isSearchActive = newState,
            searchQuery = if (!newState) "" else current.searchQuery
        )
    }

    fun toggleViewMode(paneId: Int) {
        val targetFlow = if (paneId == 0) _leftPane else _rightPane
        val current = targetFlow.value
        targetFlow.value = current.copy(
            viewMode = if (current.viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST
        )
    }

    // Drag and Drop Session
    fun startDrag(sourcePaneId: Int, items: List<FileItem>, startPos: Offset) {
        _dragSession.value = DragSession(
            isDragging = true,
            sourcePaneId = sourcePaneId,
            files = items,
            currentPosition = startPos,
            hoveredPaneId = null,
            hoveredTargetFolder = null
        )
    }

    fun updateDrag(pos: Offset, hoveredPaneId: Int?, hoveredFolder: File?) {
        if (!_dragSession.value.isDragging) return
        _dragSession.value = _dragSession.value.copy(
            currentPosition = pos,
            hoveredPaneId = hoveredPaneId,
            hoveredTargetFolder = hoveredFolder
        )
    }

    fun cancelDrag() {
        _dragSession.value = DragSession()
    }

    fun executeTransfer(
        items: List<FileItem>,
        targetDir: File,
        isMove: Boolean,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        cancelDrag()
        if (items.isEmpty()) return

        transferJob?.cancel()
        transferJob = scope.launch {
            _transferProgress.value = FileTransferProgress(
                isOperating = true,
                isMove = isMove,
                totalCount = items.size,
                totalBytes = items.sumOf { it.size }.coerceAtLeast(1L)
            )

            var processedCount = 0
            var processedBytes = 0L
            val totalBytes = _transferProgress.value.totalBytes

            var hasError = false
            var errorMsg = ""

            withContext(Dispatchers.IO) {
                for (item in items) {
                    if (!isActive) break
                    val srcFile = item.file
                    _transferProgress.value = _transferProgress.value.copy(
                        currentFile = srcFile.name,
                        processedCount = processedCount,
                        progress = (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    )

                    val res = if (isMove) {
                        FileOperations.moveFileOrDirectory(srcFile, targetDir)
                    } else {
                        FileOperations.copyFileOrDirectory(srcFile, targetDir)
                    }

                    if (res.isSuccess) {
                        processedCount++
                        processedBytes += item.size
                    } else {
                        hasError = true
                        errorMsg = res.exceptionOrNull()?.message ?: "Lỗi xử lý ${srcFile.name}"
                    }
                }
            }

            _transferProgress.value = FileTransferProgress(isOperating = false)
            refreshBoth()

            val msg = if (hasError) {
                "Hoàn tất $processedCount/${items.size} tệp. (Lỗi: $errorMsg)"
            } else {
                "${if (isMove) "Đã di chuyển" else "Đã sao chép"} thành công $processedCount tệp vào ${targetDir.name}"
            }
            onComplete?.invoke(!hasError, msg)
        }
    }

    fun cancelTransfer() {
        transferJob?.cancel()
        _transferProgress.value = FileTransferProgress(isOperating = false)
        refreshBoth()
    }

    fun createFolder(paneId: Int, name: String, onResult: (Boolean, String) -> Unit) {
        val target = if (paneId == 0) _leftPane.value else _rightPane.value
        scope.launch {
            val res = FileOperations.createNewFolder(target.currentDirectory, name)
            if (res.isSuccess) {
                refreshPane(paneId)
                onResult(true, "Đã tạo thư mục: $name")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Không thể tạo thư mục")
            }
        }
    }

    fun renameFile(paneId: Int, file: File, newName: String, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            val res = FileOperations.rename(file, newName)
            if (res.isSuccess) {
                refreshBoth()
                onResult(true, "Đã đổi tên thành: $newName")
            } else {
                onResult(false, res.exceptionOrNull()?.message ?: "Không thể đổi tên")
            }
        }
    }

    fun deleteFiles(paneId: Int, items: List<FileItem>, onResult: (Boolean, String) -> Unit) {
        scope.launch {
            var count = 0
            withContext(Dispatchers.IO) {
                for (item in items) {
                    if (item.file.deleteRecursively()) count++
                }
            }
            refreshBoth()
            onResult(true, "Đã xóa $count tệp/thư mục")
        }
    }
}
