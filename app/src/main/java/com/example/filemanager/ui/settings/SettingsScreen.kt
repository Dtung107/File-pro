package com.example.filemanager.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.viewmodel.MainViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val showHidden by viewModel.showHiddenFiles.collectAsState()
    val isGridView by viewModel.isGridView.collectAsState()

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            viewModel.handleSafUri(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = TextPrimary)
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Display Settings
            item {
                Text("GIAO DIỆN & HIỂN THỊ", style = MaterialTheme.typography.labelMedium, color = ElectricBlue, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Surface(color = CharcoalSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Hiện tệp & thư mục ẩn", color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text("Hiển thị các tệp bắt đầu bằng dấu chấm (.)", color = TextMuted, fontSize = 12.sp)
                            }
                            Switch(
                                checked = showHidden,
                                onCheckedChange = { viewModel.toggleShowHiddenFiles() },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ElectricBlue)
                            )
                        }

                        HorizontalDivider(color = CharcoalSurfaceHighlight, modifier = Modifier.padding(vertical = 10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Chế độ xem dạng lưới (Grid)", color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text("Hiển thị tệp dưới dạng ô biểu tượng lớn", color = TextMuted, fontSize = 12.sp)
                            }
                            Switch(
                                checked = isGridView,
                                onCheckedChange = { viewModel.toggleGridView() },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ElectricBlue)
                            )
                        }
                    }
                }
            }

            // Storage Access Framework
            item {
                Text("QUYỀN LƯU TRỮ (STORAGE ACCESS FRAMEWORK)", style = MaterialTheme.typography.labelMedium, color = ElectricBlue, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Surface(color = CharcoalSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Ứng dụng tuân thủ nghiêm ngặt chính sách bảo mật của Android bằng Storage Access Framework (SAF). Không yêu cầu quyền Root, bảo vệ tuyệt đối dữ liệu người dùng.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { safLauncher.launch(null) },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue)
                        ) {
                            Icon(Icons.Default.FolderSpecial, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cấp quyền thư mục tùy chọn (SAF)")
                        }
                    }
                }
            }

            // About App
            item {
                Text("THÔNG TIN ỨNG DỤNG", style = MaterialTheme.typography.labelMedium, color = ElectricBlue, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Surface(color = CharcoalSurface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).background(ElectricBlue, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.FolderShared, contentDescription = null, tint = Color.White)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("FILE MANAGER PRO", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                                Text("Phiên bản 1.0.0 (Native Release)", color = ColorSuccessGreen, fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(color = CharcoalSurfaceHighlight, modifier = Modifier.padding(vertical = 4.dp))

                        Text("• Giao diện: Dark Mode than chì tối giản M3", color = TextSecondary, fontSize = 12.sp)
                        Text("• Phân tích APK: Manifest, CPU ABIs, DEX, Quyền, Chữ ký số", color = TextSecondary, fontSize = 12.sp)
                        Text("• SQLite Viewer: Phân trang lớn, console SQL SELECT chỉ đọc, xuất CSV/JSON", color = TextSecondary, fontSize = 12.sp)
                        Text("• Hex Editor: Đọc chunked streaming, tìm kiếm hex/text, nhảy offset", color = TextSecondary, fontSize = 12.sp)
                        Text("• Game Data: Nhận diện UnityFS, Unreal PAK, Dalvik DEX, Entropy mã hóa", color = TextSecondary, fontSize = 12.sp)
                        Text("• Tự động build: Tích hợp sẵn GitHub Actions CI/CD (.github/workflows/android.yml)", color = TextSecondary, fontSize = 12.sp)
                        Text("• An toàn: Hoạt động 100% offline, không gửi dữ liệu ra máy chủ", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
