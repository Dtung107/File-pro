package com.example.filemanager.ui.viewers

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.archive.ArchiveEntryInfo
import com.example.filemanager.archive.ArchiveManager
import com.example.filemanager.model.FileItem
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
    val snackbarHostState = remember { SnackbarHostState() }

    var entries by remember { mutableStateOf<List<ArchiveEntryInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var isExtracting by remember { mutableStateOf(false) }
    var extractProgress by remember { mutableFloatStateOf(0f) }
    var currentExtractEntry by remember { mutableStateOf("") }

    LaunchedEffect(archiveFile) {
        isLoading = true
        errorMessage = null
        val result = ArchiveManager.listArchiveEntries(archiveFile)
        if (result.isSuccess) {
            entries = result.getOrNull() ?: emptyList()
        } else {
            errorMessage = result.exceptionOrNull()?.message ?: "Lỗi khi mở tệp nén"
        }
        isLoading = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(archiveFile.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${FileItem.formatBytes(archiveFile.length())} • ${entries.size} mục",
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
                    // Test Integrity
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

                    // Extract All
                    IconButton(
                        onClick = {
                            val targetDir = File(archiveFile.parentFile ?: archiveFile, archiveFile.nameWithoutExtension + "_extracted")
                            isExtracting = true
                            coroutineScope.launch {
                                val res = ArchiveManager.extractAll(archiveFile, targetDir) { p, name ->
                                    extractProgress = p
                                    currentExtractEntry = name
                                }
                                if (res.isSuccess) {
                                    snackbarHostState.showSnackbar("Đã giải nén ${res.getOrNull()} tệp vào ${targetDir.name}!")
                                } else {
                                    snackbarHostState.showSnackbar("Lỗi giải nén: ${res.exceptionOrNull()?.message}")
                                }
                                isExtracting = false
                            }
                        },
                        enabled = !isExtracting
                    ) {
                        Icon(Icons.Default.Unarchive, contentDescription = "Giải nén tất cả", tint = ElectricBlue)
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
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Đang giải nén...", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Text("${(extractProgress * 100).toInt()}%", color = ElectricBlue)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { extractProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = ElectricBlue
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(currentExtractEntry, color = TextMuted, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }

            // Search filter
            Surface(color = CharcoalSurface, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Tìm tệp trong tệp nén...", color = TextMuted) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricBlue,
                        unfocusedBorderColor = CharcoalSurfaceHighlight,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
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
                        Text("Không có mục nào", color = TextMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered) { item ->
                            Surface(
                                color = CharcoalSurface,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        if (item.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = if (item.isDirectory) ColorWarningAmber else ElectricBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.path,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextPrimary,
                                            maxLines = 1
                                        )
                                        if (!item.isDirectory) {
                                            Text(
                                                text = "${FileItem.formatBytes(item.size)} (Nén: ${FileItem.formatBytes(item.compressedSize)})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextMuted
                                            )
                                        }
                                    }

                                    // Extract single item
                                    if (!item.isDirectory) {
                                        IconButton(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val targetDir = File(archiveFile.parentFile ?: archiveFile, archiveFile.nameWithoutExtension + "_extracted")
                                                    val res = ArchiveManager.extractEntry(archiveFile, item.path, targetDir)
                                                    if (res.isSuccess) {
                                                        snackbarHostState.showSnackbar("Đã trích xuất ${item.name}!")
                                                    } else {
                                                        snackbarHostState.showSnackbar("Lỗi: ${res.exceptionOrNull()?.message}")
                                                    }
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = "Trích xuất", tint = CyanAccent)
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
}
