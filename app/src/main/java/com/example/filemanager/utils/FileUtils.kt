package com.example.filemanager.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.example.filemanager.model.FileType
import java.io.File
import java.io.InputStream
import java.util.Locale

object FileUtils {

    private val APK_EXTENSIONS = setOf("apk", "xapk", "apks", "apkm")
    private val DB_EXTENSIONS = setOf("db", "sqlite", "sqlite3", "sqlitedb")
    private val ARCHIVE_EXTENSIONS = setOf("zip", "7z", "rar", "tar", "gz", "bz2", "xz", "tgz", "tbz2")
    private val GAME_DATA_EXTENSIONS = setOf(
        "obb", "pak", "asset", "assets", "bundle", "unity3d", "sav", "save", "dat",
        "bin", "res", "raw", "resource", "wad", "bsp", "vpk", "vfs"
    )
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic", "tiff")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "wmv", "ts")
    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma")
    private val TEXT_CODE_EXTENSIONS = setOf(
        "txt", "json", "xml", "html", "htm", "css", "js", "ts", "py", "java", "kt",
        "kts", "c", "cpp", "h", "cs", "php", "sh", "bat", "ini", "cfg", "conf",
        "log", "csv", "md", "yaml", "yml", "properties", "gradle"
    )
    private val DOCUMENT_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "epub")
    private val BINARY_EXTENSIONS = setOf("bin", "dat", "blob", "raw", "dex", "so", "dll", "exe", "class")

    fun getExtension(fileName: String): String {
        val lastDot = fileName.lastIndexOf('.')
        return if (lastDot >= 0 && lastDot < fileName.length - 1) {
            fileName.substring(lastDot + 1).lowercase(Locale.ROOT)
        } else ""
    }

    fun detectFileType(file: File): FileType {
        if (file.isDirectory) return FileType.FOLDER
        val ext = getExtension(file.name)
        return detectFileTypeByExtension(ext)
    }

    fun detectFileTypeByExtension(ext: String): FileType {
        return when {
            APK_EXTENSIONS.contains(ext) -> FileType.APK
            DB_EXTENSIONS.contains(ext) -> FileType.DATABASE
            ARCHIVE_EXTENSIONS.contains(ext) -> FileType.ARCHIVE
            GAME_DATA_EXTENSIONS.contains(ext) -> FileType.GAME_DATA
            IMAGE_EXTENSIONS.contains(ext) -> FileType.IMAGE
            VIDEO_EXTENSIONS.contains(ext) -> FileType.VIDEO
            AUDIO_EXTENSIONS.contains(ext) -> FileType.AUDIO
            TEXT_CODE_EXTENSIONS.contains(ext) -> FileType.TEXT_CODE
            DOCUMENT_EXTENSIONS.contains(ext) -> FileType.DOCUMENT
            BINARY_EXTENSIONS.contains(ext) -> FileType.BINARY
            else -> FileType.UNKNOWN
        }
    }

    fun getMimeType(fileName: String): String {
        val ext = getExtension(fileName)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        return mime ?: when (ext) {
            "apk" -> "application/vnd.android.package-archive"
            "db", "sqlite", "sqlite3" -> "application/x-sqlite3"
            "json" -> "application/json"
            "log", "ini", "cfg" -> "text/plain"
            "md" -> "text/markdown"
            else -> "*/*"
        }
    }

    fun openWithExternalApp(context: Context, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mime = getMimeType(file.name)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Mở bằng"))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun shareFile(context: Context, file: File): Boolean {
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mime = getMimeType(file.name)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Chia sẻ tệp"))
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Reads up to the first [maxBytes] of a file to check file header magic numbers
     */
    fun readHeaderBytes(file: File, maxBytes: Int = 16): ByteArray? {
        return try {
            if (!file.exists() || file.isDirectory || !file.canRead()) return null
            file.inputStream().use { stream ->
                val buffer = ByteArray(maxBytes)
                val read = stream.read(buffer, 0, maxBytes)
                if (read > 0) buffer.copyOf(read) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun identifyHeaderMagic(header: ByteArray): String {
        if (header.isEmpty()) return "File rỗng"
        val hex = header.joinToString("") { "%02X".format(it) }

        return when {
            hex.startsWith("89504E47") -> "PNG Image"
            hex.startsWith("FFD8FF") -> "JPEG Image"
            hex.startsWith("47494638") -> "GIF Image"
            hex.startsWith("52494646") && hex.length >= 24 && hex.substring(16, 24) == "57454250" -> "WEBP Image"
            hex.startsWith("504B0304") -> "ZIP / APK / JAR Archive"
            hex.startsWith("377ABCAF271C") -> "7-Zip Archive"
            hex.startsWith("526172211A07") -> "RAR Archive"
            hex.startsWith("1F8B") -> "GZIP Compressed Stream"
            hex.startsWith("425A68") -> "BZIP2 Archive"
            hex.startsWith("FD377A585A00") -> "XZ Archive"
            hex.startsWith("53514C69746520666F726D6174203300") -> "SQLite 3 Database"
            hex.startsWith("6465780A") -> "Android DEX Executable"
            hex.startsWith("7F454C46") -> "ELF Executable / Shared Object (.so)"
            hex.startsWith("25504446") -> "PDF Document"
            hex.startsWith("4F676753") -> "OGG Audio"
            hex.startsWith("494433") || hex.startsWith("FFF") -> "MP3 Audio"
            hex.startsWith("664C6143") -> "FLAC Audio"
            // Unity Assets magic "UnityFS"
            String(header.take(7).toByteArray()) == "UnityFS" -> "Unity Game Asset Bundle (UnityFS)"
            String(header.take(8).toByteArray()) == "UnityWeb" -> "Unity Game Asset (UnityWeb)"
            // Unreal PAK
            hex.startsWith("E1126F5A") || hex.endsWith("5A6F12E1") -> "Unreal Engine PAK Package"
            else -> "Nhị phân / Dữ liệu (${header.size} bytes đọc được)"
        }
    }
}
