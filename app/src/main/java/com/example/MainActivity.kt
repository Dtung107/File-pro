package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.ui.analyzer.AnalyzerScreen
import com.example.filemanager.ui.explorer.FileExplorerScreen
import com.example.filemanager.ui.gamedata.GameDataScreen
import com.example.filemanager.ui.home.HomeScreen
import com.example.filemanager.ui.settings.SettingsScreen
import com.example.filemanager.ui.viewers.*
import com.example.filemanager.viewmodel.MainViewModel
import com.example.filemanager.viewmodel.ScreenDestination
import com.example.ui.theme.*

data class BottomNavItem(
    val title: String,
    val destination: ScreenDestination,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FileManagerProTheme {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContainer(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val currentDir by viewModel.currentDirectory.collectAsState()

    // Handle back button smoothly (retraces folder stack in Files before returning to Home)
    BackHandler(enabled = currentScreen !is ScreenDestination.Home) {
        viewModel.navigateBack()
    }

    // 3 main bottom nav items per user requirement:
    // 1. Trang chủ (Home)
    // 2. Tệp (Files)
    // 3. Công cụ (Tools)
    val bottomNavItems = remember {
        listOf(
            BottomNavItem("Trang chủ", ScreenDestination.Home, Icons.Default.Home),
            BottomNavItem("Tệp", ScreenDestination.Files, Icons.Default.Folder),
            BottomNavItem("Công cụ", ScreenDestination.Analyzer, Icons.Default.Build)
        )
    }

    val isTopLevelScreen = currentScreen is ScreenDestination.Home ||
            currentScreen is ScreenDestination.Files ||
            currentScreen is ScreenDestination.Analyzer

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isTopLevelScreen) {
                Surface(
                    color = CharcoalSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    Column {
                        HorizontalDivider(color = Color(0xFF232733), thickness = 0.8.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bottomNavItems.forEach { item ->
                                val isSelected = when (item.destination) {
                                    is ScreenDestination.Home -> currentScreen is ScreenDestination.Home
                                    is ScreenDestination.Files -> currentScreen is ScreenDestination.Files
                                    is ScreenDestination.Analyzer -> currentScreen is ScreenDestination.Analyzer
                                    else -> false
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable { viewModel.navigateTo(item.destination) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) ElectricBlue.copy(alpha = 0.2f) else Color.Transparent,
                                            modifier = Modifier.size(width = 44.dp, height = 24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    item.icon,
                                                    contentDescription = item.title,
                                                    tint = if (isSelected) ElectricBlue else TextMuted,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.title,
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) ElectricBlue else TextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = CharcoalBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val screen = currentScreen) {
                is ScreenDestination.Home -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToFiles = { dir ->
                            viewModel.loadDirectory(dir)
                            viewModel.navigateTo(ScreenDestination.Files)
                        },
                        onNavigateToGameData = { viewModel.navigateTo(ScreenDestination.GameData) },
                        onNavigateToAnalyzer = { viewModel.navigateTo(ScreenDestination.Analyzer) },
                        onNavigateToSettings = { viewModel.navigateTo(ScreenDestination.Settings) }
                    )
                }

                is ScreenDestination.Files -> {
                    FileExplorerScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is ScreenDestination.GameData -> {
                    GameDataScreen(
                        currentDirectory = currentDir,
                        onBack = { viewModel.navigateBack() },
                        onOpenHexViewer = { file -> viewModel.navigateTo(ScreenDestination.HexViewer(file)) },
                        onOpenTextViewer = { file -> viewModel.navigateTo(ScreenDestination.TextViewer(file)) },
                        onOpenDatabaseViewer = { file -> viewModel.navigateTo(ScreenDestination.SqliteViewer(file)) },
                        onOpenArchiveViewer = { file -> viewModel.navigateTo(ScreenDestination.ArchiveViewer(file)) },
                        onOpenApkInspector = { file -> viewModel.navigateTo(ScreenDestination.ApkInspector(file)) }
                    )
                }

                is ScreenDestination.Analyzer -> {
                    AnalyzerScreen(
                        currentDirectory = currentDir,
                        onBack = { viewModel.navigateBack() },
                        onOpenHexViewer = { file -> viewModel.navigateTo(ScreenDestination.HexViewer(file)) },
                        onOpenApkInspector = { file -> viewModel.navigateTo(ScreenDestination.ApkInspector(file)) },
                        onOpenDatabaseViewer = { file -> viewModel.navigateTo(ScreenDestination.SqliteViewer(file)) },
                        onOpenGameData = { viewModel.navigateTo(ScreenDestination.GameData) }
                    )
                }

                is ScreenDestination.Settings -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is ScreenDestination.TextViewer -> {
                    TextViewerScreen(
                        file = screen.file,
                        onBack = { viewModel.navigateBack() },
                        onOpenHexViewer = { file -> viewModel.navigateTo(ScreenDestination.HexViewer(file)) }
                    )
                }

                is ScreenDestination.ApkInspector -> {
                    ApkInspectorScreen(
                        apkFile = screen.file,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is ScreenDestination.SqliteViewer -> {
                    SqliteViewerScreen(
                        dbFile = screen.file,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is ScreenDestination.HexViewer -> {
                    HexViewerScreen(
                        file = screen.file,
                        onBack = { viewModel.navigateBack() },
                        onOpenTextViewer = { file -> viewModel.navigateTo(ScreenDestination.TextViewer(file)) }
                    )
                }

                is ScreenDestination.ArchiveViewer -> {
                    ArchiveViewerScreen(
                        archiveFile = screen.file,
                        onBack = { viewModel.navigateBack() }
                    )
                }

                is ScreenDestination.MediaViewer -> {
                    MediaViewerScreen(
                        file = screen.file,
                        fileType = screen.type,
                        onBack = { viewModel.navigateBack() }
                    )
                }
            }
        }
    }
}
