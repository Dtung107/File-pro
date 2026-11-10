package com.example.filemanager.ui.gamedata

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.gamedata.GameDataExplorer
import com.example.filemanager.gamedata.GameFileAnalysis
import com.example.filemanager.model.FileItem
import com.example.filemanager.utils.FileUtils
import com.example.filemanager.utils.HashUtils
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDataScreen(
    currentDirectory: File,
    onBack: () -> Unit,
    onOpenHexViewer: (File) -> Unit,
    onOpenTextViewer: (File) -> Unit,
    onOpenDatabaseViewer: (File) -> Unit,
    onOpenArchiveViewer: (File) -> Unit,
    onOpenApkInspector: (File) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var gameFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("Tất cả") }

    var activeAnalysis by remember { mutableStateOf<GameFileAnalysis?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    val filterChips = listOf(
        "Tất cả",
        "APK",
        "OBB",
        "DB",
        "DAT / BIN",
        "BAT",
        "PAK",
        "BUNDLE",
        "ASSET",
        "ZIP",
        "Khác"
    )

    fun scanFiles() {
        isLoading = true
        coroutineScope.launch {
            val list = withContext(Dispatchers.IO) {
                val found = mutableListOf<File>()
                fun scan(dir: File, depth: Int) {
                    if (depth > 3) return
                    dir.listFiles()?.forEach { f ->
                        if (f.isDirectory) {
                            if (!f.name.startsWith(".")) scan(f, depth + 1)
                        } else if (GameDataExplorer.isGameFile(f)) {
                            found.add(f)
                        }
                    }
                }
                scan(currentDirectory, 0)
                found.sortedByDescending { it.length() }
            }
            gameFiles = list
            isLoading = false
        }
    }

    LaunchedEffect(currentDirectory) {
        scanFiles()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    if (isSearchActive) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Tìm tệp game...", color = TextMuted, fontSize = 14.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    } else {
                        Column {
                            Text(
                                "Dữ liệu Game",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${gameFiles.size} tệp • ${currentDirectory.name}",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isSearchActive = !isSearchActive
                        if (!isSearchActive) searchQuery = ""
                    }) {
                        Icon(
                            if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Tìm kiếm",
                            tint = TextPrimary
                        )
                    }
                    IconButton(onClick = { scanFiles() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Quét lại", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
            )
        },
        containerColor = CharcoalBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Horizontal Filter Chips
            Surface(
                color = CharcoalSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                LazyRow(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterChips) { chip ->
                        val isSelected = selectedFilter == chip
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) ElectricBlue else CharcoalSurfaceVariant,
                            modifier = Modifier.clickable { selectedFilter = chip }
                        ) {
                            Text(
                                text = chip,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else TextSecondary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricBlue)
                }
            } else {
                val filtered = remember(gameFiles, searchQuery, selectedFilter) {
                    gameFiles.filter { file ->
                        val ext = FileUtils.getExtension(file.name).lowercase()
                        val matchSearch = searchQuery.isBlank() || file.name.contains(searchQuery, ignoreCase = true)
                        val matchFilter = when (selectedFilter) {
                            "APK" -> ext == "apk"
                            "OBB" -> ext == "obb"
                            "DB" -> ext in setOf("db", "sqlite", "sqlite3")
                            "DAT / BIN" -> ext in setOf("dat", "bin", "raw")
                            "BAT" -> ext in setOf("bat", "cmd")
                            "PAK" -> ext == "pak"
                            "BUNDLE" -> ext in setOf("bundle", "unity3d")
                            "ASSET" -> ext in setOf("asset", "assets", "wad")
                            "ZIP" -> ext in setOf("zip", "7z", "rar", "tar")
                            "Khác" -> ext !in setOf("apk", "obb", "db", "sqlite", "sqlite3", "dat", "bin", "raw", "bat", "cmd", "pak", "bundle", "unity3d", "asset", "assets", "wad", "zip", "7z", "rar", "tar")
                            else -> true // "Tất cả"
                        }
                        matchSearch && matchFilter
                    }
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SportsEsports, contentDescription = null, tint = TextMuted, modifier = Modifier.size(56.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Không tìm thấy tệp game phù hợp", color = TextMuted, fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(filtered, key = { it.absolutePath }) { file ->
                            GameFileListItem(
                                file = file,
                                onOpen = { openFileByExtension(file, onOpenApkInspector, onOpenDatabaseViewer, onOpenArchiveViewer, onOpenTextViewer, onOpenHexViewer) },
                                onAnalyze = {
                                    isAnalyzing = true
                                    coroutineScope.launch {
                                        val res = GameDataExplorer.analyzeGameFile(file)
                                        if (res.isSuccess) {
                                            activeAnalysis = res.getOrNull()
                                        } else {
                                            snackbarHostState.showSnackbar("Lỗi phân tích: ${res.exceptionOrNull()?.message}")
                                        }
                                        isAnalyzing = false
                                    }
                                },
                                onOpenHex = { onOpenHexViewer(file) },
                                onDelete = { fileToDelete = file }
                            )
                            HorizontalDivider(color = CharcoalDivider, thickness = 0.5.dp, modifier = Modifier.padding(start = 58.dp))
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (fileToDelete != null) {
        val f = fileToDelete!!
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Xác nhận xóa tệp", color = TextPrimary) },
            text = { Text("Bạn có chắc chắn muốn xóa tệp '${f.name}' vĩnh viễn?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val deleted = f.delete()
                        if (deleted) {
                            gameFiles = gameFiles.filter { it.absolutePath != f.absolutePath }
                            coroutineScope.launch { snackbarHostState.showSnackbar("Đã xóa tệp: ${f.name}") }
                        } else {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Không thể xóa tệp (kiểm tra quyền truy cập)") }
                        }
                        fileToDelete = null
                    }
                ) {
                    Text("Xóa", color = ColorErrorRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Hủy", color = TextMuted)
                }
            },
            containerColor = CharcoalSurface
        )
    }

    // Detailed Analysis Dialog
    if (activeAnalysis != null) {
        val analysis = activeAnalysis!!
        AlertDialog(
            onDismissRequest = { activeAnalysis = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = analysis.file.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        AnalysisCardSection(title = "Định dạng / Engine nhận diện") {
                            Text(
                                text = analysis.identifiedEngineOrFormat,
                                color = ColorSuccessGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(analysis.structureOverview, color = TextSecondary, fontSize = 12.sp)
                        }
                    }

                    item {
                        AnalysisCardSection(title = "Độ hỗn loạn Entropy") {
                            val entropyStatus = if (analysis.entropyScore > 7.4) " [Đã mã hóa hoặc nén cao]" else " [Bình thường]"
                            Text(
                                text = "%.2f / 8.0 bits/byte%s".format(analysis.entropyScore, entropyStatus),
                                color = if (analysis.entropyScore > 7.4) ColorWarningAmber else CyanAccent,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            if (analysis.compressionCandidate != null) {
                                Text("Khả năng nén: ${analysis.compressionCandidate}", color = ColorSuccessGreen, fontSize = 12.sp)
                            }
                        }
                    }

                    item {
                        AnalysisCardSection(title = "Magic Header Bytes (16 bytes đầu)") {
                            Text(
                                text = analysis.headerMagicHex,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = ElectricBlue
                            )
                            Text(
                                text = "ASCII: ${analysis.headerMagicAscii}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextSecondary
                            )
                        }
                    }

                    if (analysis.md5 != null || analysis.sha256 != null) {
                        item {
                            AnalysisCardSection(title = "Checksums (Mã băm)") {
                                if (analysis.md5 != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("MD5: ", color = TextMuted, fontSize = 11.sp)
                                        Text(analysis.md5, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary)
                                    }
                                }
                                if (analysis.sha256 != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("SHA-256: ", color = TextMuted, fontSize = 11.sp)
                                        Text(analysis.sha256, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CyanAccent)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val f = analysis.file
                            activeAnalysis = null
                            openFileByExtension(f, onOpenApkInspector, onOpenDatabaseViewer, onOpenArchiveViewer, onOpenTextViewer, onOpenHexViewer)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Text("Mở tệp")
                    }
                    TextButton(onClick = { activeAnalysis = null }) {
                        Text("Đóng", color = TextMuted)
                    }
                }
            },
            containerColor = CharcoalSurface
        )
    }
}

private fun openFileByExtension(
    file: File,
    onOpenApkInspector: (File) -> Unit,
    onOpenDatabaseViewer: (File) -> Unit,
    onOpenArchiveViewer: (File) -> Unit,
    onOpenTextViewer: (File) -> Unit,
    onOpenHexViewer: (File) -> Unit
) {
    val ext = FileUtils.getExtension(file.name).lowercase()
    when (ext) {
        "apk" -> onOpenApkInspector(file)
        "db", "sqlite", "sqlite3" -> onOpenDatabaseViewer(file)
        "zip", "7z", "rar", "obb" -> onOpenArchiveViewer(file)
        "bat", "cmd", "json", "xml", "txt", "ini", "cfg", "log", "properties", "lua", "py", "sh" -> onOpenTextViewer(file)
        "dat" -> {
            if (FileUtils.isLikelyTextFile(file)) {
                onOpenTextViewer(file)
            } else {
                onOpenHexViewer(file)
            }
        }
        else -> onOpenHexViewer(file)
    }
}

@Composable
private fun AnalysisCardSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = CharcoalSurfaceHighlight,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, color = TextMuted, fontSize = 11.sp)
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun GameFileListItem(
    file: File,
    onOpen: () -> Unit,
    onAnalyze: () -> Unit,
    onOpenHex: () -> Unit,
    onDelete: () -> Unit
) {
    val ext = FileUtils.getExtension(file.name).uppercase()
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Format badge
        Surface(
            color = when (ext) {
                "OBB", "PAK" -> ElectricBlue
                "ASSET", "BUNDLE", "UNITY3D" -> ColorPurpleBadge
                "DB", "SQLITE", "SQLITE3" -> ColorSuccessGreen
                "APK" -> ColorSuccessGreen
                "DAT", "BIN", "RAW" -> ColorOrangeBadge
                "ZIP", "7Z", "RAR" -> ElectricBlue
                else -> CharcoalSurfaceHighlight
            },
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.size(width = 44.dp, height = 30.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (ext.length > 5) ext.take(4) else ext.ifEmpty { "FILE" },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // File name & details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${ext.ifEmpty { "Tệp" }} • ${FileItem.formatBytes(file.length())} • ${FileItem.formatDate(file.lastModified())}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 3-dots action menu
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu tệp", tint = TextMuted)
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(CharcoalSurface)
            ) {
                DropdownMenuItem(
                    text = { Text("Phân tích chi tiết", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onAnalyze()
                    },
                    leadingIcon = { Icon(Icons.Default.Analytics, contentDescription = null, tint = ElectricBlue) }
                )
                DropdownMenuItem(
                    text = { Text("Mở Hex Viewer", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onOpenHex()
                    },
                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = CyanAccent) }
                )
                DropdownMenuItem(
                    text = { Text("Mở tệp", color = TextPrimary) },
                    onClick = {
                        showMenu = false
                        onOpen()
                    },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null, tint = TextPrimary) }
                )
                HorizontalDivider(color = CharcoalDivider)
                DropdownMenuItem(
                    text = { Text("Xóa", color = ColorErrorRed) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ColorErrorRed) }
                )
            }
        }
    }
}
