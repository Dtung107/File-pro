package com.example.filemanager.apk

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

object ApkInstaller {

    fun installApk(context: Context, apkFile: File): Result<Unit> {
        return try {
            if (!apkFile.exists() || apkFile.length() == 0L) {
                return Result.failure(IllegalArgumentException("Tệp APK không tồn tại hoặc bị rỗng"))
            }

            // Check Unknown Sources permission on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return Result.failure(SecurityException("Cần cấp quyền 'Cài đặt ứng dụng không rõ nguồn gốc' cho File Manager Pro"))
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun findApksInZip(zipFile: File): List<String> = withContext(Dispatchers.IO) {
        val apkPaths = mutableListOf<String>()
        try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (!e.isDirectory && e.name.endsWith(".apk", ignoreCase = true)) {
                        apkPaths.add(e.name)
                    }
                }
            }
        } catch (_: Exception) {}
        apkPaths
    }

    suspend fun extractAndInstallFromZip(
        context: Context,
        zipFile: File,
        apkEntryPath: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val installDir = File(context.cacheDir, "apk_install").apply { mkdirs() }
            val cleanName = apkEntryPath.trimEnd('/').substringAfterLast('/')
            val tempApk = File(installDir, cleanName)

            ZipFile(zipFile).use { zip ->
                val entry = zip.getEntry(apkEntryPath)
                    ?: return@withContext Result.failure(IllegalArgumentException("Không tìm thấy entry $apkEntryPath trong ZIP"))

                BufferedInputStream(zip.getInputStream(entry), 128 * 1024).use { bis ->
                    BufferedOutputStream(FileOutputStream(tempApk), 128 * 1024).use { bos ->
                        val buffer = ByteArray(128 * 1024)
                        var read: Int
                        while (bis.read(buffer).also { read = it } != -1) {
                            bos.write(buffer, 0, read)
                        }
                    }
                }
            }

            // Launch package installer on Main thread
            withContext(Dispatchers.Main) {
                installApk(context, tempApk)
            }

            Result.success(tempApk)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
