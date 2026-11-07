package com.example.filemanager.ui.analyzer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.gamedata.GameDataExplorer
import com.example.filemanager.model.FileItem
import com.example.filemanager.utils.FileUtils
import com.example.filemanager.utils.HashUtils
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class ActiveToolView {
    data object None : ActiveToolView()
    data object StorageAnalyzer : ActiveToolView()
    data object HashAndCompare : ActiveToolView()
    data object DuplicateFinder : ActiveToolView()
    data object ApkPicker : ActiveToolView()
    data object DatabasePicker : ActiveToolView()
    data object HexPicker : ActiveToolView()
}

data class ToolItemDef(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconTint: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerScreen(
    currentDirectory: File,
    onBack: () -> Unit,
    onOpenHexViewer: (File) -> Unit,
    onOpenApkInspector: (File) -> Unit = {},
    onOpenDatabaseViewer: (File) -> Unit = {},
    onOpenGameData: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeSubView by remember { mutableStateOf<ActiveToolView>(ActiveToolView.None) }

    val tools = remember {
        listOf(
            ToolItemDef("storage", "Phân tích bộ nhớ", "Thống kê dung lượng theo danh mục tệp", Icons.Default.PieChart, ElectricBlue),
            ToolItemDef("apk", "Trình kiểm tra APK", "Phân tích package, version, permissions, signatures", Icons.Default.Android, ColorSuccessGreen),
            ToolItemDef("db", "Trình xem Database", "Duyệt bảng, cấu trúc schema và truy vấn SQLite", Icons.Default.Storage, ColorSuccessGreen),
            ToolItemDef("hex", "Trình xem Hex", "Xem và chỉnh sửa nhị phân chi tiết từng byte", Icons.Default.Code, CyanAccent),
            ToolItemDef("game", "Trình xem dữ liệu Game", "Phân tích OBB, PAK, Asset, Entropy và cấu trúc game", Icons.Default.SportsEsports, ColorPurpleBadge),
            ToolItemDef("hash", "Tính toán & so sánh Hash", "Tính MD5, SHA-1, SHA-256 và so sánh 2 tệp", Icons.Default.Fingerprint, ElectricBlue),
            ToolItemDef("duplicates", "Tìm tệp trùng lặp", "Quét nhanh các tệp có nội dung giống nhau bằng SHA-256", Icons.Default.Difference, ColorWarningAmber)
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    val titleText = when (activeSubView) {
                        ActiveToolView.None -> "Công cụ"
                        ActiveToolView.StorageAnalyzer -> "Phân tích bộ nhớ"
                        ActiveToolView.HashAndCompare -> "Tính toán & so sánh Hash"
                        ActiveToolView.DuplicateFinder -> "Tìm tệp trùng lặp"
                        ActiveToolView.ApkPicker -> "Chọn APK kiểm tra"
                        ActiveToolView.DatabasePicker -> "Chọn Database xem"
                        ActiveToolView.HexPicker -> "Chọn tệp xem Hex"
                    }
                    Text(titleText, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (activeSubView != ActiveToolView.None) {
                            activeSubView = ActiveToolView.None
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
            )
        },
        containerColor = CharcoalBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (activeSubView) {
                ActiveToolView.None -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(tools) { item ->
                            ToolListRow(item = item) {
                                when (item.id) {
                                    "storage" -> activeSubView = ActiveToolView.StorageAnalyzer
                                    "apk" -> activeSubView = ActiveToolView.ApkPicker
                                    "db" -> activeSubView = ActiveToolView.DatabasePicker
                                    "hex" -> activeSubView = ActiveToolView.HexPicker
                                    "game" -> onOpenGameData()
                                    "hash" -> activeSubView = ActiveToolView.HashAndCompare
                                    "duplicates" -> activeSubView = ActiveToolView.DuplicateFinder
                                }
                            }
                            HorizontalDivider(color = CharcoalDivider, thickness = 0.5.dp, modifier = Modifier.padding(start = 64.dp))
                        }
                    }
                }

                ActiveToolView.StorageAnalyzer -> {
                    StorageAnalyzerSubView(currentDirectory = currentDirectory)
                }

                ActiveToolView.HashAndCompare -> {
                    HashAndCompareSubView(
                        currentDirectory = currentDirectory,
                        snackbarHostState = snackbarHostState
                    )
                }

                ActiveToolView.DuplicateFinder -> {
                    DuplicateFinderSubView(currentDirectory = currentDirectory)
                }

                ActiveToolView.ApkPicker -> {
                    FilePickerSubView(
                        currentDirectory = currentDirectory,
                        filter = { it.name.endsWith(".apk", ignoreCase = true) },
                        emptyMsg = "Không tìm thấy tệp .apk trong thư mục hiện tại",
                        onFileSelected = { onOpenApkInspector(it) }
                    )
                }

                ActiveToolView.DatabasePicker -> {
                    FilePickerSubView(
                        currentDirectory = currentDirectory,
                        filter = {
                            val ext = FileUtils.getExtension(it.name).lowercase()
                            ext in setOf("db", "sqlite", "sqlite3")
                        },
                        emptyMsg = "Không tìm thấy tệp .db, .sqlite trong thư mục hiện tại",
                        onFileSelected = { onOpenDatabaseViewer(it) }
                    )
                }

                ActiveToolView.HexPicker -> {
                    FilePickerSubView(
                        currentDirectory = currentDirectory,
                        filter = { it.isFile },
                        emptyMsg = "Không có tệp nào trong thư mục hiện tại",
                        onFileSelected = { onOpenHexViewer(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolListRow(
    item: ToolItemDef,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = item.iconTint.copy(alpha = 0.15f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(item.icon, contentDescription = null, tint = item.iconTint, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StorageAnalyzerSubView(currentDirectory: File) {
    val coroutineScope = rememberCoroutineScope()
    var storageBreakdown by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var isAnalyzing by remember { mutableStateOf(false) }

    fun runScan() {
        isAnalyzing = true
        coroutineScope.launch {
            val map = withContext(Dispatchers.IO) {
                val stats = mutableMapOf<String, Long>()
                fun scan(dir: File, depth: Int) {
                    if (depth > 2) return
                    dir.listFiles()?.forEach { f ->
                        if (f.isDirectory) {
                            if (!f.name.startsWith(".")) scan(f, depth + 1)
                        } else {
                            val type = FileUtils.detectFileType(f).name
                            stats[type] = (stats[type] ?: 0L) + f.length()
                        }
                    }
                }
                scan(currentDirectory, 0)
                stats
            }
            storageBreakdown = map
            isAnalyzing = false
        }
    }

    LaunchedEffect(currentDirectory) {
        runScan()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Dung lượng theo loại tệp trong: ${currentDirectory.name}", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(10.dp))

        if (isAnalyzing) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ElectricBlue)
        } else {
            val total = storageBreakdown.values.sum().coerceAtLeast(1L)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(storageBreakdown.toList().sortedByDescending { it.second }) { (category, bytes) ->
                    val percent = (bytes.toFloat() / total.toFloat())
                    Surface(color = CharcoalSurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(category, fontWeight = FontWeight.Medium, color = TextPrimary)
                                Text("${FileItem.formatBytes(bytes)} (${(percent * 100).toInt()}%)", color = ElectricBlue, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { percent },
                                modifier = Modifier.fillMaxWidth(),
                                color = ElectricBlue,
                                trackColor = CharcoalSurfaceHighlight
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HashAndCompareSubView(currentDirectory: File, snackbarHostState: SnackbarHostState) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var hashProgress by remember { mutableFloatStateOf(0f) }
    var isCalculating by remember { mutableStateOf(false) }
    var calculatedHashes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val fileList = remember(currentDirectory) {
        currentDirectory.listFiles()?.filter { it.isFile } ?: emptyList()
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Chọn tệp tính mã băm (MD5, SHA-1, SHA-256):", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))

            if (fileList.isEmpty()) {
                Text("Thư mục hiện tại không có tệp nào", color = TextMuted)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 140.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(fileList) { f ->
                        val isSelected = f == selectedFile
                        Surface(
                            color = if (isSelected) ElectricBlue.copy(alpha = 0.2f) else CharcoalSurface,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                selectedFile = f
                                calculatedHashes = emptyMap()
                            }
                        ) {
                            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(f.name, color = TextPrimary, modifier = Modifier.weight(1f), maxLines = 1, fontSize = 13.sp)
                                Text(FileItem.formatBytes(f.length()), color = TextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        if (selectedFile != null) {
            item {
                Button(
                    onClick = {
                        val f = selectedFile ?: return@Button
                        isCalculating = true
                        coroutineScope.launch {
                            val res = HashUtils.calculateAllHashes(f) { p -> hashProgress = p }
                            if (res.isSuccess) {
                                calculatedHashes = res.getOrNull() ?: emptyMap()
                            } else {
                                snackbarHostState.showSnackbar("Lỗi: ${res.exceptionOrNull()?.message}")
                            }
                            isCalculating = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    enabled = !isCalculating
                ) {
                    Text(if (isCalculating) "Đang tính..." else "Bắt đầu tính Hash")
                }

                if (isCalculating) {
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { hashProgress }, modifier = Modifier.fillMaxWidth(), color = ElectricBlue)
                }
            }
        }

        if (calculatedHashes.isNotEmpty()) {
            calculatedHashes.forEach { (algo, hash) ->
                item {
                    Surface(color = CharcoalSurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(algo, fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 12.sp)
                                Text(hash, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary)
                            }
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(hash))
                                coroutineScope.launch { snackbarHostState.showSnackbar("Đã sao chép $algo!") }
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Sao chép", tint = TextPrimary, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateFinderSubView(currentDirectory: File) {
    val coroutineScope = rememberCoroutineScope()
    var duplicateMap by remember { mutableStateOf<Map<String, List<File>>>(emptyMap()) }
    var isScanning by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tìm tệp trùng lặp trong thư mục hiện tại:", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)

        Button(
            onClick = {
                isScanning = true
                coroutineScope.launch {
                    val files = withContext(Dispatchers.IO) {
                        currentDirectory.listFiles()?.filter { it.isFile } ?: emptyList()
                    }
                    val map = withContext(Dispatchers.IO) {
                        GameDataExplorer.findDuplicatesByHash(files)
                    }
                    duplicateMap = map
                    isScanning = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ColorWarningAmber),
            enabled = !isScanning
        ) {
            Text(if (isScanning) "Đang quét mã SHA-256..." else "Quét tệp trùng lặp", color = Color.Black)
        }

        if (isScanning) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ColorWarningAmber)
        } else if (duplicateMap.isNotEmpty()) {
            Text("Phát hiện ${duplicateMap.size} nhóm tệp giống hệt nhau:", color = TextPrimary, fontSize = 13.sp)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                duplicateMap.forEach { (hash, files) ->
                    item {
                        Surface(color = CharcoalSurface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("SHA-256: ${hash.take(16)}...", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CyanAccent)
                                Spacer(modifier = Modifier.height(4.dp))
                                files.forEach { f ->
                                    Text("• ${f.name} (${FileItem.formatBytes(f.length())})", color = TextPrimary, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Text("Chưa phát hiện tệp trùng lặp nào", color = TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun FilePickerSubView(
    currentDirectory: File,
    filter: (File) -> Boolean,
    emptyMsg: String,
    onFileSelected: (File) -> Unit
) {
    val files = remember(currentDirectory) {
        currentDirectory.listFiles()?.filter(filter) ?: emptyList()
    }

    if (files.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(emptyMsg, color = TextMuted, fontSize = 13.sp)
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(files) { f ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFileSelected(f) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.InsertDriveFile, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(f.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${FileItem.formatBytes(f.length())} • ${FileItem.formatDate(f.lastModified())}", color = TextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                }
                HorizontalDivider(color = CharcoalDivider, thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))
            }
        }
    }
}
