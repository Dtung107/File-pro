package com.example.filemanager.ui.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var content by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedCharsetName by remember { mutableStateOf("UTF-8") }
    var showCharsetMenu by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }

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
                content = text
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
                        Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = TextPrimary)
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
                                            file.writeText(content, charset)
                                        }
                                        isSaving = false
                                        isEditing = false
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

                    // Charset menu
                    Box {
                        IconButton(onClick = { showCharsetMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Mã hóa", tint = TextPrimary)
                        }
                        DropdownMenu(
                            expanded = showCharsetMenu,
                            onDismissRequest = { showCharsetMenu = false },
                            modifier = Modifier.background(CharcoalSurfaceVariant)
                        ) {
                            listOf("UTF-8", "UTF-16", "ISO-8859-1", "US-ASCII", "windows-1252").forEach { enc ->
                                DropdownMenuItem(
                                    text = { Text(enc, color = if (enc == selectedCharsetName) ElectricBlue else TextPrimary) },
                                    onClick = {
                                        selectedCharsetName = enc
                                        showCharsetMenu = false
                                        loadFileContent(enc)
                                    }
                                )
                            }
                            HorizontalDivider(color = CharcoalSurfaceHighlight)
                            DropdownMenuItem(
                                text = { Text("Sao chép tất cả", color = TextPrimary) },
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(content))
                                    showCharsetMenu = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Đã sao chép nội dung vào khay nhớ tạm!")
                                    }
                                }
                            )
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
                val lines = remember(content) { content.lines() }
                val vScrollState = rememberScrollState()
                val hScrollState = rememberScrollState()

                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                        ) {
                            if (isEditing) {
                                BasicTextField(
                                    value = content,
                                    onValueChange = { content = it },
                                    textStyle = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = TextPrimary,
                                        lineHeight = 20.sp
                                    ),
                                    cursorBrush = SolidColor(ElectricBlue)
                                )
                            } else {
                                Text(
                                    text = content,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    color = TextPrimary,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
