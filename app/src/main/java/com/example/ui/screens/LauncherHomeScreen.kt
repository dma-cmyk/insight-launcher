package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.widget.Toast
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.components.AppIconImage
import com.example.data.UsageTracker
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import java.util.Collections
import kotlin.math.roundToInt

fun getCategoryDisplayName(category: String, lang: String): String {
    return when (category) {
        "すべて" -> Localization.get("all", lang)
        "未解析" -> Localization.get("unanalyzed", lang)
        "未解析 (AI分類待ち)" -> Localization.get("waiting_ai", lang)
        "RECENT" -> Localization.get("category_recently_used", lang)
        "MOST_USED" -> Localization.get("category_most_used", lang)
        "FAVORITE" -> Localization.get("category_favorite", lang)
        else -> category
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherHomeScreen(
    viewModel: AppLauncherViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToAiAssistant: () -> Unit,
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
    val lastLaunchTimes by viewModel.lastLaunchTimes.collectAsState()
    val launchCounts by viewModel.launchCounts.collectAsState()
    val favorites by viewModel.favorites.collectAsState()

    val isVectorSearchEnabled by viewModel.isVectorSearchEnabled.collectAsState()
    val isVectorSearching by viewModel.isVectorSearching.collectAsState()
    val isCorrectingVoice by viewModel.isCorrectingVoice.collectAsState()

    var isSearchExpanded by remember { mutableStateOf(false) }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                viewModel.processVoiceInput(spokenText)
            }
        }
    }

    // Dynamic distinct categories in the current list
    val categories = remember(apps) {
        val uniqueCats = apps.map { it.cachedInfo?.category ?: "未解析" }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        listOf("すべて", "FAVORITE", "RECENT", "MOST_USED") + uniqueCats
    }

    val totalPageCount = 100000
    val pagerState = rememberPagerState(
        initialPage = remember(categories) {
            if (categories.isNotEmpty()) {
                val mid = totalPageCount / 2
                mid - (mid % categories.size)
            } else {
                0
            }
        },
        pageCount = { if (categories.isNotEmpty()) totalPageCount else 1 }
    )

    val lazyListStates = remember(categories) {
        List(categories.size) { LazyListState() }
    }

    var selectedCategoryFilter by remember { mutableStateOf("すべて") }
    val categoryListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Synchronize horizontal and vertical page scrolls with background parallax offset
    LaunchedEffect(pagerState, lazyListStates) {
        snapshotFlow {
            val page = pagerState.currentPage
            val realPage = if (categories.isNotEmpty()) page % categories.size else 0
            val listState = if (realPage in lazyListStates.indices) lazyListStates[realPage] else null
            val yOffset = if (listState != null) {
                listState.firstVisibleItemIndex * 400f + listState.firstVisibleItemScrollOffset
            } else {
                0f
            }
            Pair(realPage + pagerState.currentPageOffsetFraction, yOffset)
        }.collect { (x, y) ->
            onScrollOffsetChanged(x, y / 1000f)
        }
    }

    // Synchronize pager page selection with category selection state
    LaunchedEffect(pagerState.currentPage) {
        if (categories.isNotEmpty()) {
            val realPage = pagerState.currentPage % categories.size
            if (realPage in categories.indices) {
                selectedCategoryFilter = categories[realPage]
                try {
                    categoryListState.animateScrollToItem(realPage)
                } catch (e: Exception) {
                    // Ignore layout/animation exceptions
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Floating pull-out tab & Swipe detector on the right edge to open AI Assistant
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(40.dp)
                    .align(Alignment.CenterEnd)
                    .zIndex(50f)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { change, dragAmount ->
                            // Left swipe from right edge
                            if (dragAmount < -8f) {
                                onNavigateToAiAssistant()
                            }
                        }
                    }
            ) {
                // Sleek pull tab indicating AI pull-out assistant
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .background(Color(0x3342A5F5)) // transparent blue theme
                        .border(1.dp, Color(0x6690CAF9), RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                        .clickable { onNavigateToAiAssistant() }
                        .padding(horizontal = 4.dp, vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF90CAF9),
                            modifier = Modifier.size(14.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = if (aiLanguage == "ja") "スワイプまたはタップでAIアシスタントを開く" else "Swipe left or tap to open AI Assistant",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // 1. High Density Capsule Search Bar & Layout Switchers
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0x15FFFFFF))
                            .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(24.dp))
                            .animateContentSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = if (isSearchExpanded) Alignment.Top else Alignment.CenterVertically
                            ) {
                                if (isVectorSearching) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp).padding(top = if(isSearchExpanded) 2.dp else 0.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF90CAF9)
                                    )
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = Localization.get("search_icon_desc", aiLanguage), tint = Color(0x99FFFFFF), modifier = Modifier.size(20.dp).padding(top = if(isSearchExpanded) 2.dp else 0.dp))
                                }
                                
                                Spacer(modifier = Modifier.width(12.dp))

                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 14.sp),
                                    singleLine = !isSearchExpanded,
                                    maxLines = if (isSearchExpanded) 10 else 1,
                                    cursorBrush = SolidColor(Color.White),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("app_search_input"),
                                    decorationBox = { innerTextField ->
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            if (searchQuery.isEmpty()) {
                                                Text(Localization.get("search_placeholder", aiLanguage), color = Color(0x80FFFFFF), fontSize = 14.sp)
                                            }
                                            innerTextField()
                                        }
                                    }
                                )

                                if (!isSearchExpanded) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = Localization.get("clear_btn_desc", aiLanguage), tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        IconButton(onClick = { isSearchExpanded = true }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Expand Search", tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }

                            if (isSearchExpanded) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Left: Voice Search & Vector Search
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Vector Search Button
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f, fill = false)
                                                .clip(RoundedCornerShape(50))
                                                .clickable { viewModel.executeVectorSearch() }
                                                .background(if (isVectorSearchEnabled) Color(0x3342A5F5) else Color(0x1AFFFFFF))
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoAwesome,
                                                contentDescription = Localization.get("vector_search_label", aiLanguage),
                                                tint = if (isVectorSearchEnabled) Color(0xFF90CAF9) else Color(0x80FFFFFF),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = Localization.get("vector_search_label", aiLanguage),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                softWrap = false,
                                                color = if (isVectorSearchEnabled) Color(0xFF90CAF9) else Color(0xB3FFFFFF)
                                            )
                                        }

                                        // Voice Search
                                        if (isCorrectingVoice) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = Color(0xFF90CAF9)
                                            )
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, aiLanguage)
                                                        putExtra(RecognizerIntent.EXTRA_PROMPT, Localization.get("voice_search_prompt", aiLanguage))
                                                    }
                                                    try {
                                                        speechRecognizerLauncher.launch(intent)
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, Localization.get("voice_unsupported", aiLanguage), Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                                modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0x20FFFFFF))
                                            ) {
                                                Icon(Icons.Default.Mic, contentDescription = "Voice Search", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Right: Clear and Collapse
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { viewModel.onSearchQueryChanged("") },
                                                modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0x20FFFFFF))
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        IconButton(
                                            onClick = { isSearchExpanded = false },
                                            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0x20FFFFFF))
                                        ) {
                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Collapse Search", tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Settings button in a compact header style next to the search bar
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0x15FFFFFF))
                            .border(1.dp, Color(0x20FFFFFF), CircleShape)
                            .testTag("navigate_to_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // High Density Interactive Category Filter Chips
                LazyRow(
                    state = categoryListState,
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
                                        if (targetIdx >= 0 && categories.isNotEmpty()) {
                                            val size = categories.size
                                            val currentAbsPage = pagerState.currentPage
                                            val currentRealPage = currentAbsPage % size
                                            var diff = (targetIdx - currentRealPage) % size
                                            if (diff > size / 2) {
                                                diff -= size
                                            } else if (diff < -size / 2) {
                                                diff += size
                                            }
                                            val targetAbsPage = (currentAbsPage + diff).coerceIn(0, totalPageCount - 1)
                                            pagerState.animateScrollToPage(targetAbsPage)
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
                } else if (isVectorSearchEnabled && searchQuery.isNotBlank()) {
                    // Vector Search View: flat list globally sorted by similarity score descending
                    val vectorListState = rememberLazyListState()
                    LazyColumn(
                        state = vectorListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        item {
                            Text(
                                text = "${Localization.get("vector_search_label", aiLanguage)} (${apps.size} 件)",
                                color = Color(0xFF90CAF9),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        if (viewMode == "GRID") {
                            val rows = apps.chunked(3)
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
                                                aiLanguage = aiLanguage,
                                                modifier = Modifier.weight(1f)
                                            )
                                        } else {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        } else {
                            items(apps) { app ->
                                AppListItem(
                                    app = app,
                                    onClick = { viewModel.selectApp(app) },
                                    aiLanguage = aiLanguage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { absPage ->
                        val page = if (categories.isNotEmpty()) absPage % categories.size else 0
                        val currentCategory = categories[page]
                        val pageApps = remember(apps, currentCategory, lastLaunchTimes, launchCounts, favorites) {
                            when (currentCategory) {
                                "すべて" -> apps
                                "FAVORITE" -> {
                                    apps.filter { favorites.contains(it.packageName) }
                                        .sortedBy { favorites.indexOf(it.packageName) }
                                }
                                "RECENT" -> {
                                    apps.filter { (lastLaunchTimes[it.packageName] ?: 0L) > 0L }
                                        .sortedByDescending { lastLaunchTimes[it.packageName] ?: 0L }
                                }
                                "MOST_USED" -> {
                                    apps.filter { (launchCounts[it.packageName] ?: 0) > 0 }
                                        .sortedByDescending { launchCounts[it.packageName] ?: 0 }
                                }
                                else -> {
                                    apps.filter { (it.cachedInfo?.category ?: "未解析") == currentCategory }
                                }
                            }
                        }

                        val pageGroupedApps = remember(pageApps, currentCategory) {
                            if (currentCategory == "RECENT" || currentCategory == "MOST_USED" || currentCategory == "FAVORITE") {
                                mapOf(currentCategory to pageApps)
                            } else {
                                pageApps.groupBy { it.cachedInfo?.category ?: "未解析 (AI分類待ち)" }
                            }
                        }

                        val listState = remember(page, lazyListStates) {
                            if (page in lazyListStates.indices) lazyListStates[page] else LazyListState()
                        }

                        if (currentCategory == "FAVORITE") {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                CategoryHeader(category = currentCategory, count = pageApps.size, lang = aiLanguage)

                                if (pageApps.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight(0.8f)
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(32.dp).padding(top = 80.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                modifier = Modifier.size(48.dp),
                                                tint = Color.Gray
                                            )
                                            Text(
                                                text = Localization.get("no_favorites", aiLanguage),
                                                color = Color.Gray,
                                                fontSize = 14.sp,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                } else {
                                    FavoriteReorderableContent(
                                        pageApps = pageApps,
                                        favorites = favorites,
                                        viewMode = viewMode,
                                        aiLanguage = aiLanguage,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                if (pageApps.isEmpty() && (currentCategory == "RECENT" || currentCategory == "MOST_USED")) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillParentMaxHeight(0.8f)
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                                modifier = Modifier.padding(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = when (currentCategory) {
                                                        "RECENT" -> Icons.Default.History
                                                        else -> Icons.Default.TrendingUp
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(48.dp),
                                                    tint = Color.Gray
                                                )
                                                Text(
                                                    text = when (currentCategory) {
                                                        "RECENT" -> Localization.get("no_recently_used", aiLanguage)
                                                        else -> Localization.get("no_most_used", aiLanguage)
                                                    },
                                                    color = Color.Gray,
                                                    fontSize = 14.sp,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }

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
                                                            aiLanguage = aiLanguage,
                                                            modifier = Modifier.weight(1f),
                                                            isFavorite = favorites.contains(rowApps[i].packageName)
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
                                                aiLanguage = aiLanguage,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                                isFavorite = favorites.contains(app.packageName)
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

    // 5. App Details Interactive Dialog
    selectedApp?.let { app ->
        AppDetailsDialog(
            app = app,
            onDismiss = { viewModel.selectApp(null) },
            onLaunch = {
                viewModel.launchApp(app.packageName)
                viewModel.selectApp(null)
            },
            onOpenSettings = {
                viewModel.openAppSettings(app.packageName)
            },
            onAnalyze = { text, fileName, mimeType, bytes ->
                viewModel.analyzeSingleApp(app, text, fileName, mimeType, bytes)
            },
            isAnalyzing = isAnalyzing,
            apps = apps,
            onSelectApp = { viewModel.selectApp(it) },
            lang = aiLanguage,
            isFavorite = favorites.contains(app.packageName),
            onToggleFavorite = { viewModel.toggleFavorite(app.packageName) }
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
    aiLanguage: String,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x11FFFFFF)),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .height(160.dp)
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("app_grid_item_${app.packageName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier.padding(top = 2.dp)
            ) {
                AppIconImage(packageName = app.packageName, size = 48.dp)
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = app.label,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                if (app.similarityScore != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (app.isAnalyzed) Color(0x2642A5F5) else Color(0x26FFA726))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = String.format(java.util.Locale.getDefault(), Localization.get("similarity_label", aiLanguage), app.similarityScore),
                                color = if (app.isAnalyzed) Color(0xFF90CAF9) else Color(0xFFFFCC80),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (!app.isAnalyzed) {
                            Text(
                                text = Localization.get("ai_unanalyzed_badge", aiLanguage),
                                color = Color(0xFFFFB74D),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else if (app.isAnalyzed) {
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
}

@Composable
fun AppListItem(
    app: InstalledApp,
    onClick: () -> Unit,
    aiLanguage: String,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false
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
            Box {
                AppIconImage(packageName = app.packageName, size = 44.dp)
                if (isFavorite) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Favorite",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-3).dp)
                    )
                }
            }

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
                    val promptText = if (aiLanguage == "en") "Tap to perform AI analysis" else "タップしてAI解析を実行"
                    Text(
                        text = promptText,
                        color = Color(0x80FFFFFF),
                        fontSize = 11.sp
                    )
                }
            }

            if (app.similarityScore != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (app.isAnalyzed) Color(0x2642A5F5) else Color(0x26FFA726))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = String.format(java.util.Locale.getDefault(), Localization.get("similarity_label", aiLanguage), app.similarityScore),
                            color = if (app.isAnalyzed) Color(0xFF90CAF9) else Color(0xFFFFCC80),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!app.isAnalyzed) {
                        Text(
                            text = Localization.get("ai_unanalyzed_badge", aiLanguage),
                            color = Color(0xFFFFB74D),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (app.isAnalyzed) {
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

@Composable
fun FavoriteReorderableContent(
    pageApps: List<InstalledApp>,
    favorites: List<String>,
    viewMode: String,
    aiLanguage: String,
    viewModel: AppLauncherViewModel
) {
    var dragList by remember(pageApps) { mutableStateOf(pageApps) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val itemPositions = remember { mutableStateMapOf<Int, Rect>() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(dragList) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        val matchedIndex = itemPositions.entries.firstOrNull { (_, rect) ->
                            rect.contains(startOffset)
                        }?.key
                        if (matchedIndex != null && matchedIndex < dragList.size) {
                            draggingIndex = matchedIndex
                            dragOffset = Offset.Zero
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (draggingIndex != null) {
                            dragOffset += dragAmount
                            val originalBounds = itemPositions[draggingIndex]
                            if (originalBounds != null) {
                                val currentCenter = originalBounds.center + dragOffset
                                val targetIndex = itemPositions.entries.firstOrNull { (index, bounds) ->
                                    index != draggingIndex && bounds.contains(currentCenter)
                                }?.key
                                if (targetIndex != null && targetIndex < dragList.size) {
                                    val mutable = dragList.toMutableList()
                                    Collections.swap(mutable, draggingIndex!!, targetIndex)
                                    dragList = mutable

                                    val targetBounds = itemPositions[targetIndex]
                                    if (targetBounds != null) {
                                        dragOffset += originalBounds.topLeft - targetBounds.topLeft
                                    }
                                    draggingIndex = targetIndex
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        draggingIndex?.let {
                            viewModel.saveFavoritesOrder(dragList.map { it.packageName })
                        }
                        draggingIndex = null
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                        draggingIndex?.let {
                            viewModel.saveFavoritesOrder(dragList.map { it.packageName })
                        }
                        draggingIndex = null
                        dragOffset = Offset.Zero
                    }
                )
            }
            .padding(bottom = 80.dp)
    ) {
        if (viewMode == "GRID") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val chunks = dragList.chunked(3)
                chunks.forEachIndexed { rowIndex, rowApps ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (i in 0 until 3) {
                            val itemIndex = rowIndex * 3 + i
                            if (i < rowApps.size && itemIndex < dragList.size) {
                                val app = dragList[itemIndex]
                                val isCurrentDragging = draggingIndex == itemIndex
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .onGloballyPositioned { coords ->
                                            itemPositions[itemIndex] = coords.boundsInParent()
                                        }
                                        .zIndex(if (isCurrentDragging) 10f else 1f)
                                        .offset {
                                            if (isCurrentDragging) {
                                                IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                                            } else {
                                                IntOffset.Zero
                                            }
                                        }
                                        .graphicsLayer {
                                            if (isCurrentDragging) {
                                                scaleX = 1.12f
                                                scaleY = 1.12f
                                                alpha = 0.9f
                                            }
                                        }
                                ) {
                                    AppGridItem(
                                        app = app,
                                        onClick = { viewModel.selectApp(app) },
                                        aiLanguage = aiLanguage,
                                        modifier = Modifier.fillMaxWidth(),
                                        isFavorite = true
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                dragList.forEachIndexed { itemIndex, app ->
                    val isCurrentDragging = draggingIndex == itemIndex
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .onGloballyPositioned { coords ->
                                itemPositions[itemIndex] = coords.boundsInParent()
                            }
                            .zIndex(if (isCurrentDragging) 10f else 1f)
                            .offset {
                                if (isCurrentDragging) {
                                    IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt())
                                } else {
                                    IntOffset.Zero
                                }
                            }
                            .graphicsLayer {
                                if (isCurrentDragging) {
                                    scaleX = 1.05f
                                    scaleY = 1.05f
                                    alpha = 0.9f
                                }
                            }
                    ) {
                        AppListItem(
                            app = app,
                            onClick = { viewModel.selectApp(app) },
                            aiLanguage = aiLanguage,
                            modifier = Modifier.fillMaxWidth(),
                            isFavorite = true
                        )
                    }
                }
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
    onOpenSettings: () -> Unit,
    onAnalyze: (userContextText: String?, fileName: String?, fileMimeType: String?, fileBytes: ByteArray?) -> Unit,
    isAnalyzing: Boolean,
    apps: List<InstalledApp>,
    onSelectApp: (InstalledApp) -> Unit,
    lang: String,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {}
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

    val customIconLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val iconDir = File(context.filesDir, "custom_icons")
                    if (!iconDir.exists()) {
                        iconDir.mkdirs()
                    }
                    val destFile = File(iconDir, "${app.packageName}.png")
                    destFile.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    val usageTracker = UsageTracker(context.applicationContext)
                    usageTracker.setCustomIcon(app.packageName, "image:${destFile.absolutePath}")
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error setting image icon: ${e.message}", Toast.LENGTH_SHORT).show()
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
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
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
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0x0DFFFFFF))
                            .testTag("favorite_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = Localization.get("toggle_favorite_btn", lang),
                            tint = if (isFavorite) Color(0xFFFFD700) else Color(0x4DFFFFFF)
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

                // Action Buttons at the top (above Vector Analysis and AI Summary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onLaunch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("launch_app_button")
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Launch",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Localization.get("open_app", lang).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White,
                            containerColor = Color(0x0AFFFFFF)
                        ),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("open_settings_app_button")
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "App Settings",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = Localization.get("open_settings_btn", lang).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 0.5.sp,
                            maxLines = 1
                        )
                    }
                }

                // Icon Customizer Segment
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x08FFFFFF))
                        .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (lang == "ja") "表示アイコンのカスタマイズ" else "Customize Display Icon",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    val usageTracker = remember { UsageTracker(context.applicationContext) }
                    val customIcons by usageTracker.customIcons.collectAsState()
                    val currentCustomIcon = customIcons[app.packageName]

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val presets = listOf("🚀", "🎮", "💬", "🌐", "🎵", "📷", "🛠️", "❤️")
                        presets.forEach { emoji ->
                            val isSelected = currentCustomIcon == emoji
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF2563EB) else Color(0x0DFFFFFF))
                                    .border(1.dp, if (isSelected) Color(0xFF60A5FA) else Color(0x15FFFFFF), CircleShape)
                                    .clickable {
                                        usageTracker.setCustomIcon(app.packageName, if (isSelected) null else emoji)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 18.sp)
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        var manualText by remember { mutableStateOf("") }

                        BasicTextField(
                            value = manualText,
                            onValueChange = {
                                if (it.length <= 4) { // Allow brief custom text / emoji
                                    manualText = it
                                }
                            },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x0DFFFFFF))
                                .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            decorationBox = { innerTextField ->
                                if (manualText.isEmpty()) {
                                    Text(
                                        text = if (lang == "ja") "絵文字等を入力..." else "Enter emoji/text...",
                                        color = Color(0x80FFFFFF),
                                        fontSize = 12.sp
                                    )
                                }
                                innerTextField()
                            }
                        )

                        Button(
                            onClick = {
                                if (manualText.isNotEmpty()) {
                                    usageTracker.setCustomIcon(app.packageName, manualText)
                                    manualText = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(38.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text(
                                text = if (lang == "ja") "適用" else "Apply",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (currentCustomIcon != null) {
                            OutlinedButton(
                                onClick = {
                                    usageTracker.setCustomIcon(app.packageName, null)
                                },
                                border = BorderStroke(1.dp, Color(0x40FF5252)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(38.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text(
                                    text = if (lang == "ja") "リセット" else "Reset",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Custom image selection row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0x0DFFFFFF))
                            .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(8.dp))
                            .clickable {
                                customIconLauncher.launch("image/*")
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = "Select Custom Image",
                                tint = Color(0xFFA5D6A7),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (lang == "ja") "画像ファイルを選択して設定" else "Select Custom Image File",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        if (currentCustomIcon?.startsWith("image:") == true) {
                            val path = currentCustomIcon.substringAfter("image:")
                            val file = remember(path) { File(path) }
                            AsyncImage(
                                model = file,
                                contentDescription = "Selected Custom Icon Image",
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ArrowForwardIos,
                                contentDescription = "Select Icon Image",
                                tint = Color(0x80FFFFFF),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

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

                    // AI Vector Analysis Radar Chart Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x11000000))
                            .border(1.dp, Color(0x06FFFFFF), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = "Vector Analysis",
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = Localization.get("vector_title", lang).uppercase(),
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp
                            )
                        }
                        AppVectorRadarChart(
                            packageName = app.packageName,
                            category = info.category,
                            tags = info.getParsedTags(),
                            lang = lang
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

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            onAnalyze(customContextText, selectedFileName, selectedFileMimeType, selectedFileBytes)
                        },
                        enabled = !isAnalyzing,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF10B981),
                            disabledContainerColor = Color(0x3310B981)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .testTag("re_analyze_button")
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Re-analyze",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = Localization.get("re_analyze", lang).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

            }
        }
    }
}

@Composable
fun AppVectorRadarChart(
    packageName: String,
    category: String,
    tags: List<String>,
    lang: String,
    modifier: Modifier = Modifier
) {
    // 1. Calculate standard 6-axis values (0.15 to 1.0)
    val scores = remember(packageName, category, tags) {
        val catLower = category.lowercase()
        val tagsLower = tags.map { it.lowercase() }
        
        // Base value so the chart isn't empty
        val baseScores = FloatArray(6) { 0.22f }
        
        // Axis 0: Utility & Efficiency
        if (catLower.contains("system") || catLower.contains("utility") || catLower.contains("tool") || 
            catLower.contains("ツール") || catLower.contains("システム") || catLower.contains("便利")) {
            baseScores[0] += 0.45f
        }
        if (tagsLower.any { it.contains("tool") || it.contains("system") || it.contains("setting") || 
            it.contains("utility") || it.contains("便利") || it.contains("管理") || it.contains("最適化") }) {
            baseScores[0] += 0.20f
        }

        // Axis 1: Productivity & Work
        if (catLower.contains("productivity") || catLower.contains("business") || catLower.contains("finance") || 
            catLower.contains("education") || catLower.contains("生産性") || catLower.contains("ビジネス") || catLower.contains("仕事") || catLower.contains("家計簿")) {
            baseScores[1] += 0.45f
        }
        if (tagsLower.any { it.contains("note") || it.contains("task") || it.contains("calendar") || 
            it.contains("work") || it.contains("office") || it.contains("効率") || it.contains("メモ") || 
            it.contains("タスク") || it.contains("スケジュール") || it.contains("管理") }) {
            baseScores[1] += 0.20f
        }

        // Axis 2: Social & Connect
        if (catLower.contains("social") || catLower.contains("communication") || catLower.contains("messenger") || 
            catLower.contains("mail") || catLower.contains("ソーシャル") || catLower.contains("通信") || catLower.contains("連絡")) {
            baseScores[2] += 0.45f
        }
        if (tagsLower.any { it.contains("social") || it.contains("chat") || it.contains("message") || 
            it.contains("sns") || it.contains("community") || it.contains("チャット") || it.contains("メール") || 
            it.contains("トーク") || it.contains("投稿") || it.contains("交流") }) {
            baseScores[2] += 0.20f
        }

        // Axis 3: Entertainment & Fun
        if (catLower.contains("game") || catLower.contains("entertainment") || catLower.contains("video") || 
            catLower.contains("music") || catLower.contains("media") || catLower.contains("ゲーム") || 
            catLower.contains("エンタメ") || catLower.contains("メディア") || catLower.contains("音楽") || catLower.contains("動画")) {
            baseScores[3] += 0.45f
        }
        if (tagsLower.any { it.contains("game") || it.contains("play") || it.contains("movie") || 
            it.contains("fun") || it.contains("music") || it.contains("video") || it.contains("プレイヤー") || 
            it.contains("ゲーム") || it.contains("遊び") || it.contains("鑑賞") }) {
            baseScores[3] += 0.20f
        }

        // Axis 4: Creativity & Art
        if (catLower.contains("creativity") || catLower.contains("art") || catLower.contains("design") || 
            catLower.contains("photo") || catLower.contains("camera") || catLower.contains("クリエイティブ") || 
            catLower.contains("アート") || catLower.contains("写真") || catLower.contains("カメラ")) {
            baseScores[4] += 0.45f
        }
        if (tagsLower.any { it.contains("photo") || it.contains("edit") || it.contains("create") || 
            it.contains("draw") || it.contains("art") || it.contains("camera") || it.contains("作成") || 
            it.contains("編集") || it.contains("デザイン") || it.contains("描画") }) {
            baseScores[4] += 0.20f
        }

        // Axis 5: Knowledge & Info
        if (catLower.contains("news") || catLower.contains("book") || catLower.contains("weather") || 
            catLower.contains("info") || catLower.contains("map") || catLower.contains("learn") || 
            catLower.contains("情報") || catLower.contains("ニュース") || catLower.contains("天気") || 
            catLower.contains("辞書") || catLower.contains("ナビ")) {
            baseScores[5] += 0.45f
        }
        if (tagsLower.any { it.contains("news") || it.contains("weather") || it.contains("read") || 
            it.contains("book") || it.contains("learn") || it.contains("map") || it.contains("ナビ") || 
            it.contains("天気") || it.contains("ニュース") || it.contains("地図") || it.contains("検索") }) {
            baseScores[5] += 0.20f
        }

        // Deterministic variance based on packageName hash code
        val hash = kotlin.math.abs(packageName.hashCode())
        for (i in 0 until 6) {
            val variance = ((hash shr (i * 3)) % 15) / 100.0f // 0.0 to 0.14
            baseScores[i] = (baseScores[i] + variance).coerceIn(0.15f, 1.0f)
        }
        baseScores.toList()
    }

    // Axis Labels
    val labels = listOf(
        Localization.get("vector_utility", lang),
        Localization.get("vector_productivity", lang),
        Localization.get("vector_social", lang),
        Localization.get("vector_entertainment", lang),
        Localization.get("vector_creativity", lang),
        Localization.get("vector_knowledge", lang)
    )

    // Animated entry progress
    val animatedProgress = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(packageName) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 1000,
                easing = androidx.compose.animation.core.FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.height.coerceAtMost(size.width) / 2.8f
            val numAxes = 6

            // Draw concentric web rings
            val ringLevels = listOf(0.25f, 0.5f, 0.75f, 1.0f)
            ringLevels.forEach { level ->
                val ringPath = Path()
                for (i in 0 until numAxes) {
                    val angle = (i * 2 * Math.PI / numAxes) - Math.PI / 2
                    val r = radius * level
                    val x = center.x + (r * Math.cos(angle)).toFloat()
                    val y = center.y + (r * Math.sin(angle)).toFloat()
                    if (i == 0) {
                        ringPath.moveTo(x, y)
                    } else {
                        ringPath.lineTo(x, y)
                    }
                }
                ringPath.close()
                drawPath(
                    path = ringPath,
                    color = Color(0x1AFFFFFF),
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Draw axis lines and labels
            for (i in 0 until numAxes) {
                val angle = (i * 2 * Math.PI / numAxes) - Math.PI / 2
                val axisX = center.x + (radius * Math.cos(angle)).toFloat()
                val axisY = center.y + (radius * Math.sin(angle)).toFloat()

                // Axis line
                drawLine(
                    color = Color(0x22FFFFFF),
                    start = center,
                    end = Offset(axisX, axisY),
                    strokeWidth = 1.dp.toPx()
                )

                // Labels positioning
                val labelDistance = radius + 18.dp.toPx()
                val labelX = center.x + (labelDistance * Math.cos(angle)).toFloat()
                val labelY = center.y + (labelDistance * Math.sin(angle)).toFloat()

                // Draw label text manually on Native Canvas
                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#94A3B8")
                    textSize = 9.5.dp.toPx()
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                }
                
                // Adjust vertical alignment offset based on label angle
                val adjustedY = labelY + if (Math.sin(angle) > 0.1) 7.dp.toPx() else if (Math.sin(angle) < -0.1) -1.dp.toPx() else 3.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(
                    labels[i],
                    labelX,
                    adjustedY,
                    textPaint
                )
            }

            // Draw the Vector Polygon path
            val polygonPath = Path()
            for (i in 0 until numAxes) {
                val angle = (i * 2 * Math.PI / numAxes) - Math.PI / 2
                val value = scores[i] * animatedProgress.value
                val r = radius * value
                val x = center.x + (r * Math.cos(angle)).toFloat()
                val y = center.y + (r * Math.sin(angle)).toFloat()
                if (i == 0) {
                    polygonPath.moveTo(x, y)
                } else {
                    polygonPath.lineTo(x, y)
                }
            }
            polygonPath.close()

            // Outer outline stroke
            drawPath(
                path = polygonPath,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF3B82F6), Color(0xFF10B981)),
                    center = center,
                    radius = radius
                ),
                style = Stroke(width = 2.dp.toPx())
            )

            // Dynamic gradient semi-transparent fill
            drawPath(
                path = polygonPath,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x333B82F6), Color(0x6610B981)),
                    center = center,
                    radius = radius
                ),
                style = Fill
            )

            // Draw score indicator dots on vertices
            for (i in 0 until numAxes) {
                val angle = (i * 2 * Math.PI / numAxes) - Math.PI / 2
                val value = scores[i] * animatedProgress.value
                val r = radius * value
                val x = center.x + (r * Math.cos(angle)).toFloat()
                val y = center.y + (r * Math.sin(angle)).toFloat()

                // Draw outer pulse
                drawCircle(
                    color = Color(0x6610B981),
                    radius = 4.5.dp.toPx(),
                    center = Offset(x, y)
                )
                // Draw inner core
                drawCircle(
                    color = Color(0xFF10B981),
                    radius = 2.5.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}


