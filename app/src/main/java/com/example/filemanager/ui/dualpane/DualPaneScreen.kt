package com.example.filemanager.ui.dualpane

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.filemanager.model.FileItem
import com.example.filemanager.model.FileType
import com.example.filemanager.model.SortField
import com.example.filemanager.model.ViewMode
import com.example.filemanager.ui.explorer.FileIcon
import com.example.filemanager.viewmodel.MainViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualPaneScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    onOpenFile: (FileItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val dualPaneManager = remember {
        DualPaneManager(context, coroutineScope)
    }

    val leftPane by dualPaneManager.leftPane.collectAsState()
    val rightPane by dualPaneManager.rightPane.collectAsState()
    val splitRatio by dualPaneManager.splitRatio.collectAsState()
    val orientation by dualPaneManager.splitOrientation.collectAsState()
    val dragSession by dualPaneManager.dragSession.collectAsState()
    val transferProgress by dualPaneManager.transferProgress.collectAsState()

    // Dialogs
    var dropDialogData by remember { mutableStateOf<Triple<List<FileItem>, File, Int>?>(null) } // items, targetDir, targetPaneId
    var newFolderDialogPaneId by remember { mutableStateOf<Int?>(null) }
    var renameDialogItem by remember { mutableStateOf<Pair<Int, FileItem>?>(null) }
    var deleteDialogItems by remember { mutableStateOf<Pair<Int, List<FileItem>>?>(null) }
    var detailsDialogItem by remember { mutableStateOf<FileItem?>(null) }

    // Coordinates of panes to determine drop target accurately
    var leftPaneBounds by remember { mutableStateOf(Pair(Offset.Zero, Offset.Zero)) }
    var rightPaneBounds by remember { mutableStateOf(Pair(Offset.Zero, Offset.Zero)) }

    BackHandler {
        // Pop backstack on right, or left, or close dual pane
        if (rightPane.backStack.isNotEmpty()) {
            dualPaneManager.navigateBack(1)
        } else if (leftPane.backStack.isNotEmpty()) {
            dualPaneManager.navigateBack(0)
        } else {
            onBack()
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
                                Icon(Icons.Default.Splitscreen, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "DUAL PANE FILE MANAGER",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = "Kéo & Thả giữa 2 cửa sổ độc lập",
                                        color = CyanAccent,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Thoát chia đôi", tint = TextPrimary)
                            }
                        },
                        actions = {
                            // Orientation toggle
                            IconButton(onClick = { dualPaneManager.toggleSplitOrientation() }) {
                                Icon(
                                    if (orientation == SplitOrientation.HORIZONTAL) Icons.Default.ViewAgenda else Icons.Default.ViewColumn,
                                    contentDescription = "Xoay chia đôi",
                                    tint = TextPrimary
                                )
                            }
                            // Swap Panes button
                            IconButton(onClick = { dualPaneManager.swapPanes() }) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Hoán đổi trái/phải", tint = ElectricBlue)
                            }
                            // Reset 50:50 ratio
                            IconButton(onClick = { dualPaneManager.setSplitRatio(0.5f) }) {
                                Icon(Icons.Default.AspectRatio, contentDescription = "Tỷ lệ 50:50", tint = TextPrimary)
                            }
                            // Refresh both
                            IconButton(onClick = { dualPaneManager.refreshBoth() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = TextPrimary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
                    )
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
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val totalWidth = maxWidth
                val totalHeight = maxHeight

                if (orientation == SplitOrientation.HORIZONTAL) {
                    // Left and Right panes side by side
                    val leftWidth = (totalWidth * splitRatio).coerceIn(80.dp, totalWidth - 80.dp)
                    val rightWidth = totalWidth - leftWidth

                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left Pane
                        Box(
                            modifier = Modifier
                                .width(leftWidth)
                                .fillMaxHeight()
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInRoot()
                                    leftPaneBounds = Pair(pos, Offset(pos.x + coords.size.width, pos.y + coords.size.height))
                                }
                        ) {
                            SinglePaneView(
                                paneState = leftPane,
                                isTargetDrop = dragSession.isDragging && dragSession.sourcePaneId != 0,
                                manager = dualPaneManager,
                                onOpenFile = onOpenFile,
                                onNewFolder = { newFolderDialogPaneId = 0 },
                                onRename = { item -> renameDialogItem = Pair(0, item) },
                                onDelete = { items -> deleteDialogItems = Pair(0, items) },
                                onShowDetails = { item -> detailsDialogItem = item },
                                onDragStart = { items, pos ->
                                    dualPaneManager.startDrag(0, items, pos)
                                },
                                onDragMove = { pos ->
                                    val isOverRight = pos.x >= leftPaneBounds.second.x
                                    dualPaneManager.updateDrag(pos, if (isOverRight) 1 else 0, null)
                                },
                                onDragEnd = {
                                    val isOverRight = dragSession.currentPosition.x >= leftPaneBounds.second.x
                                    if (isOverRight && dragSession.files.isNotEmpty()) {
                                        dropDialogData = Triple(dragSession.files, rightPane.currentDirectory, 1)
                                    }
                                    dualPaneManager.cancelDrag()
                                }
                            )
                        }

                        // Draggable Divider
                        Box(
                            modifier = Modifier
                                .width(12.dp)
                                .fillMaxHeight()
                                .background(CharcoalSurface)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaRatio = dragAmount.x / size.width.toFloat()
                                        dualPaneManager.setSplitRatio(splitRatio + deltaRatio * 0.1f)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(40.dp)
                                    .background(ElectricBlue.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                            )
                        }

                        // Right Pane
                        Box(
                            modifier = Modifier
                                .width(rightWidth)
                                .fillMaxHeight()
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInRoot()
                                    rightPaneBounds = Pair(pos, Offset(pos.x + coords.size.width, pos.y + coords.size.height))
                                }
                        ) {
                            SinglePaneView(
                                paneState = rightPane,
                                isTargetDrop = dragSession.isDragging && dragSession.sourcePaneId != 1,
                                manager = dualPaneManager,
                                onOpenFile = onOpenFile,
                                onNewFolder = { newFolderDialogPaneId = 1 },
                                onRename = { item -> renameDialogItem = Pair(1, item) },
                                onDelete = { items -> deleteDialogItems = Pair(1, items) },
                                onShowDetails = { item -> detailsDialogItem = item },
                                onDragStart = { items, pos ->
                                    dualPaneManager.startDrag(1, items, pos)
                                },
                                onDragMove = { pos ->
                                    val isOverLeft = pos.x < rightPaneBounds.first.x
                                    dualPaneManager.updateDrag(pos, if (isOverLeft) 0 else 1, null)
                                },
                                onDragEnd = {
                                    val isOverLeft = dragSession.currentPosition.x < rightPaneBounds.first.x
                                    if (isOverLeft && dragSession.files.isNotEmpty()) {
                                        dropDialogData = Triple(dragSession.files, leftPane.currentDirectory, 0)
                                    }
                                    dualPaneManager.cancelDrag()
                                }
                            )
                        }
                    }
                } else {
                    // Top and Bottom panes
                    val topHeight = (totalHeight * splitRatio).coerceIn(120.dp, totalHeight - 120.dp)
                    val bottomHeight = totalHeight - topHeight

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Top Pane (Pane 0)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(topHeight)
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInRoot()
                                    leftPaneBounds = Pair(pos, Offset(pos.x + coords.size.width, pos.y + coords.size.height))
                                }
                        ) {
                            SinglePaneView(
                                paneState = leftPane,
                                isTargetDrop = dragSession.isDragging && dragSession.sourcePaneId != 0,
                                manager = dualPaneManager,
                                onOpenFile = onOpenFile,
                                onNewFolder = { newFolderDialogPaneId = 0 },
                                onRename = { item -> renameDialogItem = Pair(0, item) },
                                onDelete = { items -> deleteDialogItems = Pair(0, items) },
                                onShowDetails = { item -> detailsDialogItem = item },
                                onDragStart = { items, pos -> dualPaneManager.startDrag(0, items, pos) },
                                onDragMove = { pos ->
                                    val isOverBottom = pos.y >= leftPaneBounds.second.y
                                    dualPaneManager.updateDrag(pos, if (isOverBottom) 1 else 0, null)
                                },
                                onDragEnd = {
                                    val isOverBottom = dragSession.currentPosition.y >= leftPaneBounds.second.y
                                    if (isOverBottom && dragSession.files.isNotEmpty()) {
                                        dropDialogData = Triple(dragSession.files, rightPane.currentDirectory, 1)
                                    }
                                    dualPaneManager.cancelDrag()
                                }
                            )
                        }

                        // Horizontal Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .background(CharcoalSurface)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaRatio = dragAmount.y / size.height.toFloat()
                                        dualPaneManager.setSplitRatio(splitRatio + deltaRatio * 0.1f)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .height(4.dp)
                                    .width(40.dp)
                                    .background(ElectricBlue.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                            )
                        }

                        // Bottom Pane (Pane 1)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(bottomHeight)
                                .onGloballyPositioned { coords ->
                                    val pos = coords.positionInRoot()
                                    rightPaneBounds = Pair(pos, Offset(pos.x + coords.size.width, pos.y + coords.size.height))
                                }
                        ) {
                            SinglePaneView(
                                paneState = rightPane,
                                isTargetDrop = dragSession.isDragging && dragSession.sourcePaneId != 1,
                                manager = dualPaneManager,
                                onOpenFile = onOpenFile,
                                onNewFolder = { newFolderDialogPaneId = 1 },
                                onRename = { item -> renameDialogItem = Pair(1, item) },
                                onDelete = { items -> deleteDialogItems = Pair(1, items) },
                                onShowDetails = { item -> detailsDialogItem = item },
                                onDragStart = { items, pos -> dualPaneManager.startDrag(1, items, pos) },
                                onDragMove = { pos ->
                                    val isOverTop = pos.y < rightPaneBounds.first.y
                                    dualPaneManager.updateDrag(pos, if (isOverTop) 0 else 1, null)
                                },
                                onDragEnd = {
                                    val isOverTop = dragSession.currentPosition.y < rightPaneBounds.first.y
                                    if (isOverTop && dragSession.files.isNotEmpty()) {
                                        dropDialogData = Triple(dragSession.files, leftPane.currentDirectory, 0)
                                    }
                                    dualPaneManager.cancelDrag()
                                }
                            )
                        }
                    }
                }
            }

            // Floating Drag Preview Card (follows touch position)
            if (dragSession.isDragging) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(dragSession.currentPosition.x.roundToInt() - 50, dragSession.currentPosition.y.roundToInt() - 50) }
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CharcoalCard,
                        border = BorderStroke(1.5.dp, ElectricBlue),
                        shadowElevation = 12.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DragIndicator, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Kéo ${dragSession.files.size} tệp",
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // In-Progress Transfer Overlay
            if (transferProgress.isOperating) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(22.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = if (transferProgress.isMove) "Đang di chuyển tệp..." else "Đang sao chép tệp...",
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                LinearProgressIndicator(
                                    progress = { transferProgress.progress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = ElectricBlue,
                                    trackColor = DocumentsUIDivider,
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = transferProgress.currentFile,
                                    color = TextMuted,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${transferProgress.processedCount}/${transferProgress.totalCount} tệp",
                                        color = CyanAccent,
                                        fontSize = 11.5.sp
                                    )
                                    Text(
                                        text = "${(transferProgress.progress * 100).toInt()}%",
                                        color = ElectricBlue,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                OutlinedButton(
                                    onClick = { dualPaneManager.cancelTransfer() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                                ) {
                                    Text("Hủy thao tác")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Drop Confirmation Dialog
    dropDialogData?.let { (items, targetDir, targetPaneId) ->
        AlertDialog(
            onDismissRequest = { dropDialogData = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MoveToInbox, null, tint = ElectricBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Thao tác thả tệp", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Đang chuyển ${items.size} mục vào:",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = targetDir.name.ifEmpty { targetDir.absolutePath },
                        color = CyanAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Vui lòng chọn thao tác bạn muốn thực hiện:", color = TextPrimary, fontSize = 12.5.sp)
                }
            },
            confirmButton = {
                Row {
                    Button(
                        onClick = {
                            dropDialogData = null
                            dualPaneManager.executeTransfer(items, targetDir, isMove = false) { ok, msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                    ) {
                        Text("Sao chép tại đây")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            dropDialogData = null
                            dualPaneManager.executeTransfer(items, targetDir, isMove = true) { ok, msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("Di chuyển")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { dropDialogData = null }) {
                    Text("Hủy", color = TextMuted)
                }
            },
            containerColor = CharcoalCard
        )
    }

    // New Folder Dialog
    newFolderDialogPaneId?.let { paneId ->
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { newFolderDialogPaneId = null },
            title = { Text("Tạo thư mục mới", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text("Nhập tên thư mục...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = DocumentsUIDivider,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = folderName.trim()
                        if (name.isNotEmpty()) {
                            dualPaneManager.createFolder(paneId, name) { ok, msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                        newFolderDialogPaneId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Tạo")
                }
            },
            dismissButton = {
                TextButton(onClick = { newFolderDialogPaneId = null }) {
                    Text("Hủy", color = TextMuted)
                }
            },
            containerColor = CharcoalCard
        )
    }

    // Rename Dialog
    renameDialogItem?.let { (paneId, item) ->
        var newName by remember { mutableStateOf(item.name) }
        AlertDialog(
            onDismissRequest = { renameDialogItem = null },
            title = { Text("Đổi tên", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = DocumentsUIDivider,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newName.trim()
                        if (name.isNotEmpty() && name != item.name) {
                            dualPaneManager.renameFile(paneId, item.file, name) { ok, msg ->
                                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        }
                        renameDialogItem = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogItem = null }) {
                    Text("Hủy", color = TextMuted)
                }
            },
            containerColor = CharcoalCard
        )
    }

    // Delete Dialog
    deleteDialogItems?.let { (paneId, items) ->
        AlertDialog(
            onDismissRequest = { deleteDialogItems = null },
            title = { Text("Xác nhận xóa", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Bạn có chắc chắn muốn xóa ${items.size} mục đã chọn vĩnh viễn không?",
                    color = TextMuted,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        dualPaneManager.deleteFiles(paneId, items) { ok, msg ->
                            coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                        deleteDialogItems = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) {
                    Text("Xóa vĩnh viễn")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogItems = null }) {
                    Text("Hủy", color = TextMuted)
                }
            },
            containerColor = CharcoalCard
        )
    }

    // File Details Dialog
    detailsDialogItem?.let { item ->
        AlertDialog(
            onDismissRequest = { detailsDialogItem = null },
            title = { Text("Thông tin chi tiết", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Tên:", color = TextMuted, fontSize = 11.sp)
                    Text(item.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Đường dẫn:", color = TextMuted, fontSize = 11.sp)
                    Text(item.path, color = TextPrimary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Dung lượng:", color = TextMuted, fontSize = 11.sp)
                    Text(item.formattedSize, color = CyanAccent, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Thời gian sửa:", color = TextMuted, fontSize = 11.sp)
                    Text(item.formattedDate, color = TextPrimary, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { detailsDialogItem = null }) {
                    Text("Đóng", color = ElectricBlue)
                }
            },
            containerColor = CharcoalCard
        )
    }
}

@Composable
private fun SinglePaneView(
    paneState: PaneState,
    isTargetDrop: Boolean,
    manager: DualPaneManager,
    onOpenFile: (FileItem) -> Unit,
    onNewFolder: () -> Unit,
    onRename: (FileItem) -> Unit,
    onDelete: (List<FileItem>) -> Unit,
    onShowDetails: (FileItem) -> Unit,
    onDragStart: (List<FileItem>, Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var itemMenuTarget by remember { mutableStateOf<FileItem?>(null) }

    Surface(
        color = CharcoalBackground,
        border = if (isTargetDrop) BorderStroke(2.dp, ElectricBlue) else BorderStroke(0.5.dp, DocumentsUIDivider),
        modifier = Modifier.fillMaxSize()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Pane Header
            Surface(
                color = if (isTargetDrop) ElectricBlue.copy(alpha = 0.15f) else CharcoalSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { manager.navigateBack(paneState.id) },
                            enabled = paneState.backStack.isNotEmpty() || paneState.currentDirectory.parentFile != null,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Lùi lại",
                                tint = if (paneState.backStack.isNotEmpty() || paneState.currentDirectory.parentFile != null) TextPrimary else TextMuted.copy(alpha = 0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Breadcrumb path title
                        Text(
                            text = paneState.currentDirectory.name.ifEmpty { "Bộ nhớ" },
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp)
                        )

                        // Quick buttons
                        IconButton(onClick = { manager.toggleSearch(paneState.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Search, null, tint = if (paneState.isSearchActive) ElectricBlue else TextMuted, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { manager.toggleViewMode(paneState.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(if (paneState.viewMode == ViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList, null, tint = TextMuted, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onNewFolder, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.CreateNewFolder, null, tint = ElectricBlue, modifier = Modifier.size(16.dp))
                        }
                    }

                    // Search row if active
                    if (paneState.isSearchActive) {
                        OutlinedTextField(
                            value = paneState.searchQuery,
                            onValueChange = { manager.setSearch(paneState.id, it) },
                            placeholder = { Text("Tìm trong cửa sổ này...", fontSize = 11.sp, color = TextMuted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .height(40.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = DocumentsUIDivider,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                    }

                    // Multi-select or count bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (paneState.isMultiSelect) {
                            Text(
                                text = "Đã chọn ${paneState.selectedFiles.size} tệp",
                                fontSize = 11.sp,
                                color = CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                            Row {
                                Text(
                                    text = "Tất cả",
                                    color = ElectricBlue,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clickable { manager.selectAll(paneState.id) }
                                        .padding(horizontal = 4.dp)
                                )
                                Text(
                                    text = "Bỏ chọn",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clickable { manager.clearSelection(paneState.id) }
                                        .padding(horizontal = 4.dp)
                                )
                                Text(
                                    text = "Xóa",
                                    color = Color(0xFFEF5350),
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clickable { onDelete(paneState.selectedFiles.toList()) }
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "${paneState.displayedFiles.size} mục",
                                fontSize = 10.5.sp,
                                color = TextMuted
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (paneState.sortField) {
                                        SortField.NAME -> "Tên"
                                        SortField.SIZE -> "Size"
                                        SortField.DATE -> "Ngày"
                                        SortField.TYPE -> "Loại"
                                    },
                                    fontSize = 10.5.sp,
                                    color = ElectricBlue,
                                    modifier = Modifier.clickable {
                                        val next = when (paneState.sortField) {
                                            SortField.NAME -> SortField.SIZE
                                            SortField.SIZE -> SortField.DATE
                                            SortField.DATE -> SortField.TYPE
                                            SortField.TYPE -> SortField.NAME
                                        }
                                        manager.setSort(paneState.id, next)
                                    }
                                )
                                Icon(
                                    if (paneState.sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                    null,
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = DocumentsUIDivider, thickness = 0.5.dp)
                }
            }

            // Pane Body
            Box(modifier = Modifier.weight(1f)) {
                if (paneState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ElectricBlue, modifier = Modifier.size(24.dp))
                    }
                } else if (paneState.displayedFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Thư mục rỗng", color = TextMuted, fontSize = 12.sp)
                    }
                } else if (paneState.viewMode == ViewMode.GRID) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(paneState.displayedFiles, key = { it.path }) { item ->
                            val isSelected = paneState.selectedFiles.contains(item)
                            DualPaneGridItem(
                                item = item,
                                isSelected = isSelected,
                                onClick = {
                                    if (paneState.isMultiSelect) {
                                        manager.toggleSelect(paneState.id, item)
                                    } else if (item.isDirectory) {
                                        manager.loadDirectory(paneState.id, item.file)
                                    } else {
                                        onOpenFile(item)
                                    }
                                },
                                onLongClick = {
                                    manager.toggleSelect(paneState.id, item)
                                },
                                onDragGesture = { startOffset ->
                                    val filesToDrag = if (isSelected) paneState.selectedFiles.toList() else listOf(item)
                                    onDragStart(filesToDrag, startOffset)
                                },
                                onDragMove = onDragMove,
                                onDragEnd = onDragEnd
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        items(paneState.displayedFiles, key = { it.path }) { item ->
                            val isSelected = paneState.selectedFiles.contains(item)
                            DualPaneListItem(
                                item = item,
                                isSelected = isSelected,
                                onClick = {
                                    if (paneState.isMultiSelect) {
                                        manager.toggleSelect(paneState.id, item)
                                    } else if (item.isDirectory) {
                                        manager.loadDirectory(paneState.id, item.file)
                                    } else {
                                        onOpenFile(item)
                                    }
                                },
                                onLongClick = {
                                    manager.toggleSelect(paneState.id, item)
                                },
                                onMoreClick = { itemMenuTarget = item },
                                onDragGesture = { startOffset ->
                                    val filesToDrag = if (isSelected) paneState.selectedFiles.toList() else listOf(item)
                                    onDragStart(filesToDrag, startOffset)
                                },
                                onDragMove = onDragMove,
                                onDragEnd = onDragEnd
                            )
                        }
                    }
                }
            }
        }
    }

    // Context Action Menu on item
    itemMenuTarget?.let { item ->
        DropdownMenu(
            expanded = true,
            onDismissRequest = { itemMenuTarget = null },
            modifier = Modifier.background(CharcoalCard)
        ) {
            DropdownMenuItem(
                text = { Text("Đổi tên", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Default.Edit, null, tint = TextMuted) },
                onClick = {
                    itemMenuTarget = null
                    onRename(item)
                }
            )
            DropdownMenuItem(
                text = { Text("Chi tiết", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Default.Info, null, tint = TextMuted) },
                onClick = {
                    itemMenuTarget = null
                    onShowDetails(item)
                }
            )
            DropdownMenuItem(
                text = { Text("Xóa", color = Color(0xFFEF5350)) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFEF5350)) },
                onClick = {
                    itemMenuTarget = null
                    onDelete(listOf(item))
                }
            )
        }
    }
}

@Composable
private fun DualPaneListItem(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoreClick: () -> Unit,
    onDragGesture: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var globalPos by remember { mutableStateOf(Offset.Zero) }

    Surface(
        color = if (isSelected) ElectricBlue.copy(alpha = 0.15f) else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { globalPos = it.positionInRoot() }
            .pointerInput(item) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        onDragGesture(globalPos + offset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        globalPos += dragAmount
                        onDragMove(globalPos)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            }
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileIcon(item = item, isSelected = isSelected, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (item.isDirectory) item.formattedDate else "${item.formattedSize} • ${item.formattedDate}",
                    color = TextMuted,
                    fontSize = 10.sp
                )
            }
            IconButton(onClick = onMoreClick, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.MoreVert, null, tint = TextMuted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun DualPaneGridItem(
    item: FileItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDragGesture: (Offset) -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var globalPos by remember { mutableStateOf(Offset.Zero) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) ElectricBlue.copy(alpha = 0.2f) else CharcoalCard
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { globalPos = it.positionInRoot() }
            .pointerInput(item) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        onDragGesture(globalPos + offset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        globalPos += dragAmount
                        onDragMove(globalPos)
                    },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd
                )
            }
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FileIcon(item = item, isSelected = isSelected, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.name,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (item.isDirectory) "Thư mục" else item.formattedSize,
                color = TextMuted,
                fontSize = 9.5.sp
            )
        }
    }
}
