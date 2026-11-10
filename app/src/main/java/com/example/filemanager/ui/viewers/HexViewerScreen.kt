package com.example.filemanager.ui.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.binary.HexEditor
import com.example.filemanager.binary.HexPage
import com.example.filemanager.model.FileItem
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexViewerScreen(
    file: File,
    onBack: () -> Unit,
    onOpenTextViewer: ((File) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var currentPage by remember { mutableStateOf<HexPage?>(null) }
    var pageIndex by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpOffsetInput by remember { mutableStateOf("") }

    var showSearchDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isHexSearch by remember { mutableStateOf(false) }
    var searchMatches by remember { mutableStateOf<List<Long>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var showHeaderInfoDialog by remember { mutableStateOf(false) }
    var headerAnalysisText by remember { mutableStateOf("") }

    var showEditDialog by remember { mutableStateOf(false) }
    var editOffsetInput by remember { mutableStateOf("") }
    var editByteHexInput by remember { mutableStateOf("") }
    var isSavingEdit by remember { mutableStateOf(false) }

    fun loadPage(targetPage: Int) {
        isLoading = true
        coroutineScope.launch {
            val result = HexEditor.readHexPage(file, targetPage)
            if (result.isSuccess) {
                currentPage = result.getOrNull()
                pageIndex = targetPage
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Lỗi đọc dữ liệu HEX"
            }
            isLoading = false
        }
    }

    LaunchedEffect(file) {
        loadPage(0)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${FileItem.formatBytes(file.length())} • Khối ${(currentPage?.currentPageIndex ?: 0) + 1}/${(currentPage?.totalPages ?: 1)}",
                            color = CyanAccent,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
                    }
                },
                actions = {
                    // Header Analysis
                    IconButton(onClick = {
                        headerAnalysisText = HexEditor.getHeaderAnalysis(file)
                        showHeaderInfoDialog = true
                    }) {
                        Icon(Icons.Default.Info, contentDescription = "Phân tích Header", tint = TextPrimary)
                    }

                    // Search Hex / Text
                    IconButton(onClick = { showSearchDialog = true }) {
                        Icon(Icons.Default.Search, contentDescription = "Tìm kiếm Hex/Text", tint = TextPrimary)
                    }

                    // Jump to offset
                    IconButton(onClick = { showJumpDialog = true }) {
                        Icon(Icons.Default.DoubleArrow, contentDescription = "Chuyển đến Offset", tint = TextPrimary)
                    }

                    // Edit byte
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Sửa byte nhị phân", tint = ElectricBlue)
                    }

                    // Switch to Text Viewer
                    if (onOpenTextViewer != null) {
                        IconButton(onClick = { onOpenTextViewer(file) }) {
                            Icon(Icons.Default.Article, contentDescription = "Xem dạng Văn bản", tint = ColorSuccessGreen)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
            )
        },
        bottomBar = {
            // Navigation paging bar
            Surface(
                color = CharcoalSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { if (pageIndex > 0) loadPage(pageIndex - 1) },
                        enabled = pageIndex > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = CharcoalSurfaceVariant)
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = null)
                        Text("Trang trước")
                    }

                    val cur = currentPage
                    Text(
                        text = if (cur != null) "Khối ${cur.currentPageIndex + 1} / ${cur.totalPages}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Button(
                        onClick = {
                            val curTotal = currentPage?.totalPages ?: 1
                            if (pageIndex + 1 < curTotal) loadPage(pageIndex + 1)
                        },
                        enabled = currentPage != null && (pageIndex + 1 < (currentPage?.totalPages ?: 1)),
                        colors = ButtonDefaults.buttonColors(containerColor = CharcoalSurfaceVariant)
                    ) {
                        Text("Trang sau")
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }
        },
        containerColor = CharcoalBackground
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ElectricBlue)
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(errorMessage ?: "", color = ColorErrorRed)
            }
        } else {
            val page = currentPage
            if (page == null || page.rows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Tệp rỗng hoặc không có dữ liệu", color = TextMuted)
                }
            } else {
                val hScrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    // Header Bar
                    Surface(color = CharcoalSurfaceHighlight, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .horizontalScroll(hScrollState)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "OFFSET   ",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                modifier = Modifier.width(90.dp)
                            )
                            Text(
                                text = "00 01 02 03 04 05 06 07  08 09 0A 0B 0C 0D 0E 0F",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricBlue,
                                modifier = Modifier.width(360.dp)
                            )
                            Text(
                                text = "ASCII TEXT",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ColorWarningAmber,
                                modifier = Modifier.width(160.dp)
                            )
                        }
                    }

                    // Hex Rows
                    Box(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxSize().horizontalScroll(hScrollState)) {
                            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                                items(page.rows) { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Offset
                                        Text(
                                            text = row.offsetFormatted,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = CyanAccent,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.width(90.dp)
                                        )

                                        // Hex bytes
                                        Text(
                                            text = row.hexString,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = TextPrimary,
                                            modifier = Modifier.width(360.dp)
                                        )

                                        // ASCII string
                                        Text(
                                            text = row.asciiString,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = ColorSuccessGreen,
                                            modifier = Modifier.width(160.dp)
                                        )
                                    }
                                    HorizontalDivider(color = CharcoalSurfaceHighlight.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: Jump to Offset
    if (showJumpDialog) {
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Chuyển đến Offset", color = TextPrimary) },
            text = {
                Column {
                    Text("Nhập địa chỉ Offset (Hex ví dụ 1A0 hoặc thập phân ví dụ 416):", color = TextSecondary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = jumpOffsetInput,
                        onValueChange = { jumpOffsetInput = it },
                        placeholder = { Text("Ví dụ: 0x100 hoặc 256", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = CharcoalSurfaceHighlight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showJumpDialog = false
                        try {
                            val offset = if (jumpOffsetInput.startsWith("0x", ignoreCase = true)) {
                                jumpOffsetInput.substring(2).toLong(16)
                            } else if (jumpOffsetInput.all { it.isDigit() }) {
                                jumpOffsetInput.toLong()
                            } else {
                                jumpOffsetInput.toLong(16)
                            }
                            val targetPage = (offset / (HexEditor.ROWS_PER_PAGE * HexEditor.BYTES_PER_ROW)).toInt()
                            loadPage(targetPage)
                        } catch (e: Exception) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Offset không hợp lệ") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Đi tới")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) { Text("Hủy", color = TextMuted) }
            },
            containerColor = CharcoalSurface
        )
    }

    // Dialog: Search
    if (showSearchDialog) {
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Tìm kiếm trong tệp nhị phân", color = TextPrimary) },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !isHexSearch,
                            onClick = { isHexSearch = false },
                            colors = RadioButtonDefaults.colors(selectedColor = ElectricBlue)
                        )
                        Text("Chuỗi văn bản (ASCII)", color = TextPrimary, fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(
                            selected = isHexSearch,
                            onClick = { isHexSearch = true },
                            colors = RadioButtonDefaults.colors(selectedColor = ElectricBlue)
                        )
                        Text("HEX Bytes (vd: 4D 5A)", color = TextPrimary, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(if (isHexSearch) "Ví dụ: 50 4B 03 04" else "Ví dụ: UnityFS", color = TextMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = CharcoalSurfaceHighlight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    if (isSearching) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ElectricBlue)
                    }

                    if (searchMatches.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tìm thấy ${searchMatches.size} vị trí:", color = ColorSuccessGreen, fontSize = 12.sp)
                        LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                            items(searchMatches) { off ->
                                TextButton(
                                    onClick = {
                                        showSearchDialog = false
                                        val targetPage = (off / (HexEditor.ROWS_PER_PAGE * HexEditor.BYTES_PER_ROW)).toInt()
                                        loadPage(targetPage)
                                    }
                                ) {
                                    Text("Offset 0x%08X (%d)".format(off, off), color = CyanAccent, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSearching = true
                        coroutineScope.launch {
                            val res = HexEditor.searchPattern(file, searchQuery, isHexSearch)
                            if (res.isSuccess) {
                                searchMatches = res.getOrNull() ?: emptyList()
                                if (searchMatches.isEmpty()) {
                                    snackbarHostState.showSnackbar("Không tìm thấy kết quả phù hợp")
                                }
                            } else {
                                snackbarHostState.showSnackbar("Lỗi tìm kiếm: ${res.exceptionOrNull()?.message}")
                            }
                            isSearching = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                    enabled = !isSearching && searchQuery.isNotBlank()
                ) {
                    Text("Tìm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) { Text("Đóng", color = TextMuted) }
            },
            containerColor = CharcoalSurface
        )
    }

    // Dialog: Header Analysis
    if (showHeaderInfoDialog) {
        AlertDialog(
            onDismissRequest = { showHeaderInfoDialog = false },
            title = { Text("Phân tích Header & Magic Bytes", color = ElectricBlue) },
            text = {
                Text(
                    text = headerAnalysisText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(headerAnalysisText))
                        showHeaderInfoDialog = false
                        coroutineScope.launch { snackbarHostState.showSnackbar("Đã sao chép phân tích header!") }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Sao chép")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHeaderInfoDialog = false }) { Text("Đóng", color = TextMuted) }
            },
            containerColor = CharcoalSurface
        )
    }

    // Dialog: Edit Byte
    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Chỉnh sửa Byte nhị phân", color = ColorWarningAmber) },
            text = {
                Column {
                    Text("Lưu ý an toàn: Bạn có thể lưu đè lên file hoặc tạo bản sao mới.", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editOffsetInput,
                        onValueChange = { editOffsetInput = it },
                        label = { Text("Offset (Hex vd: 0x00 hoặc 0)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = CharcoalSurfaceHighlight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editByteHexInput,
                        onValueChange = { editByteHexInput = it.take(2) },
                        label = { Text("Giá trị Byte mới (2 ký tự Hex, vd: FF hoặc 90)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            unfocusedBorderColor = CharcoalSurfaceHighlight,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val off = if (editOffsetInput.startsWith("0x", ignoreCase = true)) {
                                editOffsetInput.substring(2).toLong(16)
                            } else {
                                editOffsetInput.toLong()
                            }
                            val b = editByteHexInput.toInt(16).toByte()
                            isSavingEdit = true
                            coroutineScope.launch {
                                val res = HexEditor.saveModifiedByte(file, file, off, b)
                                if (res.isSuccess) {
                                    showEditDialog = false
                                    snackbarHostState.showSnackbar("Đã cập nhật byte tại offset 0x%08X thành %02X".format(off, b))
                                    loadPage(pageIndex)
                                } else {
                                    snackbarHostState.showSnackbar("Lỗi: ${res.exceptionOrNull()?.message}")
                                }
                                isSavingEdit = false
                            }
                        } catch (e: Exception) {
                            coroutineScope.launch { snackbarHostState.showSnackbar("Giá trị Hex hoặc Offset không hợp lệ") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorWarningAmber),
                    enabled = !isSavingEdit && editOffsetInput.isNotBlank() && editByteHexInput.length == 2
                ) {
                    Text("Lưu Byte", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text("Hủy", color = TextMuted) }
            },
            containerColor = CharcoalSurface
        )
    }
}
