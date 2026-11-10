package com.example.filemanager.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import java.io.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.*

object NetworkSharingManager {

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var isRunning = false
    private var currentSharedDir: File? = null

    val isServerRunning: Boolean get() = isRunning

    fun getLocalIpAddress(context: Context): String {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiIp = wifiManager?.connectionInfo?.ipAddress
            if (wifiIp != null && wifiIp != 0) {
                return String.format(
                    Locale.US,
                    "%d.%d.%d.%d",
                    wifiIp and 0xff,
                    wifiIp shr 8 and 0xff,
                    wifiIp shr 16 and 0xff,
                    wifiIp shr 24 and 0xff
                )
            }

            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return "127.0.0.1"
    }

    fun startServer(sharedDir: File, port: Int = 8080): Boolean {
        if (isRunning) return true
        currentSharedDir = sharedDir
        return try {
            serverSocket = ServerSocket(port)
            isRunning = true

            serverJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive && isRunning) {
                    try {
                        val clientSocket = serverSocket?.accept() ?: break
                        launch {
                            handleClient(clientSocket, currentSharedDir ?: sharedDir)
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
            }
            true
        } catch (e: Exception) {
            isRunning = false
            false
        }
    }

    fun stopServer() {
        isRunning = false
        try {
            serverJob?.cancel()
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        } finally {
            serverSocket = null
            serverJob = null
        }
    }

    private fun handleClient(socket: Socket, rootDir: File) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val output = BufferedOutputStream(s.getOutputStream())

                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val requestedPath = java.net.URLDecoder.decode(parts[1], "UTF-8").trimStart('/')
                val targetFile = if (requestedPath.isEmpty()) rootDir else File(rootDir, requestedPath)

                // Guard against directory traversal
                if (!targetFile.canonicalPath.startsWith(rootDir.canonicalPath)) {
                    sendResponse(output, 403, "Forbidden", "text/plain", "Truy cập bị từ chối".toByteArray())
                    return
                }

                if (!targetFile.exists()) {
                    sendResponse(output, 404, "Not Found", "text/plain", "Không tìm thấy tệp".toByteArray())
                    return
                }

                if (targetFile.isDirectory) {
                    val html = buildDirectoryHtml(targetFile, rootDir)
                    sendResponse(output, 200, "OK", "text/html; charset=UTF-8", html.toByteArray())
                } else {
                    val mime = FileUtils.getMimeType(targetFile.name)
                    val fileBytes = targetFile.readBytes()
                    sendResponse(output, 200, "OK", mime, fileBytes)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun buildDirectoryHtml(dir: File, rootDir: File): String {
        val files = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })) ?: emptyList()
        val relPath = dir.relativeToOrNull(rootDir)?.path ?: ""

        val sb = StringBuilder()
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><title>File Manager Pro - Wi-Fi Share</title>")
        sb.append("<meta name='viewport' content='width=device-width, initial-scale=1'>")
        sb.append("<style>")
        sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #121316; color: #E1E3EB; padding: 20px; }")
        sb.append("h2 { color: #FFB300; }")
        sb.append("a { color: #4D88FF; text-decoration: none; }")
        sb.append("a:hover { text-decoration: underline; }")
        sb.append("ul { list-style: none; padding: 0; }")
        sb.append("li { padding: 10px 12px; border-bottom: 1px solid #232732; display: flex; justify-content: space-between; }")
        sb.append(".dir { font-weight: bold; color: #FFB300; }")
        sb.append("</style></head><body>")
        sb.append("<h2>📁 File Manager Pro - Chia sẻ Wi-Fi</h2>")
        sb.append("<p>Đang duyệt: <code>/${relPath}</code></p>")

        if (dir != rootDir && dir.parentFile != null) {
            val parentRel = dir.parentFile?.relativeToOrNull(rootDir)?.path ?: ""
            sb.append("<p><a href='/$parentRel'>⬅ Quay lại thư mục cha</a></p>")
        }

        sb.append("<ul>")
        for (f in files) {
            val fRel = f.relativeToOrNull(rootDir)?.path ?: f.name
            val icon = if (f.isDirectory) "📁" else "📄"
            val cssClass = if (f.isDirectory) "dir" else "file"
            val sizeStr = if (f.isDirectory) "${f.listFiles()?.size ?: 0} mục" else "${f.length() / 1024} KB"
            sb.append("<li><a class='$cssClass' href='/$fRel'>$icon ${f.name}</a><span>$sizeStr</span></li>")
        }
        sb.append("</ul></body></html>")
        return sb.toString()
    }

    private fun sendResponse(output: OutputStream, code: Int, status: String, contentType: String, data: ByteArray) {
        val writer = PrintWriter(output)
        writer.print("HTTP/1.1 $code $status\r\n")
        writer.print("Content-Type: $contentType\r\n")
        writer.print("Content-Length: ${data.size}\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()
        output.write(data)
        output.flush()
    }
}
