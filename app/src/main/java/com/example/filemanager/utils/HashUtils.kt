package com.example.filemanager.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object HashUtils {

    suspend fun calculateChecksum(
        file: File,
        algorithm: String, // "MD5", "SHA-1", "SHA-256"
        onProgress: ((progress: Float, processedBytes: Long, totalBytes: Long) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || !file.canRead()) {
                return@withContext Result.failure(IllegalArgumentException("Không thể đọc tệp"))
            }

            val digest = MessageDigest.getInstance(algorithm)
            val totalBytes = file.length()
            var processedBytes = 0L

            FileInputStream(file).use { fis ->
                val buffer = ByteArray(64 * 1024) // 64 KB buffer
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                    processedBytes += bytesRead
                    if (totalBytes > 0 && onProgress != null) {
                        val progress = (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        onProgress(progress, processedBytes, totalBytes)
                    }
                }
            }

            val hashBytes = digest.digest()
            val hexString = hashBytes.joinToString("") { "%02x".format(it) }
            Result.success(hexString)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun calculateAllHashes(
        file: File,
        onProgress: ((progress: Float) -> Unit)? = null
    ): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val md5 = MessageDigest.getInstance("MD5")
            val sha1 = MessageDigest.getInstance("SHA-1")
            val sha256 = MessageDigest.getInstance("SHA-256")

            val totalBytes = file.length()
            var processedBytes = 0L

            FileInputStream(file).use { fis ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    md5.update(buffer, 0, bytesRead)
                    sha1.update(buffer, 0, bytesRead)
                    sha256.update(buffer, 0, bytesRead)
                    processedBytes += bytesRead
                    if (totalBytes > 0 && onProgress != null) {
                        onProgress((processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }

            val result = mapOf(
                "MD5" to md5.digest().joinToString("") { "%02x".format(it) },
                "SHA-1" to sha1.digest().joinToString("") { "%02x".format(it) },
                "SHA-256" to sha256.digest().joinToString("") { "%02x".format(it) }
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun compareFiles(
        file1: File,
        file2: File,
        onProgress: ((Float) -> Unit)? = null
    ): ComparisonResult = withContext(Dispatchers.IO) {
        if (!file1.exists() || !file2.exists()) {
            return@withContext ComparisonResult(
                isEqual = false,
                size1 = file1.length(),
                size2 = file2.length(),
                notes = "Một hoặc cả hai tệp không tồn tại"
            )
        }

        val size1 = file1.length()
        val size2 = file2.length()
        if (size1 != size2) {
            return@withContext ComparisonResult(
                isEqual = false,
                size1 = size1,
                size2 = size2,
                notes = "Dung lượng khác nhau (${file1.name}: $size1 bytes vs ${file2.name}: $size2 bytes)"
            )
        }

        // Compare hashes
        val hash1 = calculateChecksum(file1, "SHA-256") { p, _, _ -> onProgress?.invoke(p * 0.5f) }.getOrNull()
        val hash2 = calculateChecksum(file2, "SHA-256") { p, _, _ -> onProgress?.invoke(0.5f + p * 0.5f) }.getOrNull()

        val isMatch = hash1 != null && hash1.equals(hash2, ignoreCase = true)
        ComparisonResult(
            isEqual = isMatch,
            size1 = size1,
            size2 = size2,
            hash1 = hash1 ?: "Lỗi tính hash",
            hash2 = hash2 ?: "Lỗi tính hash",
            notes = if (isMatch) "Tệp hoàn toàn trùng khớp (SHA-256 giống nhau)" else "Nội dung tệp khác nhau (SHA-256 không khớp)"
        )
    }

    data class ComparisonResult(
        val isEqual: Boolean,
        val size1: Long,
        val size2: Long,
        val hash1: String = "",
        val hash2: String = "",
        val notes: String = ""
    )
}
