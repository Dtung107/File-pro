package com.example.filemanager.ui.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.getSelectedText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerScreen(
    file: File,
    onBack: () -> Unit,
    onOpenHexViewer: ((File) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current

    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var isEditing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedCharsetName by remember { mutableStateOf("UTF-8") }
    var showActionMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

    val vScrollState = rememberScrollState()
    val hScrollState = rememberScrollState()

    fun loadFileContent(charsetName: String) {
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            try {
                val charset = try {
                    Charset.forName(charsetName)
                } catch (e: Exception) {
                    Charsets.UTF_8
                }
                val text = withContext(Dispatchers.IO) {
                    if (file.length() > 3 * 1024 * 1024) {
                        file.inputStream().use { stream ->
                            val buffer = ByteArray(2 * 1024 * 1024)
                            val read = stream.read(buffer)
                            String(buffer, 0, read, charset) + "\n\n[...Đã cắt nội dung vì tệp lớn hơn 2MB...]"
                        }
                    } else {
                        file.readText(charset)
                    }
                }
                textFieldValue = TextFieldValue(text = text, selection = TextRange.Zero)
                isLoading = false
            } catch (e: Exception) {
                errorMessage = "Lỗi khi đọc tệp: ${e.message}"
                isLoading = false
            }
        }
    }

    LaunchedEffect(file) {
        loadFileContent(selectedCharsetName)
    }

    // Auto-scroll when cursor position changes in edit mode so cursor is never hidden by the keyboard
    LaunchedEffect(textFieldValue.selection, isEditing) {
        if (isEditing) {
            val cursorIndex = textFieldValue.selection.max
            val textBeforeCursor = textFieldValue.text.take(cursorIndex)
            val cursorLine = textBeforeCursor.count { it == '\n' }
            val approximateLineHeightPx = with(density) { 22.sp.toPx() }
            val targetScroll = (cursorLine * approximateLineHeightPx).toInt()
            val viewportHeight = vScrollState.viewportSize
            // Keep cursor in middle of viewport if possible
            val adjustedScroll = (targetScroll - viewportHeight / 2).coerceIn(0, vScrollState.maxValue)
            vScrollState.animateScrollTo(adjustedScroll)
        }
    }

    // Clipboard helpers
    fun doSelectAll() {
        textFieldValue = textFieldValue.copy(
            selection = TextRange(0, textFieldValue.text.length)
        )
        isEditing = true
    }

    fun doCopy() {
        val selected = textFieldValue.getSelectedText()
        val textToCopy = if (selected.text.isNotEmpty()) selected.text else textFieldValue.text
        if (textToCopy.isNotEmpty()) {
            clipboardManager.setText(AnnotatedString(textToCopy))
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Đã sao chép vào khay nhớ tạm!")
            }
        }
    }

    fun doCut() {
        val selected = textFieldValue.getSelectedText()
        if (selected.text.isNotEmpty()) {
            clipboardManager.setText(AnnotatedString(selected.text))
            val currentText = textFieldValue.text
            val start = textFieldValue.selection.min
            val end = textFieldValue.selection.max
            val newText = currentText.substring(0, start) + currentText.substring(end)
            textFieldValue = textFieldValue.copy(
                text = newText,
                selection = TextRange(start)
            )
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Đã cắt văn bản vào khay nhớ tạm!")
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Chưa chọn đoạn văn bản nào để cắt")
            }
        }
    }

    fun doPaste() {
        val clipText = clipboardManager.getText()?.text ?: ""
        if (clipText.isNotEmpty()) {
            val currentText = textFieldValue.text
            val start = textFieldValue.selection.min
            val end = textFieldValue.selection.max
            val newText = currentText.substring(0, start) + clipText + currentText.substring(end)
            textFieldValue = textFieldValue.copy(
                text = newText,
                selection = TextRange(start + clipText.length)
            )
            isEditing = true
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Đã dán nội dung từ khay nhớ tạm!")
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Khay nhớ tạm đang rỗng")
            }
        }
    }

    fun doDelete() {
        val selected = textFieldValue.getSelectedText()
        if (selected.text.isNotEmpty()) {
            val currentText = textFieldValue.text
            val start = textFieldValue.selection.min
            val end = textFieldValue.selection.max
            val newText = currentText.substring(0, start) + currentText.substring(end)
            textFieldValue = textFieldValue.copy(
                text = newText,
                selection = TextRange(start)
            )
        } else {
            // If no text selected, clear all or delete previous char
            if (textFieldValue.text.isNotEmpty()) {
                textFieldValue = textFieldValue.copy(text = "", selection = TextRange.Zero)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Đã xóa toàn bộ nội dung")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = file.name,
                            maxLines = 1,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "${com.example.filemanager.model.FileItem.formatBytes(file.length())} • $selectedCharsetName",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
                    }
                },
                actions = {
                    // Search toggle
                    IconButton(onClick = { isSearching = !isSearching }) {
                        Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = if (isSearching) ElectricBlue else TextPrimary)
                    }

                    // Edit / View toggle
                    IconButton(onClick = { isEditing = !isEditing }) {
                        Icon(
                            if (isEditing) Icons.Default.Visibility else Icons.Default.Edit,
                            contentDescription = if (isEditing) "Chế độ xem" else "Chỉnh sửa",
                            tint = if (isEditing) ElectricBlue else TextPrimary
                        )
                    }

                    // Save button if editing
                    if (isEditing) {
                        IconButton(
                            onClick = {
                                isSaving = true
                                coroutineScope.launch {
                                    try {
                                        val charset = Charset.forName(selectedCharsetName)
                                        withContext(Dispatchers.IO) {
                                            file.writeText(textFieldValue.text, charset)
                                        }
                                        isSaving = false
                                        isEditing = false
                                        keyboardController?.hide()
                                        snackbarHostState.showSnackbar("Đã lưu tệp thành công!")
                                    } catch (e: Exception) {
                                        isSaving = false
                                        snackbarHostState.showSnackbar("Lỗi khi lưu: ${e.message}")
                                    }
                                }
                            },
                            enabled = !isSaving
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Lưu", tint = ColorSuccessGreen)
                        }
                    }

                    // 3-dots Menu with full Editing, Clipboard, Encoding, and Hex actions
                    Box {
                        IconButton(onClick = { showActionMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Tùy chọn", tint = TextPrimary)
                        }
                        DropdownMenu(
                            expanded = showActionMenu,
                            onDismissRequest = { showActionMenu = false },
                            modifier = Modifier.background(CharcoalSurfaceVariant)
                        ) {
                            // Section: Edit & Clipboard Actions
                            Text(
                                text = "THAO TÁC VĂN BẢN",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            DropdownMenuItem(
                                text = { Text("Chọn tất cả", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.SelectAll, null, tint = ElectricBlue) },
                                onClick = {
                                    showActionMenu = false
                                    doSelectAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sao chép", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = ElectricBlue) },
                                onClick = {
                                    showActionMenu = false
                                    doCopy()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Dán", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.ContentPaste, null, tint = CyanAccent) },
                                onClick = {
                                    showActionMenu = false
                                    doPaste()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Cắt", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.ContentCut, null, tint = ColorWarningAmber) },
                                onClick = {
                                    showActionMenu = false
                                    doCut()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Xóa", color = ColorErrorRed) },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = ColorErrorRed) },
                                onClick = {
                                    showActionMenu = false
                                    doDelete()
                                }
                            )

                            HorizontalDivider(color = CharcoalSurfaceHighlight, modifier = Modifier.padding(vertical = 4.dp))

                            // Section: Charset
                            Text(
                                text = "BẢNG MÃ KÝ TỰ",
                                fontSize = 11.sp,
                                color = TextMuted,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                            listOf("UTF-8", "UTF-16", "ISO-8859-1", "US-ASCII", "windows-1252").forEach { enc ->
                                DropdownMenuItem(
                                    text = { Text(enc, color = if (enc == selectedCharsetName) ElectricBlue else TextPrimary) },
                                    onClick = {
                                        selectedCharsetName = enc
                                        showActionMenu = false
                                        loadFileContent(enc)
                                    }
                                )
                            }

                            if (onOpenHexViewer != null) {
                                HorizontalDivider(color = CharcoalSurfaceHighlight, modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Mở trong Hex Viewer", color = CyanAccent) },
                                    leadingIcon = { Icon(Icons.Default.DataObject, contentDescription = null, tint = CyanAccent) },
                                    onClick = {
                                        showActionMenu = false
                                        onOpenHexViewer(file)
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CharcoalSurface
                )
            )
        },
        containerColor = CharcoalBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding() // CRITICAL: Shrinks container height when soft keyboard is shown
        ) {
            // Search bar
            if (isSearching) {
                Surface(
                    color = CharcoalSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Tìm kiếm văn bản...", color = TextMuted) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricBlue,
                                unfocusedBorderColor = CharcoalSurfaceHighlight,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        IconButton(onClick = { searchQuery = ""; isSearching = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Đóng tìm kiếm", tint = TextPrimary)
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricBlue)
                }
            } else if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = errorMessage ?: "", color = ColorErrorRed, style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val lines = remember(textFieldValue.text) { textFieldValue.text.lines() }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(CharcoalBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(vScrollState)
                            .horizontalScroll(hScrollState)
                    ) {
                        // Line numbers gutter
                        Column(
                            modifier = Modifier
                                .background(CharcoalSurface)
                                .padding(horizontal = 8.dp, vertical = 12.dp)
                        ) {
                            lines.indices.forEach { index ->
                                Text(
                                    text = "${index + 1}",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        // Content editor / viewer
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .widthIn(min = 300.dp)
                        ) {
                            if (isEditing) {
                                BasicTextField(
                                    value = textFieldValue,
                                    onValueChange = { textFieldValue = it },
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = TextPrimary,
                                        lineHeight = 20.sp
                                    ),
                                    cursorBrush = SolidColor(ElectricBlue),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = textFieldValue.text,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

                // Quick Keyboard Accessory Toolbar when editing
                if (isEditing) {
                    Surface(
                        color = CharcoalSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            HorizontalDivider(color = DocumentsUIDivider, thickness = 0.8.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Quick Tab / Indent
                                    TextButton(
                                        onClick = {
                                            val currentText = textFieldValue.text
                                            val start = textFieldValue.selection.min
                                            val end = textFieldValue.selection.max
                                            val newText = currentText.substring(0, start) + "  " + currentText.substring(end)
                                            textFieldValue = textFieldValue.copy(text = newText, selection = TextRange(start + 2))
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("⇥ Tab", color = CyanAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Quick Select All
                                    IconButton(onClick = { doSelectAll() }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.SelectAll, contentDescription = "Chọn tất cả", tint = ElectricBlue, modifier = Modifier.size(18.dp))
                                    }

                                    // Quick Copy
                                    IconButton(onClick = { doCopy() }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Sao chép", tint = ElectricBlue, modifier = Modifier.size(18.dp))
                                    }

                                    // Quick Cut
                                    IconButton(onClick = { doCut() }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.ContentCut, contentDescription = "Cắt", tint = ColorWarningAmber, modifier = Modifier.size(18.dp))
                                    }

                                    // Quick Paste
                                    IconButton(onClick = { doPaste() }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.ContentPaste, contentDescription = "Dán", tint = CyanAccent, modifier = Modifier.size(18.dp))
                                    }

                                    // Quick Delete
                                    IconButton(onClick = { doDelete() }, modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = ColorErrorRed, modifier = Modifier.size(18.dp))
                                    }
                                }

                                // Hide keyboard button
                                IconButton(onClick = { keyboardController?.hide() }, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.KeyboardHide, contentDescription = "Ẩn bàn phím", tint = TextMuted, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
