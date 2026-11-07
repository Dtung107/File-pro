package com.example.filemanager.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.filemanager.model.FileItem
import com.example.filemanager.model.FileType
import com.example.filemanager.model.StorageVolumeInfo
import com.example.filemanager.ui.explorer.FileIcon
import com.example.filemanager.utils.FileUtils
import com.example.filemanager.viewmodel.MainViewModel
import com.example.filemanager.viewmodel.ScreenDestination
import com.example.ui.theme.*
import java.io.File

private data class HomeCategory(
    val title: String,
    val icon: ImageVector,
    val iconColor: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToFiles: (File) -> Unit,
    onNavigateToGameData: () -> Unit,
    onNavigateToAnalyzer: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current
    val storageVolumes by viewModel.storageVolumes.collectAsState()
    val recentFilePaths by viewModel.recentFilePaths.collectAsState()

    fun checkPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    var hasPermission by remember { mutableStateOf(checkPermission()) }

    val manageStorageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        hasPermission = checkPermission()
        if (hasPermission) viewModel.refreshStorageVolumes()
    }

    val standardPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasPermission = checkPermission()
        if (hasPermission) viewModel.refreshStorageVolumes()
    }

    fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(intent)
            }
        } else {
            standardPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.handleSafUri(uri)
        }
    }

    val internalDir = remember { Environment.getExternalStorageDirectory() }
    val downloadsDir = remember { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) }

    val categories = remember {
        listOf(
            HomeCategory("Bộ nhớ trong", Icons.Default.PhoneAndroid, ElectricBlue) {
                viewModel.openStorageRoot(internalDir)
            },
            HomeCategory("Thẻ nhớ SD", Icons.Default.SdCard, CyanAccent) {
                val sd = storageVolumes.find { it.isRemovable }
                if (sd != null) {
                    viewModel.openStorageRoot(File(sd.path))
                } else {
                    safLauncher.launch(null)
                }
            },
            HomeCategory("Tải xuống", Icons.Default.Download, CyanAccent) {
                if (downloadsDir.exists()) viewModel.openStorageRoot(downloadsDir)
                else viewModel.openStorageRoot(internalDir)
            },
            HomeCategory("Hình ảnh", Icons.Default.Image, Color(0xFFF48FB1)) {
                viewModel.loadCategoryFiles(FileType.IMAGE, "Hình ảnh")
            },
            HomeCategory("Video", Icons.Default.VideoFile, Color(0xFFEF5350)) {
                viewModel.loadCategoryFiles(FileType.VIDEO, "Video")
            },
            HomeCategory("Âm thanh", Icons.Default.AudioFile, CyanAccent) {
                viewModel.loadCategoryFiles(FileType.AUDIO, "Âm thanh")
            },
            HomeCategory("Tài liệu", Icons.Default.Article, ElectricBlue) {
                viewModel.loadCategoryFiles(FileType.DOCUMENT, "Tài liệu")
            },
            HomeCategory("File APK", Icons.Default.Android, ColorSuccessGreen) {
                viewModel.loadCategoryFiles(FileType.APK, "File APK")
            },
            HomeCategory("Database", Icons.Default.Storage, ColorSuccessGreen) {
                viewModel.loadCategoryFiles(FileType.DATABASE, "Database (SQLite)")
            },
            HomeCategory("Dữ liệu Game", Icons.Default.SportsEsports, ColorPurpleBadge) {
                onNavigateToGameData()
            },
            HomeCategory("File nén", Icons.Default.FolderZip, ElectricBlue) {
                viewModel.loadCategoryFiles(FileType.ARCHIVE, "File nén")
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ElectricBlue.copy(alpha = 0.18f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = "Logo",
                                    tint = ElectricBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "FILE MANAGER PRO",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.openStorageRoot(internalDir)
                        viewModel.setSearchQuery("")
                    }) {
                        Icon(Icons.Default.Search, contentDescription = "Tìm kiếm", tint = TextPrimary)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Cài đặt", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CharcoalSurface)
            )
        },
        containerColor = CharcoalBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!hasPermission) {
                item {
                    Surface(
                        color = CharcoalSurfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ElectricBlue.copy(alpha = 0.2f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = ElectricBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Cần quyền truy cập bộ nhớ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Để duyệt và phân tích tệp APK, Database và tệp game, vui lòng cấp quyền bộ nhớ.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { requestStoragePermission() },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Cấp quyền", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // Storage Volume Section (Compact, Samsung My Files style)
            item {
                Text(
                    text = "BỘ NHỚ LƯU TRỮ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                val primaryVolume = storageVolumes.find { it.isPrimary } ?: StorageVolumeInfo(
                    name = "Bộ nhớ trong",
                    path = internalDir.absolutePath,
                    totalBytes = 64L * 1024 * 1024 * 1024,
                    freeBytes = 32L * 1024 * 1024 * 1024,
                    isPrimary = true
                )

                CompactStorageCard(
                    volume = primaryVolume,
                    onClick = { viewModel.openStorageRoot(File(primaryVolume.path)) }
                )

                val removableVolumes = storageVolumes.filter { !it.isPrimary }
                if (removableVolumes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    removableVolumes.forEach { vol ->
                        CompactStorageCard(
                            volume = vol,
                            onClick = { viewModel.openStorageRoot(File(vol.path)) }
                        )
                    }
                }
            }

            // Categories Section (Clean, 2-column native grid layout)
            item {
                Text(
                    text = "DANH MỤC",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Build a compact 2-column paired layout for smooth native scrolling without nested grid overhead
                val chunkedCategories = categories.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunkedCategories.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CategoryRowCard(
                                category = pair[0],
                                modifier = Modifier.weight(1f)
                            )
                            if (pair.size > 1) {
                                CategoryRowCard(
                                    category = pair[1],
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Recent Files Section (Simple list)
            item {
                Text(
                    text = "TỆP GẦN ĐÂY",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (recentFilePaths.isEmpty()) {
                    Surface(
                        color = CharcoalSurface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Chưa có tệp mở gần đây",
                            color = TextMuted,
                            modifier = Modifier.padding(vertical = 16.dp, horizontal = 14.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        recentFilePaths.take(5).forEach { path ->
                            val f = File(path)
                            if (f.exists()) {
                                Surface(
                                    color = CharcoalSurface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val type = FileUtils.detectFileType(f)
                                            val item = FileItem(
                                                name = f.name,
                                                path = f.absolutePath,
                                                size = f.length(),
                                                lastModified = f.lastModified(),
                                                isDirectory = f.isDirectory,
                                                fileType = type,
                                                extension = FileUtils.getExtension(f.name)
                                            )
                                            viewModel.openFile(item)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FileIcon(FileUtils.detectFileType(f), FileUtils.getExtension(f.name), size = 24.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = f.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "${FileItem.formatBytes(f.length())} • ${FileItem.formatDate(f.lastModified())}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextMuted,
                                                fontSize = 11.sp
                                            )
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

@Composable
private fun CompactStorageCard(
    volume: StorageVolumeInfo,
    onClick: () -> Unit
) {
    Surface(
        color = CharcoalSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (volume.isRemovable) Icons.Default.SdCard else Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = ElectricBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = volume.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }

                Text(
                    text = "Còn trống: ${FileItem.formatBytes(volume.freeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ColorSuccessGreen,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { volume.usagePercent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (volume.usagePercent > 0.9f) ColorErrorRed else ElectricBlue,
                trackColor = CharcoalSurfaceHighlight
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Đã dùng: ${FileItem.formatBytes(volume.usedBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                Text(
                    text = "Tổng: ${FileItem.formatBytes(volume.totalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CategoryRowCard(
    category: HomeCategory,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CharcoalSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .height(54.dp)
            .clickable { category.onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(category.iconColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = category.iconColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = category.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                fontSize = 13.sp
            )
        }
    }
}
