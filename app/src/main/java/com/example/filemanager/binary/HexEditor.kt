package com.example.filemanager.binary

import com.example.filemanager.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

data class HexRow(
    val offset: Long,
    val offsetFormatted: String, // e.g., "00000000"
    val bytes: ByteArray,
    val hexString: String,       // e.g., "4D 5A 90 00 03 00 00 00 04 00 00 00 FF FF 00 00"
    val asciiString: String      // e.g., "MZ.............."
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HexRow
        return offset == other.offset && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = offset.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

data class HexPage(
    val rows: List<HexRow>,
    val startOffset: Long,
    val endOffset: Long,
    val totalFileSize: Long,
    val totalPages: Int,
    val currentPageIndex: Int
)

object HexEditor {

    const val BYTES_PER_ROW = 16
    const val ROWS_PER_PAGE = 32 // 512 bytes per page for super smooth rendering

    suspend fun readHexPage(
        file: File,
        pageIndex: Int,
        rowsPerPage: Int = ROWS_PER_PAGE
    ): Result<HexPage> = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || !file.canRead()) {
                return@withContext Result.failure(IllegalArgumentException("Không thể đọc tệp nhị phân"))
            }

            val totalSize = file.length()
            val bytesPerPage = rowsPerPage * BYTES_PER_ROW
            val totalPages = if (totalSize == 0L) 1 else (((totalSize - 1) / bytesPerPage) + 1).toInt()
            val safePage = pageIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
            val startOffset = safePage.toLong() * bytesPerPage

            val rows = mutableListOf<HexRow>()
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(startOffset)
                val buffer = ByteArray(BYTES_PER_ROW)

                for (rowIndex in 0 until rowsPerPage) {
                    val currentOffset = startOffset + (rowIndex * BYTES_PER_ROW)
                    if (currentOffset >= totalSize) break

                    val bytesToRead = (totalSize - currentOffset).coerceAtMost(BYTES_PER_ROW.toLong()).toInt()
                    val read = raf.read(buffer, 0, bytesToRead)
                    if (read <= 0) break

                    val actualBytes = buffer.copyOf(read)
                    val offsetHex = "%08X".format(currentOffset)

                    val hexSb = StringBuilder()
                    val asciiSb = StringBuilder()

                    for (i in 0 until BYTES_PER_ROW) {
                        if (i < read) {
                            val b = actualBytes[i].toInt() and 0xFF
                            hexSb.append("%02X ".format(b))
                            // Printable ASCII range (32 to 126)
                            if (b in 32..126) {
                                asciiSb.append(b.toChar())
                            } else {
                                asciiSb.append('·')
                            }
                        } else {
                            hexSb.append("   ")
                            asciiSb.append(' ')
                        }
                        if (i == 7) hexSb.append(" ") // separator gap
                    }

                    rows.add(
                        HexRow(
                            offset = currentOffset,
                            offsetFormatted = offsetHex,
                            bytes = actualBytes,
                            hexString = hexSb.toString().trimEnd(),
                            asciiString = asciiSb.toString()
                        )
                    )
                }
            }

            val endOffset = if (rows.isNotEmpty()) rows.last().offset + rows.last().bytes.size else startOffset

            Result.success(
                HexPage(
                    rows = rows,
                    startOffset = startOffset,
                    endOffset = endOffset,
                    totalFileSize = totalSize,
                    totalPages = totalPages,
                    currentPageIndex = safePage
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchPattern(
        file: File,
        searchQuery: String,
        isHexSearch: Boolean,
        maxResults: Int = 50
    ): Result<List<Long>> = withContext(Dispatchers.IO) {
        try {
            val searchBytes = if (isHexSearch) {
                // Parse hex string e.g. "4D 5A" or "4D5A"
                val cleaned = searchQuery.replace(" ", "").replace("0x", "")
                if (cleaned.length % 2 != 0) {
                    return@withContext Result.failure(IllegalArgumentException("Chuỗi HEX không hợp lệ (số lượng ký tự phải chẵn)"))
                }
                ByteArray(cleaned.length / 2) { i ->
                    cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
            } else {
                searchQuery.toByteArray(Charsets.UTF_8)
            }

            if (searchBytes.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            val matches = mutableListOf<Long>()
            val buffer = ByteArray(64 * 1024)
            var currentFileOffset = 0L

            RandomAccessFile(file, "r").use { raf ->
                val overlap = searchBytes.size - 1
                var bytesRead: Int

                while (raf.read(buffer).also { bytesRead = it } != -1 && matches.size < maxResults) {
                    for (i in 0..(bytesRead - searchBytes.size)) {
                        var found = true
                        for (j in searchBytes.indices) {
                            if (buffer[i + j] != searchBytes[j]) {
                                found = false
                                break
                            }
                        }
                        if (found) {
                            matches.add(currentFileOffset + i)
                            if (matches.size >= maxResults) break
                        }
                    }

                    currentFileOffset += (bytesRead - overlap).coerceAtLeast(0)
                    if (raf.filePointer < raf.length() && overlap > 0) {
                        raf.seek(currentFileOffset)
                    }
                }
            }

            Result.success(matches)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveModifiedByte(
        sourceFile: File,
        targetFile: File,
        offset: Long,
        newByte: Byte
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // If target is different, copy first
            if (sourceFile.absolutePath != targetFile.absolutePath) {
                sourceFile.copyTo(targetFile, overwrite = true)
            }
            RandomAccessFile(targetFile, "rw").use { raf ->
                raf.seek(offset)
                raf.write(newByte.toInt())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getHeaderAnalysis(file: File): String {
        val header = FileUtils.readHeaderBytes(file, 32) ?: return "Không thể đọc header"
        val magic = FileUtils.identifyHeaderMagic(header)
        val hex = header.joinToString(" ") { "%02X".format(it) }
        val ascii = header.map { if (it in 32..126) it.toInt().toChar() else '.' }.joinToString("")

        return "Loại nhận diện: $magic\n\n" +
                "Header Hex (32 bytes):\n$hex\n\n" +
                "Header ASCII:\n$ascii\n\n" +
                "Dung lượng: ${FileUtils.detectFileType(file).name} - ${file.length()} bytes"
    }
}
