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
import androidx.compose.ui.text.style.TextOverflow
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
    onNavigateToSettings: () -> Unit,
    onNavigateToDualPane: () -> Unit = { viewModel.navigateTo(ScreenDestination.DualPane) }
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
            HomeCategory("Bộ nhớ trong", Icons.Default.PhoneAndroid, Color(0xFFFFB300)) {
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
            },
            HomeCategory("CHIA ĐÔI CỬA SỔ", Icons.Default.Splitscreen, CyanAccent) {
                onNavigateToDualPane()
            }
        )
    }
    val recentFiles = remember(recentFilePaths) {
        recentFilePaths.mapNotNull { path ->
            val f = File(path)
            if (f.exists() && f.isFile) {
                FileItem(
                    name = f.name,
                    path = f.absolutePath,
                    size = f.length(),
                    lastModified = f.lastModified(),
                    isDirectory = false,
                    isFavorite = false,
                    fileType = FileUtils.detectFileType(f),
                    mimeType = FileUtils.getMimeType(f.name),
                    extension = FileUtils.getExtension(f.name),
                    childCount = 0,
                    isReadable = f.canRead(),
                    isWritable = f.canWrite()
                )
            } else null
        }.take(6)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFB300).copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = "Logo",
                                    tint = Color(0xFFFFB300),
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
                .padding(horizontal = 14.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Permission Banner (if needed)
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
                                color = Color(0xFFFFB300).copy(alpha = 0.2f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
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
                                    text = "Để duyệt và quản lý toàn bộ tệp, vui lòng cấp quyền bộ nhớ thiết bị.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { requestStoragePermission() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("Cấp quyền", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Quick Actions: Duyệt tệp | Cửa sổ kép (Chia đôi) | Dữ liệu Game
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionCard(
                        title = "Duyệt tệp",
                        subtitle = "Bộ nhớ trong",
                        icon = Icons.Default.Folder,
                        iconTint = Color(0xFFFFB300),
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.openStorageRoot(internalDir)
                    }

                    QuickActionCard(
                        title = "Cửa sổ kép",
                        subtitle = "Chia đôi màn hình",
                        icon = Icons.Default.Splitscreen,
                        iconTint = ElectricBlue,
                        modifier = Modifier.weight(1f)
                    ) {
                        viewModel.toggleDualPane(true)
                        viewModel.openStorageRoot(internalDir)
                    }

                    QuickActionCard(
                        title = "Game Data",
                        subtitle = "Phân tích OBB",
                        icon = Icons.Default.SportsEsports,
                        iconTint = ColorPurpleBadge,
                        modifier = Modifier.weight(1f)
                    ) {
                        onNavigateToGameData()
                    }
                }
            }

            // Storage Volume Section (Compact)
            item {
                Text(
                    text = "BỘ NHỚ THIẾT BỊ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(4.dp))

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
                    Spacer(modifier = Modifier.height(4.dp))
                    removableVolumes.forEach { vol ->
                        CompactStorageCard(
                            volume = vol,
                            onClick = { viewModel.openStorageRoot(File(vol.path)) }
                        )
                    }
                }
            }

            // Categories Section (Clean 2-column paired layout)
            item {
                Text(
                    text = "DANH MỤC TỆP",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(4.dp))

                val chunkedCategories = categories.chunked(2)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chunkedCategories.forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
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

            // Recent Files Section
            if (recentFiles.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "TỆP GẦN ĐÂY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                items(recentFiles, key = { it.path }) { item ->
                    RecentFileRow(item = item, onClick = { viewModel.openFile(item) })
                }
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = CharcoalSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = iconTint.copy(alpha = 0.16f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1)
            Text(subtitle, color = TextMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun CompactStorageCard(
    volume: StorageVolumeInfo,
    onClick: () -> Unit
) {
    val usedBytes = volume.totalBytes - volume.freeBytes
    val percentUsed = if (volume.totalBytes > 0) {
        (usedBytes.toFloat() / volume.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        color = CharcoalSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (volume.isPrimary) Color(0xFFFFB300).copy(alpha = 0.16f) else CyanAccent.copy(alpha = 0.16f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (volume.isPrimary) Icons.Default.PhoneAndroid else Icons.Default.SdCard,
                        contentDescription = null,
                        tint = if (volume.isPrimary) Color(0xFFFFB300) else CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = volume.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "${(percentUsed * 100).toInt()}% đã dùng",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (percentUsed > 0.9f) ColorErrorRed else TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { percentUsed },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (percentUsed > 0.9f) ColorErrorRed else Color(0xFFFFB300),
                    trackColor = CharcoalSurfaceHighlight
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${FileItem.formatBytes(usedBytes)} / ${FileItem.formatBytes(volume.totalBytes)} (Trống ${FileItem.formatBytes(volume.freeBytes)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
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
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .height(50.dp)
            .clickable { category.onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = category.iconColor.copy(alpha = 0.14f),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        category.icon,
                        contentDescription = null,
                        tint = category.iconColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = category.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun RecentFileRow(
    item: FileItem,
    onClick: () -> Unit
) {
    Surface(
        color = CharcoalSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FileIcon(item = item, isSelected = false, modifier = Modifier.size(34.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${FileItem.formatBytes(item.size)} • ${FileItem.formatDate(item.lastModified)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
        }
    }
}
