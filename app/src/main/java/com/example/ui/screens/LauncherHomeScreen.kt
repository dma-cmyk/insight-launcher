package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.AppInfo
import com.example.data.InstalledApp
import com.example.data.getParsedLinks
import com.example.data.getParsedTags
import com.example.ui.AppLauncherViewModel
import com.example.ui.Localization
import com.example.ui.components.AppIconImage

fun getCategoryDisplayName(category: String, lang: String): String {
    return when (category) {
        "すべて" -> Localization.get("all", lang)
        "未解析" -> Localization.get("unanalyzed", lang)
        "未解析 (AI分類待ち)" -> Localization.get("waiting_ai", lang)
        else -> category
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherHomeScreen(
    viewModel: AppLauncherViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onScrollOffsetChanged: (Float, Float) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val apps by viewModel.appListState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val analysisProgress by viewModel.analysisProgress.collectAsState()
    val analysisProgressPercent by viewModel.analysisProgressPercent.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()
    val aiLanguage by viewModel.settingsManager.aiLanguage.collectAsState()

    // Dynamic distinct categories in the current list
    val categories = remember(apps) {
        val uniqueCats = apps.map { it.cachedInfo?.category ?: "未解析" }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        listOf("すべて") + uniqueCats
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { categories.size }
    )

    val lazyListStates = remember(categories) {
        List(categories.size) { LazyListState() }
    }

    var selectedCategoryFilter by remember { mutableStateOf("すべて") }
    val coroutineScope = rememberCoroutineScope()

    // Synchronize horizontal and vertical page scrolls with background parallax offset
    LaunchedEffect(pagerState, lazyListStates) {
        snapshotFlow {
            val page = pagerState.currentPage
            val listState = if (page in lazyListStates.indices) lazyListStates[page] else null
            val yOffset = if (listState != null) {
                listState.firstVisibleItemIndex * 400f + listState.firstVisibleItemScrollOffset
            } else {
                0f
            }
            Pair(pagerState.currentPage + pagerState.currentPageOffsetFraction, yOffset)
        }.collect { (x, y) ->
            onScrollOffsetChanged(x, y / 1000f)
        }
    }

    // Synchronize pager page selection with category selection state
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage in categories.indices) {
            selectedCategoryFilter = categories[pagerState.currentPage]
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xD002040A))
                    .border(BorderStroke(1.dp, Color(0x0DFFFFFF)), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column {
                            Text("LLM ENGINE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0x66FFFFFF), letterSpacing = 1.sp)
                            Text(viewModel.settingsManager.getPrimaryModel().substringAfterLast("/"), fontSize = 12.sp, color = Color(0xFF60A5FA), fontWeight = FontWeight.SemiBold)
                        }
                        Column {
                            Text("EMBEDDING", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0x66FFFFFF), letterSpacing = 1.sp)
                            Text(viewModel.settingsManager.getEmbeddingModel().substringAfterLast("/"), fontSize = 12.sp, color = Color(0xFF60A5FA), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x0DFFFFFF))
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xB2FFFFFF))
                    }
                }
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(3.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                )
            }
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 1. High Density Capsule Search Bar & Layout Switchers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text(Localization.get("search_placeholder", aiLanguage), color = Color(0x80FFFFFF), fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = Localization.get("search_icon_desc", aiLanguage), tint = Color(0x99FFFFFF)) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                    Icon(Icons.Default.Close, contentDescription = Localization.get("clear_btn_desc", aiLanguage), tint = Color.White)
                                }
                            }
                        },
                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0x20FFFFFF),
                            unfocusedBorderColor = Color(0x10FFFFFF),
                            focusedContainerColor = Color(0x15FFFFFF),
                            unfocusedContainerColor = Color(0x10FFFFFF)
                        ),
                        shape = RoundedCornerShape(50),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("app_search_input")
                    )

                    // Grid / List mode toggle buttons with high contrast matching High Density theme
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x0DFFFFFF))
                            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(50))
                    ) {
                        IconButton(
                            onClick = { viewModel.setViewMode("GRID") },
                            modifier = Modifier
                                .background(if (viewMode == "GRID") Color(0x1A42A5F5) else Color.Transparent)
                                .testTag("toggle_grid_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridView,
                                contentDescription = "グリッド表示",
                                tint = if (viewMode == "GRID") Color(0xFF90CAF9) else Color(0x80FFFFFF)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.setViewMode("LIST") },
                            modifier = Modifier
                                .background(if (viewMode == "LIST") Color(0x1A42A5F5) else Color.Transparent)
                                .testTag("toggle_list_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.List,
                                contentDescription = "リスト表示",
                                tint = if (viewMode == "LIST") Color(0xFF90CAF9) else Color(0x80FFFFFF)
                            )
                        }
                    }
                }

                // High Density Interactive Category Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { category ->
                        val isSelected = selectedCategoryFilter == category
                        val isSystem = category == "System" || category == "システム" || category == "ツール" || category == "Tools"
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected) Color(0x2B3B82F6) // blue-500/17
                                    else Color(0x0DFFFFFF) // white/5
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) Color(0x4D3B82F6) // blue-500/30
                                            else Color(0x08FFFFFF), // white/3
                                    shape = RoundedCornerShape(50)
                                )
                                .clickable {
                                    selectedCategoryFilter = category
                                    coroutineScope.launch {
                                        val targetIdx = categories.indexOf(category)
                                        if (targetIdx >= 0) {
                                            pagerState.animateScrollToPage(targetIdx)
                                        }
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = getCategoryDisplayName(category, aiLanguage),
                                color = if (isSelected) Color(0xFF93C5FD) // blue-300
                                        else Color(0x99FFFFFF), // gray-400
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 2. Quick Batch Analysis Ribbon if unanalyzed apps exist
                val hasUnanalyzed = remember(apps) { apps.any { !it.isAnalyzed } }
                if (hasUnanalyzed && !isAnalyzing) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x2B81C784)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .border(1.dp, Color(0x3381C784), RoundedCornerShape(12.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(Localization.get("unanalyzed_ribbon_title", aiLanguage), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(Localization.get("unanalyzed_ribbon_desc", aiLanguage), color = Color(0xCCFFFFFF), fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.autoAnalyzeAllUnanalyzed() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784)),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("batch_analyze_button")
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = "一括解析", modifier = Modifier.size(16.dp), tint = Color.Black)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(Localization.get("auto_analyze_all", aiLanguage), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 3. Batch Analysis Progress Bar
                if (isAnalyzing) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xCC0E0E1B)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .border(1.dp, Color(0x3D81C784), RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = Color(0xFF81C784),
                                        strokeWidth = 2.dp
                                    )
                                    Text(Localization.get("running_analysis", aiLanguage), color = Color(0xFF81C784), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                
                                // Cancel/Stop button to gracefully abort long-running analysis
                                TextButton(
                                    onClick = { viewModel.cancelAutoAnalysis() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("cancel_analyze_button")
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "解析中止", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(Localization.get("cancel_btn", aiLanguage), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = analysisProgress,
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            LinearProgressIndicator(
                                progress = analysisProgressPercent,
                                modifier = Modifier.fillMaxWidth().clip(CircleShape),
                                color = Color(0xFF81C784),
                                trackColor = Color(0x33FFFFFF)
                            )
                        }
                    }
                }

                // 4. Main Category App list
                if (apps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Android, contentDescription = Localization.get("no_apps", aiLanguage), modifier = Modifier.size(48.dp), tint = Color.Gray)
                            Text(Localization.get("no_apps", aiLanguage), color = Color.Gray, fontSize = 14.sp)
                        }
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { page ->
                        val currentCategory = categories[page]
                        val pageApps = remember(apps, currentCategory) {
                            if (currentCategory == "すべて") {
                                apps
                            } else {
                                apps.filter { (it.cachedInfo?.category ?: "未解析") == currentCategory }
                            }
                        }

                        val pageGroupedApps = remember(pageApps) {
                            pageApps.groupBy { it.cachedInfo?.category ?: "未解析 (AI分類待ち)" }
                        }

                        val listState = remember(page, lazyListStates) {
                            if (page in lazyListStates.indices) lazyListStates[page] else LazyListState()
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            pageGroupedApps.forEach { (category, categoryApps) ->
                                // Header segment
                                item {
                                    CategoryHeader(category = category, count = categoryApps.size, lang = aiLanguage)
                                }

                                if (viewMode == "GRID") {
                                    // Chunk apps into rows of 3
                                    val rows = categoryApps.chunked(3)
                                    items(rows) { rowApps ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            for (i in 0 until 3) {
                                                if (i < rowApps.size) {
                                                    AppGridItem(
                                                        app = rowApps[i],
                                                        onClick = { viewModel.selectApp(rowApps[i]) },
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    items(categoryApps) { app ->
                                        AppListItem(
                                            app = app,
                                            onClick = { viewModel.selectApp(app) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
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

    // 5. App Details Interactive Dialog
    selectedApp?.let { app ->
        AppDetailsDialog(
            app = app,
            onDismiss = { viewModel.selectApp(null) },
            onLaunch = {
                viewModel.launchApp(app.packageName)
                viewModel.selectApp(null)
            },
            onAnalyze = { text, fileName, mimeType, bytes ->
                viewModel.analyzeSingleApp(app, text, fileName, mimeType, bytes)
            },
            isAnalyzing = isAnalyzing,
            apps = apps,
            onSelectApp = { viewModel.selectApp(it) },
            lang = aiLanguage
        )
    }
}

@Composable
fun CategoryHeader(category: String, count: Int, lang: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, top = 18.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 16.dp)
                .clip(CircleShape)
                .background(Color(0xFF60A5FA))
        )
        Text(
            text = getCategoryDisplayName(category, lang),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 15.sp,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x0DFFFFFF))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = String.format(Localization.get("items_count", lang), count),
                color = Color(0x80FFFFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun AppGridItem(
    app: InstalledApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x11FFFFFF)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("app_grid_item_${app.packageName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppIconImage(packageName = app.packageName, size = 48.dp)

            Text(
                text = app.label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            if (app.isAnalyzed) {
                // Highlight tags count or status
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x3081C784))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "AI解析済",
                        color = Color(0xFF81C784),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x24FFFFFF))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "未解析",
                        color = Color(0xB2FFFFFF),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

@Composable
fun AppListItem(
    app: InstalledApp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x11FFFFFF)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("app_list_item_${app.packageName}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppIconImage(packageName = app.packageName, size = 44.dp)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (app.isAnalyzed) {
                    Text(
                        text = app.cachedInfo?.summary ?: "",
                        color = Color(0xCCFFFFFF),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "タップしてAI解析を実行",
                        color = Color(0x80FFFFFF),
                        fontSize = 11.sp
                    )
                }
            }

            if (app.isAnalyzed) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Analyzed",
                    tint = Color(0xFF81C784),
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Details",
                    tint = Color(0x4DFFFFFF),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppDetailsDialog(
    app: InstalledApp,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onAnalyze: (userContextText: String?, fileName: String?, fileMimeType: String?, fileBytes: ByteArray?) -> Unit,
    isAnalyzing: Boolean,
    apps: List<InstalledApp>,
    onSelectApp: (InstalledApp) -> Unit,
    lang: String
) {
    val context = LocalContext.current

    var customContextText by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedFileMimeType by remember { mutableStateOf<String?>(null) }
    var selectedFileBytes by remember { mutableStateOf<ByteArray?>(null) }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri)
            var fileName = "selected_file"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }
            try {
                val bytes = contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes()
                }
                if (bytes != null) {
                    selectedFileName = fileName
                    selectedFileMimeType = mimeType
                    selectedFileBytes = bytes
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Look up related suggestions in the same category
    val relatedApps = remember(apps, app) {
        val cat = app.cachedInfo?.category ?: ""
        if (cat.isBlank()) emptyList()
        else {
            apps.filter {
                it.packageName != app.packageName &&
                (it.cachedInfo?.category ?: "") == cat
            }.take(3)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xF0080812))
                .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(28.dp))
                .padding(22.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header Segment
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x0AFFFFFF))
                            .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(16.dp))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AppIconImage(packageName = app.packageName, size = 52.dp)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.label,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = app.packageName,
                            color = Color(0xFF60A5FA),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x0DFFFFFF))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = Localization.get("close_btn", lang), tint = Color.White)
                    }
                }

                HorizontalDivider(color = Color(0x14FFFFFF))

                if (app.isAnalyzed && app.cachedInfo != null) {
                    val info = app.cachedInfo

                    // 1. AI Summary Section in Enclosed Dark Panel
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x22000000))
                            .border(1.dp, Color(0x08FFFFFF), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "AI Summary",
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = Localization.get("ai_summary_title", lang).uppercase(),
                                color = Color(0xFF60A5FA),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = info.summary,
                            color = Color(0xFFE2E8F0),
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    }

                    // 2. Minimum 5 Tags Section (Italic design hashtag tags)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = Localization.get("tags_label", lang).uppercase() + " (AI)",
                            color = Color(0x80FFFFFF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 1.sp
                        )
                        val tags = info.getParsedTags()
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            tags.forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x0DFFFFFF))
                                        .border(0.5.dp, Color(0x10FFFFFF), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "#$tag",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            }
                        }
                    }

                    // 3. Related Suggestions Section (Dynamic AI Recommendations matching category)
                    if (relatedApps.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = Localization.get("related_suggestions", lang).uppercase(),
                                color = Color(0x80FFFFFF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                relatedApps.forEach { relApp ->
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0x05FFFFFF))
                                            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(12.dp))
                                            .clickable { onSelectApp(relApp) }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        AppIconImage(packageName = relApp.packageName, size = 26.dp)
                                        Text(
                                            text = relApp.label,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Minimum 3 Related Links Section
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Link, contentDescription = "Links", tint = Color(0xFF34D399), modifier = Modifier.size(14.dp))
                            Text(
                                text = Localization.get("related_links_label", lang).uppercase(),
                                color = Color(0xFF34D399),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }
                        val links = info.getParsedLinks()
                        if (links.isEmpty()) {
                            Text(Localization.get("no_links", lang), color = Color.Gray, fontSize = 12.sp)
                        } else {
                            links.forEach { link ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x0DFFFFFF))
                                        .border(1.dp, Color(0x08FFFFFF), RoundedCornerShape(12.dp))
                                        .clickable {
                                            try {
                                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(browserIntent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, String.format(Localization.get("link_error", lang), e.message ?: ""), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = Color(0xFF34D399), modifier = Modifier.size(12.dp))
                                        Text(
                                            text = link.title,
                                            color = Color.White,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "EXPLORE",
                                        color = Color(0x80FFFFFF),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }

                } else {
                    // Unanalyzed State UI
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x06FFFFFF), RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0x0DFFFFFF), RoundedCornerShape(16.dp))
                            .padding(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Auto analysis", tint = Color(0x66FFFFFF), modifier = Modifier.size(32.dp))
                            Text(Localization.get("no_ai_details_title", lang), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(Localization.get("no_ai_details_desc", lang), color = Color(0x99FFFFFF), fontSize = 11.sp, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    onAnalyze(customContextText, selectedFileName, selectedFileMimeType, selectedFileBytes)
                                },
                                enabled = !isAnalyzing,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                                modifier = Modifier.testTag("analyze_single_button")
                            ) {
                                Text(Localization.get("analyze_btn", lang), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Correction Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x0AFFFFFF), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = Localization.get("correction_context_title", lang),
                            color = Color(0xFF60A5FA),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        IconButton(
                            onClick = {
                                try {
                                    fileLauncher.launch("*/*")
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Picker error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AttachFile,
                                contentDescription = Localization.get("choose_file_btn", lang),
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = customContextText,
                        onValueChange = { customContextText = it },
                        placeholder = {
                            Text(
                                text = Localization.get("correction_text_placeholder", lang),
                                color = Color(0x66FFFFFF),
                                fontSize = 11.sp
                            )
                        },
                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 11.sp),
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .testTag("correction_context_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF60A5FA),
                            unfocusedBorderColor = Color(0x20FFFFFF),
                            focusedContainerColor = Color(0x08FFFFFF),
                            unfocusedContainerColor = Color.Transparent
                        )
                    )

                    selectedFileName?.let { fileName ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x0F34D399), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0x2034D399), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                val isImage = selectedFileMimeType?.startsWith("image/") == true
                                Icon(
                                    imageVector = if (isImage) Icons.Default.Image else Icons.Default.Description,
                                    contentDescription = "File Type",
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = String.format(Localization.get("selected_file_label", lang), fileName),
                                    color = Color(0xFF34D399),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    selectedFileName = null
                                    selectedFileMimeType = null
                                    selectedFileBytes = null
                                },
                                modifier = Modifier.size(20.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear file",
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 4. Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onLaunch,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("launch_app_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Launch", tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(Localization.get("open_app", lang).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 0.5.sp)
                    }

                    if (app.isAnalyzed) {
                        // Re-analyze option
                        OutlinedButton(
                            onClick = {
                                onAnalyze(customContextText, selectedFileName, selectedFileMimeType, selectedFileBytes)
                            },
                            enabled = !isAnalyzing,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, Color(0x14FFFFFF)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Re-analyze", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(Localization.get("re_analyze", lang).uppercase(), fontSize = 11.sp, letterSpacing = 0.5.sp)
                        }
                    }
                }
            }
        }
    }
}
