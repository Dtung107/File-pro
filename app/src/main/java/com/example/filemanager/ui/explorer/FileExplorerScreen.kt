package com.example.filemanager.ui.explorer

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.model.*
import com.example.filemanager.utils.FileUtils
import com.example.filemanager.utils.HashUtils
import com.example.filemanager.viewmodel.DualPaneSplit
import com.example.filemanager.viewmodel.MainViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileExplorerScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Primary pane state
    val currentDir by viewModel.currentDirectory.collectAsState()
    val activeCategoryTitle by viewModel.activeCategoryTitle.collectAsState()
    val fileItems by viewModel.fileItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Dual pane state
    val isDualPane by viewModel.isDualPaneMode.collectAsState()
    val dualPaneSplit by viewModel.dualPaneSplit.collectAsState()
    val secondaryDir by viewModel.secondaryDirectory.collectAsState()
    val secondaryFileItems by viewModel.secondaryFileItems.collectAsState()
    val secondaryLoading by viewModel.secondaryLoading.collectAsState()
    val activePaneIndex by viewModel.activePaneIndex.collectAsState()

    // Selection & Clipboard
    val clipboard by viewModel.clipboard.collectAsState()
    val selectedItems by viewModel.selectedItems.collectAsState()
    val isMultiSelect by viewModel.isMultiSelectMode.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val operationProgress by viewModel.operationProgress.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var isSearching by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var createInSecondaryPane by remember { mutableStateOf(false) }

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

    // Drag-and-drop state between panes
    var draggingItem by remember { mutableStateOf<FileItem?>(null) }
    var dragSourcePane by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableStateOf(IntOffset.Zero) }
    var showTransferConfirmDialog by remember { mutableStateOf<Pair<FileItem, File>?>(null) }

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

    val displayedSecondaryItems = remember(secondaryFileItems, searchQuery) {
        if (searchQuery.isBlank()) secondaryFileItems
        else secondaryFileItems.filter { it.name.contains(searchQuery, ignoreCase = true) }
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
                        IconButton(onClick = { viewModel.setClipboard(ClipboardAction.COPY) }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Sao chép", tint = ElectricBlue)
                        }
                        IconButton(onClick = { viewModel.setClipboard(ClipboardAction.CUT) }) {
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activeCategoryTitle != null) {
                                Text(
                                    text = "${displayedItems.size} tệp",
                                    color = ElectricBlue,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            // Retraces directory backstack without jumping straight to Home!
                            val handled = viewModel.navigateBack()
                            if (!handled) onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
                        }
                    },
                    actions = {
                        // Split screen dual pane button
                        IconButton(onClick = { viewModel.toggleDualPane() }) {
                            Icon(
                                if (isDualPane) Icons.Default.VerticalSplit else Icons.Default.Splitscreen,
                                contentDescription = if (isDualPane) "Đóng cửa sổ kép" else "Mở cửa sổ kép",
                                tint = if (isDualPane) ColorWarningAmber else TextPrimary
                            )
                        }

                        if (isDualPane) {
                            IconButton(onClick = { viewModel.toggleDualPaneSplit() }) {
                                Icon(
                                    if (dualPaneSplit == DualPaneSplit.VERTICAL) Icons.Default.ViewColumn else Icons.Default.ViewStream,
                                    contentDescription = "Đổi hướng chia màn hình",
                                    tint = CyanAccent
                                )
                            }
                        }

                        IconButton(onClick = { isSearching = !isSearching }) {
                            Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = TextPrimary)
                        }
                        IconButton(onClick = { showSortDialog = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sắp xếp", tint = TextPrimary)
                        }
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
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
                        onClick = {
                            showFabMenu = false
                            createInSecondaryPane = isDualPane && activePaneIndex == 1
                            showCreateFolderDialog = true
                        },
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Tạo thư mục", fontSize = 13.sp) },
                        containerColor = CharcoalSurfaceVariant,
                        contentColor = TextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    ExtendedFloatingActionButton(
                        onClick = {
                            showFabMenu = false
                            createInSecondaryPane = isDualPane && activePaneIndex == 1
                            showCreateFileDialog = true
                        },
                        icon = { Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Tạo tệp mới", fontSize = 13.sp) },
                        containerColor = CharcoalSurfaceVariant,
                        contentColor = TextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    if (clipboard != null) {
                        ExtendedFloatingActionButton(
                            onClick = {
                                showFabMenu = false
                                val target = if (isDualPane && activePaneIndex == 1) secondaryDir else currentDir
                                viewModel.pasteClipboard(target)
                            },
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
                    Icon(
                        if (showFabMenu) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Hành động"
                    )
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
            // Search Input Row
            if (isSearching) {
                Surface(color = CharcoalSurface, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Tìm tệp hoặc thư mục...", color = TextMuted, fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = CharcoalSurfaceHighlight,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        IconButton(onClick = {
                            viewModel.setSearchQuery("")
                            isSearching = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng tìm kiếm", tint = TextMuted)
                        }
                    }
                }
            }

            // Operation Progress Bar
            if (operationProgress.isOperating) {
                Surface(color = CharcoalSurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(operationProgress.title, style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                            Text(
                                "${operationProgress.currentFileIndex}/${operationProgress.totalFiles}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ElectricBlue
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        val progress = if (operationProgress.totalFiles > 0) {
                            operationProgress.currentFileIndex.toFloat() / operationProgress.totalFiles.toFloat()
                        } else 0f
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = ElectricBlue)
                        if (operationProgress.currentFileName.isNotBlank()) {
                            Text(
                                operationProgress.currentFileName,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Dual Pane Banner
            if (isDualPane) {
                Surface(color = CharcoalSurfaceVariant, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Splitscreen, contentDescription = null, tint = ColorWarningAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Chế độ 2 cửa sổ: Kéo thả hoặc bấm nút chuyển tệp giữa 2 bên",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        TextButton(
                            onClick = { viewModel.toggleDualPane(false) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Đóng", color = ColorErrorRed, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Content Area: Single Pane OR Dual Pane
            if (isDualPane) {
                if (dualPaneSplit == DualPaneSplit.VERTICAL) {
                    // Top / Bottom Split
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Pane A (Top)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .border(
                                    width = if (activePaneIndex == 0) 1.5.dp else 0.5.dp,
                                    color = if (activePaneIndex == 0) ElectricBlue else CharcoalSurfaceHighlight
                                )
                                .clickable { viewModel.setActivePane(0) }
                        ) {
                            ExplorerPane(
                                title = "Cửa sổ 1 (Chính)",
                                currentDir = currentDir,
                                items = displayedItems,
                                isLoading = isLoading,
                                isGridView = isGridView,
                                selectedItems = selectedItems,
                                isActivePane = activePaneIndex == 0,
                                isDualPane = true,
                                onFolderClick = { viewModel.enterDirectory(it) },
                                onFileClick = { viewModel.openFile(it) },
                                onSelectToggle = { viewModel.toggleSelectItem(it) },
                                onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                onRename = { showRenameDialog = it; renameInput = it.name },
                                onDelete = { viewModel.deleteSingleFile(it) },
                                onInfo = { showInfoDialog = it },
                                onTransferToOtherPane = { showTransferConfirmDialog = Pair(it, secondaryDir) },
                                onDragStart = { item, offset ->
                                    draggingItem = item
                                    dragSourcePane = 0
                                    dragOffset = offset
                                },
                                onDragEnd = {
                                    draggingItem?.let { item ->
                                        showTransferConfirmDialog = Pair(item, secondaryDir)
                                    }
                                    draggingItem = null
                                }
                            )
                        }

                        // Divider bar
                        Surface(color = CharcoalSurfaceHighlight, modifier = Modifier.fillMaxWidth().height(4.dp)) {}

                        // Pane B (Bottom)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .border(
                                    width = if (activePaneIndex == 1) 1.5.dp else 0.5.dp,
                                    color = if (activePaneIndex == 1) ElectricBlue else CharcoalSurfaceHighlight
                                )
                                .clickable { viewModel.setActivePane(1) }
                        ) {
                            ExplorerPane(
                                title = "Cửa sổ 2 (Phụ)",
                                currentDir = secondaryDir,
                                items = displayedSecondaryItems,
                                isLoading = secondaryLoading,
                                isGridView = isGridView,
                                selectedItems = emptySet(),
                                isActivePane = activePaneIndex == 1,
                                isDualPane = true,
                                onFolderClick = { viewModel.enterSecondaryDirectory(it) },
                                onFileClick = { viewModel.openFile(it) },
                                onSelectToggle = {},
                                onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                onRename = { showRenameDialog = it; renameInput = it.name },
                                onDelete = { viewModel.deleteSingleFile(it) },
                                onInfo = { showInfoDialog = it },
                                onTransferToOtherPane = { showTransferConfirmDialog = Pair(it, currentDir) },
                                onDragStart = { item, offset ->
                                    draggingItem = item
                                    dragSourcePane = 1
                                    dragOffset = offset
                                },
                                onDragEnd = {
                                    draggingItem?.let { item ->
                                        showTransferConfirmDialog = Pair(item, currentDir)
                                    }
                                    draggingItem = null
                                }
                            )
                        }
                    }
                } else {
                    // Left / Right Split
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Pane A (Left)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(
                                    width = if (activePaneIndex == 0) 1.5.dp else 0.5.dp,
                                    color = if (activePaneIndex == 0) ElectricBlue else CharcoalSurfaceHighlight
                                )
                                .clickable { viewModel.setActivePane(0) }
                        ) {
                            ExplorerPane(
                                title = "Cửa sổ 1",
                                currentDir = currentDir,
                                items = displayedItems,
                                isLoading = isLoading,
                                isGridView = isGridView,
                                selectedItems = selectedItems,
                                isActivePane = activePaneIndex == 0,
                                isDualPane = true,
                                onFolderClick = { viewModel.enterDirectory(it) },
                                onFileClick = { viewModel.openFile(it) },
                                onSelectToggle = { viewModel.toggleSelectItem(it) },
                                onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                onRename = { showRenameDialog = it; renameInput = it.name },
                                onDelete = { viewModel.deleteSingleFile(it) },
                                onInfo = { showInfoDialog = it },
                                onTransferToOtherPane = { showTransferConfirmDialog = Pair(it, secondaryDir) },
                                onDragStart = { item, offset ->
                                    draggingItem = item
                                    dragSourcePane = 0
                                    dragOffset = offset
                                },
                                onDragEnd = {
                                    draggingItem?.let { item ->
                                        showTransferConfirmDialog = Pair(item, secondaryDir)
                                    }
                                    draggingItem = null
                                }
                            )
                        }

                        // Divider bar
                        Surface(color = CharcoalSurfaceHighlight, modifier = Modifier.fillMaxHeight().width(4.dp)) {}

                        // Pane B (Right)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(
                                    width = if (activePaneIndex == 1) 1.5.dp else 0.5.dp,
                                    color = if (activePaneIndex == 1) ElectricBlue else CharcoalSurfaceHighlight
                                )
                                .clickable { viewModel.setActivePane(1) }
                        ) {
                            ExplorerPane(
                                title = "Cửa sổ 2",
                                currentDir = secondaryDir,
                                items = displayedSecondaryItems,
                                isLoading = secondaryLoading,
                                isGridView = isGridView,
                                selectedItems = emptySet(),
                                isActivePane = activePaneIndex == 1,
                                isDualPane = true,
                                onFolderClick = { viewModel.enterSecondaryDirectory(it) },
                                onFileClick = { viewModel.openFile(it) },
                                onSelectToggle = {},
                                onFavoriteToggle = { viewModel.toggleFavorite(it) },
                                onRename = { showRenameDialog = it; renameInput = it.name },
                                onDelete = { viewModel.deleteSingleFile(it) },
                                onInfo = { showInfoDialog = it },
                                onTransferToOtherPane = { showTransferConfirmDialog = Pair(it, currentDir) },
                                onDragStart = { item, offset ->
                                    draggingItem = item
                                    dragSourcePane = 1
                                    dragOffset = offset
                                },
                                onDragEnd = {
                                    draggingItem?.let { item ->
                                        showTransferConfirmDialog = Pair(item, currentDir)
                                    }
                                    draggingItem = null
                                }
                            )
                        }
                    }
                }
            } else {
                // Single Pane Mode
                ExplorerPane(
                    title = "Trình duyệt tệp",
                    currentDir = currentDir,
                    items = displayedItems,
                    isLoading = isLoading,
                    isGridView = isGridView,
                    selectedItems = selectedItems,
                    isActivePane = true,
                    isDualPane = false,
                    onFolderClick = { viewModel.enterDirectory(it) },
                    onFileClick = { viewModel.openFile(it) },
                    onSelectToggle = { viewModel.toggleSelectItem(it) },
                    onFavoriteToggle = { viewModel.toggleFavorite(it) },
                    onRename = { showRenameDialog = it; renameInput = it.name },
                    onDelete = { viewModel.deleteSingleFile(it) },
                    onInfo = { showInfoDialog = it },
                    onTransferToOtherPane = {},
                    onDragStart = { _, _ -> },
                    onDragEnd = {}
                )
            }
        }
    }

    // Drag-and-drop Transfer Dialog
    if (showTransferConfirmDialog != null) {
        val (item, targetDir) = showTransferConfirmDialog!!
        AlertDialog(
            onDismissRequest = { showTransferConfirmDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CompareArrows, contentDescription = null, tint = ElectricBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Chuyển tệp sang cửa sổ khác", fontSize = 16.sp, color = TextPrimary)
                }
            },
            text = {
                Column {
                    Text("Tệp: ${item.name}", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Đích đến: ${targetDir.name} (${targetDir.absolutePath})", color = TextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.transferFileBetweenPanes(item, targetDir, isMove = false)
                            showTransferConfirmDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Text("Sao chép")
                    }
                    Button(
                        onClick = {
                            viewModel.transferFileBetweenPanes(item, targetDir, isMove = true)
                            showTransferConfirmDialog = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorWarningAmber)
                    ) {
                        Text("Di chuyển", color = Color.Black)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransferConfirmDialog = null }) {
                    Text("Hủy", color = TextMuted)
                }
            },
            containerColor = CharcoalSurface
        )
    }

    // Dialogs: Create Folder
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(if (createInSecondaryPane) "Tạo thư mục ở Cửa sổ 2" else "Tạo thư mục mới", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("Tên thư mục", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createFolder(newFolderName.trim(), inSecondaryPane = createInSecondaryPane)
                            newFolderName = ""
                            showCreateFolderDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) { Text("Tạo") }
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
            title = { Text(if (createInSecondaryPane) "Tạo tệp mới ở Cửa sổ 2" else "Tạo tệp văn bản mới", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newFileName,
                    onValueChange = { newFileName = it },
                    placeholder = { Text("Tên tệp (vd: note.txt, script.py)", color = TextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            viewModel.createFile(newFileName.trim(), inSecondaryPane = createInSecondaryPane)
                            newFileName = ""
                            showCreateFileDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) { Text("Tạo") }
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
            title = { Text("Đổi tên", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank() && renameInput != item.name) {
                            viewModel.renameFile(item, renameInput.trim())
                            showRenameDialog = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) { Text("Đổi tên") }
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
            title = { Text("Xác nhận xóa tệp", color = TextPrimary) },
            text = { Text("Bạn có chắc chắn muốn xóa ${selectedItems.size} tệp/thư mục đã chọn?", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedItems()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorErrorRed)
                ) { Text("Xóa vĩnh viễn") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) { Text("Hủy", color = TextMuted) }
            },
            containerColor = CharcoalSurface
        )
    }

    // Dialog: Zip
    if (showZipDialog) {
        AlertDialog(
            onDismissRequest = { showZipDialog = false },
            title = { Text("Nén thành tệp ZIP", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = zipInputName,
                    onValueChange = { zipInputName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createZipFromSelected(zipInputName.trim())
                        showZipDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) { Text("Bắt đầu nén") }
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
            title = { Text(item.name, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Đường dẫn: ${item.path}", color = TextSecondary, fontSize = 12.sp)
                    Text("Dung lượng: ${FileItem.formatBytes(item.size)} (${item.size} bytes)", color = TextSecondary, fontSize = 12.sp)
                    Text("Ngày sửa đổi: ${FileItem.formatDate(item.lastModified)}", color = TextSecondary, fontSize = 12.sp)
                    Text("Định dạng: ${item.extension.ifEmpty { "Không có" }} (${item.fileType.name})", color = TextSecondary, fontSize = 12.sp)

                    if (!item.isDirectory) {
                        Spacer(modifier = Modifier.height(4.dp))
                        if (infoHashText == null) {
                            Button(
                                onClick = {
                                    isCalculatingInfoHash = true
                                    coroutineScope.launch {
                                        val hashes = HashUtils.calculateAllHashes(file).getOrNull()
                                        infoHashText = hashes?.let { "MD5: ${it["MD5"]}\nSHA-256: ${it["SHA-256"]}" } ?: "Không thể tính"
                                        isCalculatingInfoHash = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CharcoalSurfaceHighlight),
                                enabled = !isCalculatingInfoHash,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(if (isCalculatingInfoHash) "Đang tính..." else "Tính mã Checksum", fontSize = 11.sp)
                            }
                        } else {
                            Text(infoHashText!!, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = CyanAccent)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showInfoDialog = null
                    infoHashText = null
                }) { Text("Đóng", color = ElectricBlue) }
            },
            containerColor = CharcoalSurface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorerPane(
    title: String,
    currentDir: File,
    items: List<FileItem>,
    isLoading: Boolean,
    isGridView: Boolean,
    selectedItems: Set<FileItem>,
    isActivePane: Boolean,
    isDualPane: Boolean,
    onFolderClick: (File) -> Unit,
    onFileClick: (FileItem) -> Unit,
    onSelectToggle: (FileItem) -> Unit,
    onFavoriteToggle: (FileItem) -> Unit,
    onRename: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onInfo: (FileItem) -> Unit,
    onTransferToOtherPane: (FileItem) -> Unit,
    onDragStart: (FileItem, IntOffset) -> Unit,
    onDragEnd: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Breadcrumb and path bar
        Surface(color = CharcoalSurface, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDualPane) {
                    Surface(
                        color = if (isActivePane) ElectricBlue else CharcoalSurfaceHighlight,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))

                val pathSegments = remember(currentDir) {
                    val root = "/storage/emulated/0"
                    val path = currentDir.absolutePath
                    val relative = if (path.startsWith(root)) path.removePrefix(root).trimStart('/') else path.trimStart('/')
                    if (relative.isEmpty()) listOf("Bộ nhớ trong" to currentDir)
                    else {
                        val segs = relative.split('/')
                        val list = mutableListOf("Bộ nhớ trong" to File(root))
                        var cum = root
                        for (s in segs) {
                            cum = "$cum/$s"
                            list.add(s to File(cum))
                        }
                        list
                    }
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pathSegments.forEachIndexed { idx, (name, dir) ->
                        if (idx > 0) {
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                        Text(
                            text = name,
                            color = if (idx == pathSegments.lastIndex) ElectricBlue else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (idx == pathSegments.lastIndex) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onFolderClick(dir) }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = "${items.size} mục",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }

        HorizontalDivider(color = CharcoalDivider, thickness = 0.5.dp)

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(32.dp))
            }
        } else if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Thư mục trống", color = TextMuted, fontSize = 13.sp)
                }
            }
        } else {
            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = if (isDualPane) 80.dp else 95.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items, key = { it.path }) { item ->
                        GridFileCard(
                            item = item,
                            isSelected = selectedItems.contains(item),
                            onItemClick = {
                                if (item.isDirectory) onFolderClick(File(item.path))
                                else onFileClick(item)
                            },
                            onLongClick = { onSelectToggle(item) }
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.path }) { item ->
                        ListFileRow(
                            item = item,
                            isSelected = selectedItems.contains(item),
                            isDualPane = isDualPane,
                            onItemClick = {
                                if (item.isDirectory) onFolderClick(File(item.path))
                                else onFileClick(item)
                            },
                            onLongClick = { onSelectToggle(item) },
                            onFavorite = { onFavoriteToggle(item) },
                            onRename = { onRename(item) },
                            onDelete = { onDelete(item) },
                            onInfo = { onInfo(item) },
                            onTransferToOtherPane = { onTransferToOtherPane(item) },
                            onDragStart = { offset -> onDragStart(item, offset) },
                            onDragEnd = onDragEnd
                        )
                        HorizontalDivider(color = CharcoalDivider, thickness = 0.5.dp, modifier = Modifier.padding(start = 52.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListFileRow(
    item: FileItem,
    isSelected: Boolean,
    isDualPane: Boolean,
    onItemClick: () -> Unit,
    onLongClick: () -> Unit,
    onFavorite: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onInfo: () -> Unit,
    onTransferToOtherPane: () -> Unit,
    onDragStart: (IntOffset) -> Unit,
    onDragEnd: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) ElectricBlue.copy(alpha = 0.15f) else Color.Transparent)
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onLongClick
            )
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        onDragStart(IntOffset(offset.x.roundToInt(), offset.y.roundToInt()))
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                    }
                )
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileIcon(item = item, isSelected = isSelected, modifier = Modifier.size(36.dp))

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val detail = if (item.isDirectory) {
                "${item.childCount} mục • ${FileItem.formatDate(item.lastModified)}"
            } else {
                "${FileItem.formatBytes(item.size)} • ${FileItem.formatDate(item.lastModified)}"
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Quick Transfer button in dual-pane mode
        if (isDualPane) {
            IconButton(
                onClick = onTransferToOtherPane,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Chuyển sang cửa sổ bên",
                    tint = ElectricBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = TextMuted, modifier = Modifier.size(18.dp))
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(CharcoalSurface)
            ) {
                if (isDualPane) {
                    DropdownMenuItem(
                        text = { Text("Chuyển sang cửa sổ bên", color = ElectricBlue) },
                        onClick = { showMenu = false; onTransferToOtherPane() },
                        leadingIcon = { Icon(Icons.Default.CompareArrows, contentDescription = null, tint = ElectricBlue) }
                    )
                    HorizontalDivider(color = CharcoalDivider)
                }
                DropdownMenuItem(
                    text = { Text(if (item.isFavorite) "Bỏ yêu thích" else "Yêu thích", color = TextPrimary) },
                    onClick = { showMenu = false; onFavorite() },
                    leadingIcon = {
                        Icon(
                            if (item.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = null,
                            tint = if (item.isFavorite) ColorWarningAmber else TextPrimary
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Đổi tên", color = TextPrimary) },
                    onClick = { showMenu = false; onRename() },
                    leadingIcon = { Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null, tint = TextPrimary) }
                )
                DropdownMenuItem(
                    text = { Text("Thông tin", color = TextPrimary) },
                    onClick = { showMenu = false; onInfo() },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = TextPrimary) }
                )
                HorizontalDivider(color = CharcoalDivider)
                DropdownMenuItem(
                    text = { Text("Xóa", color = ColorErrorRed) },
                    onClick = { showMenu = false; onDelete() },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ColorErrorRed) }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridFileCard(
    item: FileItem,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        color = if (isSelected) ElectricBlue.copy(alpha = 0.2f) else CharcoalSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onItemClick, onLongClick = onLongClick)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FileIcon(item = item, isSelected = isSelected, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 11.sp
            )
            Text(
                text = if (item.isDirectory) "${item.childCount} mục" else FileItem.formatBytes(item.size),
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun FileIcon(item: FileItem, isSelected: Boolean, modifier: Modifier = Modifier) {
    if (isSelected) {
        Surface(shape = RoundedCornerShape(8.dp), color = ElectricBlue, modifier = modifier) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = "Đã chọn", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    } else if (item.isDirectory) {
        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFFFB300).copy(alpha = 0.2f), modifier = modifier) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Folder, contentDescription = "Thư mục", tint = Color(0xFFFFB300), modifier = Modifier.size(22.dp))
            }
        }
    } else {
        val (icon, color) = when (item.fileType) {
            FileType.APK -> Icons.Default.Android to ColorSuccessGreen
            FileType.DATABASE -> Icons.Default.Storage to ColorSuccessGreen
            FileType.ARCHIVE -> Icons.Default.FolderZip to ElectricBlue
            FileType.TEXT_CODE -> Icons.Default.Description to CyanAccent
            FileType.IMAGE -> Icons.Default.Image to Color(0xFFF48FB1)
            FileType.VIDEO -> Icons.Default.VideoFile to Color(0xFFEF5350)
            FileType.AUDIO -> Icons.Default.AudioFile to CyanAccent
            FileType.DOCUMENT -> Icons.Default.Article to ElectricBlue
            FileType.GAME_DATA -> Icons.Default.SportsEsports to ColorPurpleBadge
            else -> Icons.Default.InsertDriveFile to TextMuted
        }

        Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.15f), modifier = modifier) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = item.extension, tint = color, modifier = Modifier.size(20.dp))
            }
        }
    }
}
