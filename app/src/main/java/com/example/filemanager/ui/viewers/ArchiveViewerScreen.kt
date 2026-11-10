package com.example.filemanager.ui.viewers

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.apk.ApkInstaller
import com.example.filemanager.archive.*
import com.example.filemanager.model.FileItem
import com.example.filemanager.ui.explorer.ZipDocumentBadgeIcon
import com.example.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

data class ArchiveStackItem(
    val file: File,
    val title: String,
    val isTemporary: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewerScreen(
    archiveFile: File,
    onBack: () -> Unit,
    onOpenApkInspector: ((File) -> Unit)? = null,
    onOpenSqliteViewer: ((File) -> Unit)? = null,
    onOpenHexViewer: ((File) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Nested archive stack
    val archiveStack = remember { mutableStateListOf<ArchiveStackItem>() }
    val currentArchiveItem = archiveStack.lastOrNull() ?: ArchiveStackItem(archiveFile, archiveFile.name, false)
    val activeArchiveFile = currentArchiveItem.file

    // Tree navigation state
    var rootNode by remember { mutableStateOf<ZipNode?>(null) }
    var currentNode by remember { mutableStateOf<ZipNode?>(null) }
    var rawEntries by remember { mutableStateOf<List<ArchiveEntryInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Search and Sort
    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(ZipSortOption.NAME) }
    var sortAscending by remember { mutableStateOf(true) }
    var isGridView by remember { mutableStateOf(false) }

    // Multi-selection
    var isSelectMode by remember { mutableStateOf(false) }
    val selectedNodes = remember { mutableStateListOf<ZipNode>() }

    // Extraction progress
    var isExtracting by remember { mutableStateOf(false) }
    var extractProgress by remember { mutableFloatStateOf(0f) }
    var currentExtractName by remember { mutableStateOf("") }
    var extractJob by remember { mutableStateOf<Job?>(null) }

    // Dialogs
    var showExtractOptionsDialog by remember { mutableStateOf(false) }
    var previewTextDialog by remember { mutableStateOf<Pair<ZipNode, String>?>(null) }
    var previewImageDialog by remember { mutableStateOf<Pair<ZipNode, ImageBitmap>?>(null) }
    var showDetailsDialog by remember { mutableStateOf<ZipNode?>(null) }
    var isPreviewLoading by remember { mutableStateOf(false) }

    val apksInZip = remember(rawEntries) {
        rawEntries.filter { !it.isDirectory && it.path.endsWith(".apk", ignoreCase = true) }
    }
    var showApksInZipDialog by remember { mutableStateOf(false) }
    var apkNodeToInstall by remember { mutableStateOf<ZipNode?>(null) }

    fun loadArchive(file: File) {
        isLoading = true
        errorMessage = null
        selectedNodes.clear()
        isSelectMode = false
        coroutineScope.launch {
            val result = ArchiveManager.listArchiveEntries(file)
            if (result.isSuccess) {
                rawEntries = result.getOrNull() ?: emptyList()
                val tree = ZipTreeBuilder.buildTree(rawEntries)
                rootNode = tree
                currentNode = tree
            } else {
                errorMessage = result.exceptionOrNull()?.message ?: "Lỗi khi mở tệp nén"
            }
            isLoading = false
        }
    }

    LaunchedEffect(activeArchiveFile) {
        loadArchive(activeArchiveFile)
    }

    DisposableEffect(Unit) {
        onDispose {
            ArchiveManager.cleanTempFiles(context.cacheDir)
        }
    }

    // Back handling: navigates up tree, then pops nested zip stack, then exits screen
    fun handleBack() {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
            return
        }
        if (isSelectMode) {
            isSelectMode = false
            selectedNodes.clear()
            return
        }
        val cur = currentNode
        val parent = cur?.parent
        if (parent != null) {
            currentNode = parent
        } else if (archiveStack.size > 1) {
            val popped = archiveStack.removeAt(archiveStack.lastIndex)
            if (popped.isTemporary) {
                popped.file.delete()
            }
        } else {
            onBack()
        }
    }

    BackHandler(onBack = { handleBack() })

    // Open item preview
    fun openNode(node: ZipNode) {
        if (node.isDirectory) {
            currentNode = node
            return
        }

        val ext = node.extension
        when (ext) {
            "txt", "json", "xml", "ts", "tsx", "css", "bat", "dat", "kt", "java", "py", "c", "cpp", "h",
            "html", "md", "ini", "cfg", "properties", "yaml", "yml", "gradle", "sh" -> {
                isPreviewLoading = true
                coroutineScope.launch {
                    val res = ArchiveManager.readEntryText(activeArchiveFile, node.fullPath)
                    if (res.isSuccess) {
                        previewTextDialog = Pair(node, res.getOrNull() ?: "")
                    } else {
                        snackbarHostState.showSnackbar("Không thể đọc tệp văn bản: ${res.exceptionOrNull()?.message}")
                    }
                    isPreviewLoading = false
                }
            }

            "png", "jpg", "jpeg", "webp", "gif", "bmp" -> {
                isPreviewLoading = true
                coroutineScope.launch {
                    val res = ArchiveManager.readEntryBytes(activeArchiveFile, node.fullPath)
                    if (res.isSuccess) {
                        val bytes = res.getOrNull()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bmp != null) {
                                previewImageDialog = Pair(node, bmp.asImageBitmap())
                            } else {
                                snackbarHostState.showSnackbar("Không thể giải mã hình ảnh")
                            }
                        }
                    } else {
                        snackbarHostState.showSnackbar("Lỗi đọc hình ảnh: ${res.exceptionOrNull()?.message}")
                    }
                    isPreviewLoading = false
                }
            }

            "apk" -> {
                apkNodeToInstall = node
            }

            "zip", "jar", "obb" -> {
                // Nested ZIP explorer!
                isPreviewLoading = true
                coroutineScope.launch {
                    val res = ArchiveManager.extractNestedZip(context.cacheDir, activeArchiveFile, node.fullPath)
                    if (res.isSuccess) {
                        val nestedFile = res.getOrThrow()
                        if (archiveStack.isEmpty()) {
                            archiveStack.add(ArchiveStackItem(archiveFile, archiveFile.name, false))
                        }
                        archiveStack.add(ArchiveStackItem(nestedFile, node.name, true))
                    } else {
                        snackbarHostState.showSnackbar("Lỗi khi mở ZIP con: ${res.exceptionOrNull()?.message}")
                    }
                    isPreviewLoading = false
                }
            }

            else -> {
                showDetailsDialog = node
            }
        }
    }

    // Extraction executor
    fun startExtraction(
        targetDir: File,
        mode: ExtractMode,
        targetNode: ZipNode? = null
    ) {
        showExtractOptionsDialog = false
        isExtracting = true
        extractProgress = 0f
        currentExtractName = ""

        extractJob = coroutineScope.launch {
            val res = when (mode) {
                ExtractMode.ALL -> {
                    ArchiveManager.extractAll(activeArchiveFile, targetDir) { p, name ->
                        extractProgress = p
                        currentExtractName = name
                    }
                }
                ExtractMode.CURRENT_DIR -> {
                    val prefix = currentNode?.fullPath ?: ""
                    ArchiveManager.extractDirectory(activeArchiveFile, prefix, targetDir) { p, name ->
                        extractProgress = p
                        currentExtractName = name
                    }
                }
                ExtractMode.SELECTED -> {
                    val paths = selectedNodes.map { it.fullPath }
                    ArchiveManager.extractEntries(activeArchiveFile, paths, targetDir) { p, name ->
                        extractProgress = p
                        currentExtractName = name
                    }
                }
                ExtractMode.SINGLE -> {
                    val path = targetNode?.fullPath ?: ""
                    ArchiveManager.extractEntries(activeArchiveFile, listOf(path), targetDir) { p, name ->
                        extractProgress = p
                        currentExtractName = name
                    }
                }
            }

            if (res.isSuccess) {
                val count = res.getOrNull() ?: 0
                snackbarHostState.showSnackbar("Đã trích xuất thành công $count tệp vào: ${targetDir.name}")
                selectedNodes.clear()
                isSelectMode = false
            } else {
                snackbarHostState.showSnackbar("Lỗi giải nén: ${res.exceptionOrNull()?.message}")
            }
            isExtracting = false
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Surface(color = CharcoalSurface) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                ) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ZipDocumentBadgeIcon(modifier = Modifier.size(width = 24.dp, height = 30.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = currentArchiveItem.title,
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (archiveStack.size > 1) "Cấp độ ZIP lồng nhau #${archiveStack.size}" else "${FileItem.formatBytes(activeArchiveFile.length())} • ${rawEntries.size} mục",
                                        color = CyanAccent,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { handleBack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = if (isSearchActive) ElectricBlue else TextPrimary)
                            }
                            IconButton(onClick = { isGridView = !isGridView }) {
                                Icon(if (isGridView) Icons.Default.ViewList else Icons.Default.GridView, contentDescription = "Kiểu xem", tint = TextPrimary)
                            }
                            IconButton(onClick = {
                                isSelectMode = !isSelectMode
                                if (!isSelectMode) selectedNodes.clear()
                            }) {
                                Icon(Icons.Default.Checklist, contentDescription = "Chọn nhiều", tint = if (isSelectMode) ElectricBlue else TextPrimary)
                            }
                            if (apksInZip.isNotEmpty()) {
                                IconButton(onClick = {
                                    if (apksInZip.size == 1) {
                                        val singleApk = apksInZip.first()
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Đang giải nén và chuẩn bị cài đặt ${singleApk.name}...")
                                            val res = ApkInstaller.extractAndInstallFromZip(context, activeArchiveFile, singleApk.path)
                                            if (res.isFailure) {
                                                snackbarHostState.showSnackbar("Lỗi: ${res.exceptionOrNull()?.message}")
                                            }
                                        }
                                    } else {
                                        showApksInZipDialog = true
                                    }
                                }) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = ColorSuccessGreen) {
                                                Text("${apksInZip.size}", fontSize = 10.sp, color = Color.White)
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Android, contentDescription = "Cài đặt APK trong ZIP", tint = ColorSuccessGreen)
                                    }
                                }
                            }
                            IconButton(onClick = { showExtractOptionsDialog = true }) {
                                Icon(Icons.Default.FolderZip, contentDescription = "Trích xuất", tint = ElectricBlue)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
                    )

                    // Search row if active
                    if (isSearchActive) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Tìm kiếm tệp trong file nén...", fontSize = 13.sp, color = TextMuted) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricBlue,
                                    unfocusedBorderColor = DocumentsUIDivider,
                                    focusedContainerColor = CharcoalCard,
                                    unfocusedContainerColor = CharcoalCard,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Xóa", tint = TextMuted)
                                }
                            }
                        }
                    }

                    // Breadcrumbs bar inside ZIP
                    val cur = currentNode
                    if (cur != null && !isSearchActive) {
                        val crumbs = cur.getBreadcrumbs()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CharcoalBackground)
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            crumbs.forEachIndexed { index, crumb ->
                                val isCurrent = index == crumbs.lastIndex
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { currentNode = crumb }
                                ) {
                                    Text(
                                        text = if (crumb.name == "/") "Gốc ZIP" else crumb.name,
                                        color = if (isCurrent) ElectricBlue else TextMuted,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.5.sp
                                    )
                                    if (!isCurrent) {
                                        Icon(
                                            Icons.Default.ChevronRight,
                                            contentDescription = null,
                                            tint = TextMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sort & Filter bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isSearchActive) "Kết quả tìm kiếm" else "${currentNode?.directChildrenCount ?: 0} mục trong thư mục này",
                            fontSize = 11.5.sp,
                            color = TextMuted
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (sortOption) {
                                    ZipSortOption.NAME -> "Tên"
                                    ZipSortOption.SIZE -> "Kích thước"
                                    ZipSortOption.TYPE -> "Loại"
                                    ZipSortOption.DATE -> "Ngày"
                                },
                                fontSize = 11.5.sp,
                                color = ElectricBlue,
                                modifier = Modifier.clickable {
                                    sortOption = when (sortOption) {
                                        ZipSortOption.NAME -> ZipSortOption.SIZE
                                        ZipSortOption.SIZE -> ZipSortOption.TYPE
                                        ZipSortOption.TYPE -> ZipSortOption.DATE
                                        ZipSortOption.DATE -> ZipSortOption.NAME
                                    }
                                }
                            )
                            IconButton(
                                onClick = { sortAscending = !sortAscending },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    contentDescription = "Thứ tự sắp xếp",
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = DocumentsUIDivider, thickness = 0.8.dp)
                }
            }
        },
        containerColor = CharcoalBackground
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = ElectricBlue)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Đang phân tích cấu trúc tệp nén...", color = TextMuted, fontSize = 13.sp)
                    }
                }

                errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFEF5350), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(errorMessage ?: "", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { loadArchive(activeArchiveFile) }) {
                            Text("Thử lại")
                        }
                    }
                }

                else -> {
                    val displayedNodes = remember(currentNode, searchQuery, isSearchActive, sortOption, sortAscending) {
                        if (isSearchActive && searchQuery.isNotEmpty() && rootNode != null) {
                            ZipTreeBuilder.searchNodes(rootNode!!, searchQuery)
                        } else {
                            val children = currentNode?.children?.values ?: emptyList()
                            ZipTreeBuilder.sortChildren(children, sortOption, sortAscending)
                        }
                    }

                    if (displayedNodes.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.FolderOpen, null, tint = TextMuted, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Thư mục rỗng hoặc không có kết quả phù hợp", color = TextMuted, fontSize = 13.sp)
                        }
                    } else if (isGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(displayedNodes, key = { it.fullPath }) { node ->
                                ZipGridNodeItem(
                                    node = node,
                                    isSelected = selectedNodes.contains(node),
                                    isSelectMode = isSelectMode,
                                    onSelectToggle = {
                                        if (selectedNodes.contains(node)) selectedNodes.remove(node)
                                        else selectedNodes.add(node)
                                    },
                                    onClick = {
                                        if (isSelectMode) {
                                            if (selectedNodes.contains(node)) selectedNodes.remove(node)
                                            else selectedNodes.add(node)
                                        } else {
                                            openNode(node)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectMode) {
                                            isSelectMode = true
                                            selectedNodes.add(node)
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(displayedNodes, key = { it.fullPath }) { node ->
                                ZipListNodeItem(
                                    node = node,
                                    isSelected = selectedNodes.contains(node),
                                    isSelectMode = isSelectMode,
                                    onSelectToggle = {
                                        if (selectedNodes.contains(node)) selectedNodes.remove(node)
                                        else selectedNodes.add(node)
                                    },
                                    onClick = {
                                        if (isSelectMode) {
                                            if (selectedNodes.contains(node)) selectedNodes.remove(node)
                                            else selectedNodes.add(node)
                                        } else {
                                            openNode(node)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectMode) {
                                            isSelectMode = true
                                            selectedNodes.add(node)
                                        }
                                    },
                                    onActionClick = {
                                        showDetailsDialog = node
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Preview Loading Overlay
            if (isPreviewLoading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("Đang đọc luồng tệp trong ZIP...", color = TextPrimary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Text File In-Archive Preview Dialog
    previewTextDialog?.let { (node, content) ->
        AlertDialog(
            onDismissRequest = { previewTextDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Article, null, tint = ElectricBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(node.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(FileItem.formatBytes(node.size), color = CyanAccent, fontSize = 11.sp)
                    }
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .background(CharcoalBackground, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = content,
                        color = TextPrimary,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        startExtraction(downloads, ExtractMode.SINGLE, node)
                        previewTextDialog = null
                    }
                ) {
                    Text("Trích xuất tệp này", color = ElectricBlue)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        clipboardManager.setText(AnnotatedString(content))
                        coroutineScope.launch { snackbarHostState.showSnackbar("Đã sao chép nội dung vào khay nhớ tạm") }
                    }) {
                        Text("Sao chép", color = TextMuted)
                    }
                    TextButton(onClick = { previewTextDialog = null }) {
                        Text("Đóng", color = TextMuted)
                    }
                }
            },
            containerColor = CharcoalCard
        )
    }

    // Image In-Archive Preview Dialog
    previewImageDialog?.let { (node, bmp) ->
        AlertDialog(
            onDismissRequest = { previewImageDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, null, tint = Color(0xFFF48FB1))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(node.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                            .background(CharcoalBackground, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bmp,
                            contentDescription = node.name,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${bmp.width} × ${bmp.height} • ${FileItem.formatBytes(node.size)}",
                        color = CyanAccent,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        startExtraction(downloads, ExtractMode.SINGLE, node)
                        previewImageDialog = null
                    }
                ) {
                    Text("Trích xuất ảnh", color = ElectricBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { previewImageDialog = null }) {
                    Text("Đóng", color = TextMuted)
                }
            },
            containerColor = CharcoalCard
        )
    }

    // Node Details / Action Dialog
    showDetailsDialog?.let { node ->
        AlertDialog(
            onDismissRequest = { showDetailsDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (node.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null, tint = if (node.isDirectory) Color(0xFFFFB300) else ElectricBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(node.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    DetailRow("Đường dẫn trong ZIP:", node.fullPath)
                    if (node.isDirectory) {
                        DetailRow("Số file con:", "${node.fileCount} tệp")
                        DetailRow("Số thư mục con:", "${node.dirCount} thư mục")
                        DetailRow("Tổng dung lượng:", FileItem.formatBytes(node.totalSize))
                    } else {
                        DetailRow("Kích thước gốc:", FileItem.formatBytes(node.size))
                        DetailRow("Kích thước nén:", FileItem.formatBytes(node.compressedSize))
                        DetailRow("Tỷ lệ nén:", "${String.format("%.1f", node.compressionRatio)}%")
                        DetailRow("Phần mở rộng:", node.extension.ifEmpty { "Không có" })
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DocumentsUIDivider)
                    Spacer(modifier = Modifier.height(8.dp))

                    // External viewers if applicable
                    val ext = node.extension
                    if (!node.isDirectory) {
                        when (ext) {
                            "apk" -> {
                                Button(
                                    onClick = {
                                        showDetailsDialog = null
                                        coroutineScope.launch {
                                            val res = ArchiveManager.extractEntryToTemp(context.cacheDir, activeArchiveFile, node.fullPath)
                                            if (res.isSuccess) {
                                                onOpenApkInspector?.invoke(res.getOrThrow())
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessGreen)
                                ) {
                                    Icon(Icons.Default.Android, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mở bằng APK Inspector")
                                }
                            }
                            "db", "sqlite" -> {
                                Button(
                                    onClick = {
                                        showDetailsDialog = null
                                        coroutineScope.launch {
                                            val res = ArchiveManager.extractEntryToTemp(context.cacheDir, activeArchiveFile, node.fullPath)
                                            if (res.isSuccess) {
                                                onOpenSqliteViewer?.invoke(res.getOrThrow())
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                                ) {
                                    Icon(Icons.Default.Storage, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mở bằng SQLite Viewer")
                                }
                            }
                            "bin", "dat", "hex" -> {
                                Button(
                                    onClick = {
                                        showDetailsDialog = null
                                        coroutineScope.launch {
                                            val res = ArchiveManager.extractEntryToTemp(context.cacheDir, activeArchiveFile, node.fullPath)
                                            if (res.isSuccess) {
                                                onOpenHexViewer?.invoke(res.getOrThrow())
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                                ) {
                                    Icon(Icons.Default.Code, null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Mở bằng HEX Viewer")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val downloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                        startExtraction(downloads, if (node.isDirectory) ExtractMode.CURRENT_DIR else ExtractMode.SINGLE, node)
                        showDetailsDialog = null
                    }
                ) {
                    Text("Trích xuất mục này", color = ElectricBlue)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDetailsDialog = null }) {
                    Text("Đóng", color = TextMuted)
                }
            },
            containerColor = CharcoalCard
        )
    }

    // Extraction Options Dialog
    if (showExtractOptionsDialog) {
        val parentDir = activeArchiveFile.parentFile ?: android.os.Environment.getExternalStorageDirectory()
        val defaultNewFolder = File(parentDir, activeArchiveFile.nameWithoutExtension)

        AlertDialog(
            onDismissRequest = { showExtractOptionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderZip, null, tint = ElectricBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tùy chọn trích xuất", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Chọn phương thức giải nén phù hợp:", color = TextMuted, fontSize = 12.5.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    ExtractOptionCard(
                        title = "Trích xuất vào thư mục mới",
                        subtitle = "${defaultNewFolder.name}/ (Khuyên dùng)",
                        icon = Icons.Default.CreateNewFolder,
                        onClick = { startExtraction(defaultNewFolder, ExtractMode.ALL) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ExtractOptionCard(
                        title = "Trích xuất tại đây",
                        subtitle = parentDir.name,
                        icon = Icons.Default.Folder,
                        onClick = { startExtraction(parentDir, ExtractMode.ALL) }
                    )

                    if (currentNode != null && currentNode != rootNode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ExtractOptionCard(
                            title = "Trích xuất thư mục hiện tại trong ZIP",
                            subtitle = currentNode?.name ?: "",
                            icon = Icons.Default.FolderOpen,
                            onClick = {
                                val out = File(parentDir, currentNode?.name ?: "extracted")
                                startExtraction(out, ExtractMode.CURRENT_DIR)
                            }
                        )
                    }

                    if (selectedNodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ExtractOptionCard(
                            title = "Trích xuất ${selectedNodes.size} mục đã chọn",
                            subtitle = "Giải nén vào thư mục mới",
                            icon = Icons.Default.CheckCircle,
                            onClick = { startExtraction(defaultNewFolder, ExtractMode.SELECTED) }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExtractOptionsDialog = false }) {
                    Text("Hủy", color = TextMuted)
                }
            },
            containerColor = CharcoalCard
        )
    }

    // Extraction In-Progress Dialog
    if (isExtracting) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Đang trích xuất tệp nén...", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { extractProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ElectricBlue,
                        trackColor = DocumentsUIDivider,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = currentExtractName.substringAfterLast('/'),
                            color = TextMuted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${(extractProgress * 100).toInt()}%",
                            color = ElectricBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    extractJob?.cancel()
                    isExtracting = false
                }) {
                    Text("Hủy bỏ", color = Color(0xFFEF5350))
                }
            },
            containerColor = CharcoalCard
        )
    }

    // Direct APK Install / Inspect Dialog from inside ZIP
    apkNodeToInstall?.let { node ->
        AlertDialog(
            onDismissRequest = { apkNodeToInstall = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Android, contentDescription = null, tint = ColorSuccessGreen, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = node.name,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${FileItem.formatBytes(node.size)} (Nén: ${FileItem.formatBytes(node.compressedSize)})",
                            color = CyanAccent,
                            fontSize = 11.5.sp
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Tệp này là ứng dụng Android APK nằm trong file nén. Bạn có thể cài đặt trực tiếp, kiểm tra phân tích mã nguồn hoặc duyệt các tệp bên trong.",
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val apkPath = node.fullPath
                            apkNodeToInstall = null
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Đang giải nén và khởi động trình cài đặt APK...")
                                val res = ApkInstaller.extractAndInstallFromZip(context, activeArchiveFile, apkPath)
                                if (res.isFailure) {
                                    snackbarHostState.showSnackbar("Không thể cài APK: ${res.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessGreen),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cài đặt APK này ngay", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val apkPath = node.fullPath
                            apkNodeToInstall = null
                            coroutineScope.launch {
                                isPreviewLoading = true
                                val res = ArchiveManager.extractEntryToTemp(context.cacheDir, activeArchiveFile, apkPath)
                                isPreviewLoading = false
                                if (res.isSuccess) {
                                    onOpenApkInspector?.invoke(res.getOrThrow())
                                } else {
                                    snackbarHostState.showSnackbar("Lỗi phân tích: ${res.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp), tint = ElectricBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Phân tích APK (Inspector)", color = ElectricBlue)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val apkPath = node.fullPath
                            apkNodeToInstall = null
                            coroutineScope.launch {
                                isPreviewLoading = true
                                val res = ArchiveManager.extractNestedZip(context.cacheDir, activeArchiveFile, apkPath)
                                isPreviewLoading = false
                                if (res.isSuccess) {
                                    val nestedFile = res.getOrThrow()
                                    if (archiveStack.isEmpty()) {
                                        archiveStack.add(ArchiveStackItem(archiveFile, archiveFile.name, false))
                                    }
                                    archiveStack.add(ArchiveStackItem(nestedFile, node.name, true))
                                } else {
                                    snackbarHostState.showSnackbar("Lỗi duyệt APK: ${res.exceptionOrNull()?.message}")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp), tint = TextSecondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Duyệt cấu trúc bên trong APK", color = TextSecondary)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { apkNodeToInstall = null }) {
                    Text("Đóng", color = TextMuted)
                }
            },
            containerColor = CharcoalCard
        )
    }

    // List of All APKs inside current ZIP Dialog
    if (showApksInZipDialog) {
        AlertDialog(
            onDismissRequest = { showApksInZipDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Android, contentDescription = null, tint = ColorSuccessGreen)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Tệp APK trong file nén (${apksInZip.size})", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(apksInZip) { apkInfo ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Android, null, tint = ColorSuccessGreen, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = apkInfo.name,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${FileItem.formatBytes(apkInfo.size)} • ${apkInfo.path}",
                                        color = TextMuted,
                                        fontSize = 10.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        showApksInZipDialog = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Đang trích xuất và khởi động cài đặt ${apkInfo.name}...")
                                            val res = ApkInstaller.extractAndInstallFromZip(context, activeArchiveFile, apkInfo.path)
                                            if (res.isFailure) {
                                                snackbarHostState.showSnackbar("Lỗi: ${res.exceptionOrNull()?.message}")
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessGreen),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cài đặt", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showApksInZipDialog = false }) {
                    Text("Đóng", color = TextMuted)
                }
            },
            containerColor = CharcoalCard
        )
    }
}

enum class ExtractMode {
    ALL,
    CURRENT_DIR,
    SELECTED,
    SINGLE
}

@Composable
private fun ZipListNodeItem(
    node: ZipNode,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onActionClick: () -> Unit
) {
    Surface(
        color = if (isSelected) ElectricBlue.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onSelectToggle() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = ElectricBlue,
                        uncheckedColor = TextMuted,
                        checkmarkColor = Color.White
                    ),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            // Icon
            if (node.isDirectory) {
                Icon(
                    Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(34.dp)
                )
            } else {
                when (node.extension) {
                    "zip", "7z", "rar", "tar", "gz" -> ZipDocumentBadgeIcon(modifier = Modifier.size(width = 24.dp, height = 30.dp))
                    "apk" -> Icon(Icons.Default.Android, null, tint = ColorSuccessGreen, modifier = Modifier.size(32.dp))
                    "db", "sqlite" -> Icon(Icons.Default.Storage, null, tint = ColorSuccessGreen, modifier = Modifier.size(32.dp))
                    "png", "jpg", "jpeg", "webp", "gif" -> Icon(Icons.Default.Image, null, tint = Color(0xFFF48FB1), modifier = Modifier.size(32.dp))
                    "mp3", "wav", "ogg", "flac" -> Icon(Icons.Default.AudioFile, null, tint = CyanAccent, modifier = Modifier.size(32.dp))
                    "mp4", "mkv", "avi" -> Icon(Icons.Default.VideoFile, null, tint = Color(0xFFEF5350), modifier = Modifier.size(32.dp))
                    else -> Icon(Icons.Default.Article, null, tint = ElectricBlue, modifier = Modifier.size(32.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    color = TextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (node.isDirectory) {
                        "${node.directChildrenCount} mục (${node.fileCount} tệp)"
                    } else {
                        "${FileItem.formatBytes(node.size)} • Nén: ${FileItem.formatBytes(node.compressedSize)}"
                    },
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            IconButton(onClick = onActionClick, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Thao tác", tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ZipGridNodeItem(
    node: ZipNode,
    isSelected: Boolean,
    isSelectMode: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ElectricBlue.copy(alpha = 0.15f) else CharcoalCard
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                if (isSelectMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectToggle() },
                        colors = CheckboxDefaults.colors(checkedColor = ElectricBlue),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (node.isDirectory) {
                Icon(Icons.Default.Folder, null, tint = Color(0xFFFFB300), modifier = Modifier.size(42.dp))
            } else {
                when (node.extension) {
                    "zip", "7z", "rar", "tar" -> ZipDocumentBadgeIcon(modifier = Modifier.size(width = 28.dp, height = 36.dp))
                    "apk" -> Icon(Icons.Default.Android, null, tint = ColorSuccessGreen, modifier = Modifier.size(38.dp))
                    else -> Icon(Icons.Default.Article, null, tint = ElectricBlue, modifier = Modifier.size(38.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = node.name,
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = if (node.isDirectory) "${node.directChildrenCount} mục" else FileItem.formatBytes(node.size),
                color = TextMuted,
                fontSize = 10.5.sp
            )
        }
    }
}

@Composable
private fun ExtractOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
