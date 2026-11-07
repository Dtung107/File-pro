package com.example.filemanager.ui.explorer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.model.*
import com.example.filemanager.utils.FileUtils
import com.example.filemanager.utils.HashUtils
import com.example.filemanager.viewmodel.MainViewModel
import com.example.filemanager.viewmodel.ScreenDestination
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    val currentDir by viewModel.currentDirectory.collectAsState()
    val activeCategoryTitle by viewModel.activeCategoryTitle.collectAsState()
    val fileItems by viewModel.fileItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val isMultiSelect by viewModel.isMultiSelectMode.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val operationProgress by viewModel.operationProgress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var isSearching by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showCreateFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    var showRenameDialog by remember { mutableStateOf<FileItem?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showZipDialog by remember { mutableStateOf(false) }
    var zipInputName by remember { mutableStateOf("archive.zip") }

    var showInfoDialog by remember { mutableStateOf<FileItem?>(null) }
    var infoHashText by remember { mutableStateOf<String?>(null) }
    var isCalculatingInfoHash by remember { mutableStateOf(false) }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val displayedItems = remember(fileItems, searchQuery) {
        if (searchQuery.isBlank()) fileItems
        else fileItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (isMultiSelect) {
                // Multi-select Action Bar
                TopAppBar(
                    title = { Text("${selectedItems.size} đã chọn", color = TextPrimary, fontSize = 16.sp) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Bỏ chọn", tint = TextPrimary)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Chọn tất cả", tint = TextPrimary)
                        }
                        IconButton(onClick = { viewModel.setClipboard(ClipboardAction.COPY, selectedItems.toList()) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Sao chép", tint = ElectricBlue)
                        }
                        IconButton(onClick = { viewModel.setClipboard(ClipboardAction.CUT, selectedItems.toList()) }) {
                            Icon(Icons.Default.ContentCut, contentDescription = "Cắt", tint = ElectricBlue)
                        }
                        IconButton(onClick = { showZipDialog = true }) {
                            Icon(Icons.Default.FolderZip, contentDescription = "Nén ZIP", tint = CyanAccent)
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = ColorErrorRed)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurfaceVariant)
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            val titleText = activeCategoryTitle ?: (if (currentDir.name.isEmpty()) "Bộ nhớ trong" else currentDir.name)
                            Text(
                                text = titleText,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                                maxLines = 1
                            )
                            if (activeCategoryTitle != null) {
                                Text(
                                    text = "${displayedItems.size} tệp tìm thấy",
                                    color = ElectricBlue,
                                    fontSize = 11.sp
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
                        IconButton(onClick = { isSearching = !isSearching }) {
                            Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = TextPrimary)
                        }
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sắp xếp", tint = TextPrimary)
                        }
                        IconButton(onClick = { viewModel.toggleGridView() }) {
                            Icon(
                                if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = "Đổi chế độ xem",
                                tint = TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
                )
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (showFabMenu) {
                    ExtendedFloatingActionButton(
                        onClick = { showFabMenu = false; showCreateFolderDialog = true },
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Tạo thư mục", fontSize = 13.sp) },
                        containerColor = CharcoalSurfaceVariant,
                        contentColor = TextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    ExtendedFloatingActionButton(
                        onClick = { showFabMenu = false; showCreateFileDialog = true },
                        icon = { Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Tạo tệp mới", fontSize = 13.sp) },
                        containerColor = CharcoalSurfaceVariant,
                        contentColor = TextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    if (clipboard != null) {
                        ExtendedFloatingActionButton(
                            onClick = { showFabMenu = false; viewModel.pasteClipboard() },
                            icon = { Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            text = { Text("Dán (${clipboard?.items?.size} mục)", fontSize = 13.sp) },
                            containerColor = ElectricBlue,
                            contentColor = Color.White,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }

                FloatingActionButton(
                    onClick = { showFabMenu = !showFabMenu },
                    containerColor = ElectricBlue,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(if (showFabMenu) Icons.Default.Close else Icons.Default.Add, contentDescription = "Menu hành động")
                }
            }
        },
        containerColor = CharcoalBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Breadcrumbs Bar (When inside directory mode)
            if (activeCategoryTitle == null) {
                val pathParts = remember(currentDir) {
                    val parts = mutableListOf<File>()
                    var cur: File? = currentDir
                    while (cur != null) {
                        parts.add(0, cur)
                        cur = cur.parentFile
                    }
                    parts
                }

                val hScroll = rememberScrollState()
                Surface(
                    color = CharcoalSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(hScroll)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        pathParts.takeLast(5).forEachIndexed { index, dir ->
                            if (index > 0) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            val isCurrent = dir == currentDir
                            Text(
                                text = if (dir.name.isEmpty()) "Bộ nhớ trong" else dir.name,
                                color = if (isCurrent) ElectricBlue else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                modifier = Modifier
                                    .clickable { viewModel.loadDirectory(dir) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Search Filter Bar (if toggled)
            if (isSearching) {
                Surface(color = CharcoalSurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Tìm kiếm tên tệp...", color = TextMuted, fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = CharcoalSurfaceHighlight,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        IconButton(onClick = { viewModel.setSearchQuery(""); isSearching = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextPrimary)
                        }
                    }
                }
            }

            // Clipboard Action Banner
            val clip = clipboard
            if (clip != null && !isMultiSelect) {
                Surface(
                    color = ElectricBlue.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${if (clip.action == ClipboardAction.COPY) "Sao chép" else "Di chuyển"} ${clip.items.size} mục",
                            color = ElectricBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row {
                            TextButton(
                                onClick = { viewModel.pasteClipboard() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Dán vào đây", color = ElectricBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            IconButton(onClick = { viewModel.clearClipboard() }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Hủy", tint = TextMuted, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Main Content Area
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricBlue, strokeWidth = 3.dp)
                }
            } else if (displayedItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (searchQuery.isNotBlank()) "Không tìm thấy tệp phù hợp" else "Thư mục trống",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(displayedItems) { item ->
                            FileGridCard(
                                item = item,
                                isSelected = selectedItems.contains(item),
                                isMultiSelect = isMultiSelect,
                                onClick = {
                                    if (isMultiSelect) viewModel.toggleMultiSelect(item)
                                    else viewModel.openFile(item)
                                },
                                onLongClick = { viewModel.toggleMultiSelect(item) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 72.dp)
                    ) {
                        items(displayedItems) { item ->
                            FileRowItem(
                                item = item,
                                isSelected = selectedItems.contains(item),
                                isMultiSelect = isMultiSelect,
                                onClick = {
                                    if (isMultiSelect) viewModel.toggleMultiSelect(item)
                                    else viewModel.openFile(item)
                                },
                                onLongClick = { viewModel.toggleMultiSelect(item) },
                                onOpen = { viewModel.openFile(item) },
                                onOpenHex = { viewModel.navigateTo(ScreenDestination.HexViewer(File(item.path))) },
                                onCopy = { viewModel.setClipboard(ClipboardAction.COPY, listOf(item)) },
                                onCut = { viewModel.setClipboard(ClipboardAction.CUT, listOf(item)) },
                                onRename = {
                                    showRenameDialog = item
                                    renameInput = item.name
                                },
                                onShare = { FileUtils.shareFile(context, File(item.path)) },
                                onShowInfo = {
                                    showInfoDialog = item
                                    infoHashText = null
                                },
                                onDelete = {
                                    viewModel.toggleMultiSelect(item)
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Operation Progress Dialog
    if (operationProgress.isRunning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(operationProgress.title, color = TextPrimary, fontSize = 16.sp) },
            text = {
                Column {
                    Text(operationProgress.message, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { operationProgress.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = ElectricBlue
                    )
                }
            },
            confirmButton = {},
            containerColor = CharcoalSurface
        )
    }

    // Dialog: Create Folder
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Tạo thư mục mới", color = TextPrimary, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Tên thư mục", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = CharcoalSurfaceHighlight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createFolder(newFolderName.trim())
                            showCreateFolderDialog = false
                            newFolderName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Tạo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) { Text("Hủy", color = TextMuted) }
            },
            containerColor = CharcoalSurface
        )
    }

    // Dialog: Create File
    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Tạo tệp mới", color = TextPrimary, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    placeholder = { Text("Ví dụ: script.py hoặc data.json", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = CharcoalSurfaceHighlight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            viewModel.createFile(newFileName.trim())
                            showCreateFileDialog = false
                            newFileName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Tạo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) { Text("Hủy", color = TextMuted) }
            },
            containerColor = CharcoalSurface
        )
    }

    // Dialog: Rename
    if (showRenameDialog != null) {
        val item = showRenameDialog!!
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Đổi tên", color = TextPrimary, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = CharcoalSurfaceHighlight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameFile(item, renameInput.trim())
                            showRenameDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Đổi tên")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Hủy", color = TextMuted) }
            },
            containerColor = CharcoalSurface
        )
    }

    // Dialog: Delete Confirm
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Xác nhận xóa", color = ColorErrorRed, fontSize = 16.sp) },
            text = {
                Text(
                    text = "Xóa vĩnh viễn ${selectedItems.size} mục đã chọn? Thao tác này không thể hoàn tác.",
                    color = TextPrimary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedItems()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorErrorRed)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Hủy", color = TextMuted) }
            },
            containerColor = CharcoalSurface
        )
    }

    // Dialog: ZIP Compress Name
    if (showZipDialog) {
        AlertDialog(
            onDismissRequest = { showZipDialog = false },
            title = { Text("Nén vào tệp ZIP", color = ElectricBlue, fontSize = 16.sp) },
            text = {
                OutlinedTextField(
                    value = zipInputName,
                    onValueChange = { zipInputName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = CharcoalSurfaceHighlight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (zipInputName.isNotBlank()) {
                            viewModel.compressSelectedItemsToZip(zipInputName.trim())
                            showZipDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Nén ngay")
                }
            },
            dismissButton = {
                TextButton(onClick = { showZipDialog = false }) { Text("Hủy", color = TextMuted) }
            },
            containerColor = CharcoalSurface
        )
    }

    // Dialog: File Info
    if (showInfoDialog != null) {
        val item = showInfoDialog!!
        val file = File(item.path)

        AlertDialog(
            onDismissRequest = { showInfoDialog = null },
            title = { Text(item.name, maxLines = 1, color = TextPrimary, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Đường dẫn:", color = TextMuted, fontSize = 11.sp)
                    Text(item.path, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary)

                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Dung lượng: ${item.formattedSize}", color = TextPrimary, fontSize = 13.sp)
                    Text("Ngày sửa đổi: ${item.formattedDate}", color = TextPrimary, fontSize = 13.sp)
                    Text("Loại: ${item.fileType.name} (${item.mimeType})", color = CyanAccent, fontSize = 13.sp)
                    Text("Quyền: Đọc=${item.isReadable}, Ghi=${item.isWritable}", color = TextSecondary, fontSize = 12.sp)

                    if (infoHashText != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Checksum SHA-256:", color = TextMuted, fontSize = 11.sp)
                        Text(infoHashText ?: "", fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = ColorSuccessGreen)
                    }

                    if (!item.isDirectory && infoHashText == null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = {
                                isCalculatingInfoHash = true
                                coroutineScope.launch {
                                    val h = HashUtils.calculateChecksum(file, "SHA-256").getOrDefault("Lỗi tính hash")
                                    infoHashText = h
                                    isCalculatingInfoHash = false
                                }
                            },
                            enabled = !isCalculatingInfoHash,
                            colors = ButtonDefaults.buttonColors(containerColor = CharcoalSurfaceHighlight),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(if (isCalculatingInfoHash) "Đang tính..." else "Tính mã băm SHA-256", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val details = "Tên: ${item.name}\nĐường dẫn: ${item.path}\nDung lượng: ${item.formattedSize}\nSHA-256: ${infoHashText ?: "N/A"}"
                        clipboardManager.setText(AnnotatedString(details))
                        showInfoDialog = null
                        coroutineScope.launch { snackbarHostState.showSnackbar("Đã sao chép thông tin tệp!") }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Sao chép")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInfoDialog = null }) { Text("Đóng", color = TextMuted) }
            },
            containerColor = CharcoalSurface
        )
    }

    // Dialog: Sort Options
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sắp xếp tệp", color = TextPrimary, fontSize = 16.sp) },
            text = {
                Column {
                    listOf(
                        SortField.NAME to "Theo tên",
                        SortField.SIZE to "Theo dung lượng",
                        SortField.DATE to "Theo ngày sửa đổi",
                        SortField.TYPE to "Theo định dạng"
                    ).forEach { (field, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSortOption(sortOption.copy(field = field))
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = sortOption.field == field,
                                onClick = { viewModel.setSortOption(sortOption.copy(field = field)) },
                                colors = RadioButtonDefaults.colors(selectedColor = ElectricBlue)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, color = TextPrimary, fontSize = 14.sp)
                        }
                    }

                    HorizontalDivider(color = CharcoalSurfaceHighlight, modifier = Modifier.padding(vertical = 6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = if (sortOption.order == SortOrder.ASCENDING) SortOrder.DESCENDING else SortOrder.ASCENDING
                                viewModel.setSortOption(sortOption.copy(order = next))
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = sortOption.order == SortOrder.DESCENDING,
                            onCheckedChange = { checked ->
                                viewModel.setSortOption(sortOption.copy(order = if (checked) SortOrder.DESCENDING else SortOrder.ASCENDING))
                            },
                            colors = CheckboxDefaults.colors(checkedColor = ElectricBlue)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Thứ tự giảm dần", color = TextPrimary, fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) { Text("Đóng", color = ElectricBlue) }
            },
            containerColor = CharcoalSurface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRowItem(
    item: FileItem,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onOpen: () -> Unit,
    onOpenHex: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onShowInfo: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        color = if (isSelected) ElectricBlue.copy(alpha = 0.18f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isMultiSelect) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                        colors = CheckboxDefaults.colors(checkedColor = ElectricBlue)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }

                FileIcon(item.fileType, item.extension, size = 26.dp)

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.formattedSize} • ${item.formattedDate}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted,
                        fontSize = 11.sp
                    )
                }

                // 3-dots Menu Button
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Tùy chọn",
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(CharcoalSurfaceVariant)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Mở", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null, tint = TextPrimary) },
                            onClick = { showMenu = false; onOpen() }
                        )
                        if (!item.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Xem mã HEX", color = CyanAccent) },
                                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = CyanAccent) },
                                onClick = { showMenu = false; onOpenHex() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Sao chép", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = TextPrimary) },
                            onClick = { showMenu = false; onCopy() }
                        )
                        DropdownMenuItem(
                            text = { Text("Cắt", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null, tint = TextPrimary) },
                            onClick = { showMenu = false; onCut() }
                        )
                        DropdownMenuItem(
                            text = { Text("Đổi tên", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = TextPrimary) },
                            onClick = { showMenu = false; onRename() }
                        )
                        if (!item.isDirectory) {
                            DropdownMenuItem(
                                text = { Text("Chia sẻ", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, tint = TextPrimary) },
                                onClick = { showMenu = false; onShare() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Thông tin", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = TextPrimary) },
                            onClick = { showMenu = false; onShowInfo() }
                        )
                        HorizontalDivider(color = CharcoalSurfaceHighlight)
                        DropdownMenuItem(
                            text = { Text("Xóa", color = ColorErrorRed) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ColorErrorRed) },
                            onClick = { showMenu = false; onDelete() }
                        )
                    }
                }
            }

            // Divider between items (Files by Google / My Files style)
            HorizontalDivider(
                color = CharcoalSurfaceHighlight.copy(alpha = 0.4f),
                modifier = Modifier.padding(start = 54.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCard(
    item: FileItem,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        color = if (isSelected) ElectricBlue.copy(alpha = 0.22f) else CharcoalSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(105.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FileIcon(item.fileType, item.extension, size = 32.dp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.formattedSize,
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun FileIcon(type: FileType, extension: String, size: androidx.compose.ui.unit.Dp = 26.dp) {
    val (icon, tint) = when (type) {
        FileType.FOLDER -> Icons.Default.Folder to ColorWarningAmber
        FileType.APK -> Icons.Default.Android to ColorSuccessGreen
        FileType.GAME_DATA -> Icons.Default.SportsEsports to ColorPurpleBadge
        FileType.DATABASE -> Icons.Default.Storage to ColorSuccessGreen
        FileType.ARCHIVE -> Icons.Default.FolderZip to ElectricBlue
        FileType.IMAGE -> Icons.Default.Image to Color(0xFFF48FB1)
        FileType.VIDEO -> Icons.Default.VideoFile to Color(0xFFEF5350)
        FileType.AUDIO -> Icons.Default.AudioFile to CyanAccent
        FileType.TEXT_CODE -> Icons.Default.Description to ColorOrangeBadge
        FileType.DOCUMENT -> Icons.Default.Article to ElectricBlue
        FileType.BINARY -> Icons.Default.Memory to CyanAccent
        else -> Icons.Default.InsertDriveFile to TextSecondary
    }

    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}
