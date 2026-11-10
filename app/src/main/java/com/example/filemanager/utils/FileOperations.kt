package com.example.filemanager.utils

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import com.example.filemanager.model.StorageVolumeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object FileOperations {

    suspend fun copyFileOrDirectory(
        source: File,
        destDir: File,
        onProgress: ((progress: Float, currentFile: String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!source.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Tệp nguồn không tồn tại"))
            }

            destDir.mkdirs()
            val target = getUniqueFile(destDir, source.name)

            if (source.isDirectory) {
                val allFiles = mutableListOf<File>()
                collectFiles(source, allFiles)
                val totalBytes = allFiles.sumOf { it.length() }.coerceAtLeast(1L)
                var copiedBytes = 0L

                copyDirRecursive(source, target) { file, bytesCopied ->
                    copiedBytes += bytesCopied
                    val p = (copiedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    onProgress?.invoke(p, file.name)
                }
            } else {
                val totalBytes = source.length().coerceAtLeast(1L)
                var copiedBytes = 0L

                FileInputStream(source).use { fis ->
                    FileOutputStream(target).use { fos ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            copiedBytes += bytesRead
                            val p = (copiedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            onProgress?.invoke(p, source.name)
                        }
                    }
                }
            }

            Result.success(target)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveFileOrDirectory(
        source: File,
        destDir: File,
        onProgress: ((progress: Float, currentFile: String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (!source.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Tệp nguồn không tồn tại"))
            }
            destDir.mkdirs()
            val target = getUniqueFile(destDir, source.name)

            // Fast rename if same filesystem
            if (source.renameTo(target)) {
                onProgress?.invoke(1f, target.name)
                return@withContext Result.success(target)
            }

            // Fallback: Copy then delete
            val copyResult = copyFileOrDirectory(source, destDir, onProgress)
            if (copyResult.isSuccess) {
                source.deleteRecursively()
                Result.success(copyResult.getOrThrow())
            } else {
                Result.failure(copyResult.exceptionOrNull() ?: Exception("Lỗi khi di chuyển tệp"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteRecursively(file: File): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val success = file.deleteRecursively()
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rename(file: File, newName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val parent = file.parentFile ?: return@withContext Result.failure(Exception("Không có thư mục cha"))
            val target = File(parent, newName)
            if (target.exists()) {
                return@withContext Result.failure(Exception("Tên tệp đã tồn tại trong thư mục"))
            }
            val renamed = file.renameTo(target)
            if (renamed) Result.success(target) else Result.failure(Exception("Không thể đổi tên tệp"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createNewFolder(parentDir: File, name: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val newDir = File(parentDir, name)
            if (newDir.exists()) {
                return@withContext Result.failure(Exception("Thư mục đã tồn tại"))
            }
            val created = newDir.mkdirs()
            if (created) Result.success(newDir) else Result.failure(Exception("Không thể tạo thư mục mới"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createNewFile(parentDir: File, name: String, content: String = ""): Result<File> = withContext(Dispatchers.IO) {
        try {
            val newFile = File(parentDir, name)
            if (newFile.exists()) {
                return@withContext Result.failure(Exception("Tệp đã tồn tại"))
            }
            newFile.writeText(content, Charsets.UTF_8)
            Result.success(newFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun copyDirRecursive(sourceDir: File, targetDir: File, onFileChunk: (File, Int) -> Unit) {
        targetDir.mkdirs()
        sourceDir.listFiles()?.forEach { file ->
            val destChild = File(targetDir, file.name)
            if (file.isDirectory) {
                copyDirRecursive(file, destChild, onFileChunk)
            } else {
                FileInputStream(file).use { fis ->
                    FileOutputStream(destChild).use { fos ->
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        while (fis.read(buffer).also { bytesRead = it } != -1) {
                            fos.write(buffer, 0, bytesRead)
                            onFileChunk(file, bytesRead)
                        }
                    }
                }
            }
        }
    }

    private fun collectFiles(dir: File, list: MutableList<File>) {
        dir.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                collectFiles(f, list)
            } else {
                list.add(f)
            }
        }
    }

    private fun getUniqueFile(destDir: File, fileName: String): File {
        var file = File(destDir, fileName)
        if (!file.exists()) return file

        val nameWithoutExt = file.nameWithoutExtension
        val ext = file.extension
        val extSuffix = if (ext.isNotEmpty()) ".$ext" else ""
        var count = 1

        while (file.exists()) {
            file = File(destDir, "${nameWithoutExt}_$count$extSuffix")
            count++
        }
        return file
    }

    fun getStorageVolumes(context: Context): List<StorageVolumeInfo> {
        val volumes = mutableListOf<StorageVolumeInfo>()

        // Internal Storage
        try {
            val internalPath = Environment.getExternalStorageDirectory().absolutePath
            val stat = StatFs(internalPath)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            volumes.add(
                StorageVolumeInfo(
                    name = "Bộ nhớ trong",
                    path = internalPath,
                    totalBytes = blockSize * totalBlocks,
                    freeBytes = blockSize * availableBlocks,
                    isPrimary = true,
                    isRemovable = false
                )
            )
        } catch (e: Exception) {
            // Fallback
        }

        // Removable storage / SD Card
        try {
            val dirs = ContextCompat.getExternalFilesDirs(context, null)
            for (dir in dirs) {
                if (dir != null && Environment.isExternalStorageRemovable(dir)) {
                    val root = dir.parentFile?.parentFile?.parentFile?.parentFile
                    if (root != null && root.canRead()) {
                        val stat = StatFs(root.absolutePath)
                        val blockSize = stat.blockSizeLong
                        val totalBlocks = stat.blockCountLong
                        val availableBlocks = stat.availableBlocksLong

                        volumes.add(
                            StorageVolumeInfo(
                                name = "Thẻ nhớ SD",
                                path = root.absolutePath,
                                totalBytes = blockSize * totalBlocks,
                                freeBytes = blockSize * availableBlocks,
                                isPrimary = false,
                                isRemovable = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback
        }

        return volumes
    }
}
