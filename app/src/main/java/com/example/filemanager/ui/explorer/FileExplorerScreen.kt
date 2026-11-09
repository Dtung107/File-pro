package com.example.filemanager.ui.explorer

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
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
import com.example.MainActivity
import com.example.filemanager.model.*
import com.example.filemanager.utils.FileUtils
import com.example.filemanager.utils.HashUtils
import com.example.filemanager.utils.NetworkSharingManager
import com.example.filemanager.viewmodel.DualPaneSplit
import com.example.filemanager.viewmodel.MainViewModel
import com.example.filemanager.viewmodel.ScreenDestination
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private val CoralChipTint = Color(0xFFFF5252)
private val ChipBorderColor = Color(0xFF333844)
private val DocumentsUIDarkBg = Color(0xFF121316)
private val DocumentsUIDivider = Color(0xFF232733)
private val DocumentsUIFolderGrey = Color(0xFF8A909D)

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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Primary pane state
    val currentDir by viewModel.currentDirectory.collectAsState()
    val activeCategoryTitle by viewModel.activeCategoryTitle.collectAsState()
    val fileItems by viewModel.fileItems.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val storageVolumes by viewModel.storageVolumes.collectAsState()

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
    var showMoreMenu by remember { mutableStateOf(false) }

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

    var showConnectionsDialog by remember { mutableStateOf(false) }

    // Drag-and-drop state between panes
    var draggingItem by remember { mutableStateOf<FileItem?>(null) }
    var dragSourcePane by remember { mutableIntStateOf(0) }
    var dragOffset by remember { mutableStateOf(IntOffset.Zero) }
    var showTransferConfirmDialog by remember { mutableStateOf<Pair<FileItem, File>?>(null) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.handleSafUri(uri)
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    BackHandler {
        if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else if (isSearching) {
            isSearching = false
            viewModel.setSearchQuery("")
        } else if (isMultiSelect) {
            viewModel.clearSelection()
        } else {
            viewModel.navigateBack()
        }
    }

    val primaryDisplayName = remember(currentDir) {
        val rootPath = android.os.Environment.getExternalStorageDirectory().absolutePath
        if (currentDir.absolutePath == rootPath) {
            android.os.Build.MODEL.ifEmpty { "MNA-AL00" }
        } else {
            currentDir.name.ifEmpty { android.os.Build.MODEL.ifEmpty { "MNA-AL00" } }
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CharcoalSurface,
                drawerContentColor = TextPrimary
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "FILE MANAGER PRO",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Advanced Data Explorer",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }

                HorizontalDivider(color = DocumentsUIDivider, modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "VỊ TRÍ BỘ NHỚ",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )

                storageVolumes.forEach { vol ->
                    NavigationDrawerItem(
                        icon = { Icon(if (vol.isRemovable) Icons.Default.SdCard else Icons.Default.PhoneAndroid, null, tint = if (vol.isRemovable) CyanAccent else Color(0xFFFFB300)) },
                        label = {
                            Column {
                                Text(if (vol.isPrimary) (android.os.Build.MODEL.ifEmpty { vol.name }) else vol.name, color = TextPrimary)
                                Text(
                                    "${FileItem.formatBytes(vol.freeBytes)} trống / ${FileItem.formatBytes(vol.totalBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                            }
                        },
                        selected = currentDir.absolutePath == vol.path,
                        onClick = {
                            viewModel.openStorageRoot(File(vol.path))
                            coroutineScope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.AddCircleOutline, null, tint = ElectricBlue) },
                    label = { Text("Mở thêm thư mục SAF / USB OTG", color = TextPrimary) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        safLauncher.launch(null)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                HorizontalDivider(color = DocumentsUIDivider, modifier = Modifier.padding(vertical = 8.dp))

                Text(
                    text = "TÍNH NĂNG NÂNG CAO",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Bold
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Launch, null, tint = CyanAccent) },
                    label = { Text("Mở cửa sổ mới (2 ứng dụng độc lập)", color = TextPrimary) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        openNewAppWindow(context)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Splitscreen, null, tint = ElectricBlue) },
                    label = { Text("Mở chia đôi màn hình (Dual Pane)", color = TextPrimary) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        viewModel.navigateTo(ScreenDestination.DualPane)
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Wifi, null, tint = ColorSuccessGreen) },
                    label = { Text("Kết nối & Chia sẻ tệp Wi-Fi", color = TextPrimary) },
                    selected = false,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        showConnectionsDialog = true
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        if (isMultiSelect) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextPrimary)
                            }
                        } else {
                            IconButton(onClick = {
                                coroutineScope.launch { drawerState.open() }
                            }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu danh mục", tint = TextPrimary)
                            }
                        }
                    },
                    title = {
                        if (isSearching) {
                            TextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Tìm kiếm tệp...", color = TextMuted) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = ElectricBlue
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (isMultiSelect) {
                            Text(
                                text = "Đã chọn ${selectedItems.size} mục",
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = activeCategoryTitle ?: primaryDisplayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }
                    },
                    actions = {
                        if (isMultiSelect) {
                            IconButton(onClick = { viewModel.setClipboard(ClipboardAction.COPY) }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Sao chép", tint = TextPrimary)
                            }
                            IconButton(onClick = { viewModel.setClipboard(ClipboardAction.CUT) }) {
                                Icon(Icons.Default.ContentCut, contentDescription = "Cắt", tint = TextPrimary)
                            }
                            IconButton(onClick = { showDeleteConfirmDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = ColorErrorRed)
                            }
                            IconButton(onClick = { showZipDialog = true }) {
                                Icon(Icons.Default.FolderZip, contentDescription = "Nén ZIP", tint = CyanAccent)
                            }
                            IconButton(onClick = {
                                if (selectedItems.size == 1) {
                                    val item = selectedItems.first()
                                    FileUtils.shareFile(context, File(item.path))
                                }
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Chia sẻ", tint = TextPrimary)
                            }
                        } else {
                            IconButton(onClick = { isSearching = !isSearching }) {
                                Icon(
                                    if (isSearching) Icons.Default.Close else Icons.Default.Search,
                                    contentDescription = "Tìm kiếm",
                                    tint = TextPrimary
                                )
                            }
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Tùy chọn khác", tint = TextPrimary)
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false },
                                    modifier = Modifier.background(CharcoalSurfaceVariant)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Mở cửa sổ mới (2 ứng dụng độc lập)", color = CyanAccent) },
                                        leadingIcon = { Icon(Icons.Default.Launch, null, tint = CyanAccent) },
                                        onClick = {
                                            showMoreMenu = false
                                            openNewAppWindow(context)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isDualPane) "Tắt chia đôi màn hình" else "Chia đôi màn hình (Dual Pane)", color = ElectricBlue) },
                                        leadingIcon = { Icon(Icons.Default.Splitscreen, null, tint = ElectricBlue) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.toggleDualPane()
                                        }
                                    )
                                    if (isDualPane) {
                                        DropdownMenuItem(
                                            text = { Text("Đổi chiều chia đôi (Dọc / Ngang)", color = TextPrimary) },
                                            leadingIcon = { Icon(Icons.Default.ScreenRotation, null, tint = TextPrimary) },
                                            onClick = {
                                                showMoreMenu = false
                                                viewModel.toggleDualPaneSplit()
                                            }
                                        )
                                    }
                                    HorizontalDivider(color = DocumentsUIDivider)
                                    DropdownMenuItem(
                                        text = { Text("Tạo thư mục mới", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.CreateNewFolder, null, tint = Color(0xFFFFB300)) },
                                        onClick = {
                                            showMoreMenu = false
                                            createInSecondaryPane = false
                                            newFolderName = ""
                                            showCreateFolderDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Tạo tệp mới", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.NoteAdd, null, tint = ElectricBlue) },
                                        onClick = {
                                            showMoreMenu = false
                                            createInSecondaryPane = false
                                            newFileName = ""
                                            showCreateFileDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sắp xếp theo...", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, null, tint = TextPrimary) },
                                        onClick = {
                                            showMoreMenu = false
                                            showSortDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Hiện/Ẩn tệp ẩn", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.Visibility, null, tint = TextPrimary) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.toggleShowHiddenFiles()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Kết nối & Chia sẻ Wi-Fi", color = ColorSuccessGreen) },
                                        leadingIcon = { Icon(Icons.Default.Wifi, null, tint = ColorSuccessGreen) },
                                        onClick = {
                                            showMoreMenu = false
                                            showConnectionsDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Chọn tất cả", color = TextPrimary) },
                                        leadingIcon = { Icon(Icons.Default.SelectAll, null, tint = TextPrimary) },
                                        onClick = {
                                            showMoreMenu = false
                                            viewModel.selectAll()
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DocumentsUIDarkBg)
                )
            },
            containerColor = DocumentsUIDarkBg
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Operation progress bar
                if (operationProgress.isRunning) {
                    LinearProgressIndicator(
                        progress = { operationProgress.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = ElectricBlue
                    )
                }

                // 1. Horizontal Category Filter Chips Row (Matches DocumentsUI screenshot)
                if (activeCategoryTitle == null && !isDualPane) {
                    CategoryFilterRow(
                        onSelectCategory = { type, title ->
                            viewModel.loadCategoryFiles(type, title)
                        }
                    )
                }

                // 2. Subheader row: "Tệp trên MNA-AL00" + View Mode / Sort Toggles
                SubheaderRow(
                    folderName = if (activeCategoryTitle != null) activeCategoryTitle!! else primaryDisplayName,
                    itemCount = displayedItems.size,
                    isGridView = isGridView,
                    onToggleView = { viewModel.toggleViewMode() },
                    onOpenSort = { showSortDialog = true },
                    onOpenNewWindow = { openNewAppWindow(context) }
                )

                // 3. Breadcrumb Path Bar (if in subfolder)
                if (activeCategoryTitle == null) {
                    PathBreadcrumbsBar(
                        currentDir = currentDir,
                        onNavigateTo = { viewModel.enterDirectory(it) }
                    )
                }

                // 4. Main Body: Single Pane or Dual-Pane Split Screen
                Box(modifier = Modifier.weight(1f)) {
                    if (isDualPane) {
                        // Two completely separate independent file explorer panes
                        if (dualPaneSplit == DualPaneSplit.VERTICAL) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .border(
                                            width = if (activePaneIndex == 0) 1.5.dp else 0.dp,
                                            color = if (activePaneIndex == 0) ElectricBlue else Color.Transparent
                                        )
                                ) {
                                    PaneContent(
                                        paneIndex = 0,
                                        dir = currentDir,
                                        items = displayedItems,
                                        isLoading = isLoading,
                                        isGridView = isGridView,
                                        selectedItems = selectedItems,
                                        clipboard = clipboard,
                                        onEnterDir = { viewModel.enterDirectory(it) },
                                        onOpenFile = { viewModel.openFile(it) },
                                        onToggleSelect = { viewModel.toggleSelectItem(it) },
                                        onLongClick = { viewModel.toggleSelectItem(it) },
                                        onFocus = { viewModel.setActivePane(0) },
                                        onTransferToOtherPane = { item, isMove ->
                                            viewModel.transferFileBetweenPanes(item, secondaryDir, isMove)
                                        },
                                        otherPaneDirName = secondaryDir.name.ifEmpty { "Cửa sổ 2" },
                                        onShowInfo = { item ->
                                            showInfoDialog = item
                                            isCalculatingInfoHash = false
                                            infoHashText = null
                                        },
                                        onRename = { item ->
                                            showRenameDialog = item
                                            renameInput = item.name
                                        },
                                        onDelete = { item -> viewModel.deleteSingleFile(item) }
                                    )
                                }

                                VerticalDivider(color = DocumentsUIDivider, thickness = 2.dp)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .border(
                                            width = if (activePaneIndex == 1) 1.5.dp else 0.dp,
                                            color = if (activePaneIndex == 1) ElectricBlue else Color.Transparent
                                        )
                                ) {
                                    PaneContent(
                                        paneIndex = 1,
                                        dir = secondaryDir,
                                        items = displayedSecondaryItems,
                                        isLoading = secondaryLoading,
                                        isGridView = isGridView,
                                        selectedItems = selectedItems,
                                        clipboard = clipboard,
                                        onEnterDir = { viewModel.enterSecondaryDirectory(it) },
                                        onOpenFile = { viewModel.openFile(it) },
                                        onToggleSelect = { viewModel.toggleSelectItem(it) },
                                        onLongClick = { viewModel.toggleSelectItem(it) },
                                        onFocus = { viewModel.setActivePane(1) },
                                        onTransferToOtherPane = { item, isMove ->
                                            viewModel.transferFileBetweenPanes(item, currentDir, isMove)
                                        },
                                        otherPaneDirName = currentDir.name.ifEmpty { "Cửa sổ 1" },
                                        onShowInfo = { item ->
                                            showInfoDialog = item
                                            isCalculatingInfoHash = false
                                            infoHashText = null
                                        },
                                        onRename = { item ->
                                            showRenameDialog = item
                                            renameInput = item.name
                                        },
                                        onDelete = { item -> viewModel.deleteSingleFile(item) }
                                    )
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .border(
                                            width = if (activePaneIndex == 0) 1.5.dp else 0.dp,
                                            color = if (activePaneIndex == 0) ElectricBlue else Color.Transparent
                                        )
                                ) {
                                    PaneContent(
                                        paneIndex = 0,
                                        dir = currentDir,
                                        items = displayedItems,
                                        isLoading = isLoading,
                                        isGridView = isGridView,
                                        selectedItems = selectedItems,
                                        clipboard = clipboard,
                                        onEnterDir = { viewModel.enterDirectory(it) },
                                        onOpenFile = { viewModel.openFile(it) },
                                        onToggleSelect = { viewModel.toggleSelectItem(it) },
                                        onLongClick = { viewModel.toggleSelectItem(it) },
                                        onFocus = { viewModel.setActivePane(0) },
                                        onTransferToOtherPane = { item, isMove ->
                                            viewModel.transferFileBetweenPanes(item, secondaryDir, isMove)
                                        },
                                        otherPaneDirName = secondaryDir.name.ifEmpty { "Cửa sổ 2" },
                                        onShowInfo = { item ->
                                            showInfoDialog = item
                                            isCalculatingInfoHash = false
                                            infoHashText = null
                                        },
                                        onRename = { item ->
                                            showRenameDialog = item
                                            renameInput = item.name
                                        },
                                        onDelete = { item -> viewModel.deleteSingleFile(item) }
                                    )
                                }

                                HorizontalDivider(color = DocumentsUIDivider, thickness = 2.dp)

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .border(
                                            width = if (activePaneIndex == 1) 1.5.dp else 0.dp,
                                            color = if (activePaneIndex == 1) ElectricBlue else Color.Transparent
                                        )
                                ) {
                                    PaneContent(
                                        paneIndex = 1,
                                        dir = secondaryDir,
                                        items = displayedSecondaryItems,
                                        isLoading = secondaryLoading,
                                        isGridView = isGridView,
                                        selectedItems = selectedItems,
                                        clipboard = clipboard,
                                        onEnterDir = { viewModel.enterSecondaryDirectory(it) },
                                        onOpenFile = { viewModel.openFile(it) },
                                        onToggleSelect = { viewModel.toggleSelectItem(it) },
                                        onLongClick = { viewModel.toggleSelectItem(it) },
                                        onFocus = { viewModel.setActivePane(1) },
                                        onTransferToOtherPane = { item, isMove ->
                                            viewModel.transferFileBetweenPanes(item, currentDir, isMove)
                                        },
                                        otherPaneDirName = currentDir.name.ifEmpty { "Cửa sổ 1" },
                                        onShowInfo = { item ->
                                            showInfoDialog = item
                                            isCalculatingInfoHash = false
                                            infoHashText = null
                                        },
                                        onRename = { item ->
                                            showRenameDialog = item
                                            renameInput = item.name
                                        },
                                        onDelete = { item -> viewModel.deleteSingleFile(item) }
                                    )
                                }
                            }
                        }
                    } else {
                        // Standard Single Pane (Full DocumentsUI style)
                        PaneContent(
                            paneIndex = 0,
                            dir = currentDir,
                            items = displayedItems,
                            isLoading = isLoading,
                            isGridView = isGridView,
                            selectedItems = selectedItems,
                            clipboard = clipboard,
                            onEnterDir = { viewModel.enterDirectory(it) },
                            onOpenFile = { viewModel.openFile(it) },
                            onToggleSelect = { viewModel.toggleSelectItem(it) },
                            onLongClick = { viewModel.toggleSelectItem(it) },
                            onFocus = { },
                            onTransferToOtherPane = { _, _ -> },
                            otherPaneDirName = "",
                            onShowInfo = { item ->
                                showInfoDialog = item
                                isCalculatingInfoHash = false
                                infoHashText = null
                            },
                            onRename = { item ->
                                showRenameDialog = item
                                renameInput = item.name
                            },
                            onDelete = { item -> viewModel.deleteSingleFile(item) }
                        )
                    }

                    // Floating Clipboard Bar (Paste / Cancel)
                    if (clipboard != null && !isMultiSelect) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = CharcoalSurfaceHighlight,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${if (clipboard?.action == ClipboardAction.COPY) "Sao chép" else "Di chuyển"} (${clipboard?.items?.size} tệp)",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(
                                    onClick = { viewModel.pasteClipboard() },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Dán vào đây")
                                }
                                IconButton(onClick = { viewModel.clearClipboard() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Hủy", tint = TextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialogs ---

    // 1. Wi-Fi File Sharing & Connections Dialog
    if (showConnectionsDialog) {
        var isServerOn by remember { mutableStateOf(NetworkSharingManager.isServerRunning) }
        val ipAddress = remember { NetworkSharingManager.getLocalIpAddress(context) }
        val serverUrl = "http://$ipAddress:8080"

        AlertDialog(
            onDismissRequest = { showConnectionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = ColorSuccessGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kết nối & Chia sẻ Wi-Fi", color = TextPrimary)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Truyền tệp không dây giữa điện thoại và máy tính (PC / Laptop) qua cùng mạng Wi-Fi cục bộ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CharcoalBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isServerOn) "Máy chủ Web: ĐANG CHẠY" else "Máy chủ Web: ĐÃ TẮT",
                                    color = if (isServerOn) ColorSuccessGreen else TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Switch(
                                    checked = isServerOn,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            val started = NetworkSharingManager.startServer(currentDir, 8080)
                                            isServerOn = started
                                        } else {
                                            NetworkSharingManager.stopServer()
                                            isServerOn = false
                                        }
                                    }
                                )
                            }

                            if (isServerOn) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Nhập địa chỉ sau vào trình duyệt PC/Laptop:", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                Text(
                                    text = serverUrl,
                                    color = CyanAccent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Đang chia sẻ thư mục: /${currentDir.name.ifEmpty { "Bộ nhớ trong" }}", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                            }
                        }
                    }

                    HorizontalDivider(color = DocumentsUIDivider)

                    Text("Kết nối thiết bị ngoài:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = TextPrimary)
                    OutlinedButton(
                        onClick = {
                            showConnectionsDialog = false
                            safLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Usb, contentDescription = null, tint = ElectricBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Kết nối Thẻ nhớ ngoài / USB OTG (SAF)", color = TextPrimary)
                    }

                    OutlinedButton(
                        onClick = {
                            showConnectionsDialog = false
                            openNewAppWindow(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Launch, contentDescription = null, tint = CyanAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mở thêm cửa sổ ứng dụng mới", color = TextPrimary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConnectionsDialog = false }) {
                    Text("Đóng", color = ElectricBlue)
                }
            },
            containerColor = CharcoalSurfaceVariant
        )
    }

    // 2. Sort Dialog
    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sắp xếp tệp theo", color = TextPrimary) },
            text = {
                Column {
                    listOf(
                        "Tên (A đến Z)" to SortOption(SortField.NAME, SortOrder.ASCENDING),
                        "Tên (Z đến A)" to SortOption(SortField.NAME, SortOrder.DESCENDING),
                        "Kích thước (Lớn nhất)" to SortOption(SortField.SIZE, SortOrder.DESCENDING),
                        "Kích thước (Nhỏ nhất)" to SortOption(SortField.SIZE, SortOrder.ASCENDING),
                        "Ngày sửa đổi (Mới nhất)" to SortOption(SortField.DATE, SortOrder.DESCENDING),
                        "Ngày sửa đổi (Cũ nhất)" to SortOption(SortField.DATE, SortOrder.ASCENDING),
                        "Loại định dạng" to SortOption(SortField.TYPE, SortOrder.ASCENDING)
                    ).forEach { (label, option) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setSortOption(option)
                                    showSortDialog = false
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = viewModel.sortOption.value == option,
                                onClick = {
                                    viewModel.setSortOption(option)
                                    showSortDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("Đóng", color = ElectricBlue)
                }
            },
            containerColor = CharcoalSurfaceVariant
        )
    }

    // 3. Create Folder Dialog
    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Tạo thư mục mới", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Tên thư mục") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createFolder(newFolderName.trim(), createInSecondaryPane)
                            showCreateFolderDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Tạo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("Hủy", color = TextMuted)
                }
            },
            containerColor = CharcoalSurfaceVariant
        )
    }

    // 4. Create File Dialog (e.g. .ts, .txs, .css, .bat, .dat)
    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("Tạo tệp tin mới", color = TextPrimary) },
            text = {
                Column {
                    Text("Hỗ trợ mọi định dạng: .ts, .txs, .css, .bat, .dat, .txt, .json...", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        placeholder = { Text("ví dụ: script.ts, style.css, data.dat") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricBlue,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            viewModel.createFile(newFileName.trim(), createInSecondaryPane)
                            showCreateFileDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Tạo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false }) {
                    Text("Hủy", color = TextMuted)
                }
            },
            containerColor = CharcoalSurfaceVariant
        )
    }

    // 5. Rename Dialog
    showRenameDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Đổi tên tệp", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
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
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Hủy", color = TextMuted)
                }
            },
            containerColor = CharcoalSurfaceVariant
        )
    }

    // 6. Delete Confirm Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Xác nhận xóa", color = ColorErrorRed) },
            text = { Text("Bạn có chắc chắn muốn xóa ${selectedItems.size} mục đã chọn vĩnh viễn?", color = TextPrimary) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedItems()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorErrorRed)
                ) {
                    Text("Xóa vĩnh viễn")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Hủy", color = TextMuted)
                }
            },
            containerColor = CharcoalSurfaceVariant
        )
    }

    // 7. Zip Archive Dialog
    if (showZipDialog) {
        AlertDialog(
            onDismissRequest = { showZipDialog = false },
            title = { Text("Nén tệp thành ZIP", color = TextPrimary) },
            text = {
                OutlinedTextField(
                    value = zipInputName,
                    onValueChange = { zipInputName = it },
                    label = { Text("Tên tệp nén (.zip)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (zipInputName.isNotBlank()) {
                            viewModel.createZipFromSelected(zipInputName.trim())
                            showZipDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Nén ngay")
                }
            },
            dismissButton = {
                TextButton(onClick = { showZipDialog = false }) {
                    Text("Hủy", color = TextMuted)
                }
            },
            containerColor = CharcoalSurfaceVariant
        )
    }

    // 8. Info & Hash Dialog
    showInfoDialog?.let { item ->
        val file = File(item.path)
        AlertDialog(
            onDismissRequest = { showInfoDialog = null },
            title = { Text("Chi tiết tệp tin", color = TextPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Tên: ${item.name}", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Đường dẫn: ${item.path}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text("Kích thước: ${FileItem.formatBytes(item.size)} (${item.size} bytes)", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Text("Ngày sửa đổi: ${FileItem.formatDate(item.lastModified)}", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text("Loại tệp: ${item.fileType.name} (${item.extension.ifEmpty { "không có" }})", style = MaterialTheme.typography.bodySmall, color = CyanAccent)

                    HorizontalDivider(color = DocumentsUIDivider, modifier = Modifier.padding(vertical = 4.dp))

                    if (infoHashText != null) {
                        Text(infoHashText!!, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextPrimary)
                    } else if (isCalculatingInfoHash) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = ElectricBlue)
                    } else {
                        Button(
                            onClick = {
                                isCalculatingInfoHash = true
                                coroutineScope.launch {
                                    val hashes = HashUtils.calculateAllHashes(file).getOrDefault(emptyMap())
                                    val md5 = hashes["MD5"] ?: "Lỗi"
                                    val sha256 = hashes["SHA-256"] ?: "Lỗi"
                                    infoHashText = "MD5:\n$md5\n\nSHA-256:\n$sha256"
                                    isCalculatingInfoHash = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CharcoalSurfaceHighlight)
                        ) {
                            Text("Tính mã băm MD5 / SHA-256")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = null }) {
                    Text("Đóng", color = ElectricBlue)
                }
            },
            containerColor = CharcoalSurfaceVariant
        )
    }
}

/**
 * 1. Horizontal Category Filter Chips Row matching Android DocumentsUI screenshot
 */
@Composable
private fun CategoryFilterRow(
    onSelectCategory: (FileType, String) -> Unit
) {
    val categories = remember {
        listOf(
            Triple(FileType.IMAGE, "Hình ảnh", Icons.Default.Image),
            Triple(FileType.AUDIO, "Âm thanh", Icons.Default.Headphones),
            Triple(FileType.VIDEO, "Video", Icons.Default.Movie),
            Triple(FileType.DOCUMENT, "Tài liệu", Icons.Default.Description),
            Triple(FileType.APK, "File APK", Icons.Default.Android),
            Triple(FileType.GAME_DATA, "Game Data", Icons.Default.SportsEsports),
            Triple(FileType.ARCHIVE, "File nén", Icons.Default.FolderZip),
            Triple(FileType.DATABASE, "Database", Icons.Default.Storage)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEach { (type, title, icon) ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CharcoalSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, ChipBorderColor),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectCategory(type, title) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (type == FileType.ARCHIVE) {
                        ZipDocumentBadgeIcon(modifier = Modifier.size(width = 16.dp, height = 20.dp))
                    } else {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = CoralChipTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * 2. Subheader row: "Tệp trên [Folder Name]" + Grid/List Toggle & Sort
 */
@Composable
private fun SubheaderRow(
    folderName: String,
    itemCount: Int,
    isGridView: Boolean,
    onToggleView: () -> Unit,
    onOpenSort: () -> Unit,
    onOpenNewWindow: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Tệp trên $folderName",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF9AA0A6),
            fontSize = 13.sp
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onOpenNewWindow,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.Default.Launch,
                    contentDescription = "Mở cửa sổ mới",
                    tint = CyanAccent,
                    modifier = Modifier.size(17.dp)
                )
            }

            IconButton(
                onClick = onOpenSort,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Sort,
                    contentDescription = "Sắp xếp",
                    tint = Color(0xFF9AA0A6),
                    modifier = Modifier.size(17.dp)
                )
            }

            IconButton(
                onClick = onToggleView,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = if (isGridView) "Dạng danh sách" else "Dạng lưới",
                    tint = Color(0xFF9AA0A6),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * 3. Breadcrumb Path Bar for fast backward folder jumps
 */
@Composable
private fun PathBreadcrumbsBar(
    currentDir: File,
    onNavigateTo: (File) -> Unit
) {
    val rootPath = android.os.Environment.getExternalStorageDirectory().absolutePath
    val segments = remember(currentDir) {
        val list = mutableListOf<File>()
        var curr: File? = currentDir
        while (curr != null) {
            list.add(0, curr)
            if (curr.absolutePath == rootPath || curr.parentFile == null) break
            curr = curr.parentFile
        }
        list
    }

    if (segments.size > 1) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            segments.forEachIndexed { index, seg ->
                val name = if (index == 0) (android.os.Build.MODEL.ifEmpty { "Bộ nhớ trong" }) else seg.name
                val isLast = index == segments.size - 1
                Text(
                    text = name,
                    color = if (isLast) TextPrimary else ElectricBlue,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clickable { onNavigateTo(seg) }
                )
                if (!isLast) {
                    Text(
                        text = "  /  ",
                        color = TextMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * 4. Content pane (List / Grid) with DocumentsUI clean divider style
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaneContent(
    paneIndex: Int,
    dir: File,
    items: List<FileItem>,
    isLoading: Boolean,
    isGridView: Boolean,
    selectedItems: Set<FileItem>,
    clipboard: ClipboardState?,
    onEnterDir: (File) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onToggleSelect: (FileItem) -> Unit,
    onLongClick: (FileItem) -> Unit,
    onFocus: () -> Unit,
    onTransferToOtherPane: (FileItem, Boolean) -> Unit,
    otherPaneDirName: String,
    onShowInfo: (FileItem) -> Unit,
    onRename: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit
) {
    val context = LocalContext.current

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ElectricBlue)
        }
        return
    }

    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onFocus() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextMuted, modifier = Modifier.size(54.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text("Thư mục trống", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }

    if (isGridView) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .clickable { onFocus() }
        ) {
            items(items, key = { "${paneIndex}_${it.path}" }) { item ->
                val isSelected = selectedItems.contains(item)
                GridFileCard(
                    item = item,
                    isSelected = isSelected,
                    onClick = {
                        onFocus()
                        if (selectedItems.isNotEmpty()) onToggleSelect(item)
                        else if (item.isDirectory) onEnterDir(File(item.path))
                        else onOpenFile(item)
                    },
                    onLongClick = {
                        onFocus()
                        onLongClick(item)
                    }
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onFocus() }
        ) {
            items(items, key = { "${paneIndex}_${it.path}" }) { item ->
                val isSelected = selectedItems.contains(item)
                DocumentsUIListItem(
                    item = item,
                    isSelected = isSelected,
                    onClick = {
                        onFocus()
                        if (selectedItems.isNotEmpty()) onToggleSelect(item)
                        else if (item.isDirectory) onEnterDir(File(item.path))
                        else onOpenFile(item)
                    },
                    onLongClick = {
                        onFocus()
                        onLongClick(item)
                    },
                    onTransferToOtherPane = if (otherPaneDirName.isNotEmpty()) {
                        { isMove -> onTransferToOtherPane(item, isMove) }
                    } else null,
                    otherPaneDirName = otherPaneDirName,
                    onOpenWith = { FileUtils.openWithExternalApp(context, File(item.path)) },
                    onShowInfo = { onShowInfo(item) },
                    onRename = { onRename(item) },
                    onDelete = { onDelete(item) }
                )
            }
        }
    }
}

/**
 * DocumentsUI Item Row: Folder / File icon + clean title + thin divider line below text
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentsUIListItem(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTransferToOtherPane: ((Boolean) -> Unit)?,
    otherPaneDirName: String,
    onOpenWith: () -> Unit,
    onShowInfo: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showItemMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) ElectricBlue.copy(alpha = 0.18f) else Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Icon (Grey folder in screenshot, or distinctive file icon)
            FileIcon(item = item, isSelected = isSelected, modifier = Modifier.size(34.dp))

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.isDirectory) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${item.formattedSize}  •  ${item.formattedDate}",
                        color = Color(0xFF8A909D),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 12.sp
                    )
                }
            }

            // Quick overflow menu for actions (transfer, open with, info, rename, delete)
            Box {
                IconButton(
                    onClick = { showItemMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Tùy chọn",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showItemMenu,
                    onDismissRequest = { showItemMenu = false },
                    modifier = Modifier.background(CharcoalSurfaceVariant)
                ) {
                    if (onTransferToOtherPane != null) {
                        DropdownMenuItem(
                            text = { Text("Sao chép sang $otherPaneDirName", color = CyanAccent) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = CyanAccent) },
                            onClick = {
                                showItemMenu = false
                                onTransferToOtherPane(false)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Di chuyển sang $otherPaneDirName", color = ElectricBlue) },
                            leadingIcon = { Icon(Icons.Default.ArrowForward, null, tint = ElectricBlue) },
                            onClick = {
                                showItemMenu = false
                                onTransferToOtherPane(true)
                            }
                        )
                        HorizontalDivider(color = DocumentsUIDivider)
                    }

                    if (!item.isDirectory) {
                        DropdownMenuItem(
                            text = { Text("Mở bằng ứng dụng khác...", color = TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.OpenInNew, null, tint = TextPrimary) },
                            onClick = {
                                showItemMenu = false
                                onOpenWith()
                            }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Đổi tên", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = TextPrimary) },
                        onClick = {
                            showItemMenu = false
                            onRename()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Chi tiết & Mã băm", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Info, null, tint = TextPrimary) },
                        onClick = {
                            showItemMenu = false
                            onShowInfo()
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Xóa", color = ColorErrorRed) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = ColorErrorRed) },
                        onClick = {
                            showItemMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }

        // Thin divider line indented to align with text start (DocumentsUI style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 58.dp)
        ) {
            HorizontalDivider(
                color = DocumentsUIDivider,
                thickness = 0.6.dp
            )
        }
    }
}

/**
 * Grid Card View
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridFileCard(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) ElectricBlue.copy(alpha = 0.22f) else CharcoalSurface,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, ElectricBlue) else androidx.compose.foundation.BorderStroke(1.dp, CharcoalSurfaceHighlight),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FileIcon(item = item, isSelected = isSelected, modifier = Modifier.size(42.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.name,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (!item.isDirectory) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.formattedSize,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Custom ZIP document badge icon: Document sheet outline (📄) with bold "ZIP" text inside
 */
@Composable
fun ZipDocumentBadgeIcon(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 7.dp, bottomStart = 3.dp, bottomEnd = 3.dp),
        color = Color(0xFF1B2230),
        border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF4D88FF)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 1.dp, vertical = 1.dp)
            ) {
                // Mini decorative zipper teeth
                Row(
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    modifier = Modifier.padding(bottom = 1.5.dp)
                ) {
                    Box(modifier = Modifier.size(width = 2.5.dp, height = 1.5.dp).background(Color(0xFFFFA000)))
                    Box(modifier = Modifier.size(width = 2.5.dp, height = 1.5.dp).background(Color(0xFF4D88FF)))
                    Box(modifier = Modifier.size(width = 2.5.dp, height = 1.5.dp).background(Color(0xFFFFA000)))
                }
                // Bold "ZIP" text badge inside the document sheet
                Surface(
                    shape = RoundedCornerShape(2.dp),
                    color = Color(0xFF4D88FF)
                ) {
                    Text(
                        text = "ZIP",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 8.sp,
                        letterSpacing = 0.3.sp,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.5.dp)
                    )
                }
            }
        }
    }
}

/**
 * File icon mapper supporting folders and technical files: .ts, .txs, .css, .bat, .dat, etc.
 */
@Composable
fun FileIcon(item: FileItem, isSelected: Boolean, modifier: Modifier = Modifier) {
    if (isSelected) {
        Surface(
            shape = CircleShape,
            color = ElectricBlue,
            modifier = modifier
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        return
    }

    if (item.isDirectory) {
        Icon(
            imageVector = Icons.Default.Folder,
            contentDescription = "Thư mục",
            tint = DocumentsUIFolderGrey,
            modifier = modifier
        )
        return
    }

    val ext = item.extension.lowercase()
    if (item.fileType == FileType.ARCHIVE || ext in setOf("zip", "rar", "7z", "tar", "gz", "xz", "bz2")) {
        ZipDocumentBadgeIcon(modifier = modifier)
        return
    }

    val (icon, color) = when {
        // Code / Technical scripts (.ts, .txs, .css, .bat, .js, .py, .kt, .json, .xml)
        ext in setOf("ts", "tsx", "js", "jsx", "txs", "css", "bat", "cmd", "sh", "py", "kt", "kts", "java", "c", "cpp", "h", "cs", "php", "sql", "vbs", "ps1", "json", "xml", "html") -> {
            Icons.Default.Code to CyanAccent
        }
        // Binary / Game Data (.dat, .bin, .pak, .obb, .sav, .raw)
        ext in setOf("dat", "bin", "pak", "obb", "sav", "raw", "unity3d", "asset", "bundle", "res") -> {
            Icons.Default.DataObject to ColorPurpleBadge
        }
        // SQLite Database (.db, .sqlite, .sqlite3)
        item.fileType == FileType.DATABASE || ext in setOf("db", "sqlite", "sqlite3") -> {
            Icons.Default.Storage to ColorSuccessGreen
        }
        // APK
        item.fileType == FileType.APK || ext in setOf("apk", "xapk", "apks") -> {
            Icons.Default.Android to ColorSuccessGreen
        }
        // Media
        item.fileType == FileType.IMAGE -> Icons.Default.Image to Color(0xFFF48FB1)
        item.fileType == FileType.VIDEO -> Icons.Default.Movie to Color(0xFFEF5350)
        item.fileType == FileType.AUDIO -> Icons.Default.Headphones to CyanAccent
        item.fileType == FileType.DOCUMENT -> Icons.Default.Article to ElectricBlue
        else -> Icons.Default.InsertDriveFile to Color(0xFF9AA0A6)
    }

    Icon(
        imageVector = icon,
        contentDescription = item.extension,
        tint = color,
        modifier = modifier
    )
}

/**
 * Launches a brand new separate Android Activity instance in native Multi-Window
 */
fun openNewAppWindow(context: Context) {
    try {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
            )
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        }
        context.startActivity(intent)
    }
}
