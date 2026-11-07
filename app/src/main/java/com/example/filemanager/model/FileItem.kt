package com.example.filemanager.model

import android.net.Uri

enum class FileType {
    FOLDER,
    APK,
    GAME_DATA,
    DATABASE,
    ARCHIVE,
    IMAGE,
    VIDEO,
    AUDIO,
    TEXT_CODE,
    DOCUMENT,
    BINARY,
    UNKNOWN
}

enum class SortField {
    NAME,
    SIZE,
    DATE,
    TYPE
}

enum class SortOrder {
    ASCENDING,
    DESCENDING
}

data class SortOption(
    val field: SortField = SortField.NAME,
    val order: SortOrder = SortOrder.ASCENDING
)

data class FileItem(
    val name: String,
    val path: String,
    val uri: Uri? = null,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val isDirectory: Boolean = false,
    val isFavorite: Boolean = false,
    val fileType: FileType = FileType.UNKNOWN,
    val mimeType: String = "*/*",
    val extension: String = "",
    val childCount: Int = 0,
    val isReadable: Boolean = true,
    val isWritable: Boolean = true
) {
    val formattedSize: String
        get() = if (isDirectory) {
            if (childCount >= 0) "$childCount mục" else "Thư mục"
        } else {
            formatBytes(size)
        }

    val formattedDate: String
        get() = formatDate(lastModified)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 4)
            return String.format(java.util.Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }

        fun formatDate(timestamp: Long): String {
            if (timestamp <= 0) return "—"
            val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(timestamp))
        }
    }
}

data class StorageVolumeInfo(
    val name: String,
    val path: String,
    val uri: Uri? = null,
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L,
    val isPrimary: Boolean = false,
    val isRemovable: Boolean = false
) {
    val usedBytes: Long
        get() = (totalBytes - freeBytes).coerceAtLeast(0L)

    val usagePercent: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()) else 0f
}

data class OperationProgress(
    val isRunning: Boolean = false,
    val title: String = "",
    val message: String = "",
    val progress: Float = 0f, // 0..1
    val currentBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val canCancel: Boolean = true
)

enum class ClipboardAction {
    COPY,
    CUT
}

data class ClipboardState(
    val action: ClipboardAction,
    val items: List<FileItem>
)
