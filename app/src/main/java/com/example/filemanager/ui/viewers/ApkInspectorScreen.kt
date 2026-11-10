package com.example.filemanager.ui.viewers

import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.apk.ApkDetails
import com.example.filemanager.apk.ApkInspector
import com.example.filemanager.apk.ApkInstaller
import com.example.filemanager.model.FileItem
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkInspectorScreen(
    apkFile: File,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    var apkDetails by remember { mutableStateOf<ApkDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val tabs = listOf("Tổng quan", "Quyền hạn", "Thành phần", "Tệp APK")

    LaunchedEffect(apkFile) {
        isLoading = true
        errorMessage = null
        val result = ApkInspector.analyzeApk(context, apkFile)
        if (result.isSuccess) {
            apkDetails = result.getOrNull()
        } else {
            errorMessage = result.exceptionOrNull()?.message ?: "Lỗi khi phân tích tệp APK"
        }
        isLoading = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("APK Inspector", color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val res = ApkInstaller.installApk(context, apkFile)
                            if (res.isFailure) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "Lỗi cài đặt")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cài đặt APK", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = {
                        val details = apkDetails
                        if (details != null) {
                            val text = """
                                Ứng dụng: ${details.appName}
                                Gói: ${details.packageName}
                                Phiên bản: ${details.versionName} (${details.versionCode})
                                Min SDK: ${details.minSdk} | Target SDK: ${details.targetSdk}
                                SHA-256: ${details.sha256Checksum}
                                CPU: ${details.architectures.joinToString(", ")}
                                Quyền: ${details.permissions.size}
                                Activities: ${details.activities.size}
                                Services: ${details.services.size}
                            """.trimIndent()
                            clipboardManager.setText(AnnotatedString(text))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Đã sao chép thông tin APK!")
                            }
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Sao chép thông tin", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
            )
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ElectricBlue)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Đang phân tích APK & chữ ký số...", color = TextSecondary)
                }
            }
        } else if (errorMessage != null || apkDetails == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = errorMessage ?: "Lỗi phân tích APK", color = ColorErrorRed)
            }
        } else {
            val details = apkDetails!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Header card
                Surface(
                    color = CharcoalSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (details.iconBitmap != null) {
                                Image(
                                    bitmap = details.iconBitmap.asImageBitmap(),
                                    contentDescription = "Icon ứng dụng",
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(60.dp)
                                        .background(CharcoalSurfaceVariant, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Android, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(36.dp))
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = details.appName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = details.packageName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ElectricBlue
                                )
                                Text(
                                    text = "v${details.versionName} (${details.versionCode})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stats pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ApkStatBadge("Dung lượng", FileItem.formatBytes(details.fileSizeBytes))
                            ApkStatBadge("Min SDK", "${details.minSdk}")
                            ApkStatBadge("Target SDK", "${details.targetSdk}")
                            ApkStatBadge("CPU", if (details.architectures.isEmpty()) "Không lib" else details.architectures.first())
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val res = ApkInstaller.installApk(context, apkFile)
                                if (res.isFailure) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(res.exceptionOrNull()?.message ?: "Lỗi cài đặt")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ColorSuccessGreen),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("CÀI ĐẶT ỨNG DỤNG NÀY (INSTALL APK)", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        }
                    }
                }

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CharcoalSurfaceVariant,
                    contentColor = ElectricBlue
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index; searchQuery = "" },
                            text = {
                                Text(
                                    text = title,
                                    color = if (selectedTab == index) ElectricBlue else TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        )
                    }
                }

                // Content
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> ApkOverviewTab(details)
                        1 -> ApkListTab(
                            title = "Danh sách quyền hạn (${details.permissions.size})",
                            items = details.permissions,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it },
                            emptyMessage = "Không có quyền hạn nào được yêu cầu"
                        )
                        2 -> ApkComponentsTab(details)
                        3 -> ApkInternalFilesTab(
                            items = details.internalFiles,
                            searchQuery = searchQuery,
                            onSearchChange = { searchQuery = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ApkStatBadge(label: String, value: String) {
    Surface(
        color = CharcoalSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

@Composable
fun ApkOverviewTab(details: ApkDetails) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            InfoSectionCard(title = "Chữ ký số & Chứng chỉ (Signing)") {
                Text(
                    text = details.certificateInfo,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextPrimary,
                    lineHeight = 18.sp
                )
            }
        }

        item {
            InfoSectionCard(title = "Mã băm SHA-256 (Tệp APK)") {
                Text(
                    text = details.sha256Checksum,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = CyanAccent
                )
            }
        }

        item {
            InfoSectionCard(title = "Kiến trúc CPU hỗ trợ (Native ABIs)") {
                if (details.architectures.isEmpty()) {
                    Text("Không có thư viện C/C++ native (Chỉ bytecode DEX)", color = TextSecondary)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        details.architectures.forEach { abi ->
                            Surface(
                                color = CharcoalSurfaceHighlight,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = abi,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = ColorSuccessGreen,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            InfoSectionCard(title = "Tóm tắt cấu trúc nhị phân") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("• Tệp DEX (Bytecode): ${details.dexFiles.size} tệp", color = TextPrimary)
                    Text("• Thư viện .so (Native libs): ${details.nativeLibs.size} tệp", color = TextPrimary)
                    Text("• Tổng số tệp bên trong: ${details.internalFiles.size} tệp", color = TextPrimary)
                }
            }
        }
    }
}

@Composable
fun ApkComponentsTab(details: ApkDetails) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ComponentSection(title = "Activities (Màn hình)", count = details.activities.size, items = details.activities)
        }
        item {
            ComponentSection(title = "Services (Dịch vụ nền)", count = details.services.size, items = details.services)
        }
        item {
            ComponentSection(title = "Broadcast Receivers (Bộ thu nhận)", count = details.receivers.size, items = details.receivers)
        }
        item {
            ComponentSection(title = "Content Providers (Nhà cung cấp dữ liệu)", count = details.providers.size, items = details.providers)
        }
    }
}

@Composable
fun ComponentSection(title: String, count: Int, items: List<String>) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        color = CharcoalSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "$title ($count)", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                if (count > 0) {
                    TextButton(onClick = { expanded = !expanded }) {
                        Text(if (expanded) "Thu gọn" else "Xem chi tiết", color = ElectricBlue)
                    }
                }
            }

            if (expanded && items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { comp ->
                        Text(
                            text = comp,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ApkListTab(
    title: String,
    items: List<String>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    emptyMessage: String
) {
    val filtered = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Lọc danh sách...", color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = CharcoalSurfaceHighlight,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyMessage, color = TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered) { item ->
                    Surface(
                        color = CharcoalSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = item,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ApkInternalFilesTab(
    items: List<com.example.filemanager.apk.ApkEntryItem>,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    val filtered = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Lọc tệp trong APK (DEX, .so, assets...)", color = TextMuted) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = ElectricBlue,
                unfocusedBorderColor = CharcoalSurfaceHighlight,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered) { entry ->
                Surface(
                    color = CharcoalSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (entry.name.endsWith(".dex")) ColorWarningAmber
                                else if (entry.name.endsWith(".so")) ColorSuccessGreen
                                else if (entry.name.startsWith("assets/")) ElectricBlue
                                else TextPrimary
                            )
                        }
                        if (!entry.isDirectory) {
                            Text(
                                text = FileItem.formatBytes(entry.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InfoSectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        color = CharcoalSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = ElectricBlue
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}
