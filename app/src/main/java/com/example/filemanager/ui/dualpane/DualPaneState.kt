package com.example.filemanager.ui.dualpane

import androidx.compose.ui.geometry.Offset
import com.example.filemanager.model.FileItem
import com.example.filemanager.model.SortField
import com.example.filemanager.model.ViewMode
import java.io.File

enum class SplitOrientation {
    HORIZONTAL, // Left | Right (Side by side)
    VERTICAL    // Top / Bottom
}

data class PaneState(
    val id: Int, // 0: Left/Top, 1: Right/Bottom
    val currentDirectory: File,
    val backStack: List<File> = emptyList(),
    val files: List<FileItem> = emptyList(),
    val selectedFiles: Set<FileItem> = emptySet(),
    val isMultiSelect: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortField: SortField = SortField.NAME,
    val sortAscending: Boolean = true,
    val viewMode: ViewMode = ViewMode.LIST,
    val isLoading: Boolean = false,
    val storageTitle: String = "Bộ nhớ trong"
) {
    val displayedFiles: List<FileItem>
        get() {
            var list = files
            if (searchQuery.isNotEmpty()) {
                val q = searchQuery.lowercase()
                list = list.filter { it.name.lowercase().contains(q) }
            }
            val comparator = when (sortField) {
                SortField.NAME -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.name.lowercase() }
                SortField.SIZE -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.size }
                SortField.DATE -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.lastModified }
                SortField.TYPE -> compareBy<FileItem> { !it.isDirectory }.thenBy { it.extension }.thenBy { it.name.lowercase() }
            }
            val sorted = list.sortedWith(comparator)
            return if (sortAscending) sorted else {
                val dirs = sorted.filter { it.isDirectory }.reversed()
                val fls = sorted.filter { !it.isDirectory }.reversed()
                dirs + fls
            }
        }
}

data class DragSession(
    val isDragging: Boolean = false,
    val sourcePaneId: Int = -1,
    val files: List<FileItem> = emptyList(),
    val currentPosition: Offset = Offset.Zero,
    val hoveredPaneId: Int? = null,
    val hoveredTargetFolder: File? = null
)

data class FileTransferProgress(
    val isOperating: Boolean = false,
    val isMove: Boolean = false,
    val currentFile: String = "",
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Float = 0f
)
