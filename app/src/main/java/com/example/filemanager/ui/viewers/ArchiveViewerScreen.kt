package com.example.filemanager.ui.viewers

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.archive.ArchiveEntryInfo
import com.example.filemanager.archive.ArchiveManager
import com.example.filemanager.model.FileItem
import com.example.filemanager.ui.explorer.ZipDocumentBadgeIcon
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewerScreen(
    archiveFile: File,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var entries by remember { mutableStateOf<List<ArchiveEntryInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Multi-selection in archive
    var selectedEntries by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isSelectMode by remember { mutableStateOf(false) }

    // Extraction state
    var isExtracting by remember { mutableStateOf(false) }
    var extractProgress by remember { mutableFloatStateOf(0f) }
    var currentExtractEntry by remember { mutableStateOf("") }

    // Dialogs
    var showExtractOptionsDialog by remember { mutableStateOf(false) }
    var previewEntry by remember { mutableStateOf<ArchiveEntryInfo?>(null) }
    var previewTextContent by remember { mutableStateOf<String?>(null) }
    var isPreviewLoading by remember { mutableStateOf(false) }

    fun loadArchive() {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            val result = ArchiveManager.listArchiveEntries(archiveFile)
            if (result.isSuccess) {
                entries = result.getOrNull() ?: emptyList()
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Lỗi khi mở tệp nén"
            }
            isLoading = false
        }
    }

    LaunchedEffect(archiveFile) {
        loadArchive()
    }

    fun performExtract(targetDir: File, specificPaths: List<String>? = null) {
        isExtracting = true
        coroutineScope.launch {
            val res = if (specificPaths != null && specificPaths.isNotEmpty()) {
                ArchiveManager.extractEntries(archiveFile, specificPaths, targetDir) { p, name ->
                    extractProgress = p
                    currentExtractEntry = name
                }
            } else {
                ArchiveManager.extractAll(archiveFile, targetDir) { p, name ->
                    extractProgress = p
                    currentExtractEntry = name
                }
            }

            if (res.isSuccess) {
                val count = res.getOrNull() ?: 0
                snackbarHostState.showSnackbar("Đã trích xuất thành công $count tệp vào: ${targetDir.name}")
                selectedEntries = emptySet()
                isSelectMode = false
            } else {
                snackbarHostState.showSnackbar("Lỗi giải nén: ${res.exceptionOrNull()?.message}")
            }
            isExtracting = false
        }
    }

    fun openEntryPreview(entry: ArchiveEntryInfo) {
        previewEntry = entry
        previewTextContent = null
        isPreviewLoading = true
        coroutineScope.launch {
            val res = ArchiveManager.readEntryText(archiveFile, entry.path)
            previewTextContent = if (res.isSuccess) {
                res.getOrNull()
            } else {
                "(Không thể đọc dạng văn bản hoặc tệp nhị phân: ${res.exceptionOrNull()?.message})"
            }
            isPreviewLoading = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ZipDocumentBadgeIcon(modifier = Modifier.size(width = 24.dp, height = 30.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = archiveFile.name,
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${FileItem.formatBytes(archiveFile.length())} • ${entries.size} mục",
                                color = CyanAccent,
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
                    // Check integrity
                    IconButton(onClick = {
                        coroutineScope.launch {
                            val res = ArchiveManager.testArchiveIntegrity(archiveFile)
                            if (res.isSuccess) {
                                snackbarHostState.showSnackbar(res.getOrNull() ?: "Tệp nén tốt")
                            } else {
                                snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "Lỗi kiểm tra")
                            }
                        }
                    }) {
                        Icon(Icons.Default.Verified, contentDescription = "Kiểm tra toàn vẹn", tint = ColorSuccessGreen)
                    }

                    // Extract Options Dialog
                    IconButton(
                        onClick = { showExtractOptionsDialog = true },
                        enabled = !isExtracting
                    ) {
                        Icon(Icons.Default.Unarchive, contentDescription = "Tùy chọn trích xuất", tint = ElectricBlue)
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
            // Extraction Progress Banner
            if (isExtracting) {
                Surface(color = CharcoalSurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Đang giải nén tốc độ cao...", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${(extractProgress * 100).toInt()}%", color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { extractProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = ElectricBlue
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(currentExtractEntry, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Quick actions & Search bar
            Surface(
                color = CharcoalSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Tìm trong tệp nén...", color = TextMuted, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = CharcoalSurfaceHighlight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { showExtractOptionsDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Trích xuất", fontSize = 12.sp)
                    }
                }
            }

            // Multi-select info bar
            if (isSelectMode) {
                Surface(
                    color = ElectricBlue.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Đã chọn ${selectedEntries.size} mục",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Row {
                            TextButton(onClick = {
                                val targetDir = File(archiveFile.parentFile ?: archiveFile, archiveFile.nameWithoutExtension + "_extracted")
                                performExtract(targetDir, selectedEntries.toList())
                            }) {
                                Text("Trích xuất đã chọn", color = ElectricBlue, fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = {
                                selectedEntries = emptySet()
                                isSelectMode = false
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Hủy", tint = TextMuted)
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricBlue)
                }
            } else if (errorMessage != null) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(errorMessage ?: "", color = ColorErrorRed)
                }
            } else {
                val filtered = remember(entries, searchQuery) {
                    if (searchQuery.isBlank()) entries
                    else entries.filter { it.path.contains(searchQuery, ignoreCase = true) }
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Không tìm thấy mục nào", color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filtered, key = { it.path }) { item ->
                            val isSelected = selectedEntries.contains(item.path)

                            Surface(
                                color = if (isSelected) ElectricBlue.copy(alpha = 0.2f) else CharcoalSurface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (isSelectMode) {
                                            selectedEntries = if (isSelected) selectedEntries - item.path else selectedEntries + item.path
                                            if (selectedEntries.isEmpty()) isSelectMode = false
                                        } else if (!item.isDirectory) {
                                            openEntryPreview(item)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isSelectMode) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedEntries = if (checked) selectedEntries + item.path else selectedEntries - item.path
                                                if (selectedEntries.isEmpty()) isSelectMode = false
                                            },
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }

                                    if (item.isDirectory) {
                                        Icon(
                                            Icons.Default.Folder,
                                            contentDescription = "Thư mục",
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        val ext = item.name.substringAfterLast('.', "").lowercase()
                                        if (ext in setOf("zip", "rar", "7z", "tar", "gz")) {
                                            ZipDocumentBadgeIcon(modifier = Modifier.size(width = 20.dp, height = 24.dp))
                                        } else {
                                            Icon(
                                                Icons.Default.InsertDriveFile,
                                                contentDescription = "Tệp",
                                                tint = Color(0xFF9AA0A6),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.path,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            fontSize = 13.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (!item.isDirectory) {
                                            Text(
                                                text = "${FileItem.formatBytes(item.size)}  •  Nén: ${FileItem.formatBytes(item.compressedSize)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    // Action buttons for single entry
                                    if (!item.isDirectory) {
                                        // Quick Preview
                                        IconButton(
                                            onClick = { openEntryPreview(item) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Visibility,
                                                contentDescription = "Xem trước",
                                                tint = CyanAccent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Quick Extract single file
                                        IconButton(
                                            onClick = {
                                                val targetDir = File(archiveFile.parentFile ?: archiveFile, archiveFile.nameWithoutExtension + "_extracted")
                                                performExtract(targetDir, listOf(item.path))
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Download,
                                                contentDescription = "Trích xuất",
                                                tint = ElectricBlue,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    // 1. Tùy chọn trích xuất (Extraction Options Dialog)
    if (showExtractOptionsDialog) {
        val parentDir = archiveFile.parentFile ?: archiveFile
        val newSubfolder = File(parentDir, archiveFile.nameWithoutExtension)

        AlertDialog(
            onDismissRequest = { showExtractOptionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Unarchive, contentDescription = null, tint = ElectricBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tùy chọn trích xuất", color = TextPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Chọn vị trí và phương thức trích xuất tệp nén:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMuted
                    )

                    // Option A: Trích xuất vào thư mục mới [tên_tệp]
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CharcoalBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExtractOptionsDialog = false
                                performExtract(newSubfolder)
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("1. Trích xuất vào thư mục mới", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.5.sp)
                            Text("Tạo thư mục: /${newSubfolder.name}/", color = CyanAccent, fontSize = 12.sp)
                        }
                    }

                    // Option B: Trích xuất tại thư mục hiện tại
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CharcoalBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExtractOptionsDialog = false
                                performExtract(parentDir)
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("2. Trích xuất tại đây", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.5.sp)
                            Text("Giải nén trực tiếp vào: /${parentDir.name}/", color = TextMuted, fontSize = 12.sp)
                        }
                    }

                    // Option C: Chọn mục để trích xuất
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CharcoalBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showExtractOptionsDialog = false
                                isSelectMode = true
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("3. Chọn các tệp cần trích xuất", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.5.sp)
                            Text("Bật chế độ chọn để trích xuất từng phần", color = TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExtractOptionsDialog = false }) {
                    Text("Hủy", color = TextMuted)
                }
            },
            containerColor = CharcoalSurfaceVariant
        )
    }

    // 2. Xem trước nội dung tệp trực tiếp trong ZIP (In-Archive Preview Modal)
    previewEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { previewEntry = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = CyanAccent)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Xem trước trong ZIP", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(entry.name, color = TextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Kích thước: ${FileItem.formatBytes(entry.size)}", style = MaterialTheme.typography.labelSmall, color = CyanAccent)
                        Text("Nén: ${FileItem.formatBytes(entry.compressedSize)}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isPreviewLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ElectricBlue)
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = CharcoalBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            val scrollState = rememberScrollState()
                            Text(
                                text = previewTextContent ?: "(Không có nội dung)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .verticalScroll(scrollState)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row {
                    if (previewTextContent != null) {
                        TextButton(onClick = {
                            clipboardManager.setText(AnnotatedString(previewTextContent!!))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Đã sao chép nội dung vào khay nhớ tạm!")
                            }
                        }) {
                            Text("Sao chép", color = TextPrimary)
                        }
                    }

                    Button(
                        onClick = {
                            val targetDir = File(archiveFile.parentFile ?: archiveFile, archiveFile.nameWithoutExtension + "_extracted")
                            val itemPath = entry.path
                            previewEntry = null
                            performExtract(targetDir, listOf(itemPath))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Text("Trích xuất tệp này")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { previewEntry = null }) {
                    Text("Đóng", color = TextMuted)
                }
            },
            containerColor = CharcoalSurfaceVariant
        )
    }
}
