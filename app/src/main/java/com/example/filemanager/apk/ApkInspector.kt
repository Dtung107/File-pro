package com.example.filemanager.apk

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import com.example.filemanager.utils.HashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipFile

data class ApkDetails(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val fileSizeBytes: Long,
    val sha256Checksum: String,
    val architectures: List<String>,
    val permissions: List<String>,
    val activities: List<String>,
    val services: List<String>,
    val receivers: List<String>,
    val providers: List<String>,
    val certificateInfo: String,
    val dexFiles: List<String>,
    val nativeLibs: List<String>,
    val internalFiles: List<ApkEntryItem>,
    val iconBitmap: Bitmap? = null
)

data class ApkEntryItem(
    val name: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean
)

object ApkInspector {

    suspend fun analyzeApk(context: Context, apkFile: File): Result<ApkDetails> = withContext(Dispatchers.IO) {
        try {
            if (!apkFile.exists() || !apkFile.canRead()) {
                return@withContext Result.failure(IllegalArgumentException("Không thể đọc tệp APK: ${apkFile.name}"))
            }

            val pm = context.packageManager
            val flags = PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        PackageManager.GET_SIGNING_CERTIFICATES
                    } else {
                        @Suppress("DEPRECATION")
                        PackageManager.GET_SIGNATURES
                    }

            val packageInfo: PackageInfo? = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            val appInfo: ApplicationInfo? = packageInfo?.applicationInfo

            // Set sourceDir to allow loading resources and icons
            appInfo?.sourceDir = apkFile.absolutePath
            appInfo?.publicSourceDir = apkFile.absolutePath

            val appName = if (appInfo != null) {
                try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    apkFile.nameWithoutExtension
                }
            } else {
                apkFile.nameWithoutExtension
            }

            val packageName = packageInfo?.packageName ?: "Không xác định"
            val versionName = packageInfo?.versionName ?: "1.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode ?: 0L
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toLong() ?: 0L
            }

            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appInfo?.minSdkVersion ?: 0
            } else 0

            val targetSdk = appInfo?.targetSdkVersion ?: 0

            // Load icon
            val iconDrawable: Drawable? = try {
                appInfo?.loadIcon(pm)
            } catch (e: Exception) {
                null
            }

            val iconBitmap: Bitmap? = iconDrawable?.let { drawableToBitmap(it) }

            // Extract components
            val permissions = packageInfo?.requestedPermissions?.toList() ?: emptyList()
            val activities = packageInfo?.activities?.map { it.name } ?: emptyList()
            val services = packageInfo?.services?.map { it.name } ?: emptyList()
            val receivers = packageInfo?.receivers?.map { it.name } ?: emptyList()
            val providers = packageInfo?.providers?.map { it.name } ?: emptyList()

            // Analyze internal files via ZipFile
            val dexFiles = mutableListOf<String>()
            val nativeLibs = mutableListOf<String>()
            val internalFiles = mutableListOf<ApkEntryItem>()
            val architectures = mutableSetOf<String>()

            ZipFile(apkFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    internalFiles.add(
                        ApkEntryItem(
                            name = entry.name,
                            size = entry.size,
                            compressedSize = entry.compressedSize,
                            isDirectory = entry.isDirectory
                        )
                    )

                    if (entry.name.endsWith(".dex")) {
                        dexFiles.add(entry.name)
                    }

                    if (entry.name.startsWith("lib/") && !entry.isDirectory) {
                        nativeLibs.add(entry.name)
                        val parts = entry.name.split("/")
                        if (parts.size >= 2) {
                            architectures.add(parts[1])
                        }
                    }
                }
            }

            // Extract Certificate info
            val certInfo = extractCertificateInfo(apkFile, packageInfo)

            // SHA-256 of the entire APK file
            val sha256 = HashUtils.calculateChecksum(apkFile, "SHA-256").getOrDefault("N/A")

            val details = ApkDetails(
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                fileSizeBytes = apkFile.length(),
                sha256Checksum = sha256,
                architectures = architectures.toList().sorted(),
                permissions = permissions.sorted(),
                activities = activities,
                services = services,
                receivers = receivers,
                providers = providers,
                certificateInfo = certInfo,
                dexFiles = dexFiles.sorted(),
                nativeLibs = nativeLibs.sorted(),
                internalFiles = internalFiles.sortedBy { it.name },
                iconBitmap = iconBitmap
            )

            Result.success(details)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractCertificateInfo(apkFile: File, packageInfo: PackageInfo?): String {
        return try {
            // First try via signingInfo or signatures
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.signatures
            }

            if (!signatures.isNullOrEmpty()) {
                val certFactory = CertificateFactory.getInstance("X.509")
                val sb = StringBuilder()
                for (sig in signatures) {
                    val cert = certFactory.generateCertificate(sig.toByteArray().inputStream()) as? X509Certificate
                    if (cert != null) {
                        val mdSha256 = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                        val fingerprint = mdSha256.joinToString(":") { "%02X".format(it) }
                        sb.append("Chủ thể: ${cert.subjectDN.name}\n")
                        sb.append("Nhà phát hành: ${cert.issuerDN.name}\n")
                        sb.append("Hiệu lực từ: ${cert.notBefore} đến ${cert.notAfter}\n")
                        sb.append("SHA-256 Fingerprint:\n$fingerprint\n\n")
                    }
                }
                return sb.toString().trim()
            }

            // Fallback: check META-INF inside zip
            ZipFile(apkFile).use { zip ->
                val certEntry = zip.entries().asSequence().find {
                    it.name.startsWith("META-INF/") && (it.name.endsWith(".RSA") || it.name.endsWith(".DSA"))
                }
                if (certEntry != null) {
                    return "Chứng chỉ tìm thấy trong APK: ${certEntry.name} (${certEntry.size} bytes)"
                }
            }

            "Không tìm thấy thông tin chứng chỉ hoặc APK chưa được ký"
        } catch (e: Exception) {
            "Lỗi phân tích chứng chỉ: ${e.message}"
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
