package com.example.filemanager.archive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.Deflater
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

    private const val BUFFER_SIZE = 128 * 1024 // 128 KB buffer for high-speed I/O

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
            val totalEntries = zipFile.size().coerceAtLeast(1)

            val buffer = ByteArray(BUFFER_SIZE)

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
                        BufferedInputStream(zip.getInputStream(entry), BUFFER_SIZE).use { bis ->
                            BufferedOutputStream(FileOutputStream(outFile), BUFFER_SIZE).use { bos ->
                                var len: Int
                                while (bis.read(buffer).also { len = it } != -1) {
                                    bos.write(buffer, 0, len)
                                }
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

    suspend fun extractEntries(
        archiveFile: File,
        entryPaths: List<String>,
        targetDir: File,
        onProgress: ((progress: Float, currentEntry: String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) targetDir.mkdirs()

            var extractedCount = 0
            val zipFile = ZipFile(archiveFile)
            val total = entryPaths.size.coerceAtLeast(1)
            val buffer = ByteArray(BUFFER_SIZE)

            zipFile.use { zip ->
                entryPaths.forEachIndexed { index, path ->
                    val entry = zip.getEntry(path) ?: return@forEachIndexed
                    onProgress?.invoke(((index + 1).toFloat() / total.toFloat()).coerceIn(0f, 1f), entry.name)

                    val outFile = File(targetDir, entry.name)
                    if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        return@forEachIndexed
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        BufferedInputStream(zip.getInputStream(entry), BUFFER_SIZE).use { bis ->
                            BufferedOutputStream(FileOutputStream(outFile), BUFFER_SIZE).use { bos ->
                                var len: Int
                                while (bis.read(buffer).also { len = it } != -1) {
                                    bos.write(buffer, 0, len)
                                }
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
                    BufferedInputStream(zip.getInputStream(entry), BUFFER_SIZE).use { bis ->
                        BufferedOutputStream(FileOutputStream(outFile), BUFFER_SIZE).use { bos ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var len: Int
                            while (bis.read(buffer).also { len = it } != -1) {
                                bos.write(buffer, 0, len)
                            }
                        }
                    }
                }
                Result.success(outFile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readEntryText(
        archiveFile: File,
        entryPath: String,
        maxBytes: Int = 1024 * 1024
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(entryPath)
                    ?: return@withContext Result.failure(IllegalArgumentException("Không tìm thấy tệp: $entryPath"))

                BufferedInputStream(zip.getInputStream(entry), BUFFER_SIZE).use { bis ->
                    val buffer = ByteArray(minOf(entry.size.coerceAtLeast(1024L), maxBytes.toLong()).toInt())
                    val read = bis.read(buffer)
                    if (read > 0) {
                        val text = String(buffer, 0, read, Charsets.UTF_8)
                        val suffix = if (entry.size > maxBytes) "\n\n[...Đã cắt nội dung vì tệp lớn hơn 1MB...]" else ""
                        Result.success(text + suffix)
                    } else {
                        Result.success("(Tệp rỗng)")
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun readEntryBytes(
        archiveFile: File,
        entryPath: String,
        maxBytes: Int = 10 * 1024 * 1024 // 10MB limit for in-memory previews
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(entryPath)
                    ?: return@withContext Result.failure(IllegalArgumentException("Không tìm thấy tệp: $entryPath"))

                val streamSize = if (entry.size > 0 && entry.size <= maxBytes) entry.size.toInt() else maxBytes
                BufferedInputStream(zip.getInputStream(entry), BUFFER_SIZE).use { bis ->
                    val bytes = bis.readBytes()
                    if (bytes.size > maxBytes) {
                        Result.success(bytes.copyOf(maxBytes))
                    } else {
                        Result.success(bytes)
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractDirectory(
        archiveFile: File,
        dirPrefix: String,
        targetDir: File,
        onProgress: ((progress: Float, currentEntry: String) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (!targetDir.exists()) targetDir.mkdirs()
            val normalizedPrefix = if (dirPrefix.isEmpty() || dirPrefix.endsWith("/")) dirPrefix else "$dirPrefix/"

            var extractedCount = 0
            val zipFile = ZipFile(archiveFile)
            val matchingEntries = mutableListOf<ZipEntry>()

            zipFile.use { zip ->
                val all = zip.entries()
                while (all.hasMoreElements()) {
                    val e = all.nextElement()
                    if (e.name.startsWith(normalizedPrefix)) {
                        matchingEntries.add(e)
                    }
                }

                val total = matchingEntries.size.coerceAtLeast(1)
                val buffer = ByteArray(BUFFER_SIZE)

                matchingEntries.forEachIndexed { index, entry ->
                    onProgress?.invoke(((index + 1).toFloat() / total.toFloat()).coerceIn(0f, 1f), entry.name)
                    // Sub-path relative to dirPrefix
                    val relPath = entry.name.removePrefix(normalizedPrefix)
                    if (relPath.isEmpty()) return@forEachIndexed

                    val outFile = File(targetDir, relPath)
                    if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                        return@forEachIndexed
                    }

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        BufferedInputStream(zip.getInputStream(entry), BUFFER_SIZE).use { bis ->
                            BufferedOutputStream(FileOutputStream(outFile), BUFFER_SIZE).use { bos ->
                                var len: Int
                                while (bis.read(buffer).also { len = it } != -1) {
                                    bos.write(buffer, 0, len)
                                }
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

    suspend fun extractEntryToTemp(
        cacheDir: File,
        archiveFile: File,
        entryPath: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val tempDir = File(cacheDir, "zip_temp_viewers").apply { mkdirs() }
            val fileName = entryPath.trimEnd('/').substringAfterLast('/')
            val safeFile = File(tempDir, "${System.currentTimeMillis()}_$fileName")

            ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(entryPath)
                    ?: return@withContext Result.failure(IllegalArgumentException("Không tìm thấy tệp: $entryPath"))

                BufferedInputStream(zip.getInputStream(entry), BUFFER_SIZE).use { bis ->
                    BufferedOutputStream(FileOutputStream(safeFile), BUFFER_SIZE).use { bos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var len: Int
                        while (bis.read(buffer).also { len = it } != -1) {
                            bos.write(buffer, 0, len)
                        }
                    }
                }
            }
            Result.success(safeFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun extractNestedZip(
        cacheDir: File,
        archiveFile: File,
        entryPath: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val nestedDir = File(cacheDir, "nested_zip_temp").apply { mkdirs() }
            val fileName = entryPath.trimEnd('/').substringAfterLast('/')
            val safeNestedFile = File(nestedDir, "${System.currentTimeMillis()}_$fileName")

            ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(entryPath)
                    ?: return@withContext Result.failure(IllegalArgumentException("Không tìm thấy tệp nén con: $entryPath"))

                BufferedInputStream(zip.getInputStream(entry), BUFFER_SIZE).use { bis ->
                    BufferedOutputStream(FileOutputStream(safeNestedFile), BUFFER_SIZE).use { bos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var len: Int
                        while (bis.read(buffer).also { len = it } != -1) {
                            bos.write(buffer, 0, len)
                        }
                    }
                }
            }
            Result.success(safeNestedFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cleanTempFiles(cacheDir: File) {
        try {
            File(cacheDir, "zip_temp_viewers").deleteRecursively()
            File(cacheDir, "nested_zip_temp").deleteRecursively()
        } catch (_: Exception) {}
    }

    suspend fun createZip(
        sourceFiles: List<File>,
        destinationZip: File,
        compressionLevel: Int = Deflater.BEST_SPEED,
        onProgress: ((progress: Float, currentFile: String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            destinationZip.parentFile?.mkdirs()
            val totalFiles = sourceFiles.sumOf { countFilesIn(it) }
            var processed = 0

            // High-speed buffered ZipOutputStream with optimized compression level
            BufferedOutputStream(FileOutputStream(destinationZip), BUFFER_SIZE).use { bos ->
                ZipOutputStream(bos).use { zos ->
                    zos.setLevel(compressionLevel) // Deflater.BEST_SPEED produces up to 4x faster zip creation
                    val buffer = ByteArray(BUFFER_SIZE)

                    for (source in sourceFiles) {
                        addFileToZipFast(source, source.name, zos, buffer) { name ->
                            processed++
                            if (totalFiles > 0) {
                                onProgress?.invoke((processed.toFloat() / totalFiles.toFloat()).coerceIn(0f, 1f), name)
                            }
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

    private fun addFileToZipFast(
        file: File,
        entryPath: String,
        zos: ZipOutputStream,
        buffer: ByteArray,
        onItem: (String) -> Unit
    ) {
        onItem(file.name)
        if (file.isDirectory) {
            val dirEntry = ZipEntry(if (entryPath.endsWith("/")) entryPath else "$entryPath/")
            zos.putNextEntry(dirEntry)
            zos.closeEntry()
            file.listFiles()?.forEach { child ->
                addFileToZipFast(child, "$entryPath/${child.name}", zos, buffer, onItem)
            }
        } else {
            val entry = ZipEntry(entryPath)
            zos.putNextEntry(entry)
            BufferedInputStream(FileInputStream(file), BUFFER_SIZE).use { bis ->
                var len: Int
                while (bis.read(buffer).also { len = it } != -1) {
                    zos.write(buffer, 0, len)
                }
            }
            zos.closeEntry()
        }
    }

    suspend fun testArchiveIntegrity(archiveFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            var count = 0
            var totalBytes = 0L
            val buffer = ByteArray(BUFFER_SIZE)

            ZipInputStream(BufferedInputStream(FileInputStream(archiveFile), BUFFER_SIZE)).use { zis ->
                var entry: ZipEntry?
                while (zis.nextEntry.also { entry = it } != null) {
                    count++
                    var read: Int
                    while (zis.read(buffer).also { read = it } > 0) {
                        totalBytes += read
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
