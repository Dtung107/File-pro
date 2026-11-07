package com.example.filemanager.gamedata

import com.example.filemanager.utils.FileUtils
import com.example.filemanager.utils.HashUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import kotlin.math.log2

data class GameFileAnalysis(
    val file: File,
    val identifiedEngineOrFormat: String,
    val headerMagicHex: String,
    val headerMagicAscii: String,
    val isEncryptedOrCompressed: Boolean,
    val entropyScore: Double, // 0 to 8 bits per byte
    val compressionCandidate: String?,
    val structureOverview: String,
    val fileSizeFormatted: String,
    val md5: String? = null,
    val sha1: String? = null,
    val sha256: String? = null
)

object GameDataExplorer {

    val GAME_EXTENSIONS = setOf(
        "apk", "obb", "pak", "asset", "assets", "bundle", "unity3d", "resource",
        "sav", "save", "dat", "bin", "db", "sqlite", "sqlite3", "json", "xml",
        "txt", "ini", "cfg", "zip", "7z", "rar", "wad", "bsp", "vfs"
    )

    fun isGameFile(file: File): Boolean {
        if (file.isDirectory) return false
        val ext = FileUtils.getExtension(file.name)
        return GAME_EXTENSIONS.contains(ext)
    }

    suspend fun analyzeGameFile(file: File, computeHashes: Boolean = true): Result<GameFileAnalysis> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || !file.canRead()) {
                return@withContext Result.failure(IllegalArgumentException("Không thể đọc tệp game"))
            }

            val header = FileUtils.readHeaderBytes(file, 64) ?: ByteArray(0)
            val headerHex = header.take(16).joinToString(" ") { "%02X".format(it) }
            val headerAscii = header.take(16).map { if (it in 32..126) it.toInt().toChar() else '·' }.joinToString("")

            val entropy = calculateShannonEntropy(header)
            val (engine, compression, overview) = detectGameFormat(file, header, entropy)

            // Entropy > 7.4 indicates high probability of encryption or heavy compression
            val isEncrypted = entropy > 7.4 && compression == null && !engine.contains("SQLite")

            var md5: String? = null
            var sha1: String? = null
            var sha256: String? = null

            if (computeHashes && file.length() <= 500 * 1024 * 1024) { // Up to 500MB auto hash
                val hashes = HashUtils.calculateAllHashes(file).getOrNull()
                md5 = hashes?.get("MD5")
                sha1 = hashes?.get("SHA-1")
                sha256 = hashes?.get("SHA-256")
            }

            val analysis = GameFileAnalysis(
                file = file,
                identifiedEngineOrFormat = engine,
                headerMagicHex = headerHex,
                headerMagicAscii = headerAscii,
                isEncryptedOrCompressed = isEncrypted || compression != null,
                entropyScore = entropy,
                compressionCandidate = compression,
                structureOverview = overview,
                fileSizeFormatted = com.example.filemanager.model.FileItem.formatBytes(file.length()),
                md5 = md5,
                sha1 = sha1,
                sha256 = sha256
            )

            Result.success(analysis)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun detectGameFormat(
        file: File,
        header: ByteArray,
        entropy: Double
    ): Triple<String, String?, String> {
        val ext = FileUtils.getExtension(file.name)
        val hex = header.joinToString("") { "%02X".format(it) }
        val ascii = header.map { if (it in 32..126) it.toInt().toChar() else ' ' }.joinToString("")

        // Check Unity
        if (ascii.startsWith("UnityFS")) {
            return Triple(
                "Unity Engine AssetBundle (UnityFS)",
                "LZ4 / LZMA nén bên trong",
                "AssetBundle dạng Unity hiện đại (chứa 3D mesh, textures, prefab, shaders)."
            )
        }
        if (ascii.startsWith("UnityWeb") || ascii.startsWith("UnityRaw")) {
            return Triple(
                "Unity Engine Legacy AssetBundle",
                "Deflate / Raw",
                "AssetBundle phiên bản trước của Unity Engine."
            )
        }

        // Check Unreal Engine PAK
        if (hex.startsWith("5A6F12E1") || hex.endsWith("5A6F12E1") || ext == "pak") {
            return Triple(
                "Unreal Engine PAK / VFS Archive",
                if (entropy > 7.0) "Zlib / Oodle / LZ4" else null,
                "Gói tài nguyên Unreal Engine (chứa uasset, uexp, umap, textures)."
            )
        }

        // Check SQLite
        if (hex.startsWith("53514C69746520666F726D6174203300")) {
            return Triple(
                "SQLite 3 Game Database",
                null,
                "Cơ sở dữ liệu cấu hình hoặc save state game. Mở được bằng SQLite Viewer."
            )
        }

        // Check Android DEX
        if (hex.startsWith("6465780A")) {
            return Triple(
                "Android Dalvik Executable (DEX)",
                null,
                "Mã bytecode biên dịch của trò chơi (Java/Kotlin)."
            )
        }

        // Check ELF .so
        if (hex.startsWith("7F454C46")) {
            val is64 = header.size > 4 && header[4].toInt() == 2
            return Triple(
                "Native Library ELF (.so) ${if (is64) "64-bit" else "32-bit"}",
                null,
                "Thư viện C/C++ native của game (e.g. libil2cpp.so, libmain.so)."
            )
        }

        // Check ZIP / APK / OBB
        if (hex.startsWith("504B0304")) {
            return Triple(
                if (ext == "obb") "Google Play Expansion File (OBB - ZIP format)" else "ZIP / APK Container",
                "Deflate",
                "Gói dữ liệu dạng nén chuẩn. Có thể duyệt các file con bằng Archive Manager."
            )
        }

        // Check ZLIB stream
        if (hex.startsWith("789C") || hex.startsWith("78DA") || hex.startsWith("7801") || hex.startsWith("785E")) {
            return Triple(
                "ZLIB Compressed Game Stream",
                "ZLIB / Deflate",
                "Dữ liệu nhị phân đã nén bằng thuật toán Zlib."
            )
        }

        // Check GZIP
        if (hex.startsWith("1F8B")) {
            return Triple(
                "GZIP Compressed Game Stream",
                "GZIP",
                "Dữ liệu nén chuẩn GZIP."
            )
        }

        // Check JSON / XML / Text script
        if (header.all { it in 9..13 || it in 32..126 }) {
            return Triple(
                "Plain Text / Game Config / Script",
                null,
                "File văn bản rõ: JSON, XML, Lua, INI hoặc cấu hình game. Mở được bằng Text Viewer."
            )
        }

        // Generic Binary or Encrypted
        val formatName = if (entropy > 7.5) {
            "Dữ liệu Game đã mã hóa / nén định dạng riêng (Entropy cao: %.2f)".format(entropy)
        } else {
            "Dữ liệu nhị phân Game ($ext) - Entropy: %.2f".format(entropy)
        }

        val notes = if (entropy > 7.5) {
            "Cảnh báo: Dữ liệu có mức độ hỗn loạn cao (entropy > 7.5), có thể đã được mã hóa bằng AES/XOR hoặc nén chuyên biệt bởi nhà phát triển game."
        } else {
            "Dữ liệu nhị phân chưa rõ cấu trúc header. Bạn có thể kiểm tra từng byte bằng Hex Editor."
        }

        return Triple(formatName, null, notes)
    }

    private fun calculateShannonEntropy(data: ByteArray): Double {
        if (data.isEmpty()) return 0.0
        val freq = IntArray(256)
        for (b in data) {
            freq[b.toInt() and 0xFF]++
        }
        var entropy = 0.0
        val len = data.size.toDouble()
        for (f in freq) {
            if (f > 0) {
                val p = f / len
                entropy -= p * (log2(p))
            }
        }
        return entropy
    }

    suspend fun findDuplicatesByHash(files: List<File>): Map<String, List<File>> = withContext(Dispatchers.IO) {
        val sizeMap = mutableMapOf<Long, MutableList<File>>()
        for (f in files) {
            if (f.isFile && f.length() > 0) {
                sizeMap.getOrPut(f.length()) { mutableListOf() }.add(f)
            }
        }

        val duplicates = mutableMapOf<String, MutableList<File>>()
        for ((_, candidateFiles) in sizeMap) {
            if (candidateFiles.size > 1) {
                for (cf in candidateFiles) {
                    val hash = HashUtils.calculateChecksum(cf, "SHA-256").getOrNull()
                    if (hash != null) {
                        duplicates.getOrPut(hash) { mutableListOf() }.add(cf)
                    }
                }
            }
        }

        duplicates.filter { it.value.size > 1 }
    }
}
