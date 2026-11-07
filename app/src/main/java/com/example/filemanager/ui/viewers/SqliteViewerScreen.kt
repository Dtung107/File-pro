package com.example.filemanager.ui.viewers

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.filemanager.database.QueryResult
import com.example.filemanager.database.SqliteViewer
import com.example.filemanager.database.TableDataPage
import com.example.filemanager.database.TableSummary
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SqliteViewerScreen(
    dbFile: File,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var tableSummaries by remember { mutableStateOf<List<TableSummary>>(emptyList()) }
    var selectedTable by remember { mutableStateOf<TableSummary?>(null) }
    var tableDataPage by remember { mutableStateOf<TableDataPage?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isDataLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var currentPageIndex by remember { mutableIntStateOf(0) }
    var tableSearchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }

    var sqlQueryInput by remember { mutableStateOf("SELECT * FROM ") }
    var queryResult by remember { mutableStateOf<QueryResult?>(null) }
    var isQueryExecuting by remember { mutableStateOf(false) }

    var showTableMenu by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    fun loadPage(table: TableSummary, page: Int, search: String? = null) {
        isDataLoading = true
        coroutineScope.launch {
            val result = SqliteViewer.getTableData(
                dbFile = dbFile,
                tableName = table.name,
                pageIndex = page,
                pageSize = 50,
                searchQuery = if (search.isNullOrBlank()) null else search
            )
            if (result.isSuccess) {
                tableDataPage = result.getOrNull()
                currentPageIndex = page
            } else {
                snackbarHostState.showSnackbar("Lỗi tải trang dữ liệu: ${result.exceptionOrNull()?.message}")
            }
            isDataLoading = false
        }
    }

    LaunchedEffect(dbFile) {
        isLoading = true
        errorMessage = null
        val result = SqliteViewer.getDatabaseSummary(dbFile)
        if (result.isSuccess) {
            val summaries = result.getOrNull() ?: emptyList()
            tableSummaries = summaries
            if (summaries.isNotEmpty()) {
                val first = summaries.first()
                selectedTable = first
                sqlQueryInput = "SELECT * FROM \"${first.name}\" LIMIT 50"
                loadPage(first, 0)
            }
        } else {
            errorMessage = result.exceptionOrNull()?.message ?: "Lỗi khi mở cơ sở dữ liệu SQLite"
        }
        isLoading = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(dbFile.name, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = if (selectedTable != null) "Bảng: ${selectedTable?.name} (${selectedTable?.rowCount} bản ghi)" else "Không có bảng",
                            color = ElectricBlue,
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
                    // Table selector
                    Box {
                        TextButton(onClick = { showTableMenu = true }) {
                            Text(
                                text = selectedTable?.name?.take(14) ?: "Chọn bảng",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = TextPrimary)
                        }
                        DropdownMenu(
                            expanded = showTableMenu,
                            onDismissRequest = { showTableMenu = false },
                            modifier = Modifier.background(CharcoalSurfaceVariant)
                        ) {
                            tableSummaries.forEach { t ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(t.name, color = if (t == selectedTable) ElectricBlue else TextPrimary)
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text("${t.rowCount}", color = TextMuted)
                                        }
                                    },
                                    onClick = {
                                        selectedTable = t
                                        showTableMenu = false
                                        currentPageIndex = 0
                                        tableSearchQuery = ""
                                        sqlQueryInput = "SELECT * FROM \"${t.name}\" LIMIT 50"
                                        loadPage(t, 0)
                                    }
                                )
                            }
                        }
                    }

                    // Export menu
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Xuất dữ liệu", tint = TextPrimary)
                        }
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false },
                            modifier = Modifier.background(CharcoalSurfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Xuất bảng ra CSV", color = TextPrimary) },
                                onClick = {
                                    showExportMenu = false
                                    val t = selectedTable ?: return@DropdownMenuItem
                                    coroutineScope.launch {
                                        val parent = dbFile.parentFile ?: dbFile
                                        val outFile = File(parent, "${t.name}_export.csv")
                                        val res = SqliteViewer.exportTableToCsv(dbFile, t.name, outFile)
                                        if (res.isSuccess) {
                                            snackbarHostState.showSnackbar("Đã xuất ${res.getOrNull()} dòng ra: ${outFile.name}")
                                        } else {
                                            snackbarHostState.showSnackbar("Lỗi xuất CSV: ${res.exceptionOrNull()?.message}")
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Xuất bảng ra JSON", color = TextPrimary) },
                                onClick = {
                                    showExportMenu = false
                                    val t = selectedTable ?: return@DropdownMenuItem
                                    coroutineScope.launch {
                                        val parent = dbFile.parentFile ?: dbFile
                                        val outFile = File(parent, "${t.name}_export.json")
                                        val res = SqliteViewer.exportTableToJson(dbFile, t.name, outFile)
                                        if (res.isSuccess) {
                                            snackbarHostState.showSnackbar("Đã xuất ${res.getOrNull()} dòng ra: ${outFile.name}")
                                        } else {
                                            snackbarHostState.showSnackbar("Lỗi xuất JSON: ${res.exceptionOrNull()?.message}")
                                        }
                                    }
                                }
                            )
                        }
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
                CircularProgressIndicator(color = ElectricBlue)
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = errorMessage ?: "", color = ColorErrorRed)
            }
        } else if (tableSummaries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Cơ sở dữ liệu trống hoặc không có bảng người dùng", color = TextMuted)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = CharcoalSurfaceVariant,
                    contentColor = ElectricBlue
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Dữ liệu (${selectedTable?.rowCount ?: 0})", color = if (selectedTab == 0) ElectricBlue else TextSecondary) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Cấu trúc (Schema)", color = if (selectedTab == 1) ElectricBlue else TextSecondary) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("SQL Console", color = if (selectedTab == 2) ElectricBlue else TextSecondary) }
                    )
                }

                when (selectedTab) {
                    0 -> {
                        // Data View
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Search & Pagination controls
                            Surface(color = CharcoalSurface, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = tableSearchQuery,
                                        onValueChange = {
                                            tableSearchQuery = it
                                            selectedTable?.let { t -> loadPage(t, 0, it) }
                                        },
                                        placeholder = { Text("Tìm trong bảng...", color = TextMuted, fontSize = 13.sp) },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = ElectricBlue,
                                            unfocusedBorderColor = CharcoalSurfaceHighlight,
                                            focusedTextColor = TextPrimary,
                                            unfocusedTextColor = TextPrimary
                                        )
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Pagination buttons
                                    IconButton(
                                        onClick = {
                                            if (currentPageIndex > 0) {
                                                selectedTable?.let { t -> loadPage(t, currentPageIndex - 1, tableSearchQuery) }
                                            }
                                        },
                                        enabled = currentPageIndex > 0
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Trang trước", tint = if (currentPageIndex > 0) TextPrimary else TextMuted)
                                    }

                                    Text(
                                        text = "${currentPageIndex + 1}",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )

                                    IconButton(
                                        onClick = {
                                            val t = selectedTable
                                            if (t != null && (currentPageIndex + 1) * 50 < t.rowCount) {
                                                loadPage(t, currentPageIndex + 1, tableSearchQuery)
                                            }
                                        },
                                        enabled = selectedTable != null && ((currentPageIndex + 1) * 50 < (selectedTable?.rowCount ?: 0))
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Trang sau", tint = TextPrimary)
                                    }
                                }
                            }

                            if (isDataLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = ElectricBlue)
                                }
                            } else {
                                val page = tableDataPage
                                if (page == null || page.rows.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Không có dữ liệu trong bảng", color = TextMuted)
                                    }
                                } else {
                                    val hScroll = rememberScrollState()
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .horizontalScroll(hScroll)
                                    ) {
                                        LazyColumn(modifier = Modifier.fillMaxHeight()) {
                                            // Table Header row
                                            item {
                                                Row(
                                                    modifier = Modifier
                                                        .background(CharcoalSurfaceHighlight)
                                                        .padding(vertical = 10.dp)
                                                ) {
                                                    page.columns.forEach { colName ->
                                                        Box(
                                                            modifier = Modifier
                                                                .width(160.dp)
                                                                .padding(horizontal = 8.dp)
                                                        ) {
                                                            Text(
                                                                text = colName,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp,
                                                                color = ElectricBlue,
                                                                fontFamily = FontFamily.Monospace,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            // Rows
                                            items(page.rows) { row ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 8.dp)
                                                ) {
                                                    row.forEach { cellValue ->
                                                        Box(
                                                            modifier = Modifier
                                                                .width(160.dp)
                                                                .padding(horizontal = 8.dp)
                                                        ) {
                                                            Text(
                                                                text = cellValue,
                                                                fontSize = 12.sp,
                                                                color = if (cellValue == "NULL") TextMuted else TextPrimary,
                                                                fontFamily = FontFamily.Monospace,
                                                                maxLines = 2
                                                            )
                                                        }
                                                    }
                                                }
                                                HorizontalDivider(color = CharcoalSurfaceHighlight.copy(alpha = 0.5f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Schema View
                        val t = selectedTable
                        if (t != null) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    InfoSectionCard(title = "Cột dữ liệu (${t.columns.size})") {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            t.columns.forEach { col ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        if (col.isPrimaryKey) {
                                                            Icon(Icons.Default.VpnKey, contentDescription = "Khóa chính", tint = ColorWarningAmber, modifier = Modifier.size(16.dp))
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                        }
                                                        Text(col.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                                                    }
                                                    Text(
                                                        text = "${col.type} ${if (col.notNull) "NOT NULL" else ""}",
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 12.sp,
                                                        color = CyanAccent
                                                    )
                                                }
                                                HorizontalDivider(color = CharcoalSurfaceHighlight)
                                            }
                                        }
                                    }
                                }

                                item {
                                    InfoSectionCard(title = "Câu lệnh CREATE TABLE SQL") {
                                        Text(
                                            text = t.sqlCreate,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = TextSecondary,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // SQL Console View
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            OutlinedTextField(
                                value = sqlQueryInput,
                                onValueChange = { sqlQueryInput = it },
                                label = { Text("Câu lệnh SQL (Chỉ đọc SELECT / PRAGMA)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp),
                                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = TextPrimary),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ElectricBlue,
                                    unfocusedBorderColor = CharcoalSurfaceHighlight,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    isQueryExecuting = true
                                    coroutineScope.launch {
                                        queryResult = SqliteViewer.executeSelectQuery(dbFile, sqlQueryInput)
                                        isQueryExecuting = false
                                    }
                                },
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricBlue),
                                enabled = !isQueryExecuting
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Chạy truy vấn")
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            if (isQueryExecuting) {
                                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = ElectricBlue)
                                }
                            } else {
                                val res = queryResult
                                if (res != null) {
                                    if (res.errorMessage != null) {
                                        Surface(
                                            color = CharcoalSurface,
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = res.errorMessage,
                                                color = ColorErrorRed,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    } else {
                                        Text(
                                            text = "Kết quả: ${res.rowCount} dòng (Thời gian: ${res.executionTimeMs} ms)",
                                            color = ColorSuccessGreen,
                                            fontSize = 12.sp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))

                                        val hScroll = rememberScrollState()
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .horizontalScroll(hScroll)
                                        ) {
                                            LazyColumn(modifier = Modifier.fillMaxHeight()) {
                                                item {
                                                    Row(
                                                        modifier = Modifier
                                                            .background(CharcoalSurfaceHighlight)
                                                            .padding(vertical = 8.dp)
                                                    ) {
                                                        res.columns.forEach { colName ->
                                                            Box(modifier = Modifier.width(140.dp).padding(horizontal = 6.dp)) {
                                                                Text(colName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ElectricBlue, fontFamily = FontFamily.Monospace)
                                                            }
                                                        }
                                                    }
                                                }

                                                items(res.rows) { row ->
                                                    Row(modifier = Modifier.padding(vertical = 6.dp)) {
                                                        row.forEach { cell ->
                                                            Box(modifier = Modifier.width(140.dp).padding(horizontal = 6.dp)) {
                                                                Text(cell, fontSize = 12.sp, color = TextPrimary, fontFamily = FontFamily.Monospace, maxLines = 1)
                                                            }
                                                        }
                                                    }
                                                    HorizontalDivider(color = CharcoalSurfaceHighlight.copy(alpha = 0.4f))
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
    }
}
