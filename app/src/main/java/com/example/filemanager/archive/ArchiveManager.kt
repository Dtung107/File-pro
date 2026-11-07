package com.example.filemanager.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ArchiveEntryInfo(
    val name: String,
    val path: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val lastModified: Long
)

object ArchiveManager {

    suspend fun listArchiveEntries(archiveFile: File): Result<List<ArchiveEntryInfo>> = withContext(Dispatchers.IO) {
        try {
            if (!archiveFile.exists() || !archiveFile.canRead()) {
                return@withContext Result.failure(IllegalArgumentException("Không thể đọc tệp nén"))
            }

            val entriesList = mutableListOf<ArchiveEntryInfo>()
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val path = entry.name
                    val name = path.trimEnd('/').substringAfterLast('/')
                    entriesList.add(
                        ArchiveEntryInfo(
                            name = if (name.isEmpty()) path else name,
                            path = path,
                            size = entry.size.coerceAtLeast(0L),
                            compressedSize = entry.compressedSize.coerceAtLeast(0L),
                            isDirectory = entry.isDirectory,
                            lastModified = entry.time
                        )
                    )
                }
            }

            Result.success(entriesList.sortedWith(compareBy({ !it.isDirectory }, { it.path })))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractAll(
        archiveFile: File,
        targetDir: File,
        onProgress: ((progress: Float, currentEntry: String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) targetDir.mkdirs()

            var extractedCount = 0
            val zipFile = ZipFile(archiveFile)
            val totalEntries = zipFile.size()

            zipFile.use { zip ->
                val entries = zip.entries()
                var current = 0

                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    current++
                    onProgress?.invoke((current.toFloat() / totalEntries.toFloat()).coerceIn(0f, 1f), entry.name)

                    val outFile = File(targetDir, entry.name)
                    // Security: Guard against Zip Slip vulnerability
                    if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        continue
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        extractedCount++
                    }
                }
            }

            Result.success(extractedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractEntry(
        archiveFile: File,
        entryPath: String,
        targetDir: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) targetDir.mkdirs()

            ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(entryPath)
                    ?: return@withContext Result.failure(IllegalArgumentException("Không tìm thấy tệp trong tệp nén: $entryPath"))

                val fileName = entryPath.trimEnd('/').substringAfterLast('/')
                val outFile = File(targetDir, fileName)

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                Result.success(outFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createZip(
        sourceFiles: List<File>,
        destinationZip: File,
        onProgress: ((progress: Float, currentFile: String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destinationZip.parentFile?.mkdirs()
            val totalFiles = sourceFiles.sumOf { countFilesIn(it) }
            var processed = 0

            ZipOutputStream(FileOutputStream(destinationZip)).use { zos ->
                for (source in sourceFiles) {
                    addFileToZip(source, source.name, zos) { name ->
                        processed++
                        if (totalFiles > 0) {
                            onProgress?.invoke((processed.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f), name)
                        }
                    }
                }
            }

            Result.success(destinationZip)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun countFilesIn(file: File): Int {
        if (!file.isDirectory) return 1
        return 1 + (file.listFiles()?.sumOf { countFilesIn(it) } ?: 0)
    }

    private fun addFileToZip(file: File, entryPath: String, zos: ZipOutputStream, onItem: (String) -> Unit) {
        onItem(file.name)
        if (file.isDirectory) {
            val dirEntry = ZipEntry(if (entryPath.endsWith("/")) entryPath else "$entryPath/")
            zos.putNextEntry(dirEntry)
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                addFileToZip(child, "$entryPath/${child.name}", zos, onItem)
            }
        } else {
            val entry = ZipEntry(entryPath)
            zos.putNextEntry(entry)
            FileInputStream(file).use { fis ->
                fis.copyTo(zos)
            }
            zos.closeEntry()
        }
    }

    suspend fun testArchiveIntegrity(archiveFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            var count = 0
            var totalBytes = 0L
            val buffer = ByteArray(16 * 1024)

            ZipInputStream(FileInputStream(archiveFile)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    count++
                    while (zis.read(buffer) > 0) {
                        totalBytes += buffer.size
                    }
                    zis.closeEntry()
                }
            }
            Result.success("Tệp nén toàn vẹn tốt: $count mục đã kiểm tra thành công, không có lỗi cấu trúc.")
        } catch (e: Exception) {
            Result.failure(Exception("Phát hiện lỗi trong tệp nén: ${e.message}"))
        }
    }
}
