package com.example.ui.screens

import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RecommendedStoreApp
import com.example.ui.AppLauncherViewModel
import com.example.ui.Localization
import com.example.ui.components.AppIconImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: AppLauncherViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // ViewModel states
    val isAssistantLoading by viewModel.isAssistantLoading.collectAsState()
    val assistantResponse by viewModel.assistantResponse.collectAsState()
    val apps by viewModel.appListState.collectAsState()
    val aiLanguage by viewModel.settingsManager.aiLanguage.collectAsState()
    val wikiEntries by viewModel.wikiEntries.collectAsState()
    val githubRepos by viewModel.githubRepos.collectAsState()
    val isGithubLoading by viewModel.isGithubLoading.collectAsState()
    
    var textInput by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) } // 0 = Chat, 1 = LLM Wiki
    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<com.example.data.LlmWikiEntry?>(null) }
    var searchWikiQuery by remember { mutableStateOf("") }
    
    // Voice recognition launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!results.isNullOrEmpty()) {
                val spokenText = results[0]
                textInput = spokenText
                // Automatically search when voice is completed
                viewModel.askAiAssistant(spokenText)
            }
        }
    }

    // Suggested starters
    val starters = remember(aiLanguage) {
        if (aiLanguage == "ja") {
            listOf(
                "実用的なツールを探して" to "🛠️ ツール",
                "楽しいゲームを提案して" to "🎮 ゲーム",
                "SNSやコミュニケーションアプリを見せて" to "💬 SNS",
                "仕事や生産性を高めるアプリは？" to "📈 生産性",
                "お気に入りやよく使うアプリ" to "⭐ 人気"
            )
        } else {
            listOf(
                "Find useful utility tools" to "🛠️ Utilities",
                "Recommend some fun games" to "🎮 Games",
                "Show me my social or communication apps" to "💬 Social",
                "Which apps are for productivity?" to "📈 Productivity",
                "Show my favorites or top apps" to "⭐ Popular"
            )
        }
    }

    var dragDistance by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        // Detect drags starting within 120px (approx 40dp) of the left edge
                        dragDistance = if (offset.x < 120f) 0f else -9999f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (dragDistance != -9999f) {
                            dragDistance += dragAmount
                            if (dragDistance > 150f) { // Swipe right 150px to go back
                                dragDistance = -9999f // Prevent double trigger
                                onBack()
                            }
                        }
                    },
                    onDragEnd = {
                        dragDistance = 0f
                    }
                )
            }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF90CAF9),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (aiLanguage == "ja") "AI アシスタント" else "AI Assistant",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = if (aiLanguage == "ja") "戻る" else "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.clearAssistantState() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = if (aiLanguage == "ja") "クリア" else "Clear",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0x2B000000)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Modern Pill Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .background(Color(0x11FFFFFF), RoundedCornerShape(24.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = if (aiLanguage == "ja") listOf("チャット", "AIの記憶 (Wiki)") else listOf("Chat", "AI Memory (Wiki)")
                    tabs.forEachIndexed { index, title ->
                        val isSelected = activeTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) Color(0xFFEF5350) else Color.Transparent)
                                .clickable { activeTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) Color.White else Color(0x99FFFFFF),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Central scrollable content area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    if (activeTab == 0) {
                        if (assistantResponse == null && !isAssistantLoading) {
                        // --- GREETING AND STARTERS VIEW ---
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Pulsing glowing AI icon
                            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                            val scale by infiniteTransition.animateFloat(
                                initialValue = 0.95f,
                                targetValue = 1.05f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1500, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "scale"
                            )

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(100.dp)
                                    .scale(scale)
                                    .background(
                                        brush = Brush.radialGradient(
                                            colors = listOf(Color(0x3D90CAF9), Color.Transparent)
                                        ),
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF90CAF9),
                                    modifier = Modifier.size(56.dp)
                                )
                            }

                            Text(
                                text = if (aiLanguage == "ja") "何をお手伝いしましょうか？" else "How can I help you?",
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = if (aiLanguage == "ja") 
                                    "インストール済みのアプリから、指示に合うものをAIがインテリジェントに検索・提案します。" 
                                    else "The AI will intelligently search and recommend apps from your device based on your request.",
                                color = Color(0xB3FFFFFF),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Suggestions Title
                            Text(
                                text = if (aiLanguage == "ja") "クイックアクション" else "Quick Actions",
                                color = Color(0xFF90CAF9),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )

                            // Grid-like list of starters
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                starters.chunked(2).forEach { rowStarters ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        rowStarters.forEach { (prompt, label) ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0x1CFFFFFF)),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(16.dp))
                                                    .clickable {
                                                        textInput = prompt
                                                        viewModel.askAiAssistant(prompt)
                                                    }
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Text(
                                                        text = label,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                }
                                            }
                                        }
                                        // Pad empty spot if odd number of starters
                                        if (rowStarters.size < 2) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // --- AI RESPONSE VIEW (GIANT DISPLAY) ---
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            assistantResponse?.let { response ->
                                // 1. Headline - DEKADEKA / GIANT Text
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0x2B42A5F5)),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0x3342A5F5), RoundedCornerShape(20.dp))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(20.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = if (aiLanguage == "ja") "AIの回答" else "AI SUGGESTION",
                                                color = Color(0xFF90CAF9),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = response.headline,
                                                color = Color.White,
                                                fontSize = 26.sp, // DEKADEKA size
                                                fontWeight = FontWeight.ExtraBold,
                                                lineHeight = 34.sp
                                            )
                                        }
                                    }
                                }

                                // 2. Detailed Explanation
                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0x12FFFFFF)),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(20.dp))
                                    ) {
                                        Text(
                                            text = response.answer,
                                            color = Color(0xEEFFFFFF),
                                            fontSize = 16.sp,
                                            lineHeight = 26.sp,
                                            modifier = Modifier.padding(20.dp)
                                        )
                                    }
                                }

                                // 2.5 Extract Memory Button
                                item {
                                    val isExtracting by viewModel.isExtractingWiki.collectAsState()
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0x15EF5350)),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0x33EF5350), RoundedCornerShape(20.dp))
                                            .clickable {
                                                viewModel.extractAndSaveWikiFromConversation(
                                                    userPrompt = textInput.ifBlank { "App suggestions" },
                                                    aiAnswer = response.answer
                                                )
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            if (isExtracting) {
                                                CircularProgressIndicator(
                                                    color = Color(0xFFEF5350),
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Text(
                                                    text = if (aiLanguage == "ja") "会話から記憶を整理中..." else "Consolidating conversation memories...",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Default.Bookmark,
                                                    contentDescription = null,
                                                    tint = Color(0xFFEF5350),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (aiLanguage == "ja") "💡 会話からWikiエントリーを生成" else "💡 Extract & Save Wiki Entry",
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = if (aiLanguage == "ja") "会話内容をAIの記憶に登録し、次回以降の対話に反映します" else "Saves this detail to AI Memory so it won't forget",
                                                        color = Color(0x80FFFFFF),
                                                        fontSize = 11.sp,
                                                        lineHeight = 15.sp
                                                    )
                                                }
                                                Icon(
                                                    imageVector = Icons.Default.ArrowForward,
                                                    contentDescription = null,
                                                    tint = Color(0x80FFFFFF),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // 3. Matched Apps List (Dynamic Search Results Cards)
                                val matchedApps = response.relevantPackages?.mapNotNull { pkg ->
                                    apps.find { it.packageName == pkg }
                                } ?: emptyList()

                                if (matchedApps.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = (if (aiLanguage == "ja") "🔍 AIが発見したアプリ" else "🔍 Apps Found").uppercase(),
                                            color = Color(0xFF90CAF9),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                        )
                                    }

                                    items(matchedApps) { app ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0x21FFFFFF)),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(16.dp))
                                                .clickable { viewModel.launchApp(app.packageName) }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                AppIconImage(packageName = app.packageName, size = 52.dp)
                                                
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = app.label,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = app.cachedInfo?.category ?: (if (aiLanguage == "ja") "未解析" else "Unanalyzed"),
                                                        color = Color(0x99FFFFFF),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                    if (!app.cachedInfo?.summary.isNullOrBlank()) {
                                                        Text(
                                                            text = app.cachedInfo!!.summary,
                                                            color = Color(0xBBFFFFFF),
                                                            fontSize = 13.sp,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.padding(top = 4.dp)
                                                        )
                                                    }
                                                }

                                                IconButton(
                                                    onClick = { viewModel.launchApp(app.packageName) },
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(Color(0x33FFFFFF))
                                                        .size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = "Launch",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3.1 Recommended Play Store Apps
                                val recommendedStoreAppsRaw = response.recommendedStoreApps ?: emptyList()
                                val recommendedStoreApps = recommendedStoreAppsRaw.mapNotNull {
                                    try {
                                        RecommendedStoreApp(
                                            name = it["name"]?.toString() ?: "",
                                            packageName = it["packageName"]?.toString() ?: "",
                                            description = it["description"]?.toString() ?: "",
                                            playStoreUrl = it["playStoreUrl"]?.toString() ?: "",
                                            category = it["category"]?.toString() ?: "General"
                                        )
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                if (recommendedStoreApps.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = (if (aiLanguage == "ja") "🛒 Playストアの推奨アプリ" else "🛒 Recommended Play Store Apps").uppercase(),
                                            color = Color(0xFFEF5350),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                        )
                                    }

                                    items(recommendedStoreApps) { storeApp ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = Color(0x11EF5350)),
                                            shape = RoundedCornerShape(16.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, Color(0x22EF5350), RoundedCornerShape(16.dp))
                                                .clickable {
                                                    try {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(storeApp.playStoreUrl)).apply {
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(intent)
                                                    } catch (e: Exception) {
                                                        val queryUrl = "https://play.google.com/store/search?q=${storeApp.packageName}"
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(queryUrl)).apply {
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(intent)
                                                    }
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(14.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(Color(0x22FFFFFF)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ShoppingCart,
                                                        contentDescription = null,
                                                        tint = Color(0xFFEF5350),
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = storeApp.name,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 16.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(6.dp))
                                                                .background(Color(0x33EF5350))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = storeApp.category,
                                                                color = Color(0xFFEF5350),
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Black
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = storeApp.packageName,
                                                        color = Color(0x80FFFFFF),
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = storeApp.description,
                                                        color = Color(0xBBFFFFFF),
                                                        fontSize = 13.sp,
                                                        maxLines = 3,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    )
                                                }

                                                IconButton(
                                                    onClick = {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(storeApp.playStoreUrl)).apply {
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(intent)
                                                    },
                                                    modifier = Modifier
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFEF5350))
                                                        .size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ArrowForward,
                                                        contentDescription = "Install from Play Store",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3.2 GitHub Real-time Search Results
                                val ghQuery = response.githubSearchQuery
                                if (!ghQuery.isNullOrBlank() || isGithubLoading || githubRepos.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = (if (aiLanguage == "ja") "🐙 GitHub リアルタイム検索" else "🐙 GitHub Real-time Search").uppercase(),
                                            color = Color(0xFF90CAF9),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                        )
                                    }

                                    if (isGithubLoading) {
                                        item {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0x1190CAF9)),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, Color(0x2290CAF9), RoundedCornerShape(16.dp))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        color = Color(0xFF90CAF9),
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.5.dp
                                                    )
                                                    Text(
                                                        text = if (aiLanguage == "ja") "GitHubから最新のリポジトリを検索中..." else "Searching GitHub for live repositories...",
                                                        color = Color(0xCCFFFFFF),
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    } else if (githubRepos.isEmpty()) {
                                        item {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0x11FFFFFF)),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(16.dp))
                                            ) {
                                                Text(
                                                    text = if (aiLanguage == "ja") "「$ghQuery」に一致するリポジトリが見つかりませんでした。" else "No repositories found for '$ghQuery'.",
                                                    color = Color(0x80FFFFFF),
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(16.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        items(githubRepos) { repo ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0x1190CAF9)),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, Color(0x2290CAF9), RoundedCornerShape(16.dp))
                                                    .clickable {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(repo.htmlUrl)).apply {
                                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                        context.startActivity(intent)
                                                    }
                                            ) {
                                                Column(modifier = Modifier.padding(14.dp)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            // Owner icon fallback
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(24.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0x22FFFFFF)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = repo.owner.login.take(1).uppercase(),
                                                                    color = Color(0xFF90CAF9),
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Black
                                                                )
                                                            }
                                                            Text(
                                                                text = repo.owner.login,
                                                                color = Color(0x99FFFFFF),
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }

                                                        // Stars badge
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Star,
                                                                contentDescription = "Stars",
                                                                tint = Color(0xFFFFD54F),
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Text(
                                                                text = String.format("%,d", repo.stargazersCount),
                                                                color = Color(0xFFFFD54F),
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    Text(
                                                        text = repo.name,
                                                        color = Color.White,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    if (!repo.description.isNullOrBlank()) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = repo.description,
                                                            color = Color(0xCCFFFFFF),
                                                            fontSize = 13.sp,
                                                            lineHeight = 18.sp,
                                                            maxLines = 3,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // 4. AI Follow-up suggestions
                                val suggestions = response.suggestions ?: emptyList()
                                if (suggestions.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = if (aiLanguage == "ja") "次の質問候補" else "Suggested follow-ups",
                                            color = Color(0xFF90CAF9),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                                        )
                                        
                                        Spacer(modifier = Modifier.height(8.dp))

                                        LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(suggestions) { suggestionText ->
                                                Card(
                                                    colors = CardDefaults.cardColors(containerColor = Color(0x1BFFFFFF)),
                                                    shape = RoundedCornerShape(50),
                                                    modifier = Modifier
                                                        .border(1.dp, Color(0x21FFFFFF), RoundedCornerShape(50))
                                                        .clickable {
                                                            textInput = suggestionText
                                                            viewModel.askAiAssistant(suggestionText)
                                                        }
                                                ) {
                                                    Text(
                                                        text = suggestionText,
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- PULSING THINKING/LOADING SCREEN Overlay ---
                    if (isAssistantLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xCD0A0A12)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                val infiniteTransition = rememberInfiniteTransition(label = "loader")
                                val rotation by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 360f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(2000, easing = LinearEasing)
                                    ),
                                    label = "rotation"
                                )

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .scale(1.2f),
                                        color = Color(0xFF90CAF9),
                                        strokeWidth = 3.dp
                                    )
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0xFF90CAF9),
                                        modifier = Modifier
                                            .size(36.dp)
                                            .scale(1f)
                                    )
                                }

                                Text(
                                    text = if (aiLanguage == "ja") "AIが探索・分析中..." else "AI is searching & analyzing...",
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = if (aiLanguage == "ja") "インストールされたアプリから最適なものを検索しています" else "Scanning your installed apps list for matches",
                                    color = Color(0x80FFFFFF),
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                    }
                } else {
                    // --- LLM WIKI / AI MEMORY SCREEN ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header Banner
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x1190CAF9)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0x2290CAF9), RoundedCornerShape(16.dp))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (aiLanguage == "ja") "🧠 AIの長期記憶 (LLM Wiki)" else "🧠 AI Long-term Memory (LLM Wiki)",
                                    color = Color(0xFF90CAF9),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (aiLanguage == "ja") 
                                        "ここに保存された事実や設定・指示は、AIアシスタントが会話をする際に常に前提知識として参照されます。" 
                                        else "Facts, preferences, and instructions saved here are automatically fed to the AI as context, so it never forgets them.",
                                    color = Color(0xB3FFFFFF),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        // Actions Bar: Search, Add
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Search Input
                            OutlinedTextField(
                                value = searchWikiQuery,
                                onValueChange = { searchWikiQuery = it },
                                placeholder = { 
                                    Text(
                                        if (aiLanguage == "ja") "記憶を検索..." else "Search memories...", 
                                        fontSize = 13.sp,
                                        color = Color(0x66FFFFFF)
                                    ) 
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color(0x80FFFFFF),
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(24.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFEF5350),
                                    unfocusedBorderColor = Color(0x22FFFFFF),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )

                            // Add Button
                            Button(
                                onClick = { showAddDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350)),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Memory",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (aiLanguage == "ja") "追加" else "Add",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Memories List
                        val filteredWikis = remember(wikiEntries, searchWikiQuery) {
                            if (searchWikiQuery.isBlank()) {
                                wikiEntries
                            } else {
                                wikiEntries.filter {
                                    it.title.contains(searchWikiQuery, ignoreCase = true) ||
                                    it.content.contains(searchWikiQuery, ignoreCase = true) ||
                                    it.category.contains(searchWikiQuery, ignoreCase = true)
                                }
                            }
                        }

                        if (filteredWikis.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = Color(0x33FFFFFF),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = if (aiLanguage == "ja") "記憶はまだありません" else "No memories found",
                                        color = Color(0x66FFFFFF),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (aiLanguage == "ja") 
                                            "チャットの回答カードにある「💡 会話をAIの記憶に登録」を押すか、右上の「追加」ボタンから登録してください。" 
                                            else "Click '💡 Save this conversation' during chat, or use the 'Add' button to record memories manually.",
                                        color = Color(0x40FFFFFF),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(filteredWikis, key = { it.id }) { entry ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0x12FFFFFF)),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0x1BFFFFFF), RoundedCornerShape(16.dp))
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Category Badge
                                                val badgeColor = when (entry.category.lowercase()) {
                                                    "preference" -> Color(0xFF81C784)
                                                    "instruction" -> Color(0xFFB39DDB)
                                                    "fact" -> Color(0xFF90CAF9)
                                                    else -> Color(0xFFB0BEC5)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(badgeColor.copy(alpha = 0.2f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = entry.category.uppercase(),
                                                        color = badgeColor,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black
                                                    )
                                                }

                                                // Action buttons
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    IconButton(
                                                        onClick = { editingEntry = entry },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Edit,
                                                            contentDescription = "Edit",
                                                            tint = Color(0xB3FFFFFF),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { viewModel.deleteWikiEntry(entry.id) },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete",
                                                            tint = Color(0xFFEF5350),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Text(
                                                text = entry.title,
                                                color = Color.White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = entry.content,
                                                color = Color(0xCCFFFFFF),
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp
                                            )
                                        }
                                    }
                                }

                                // Clear all memories button at the bottom of the list
                                item {
                                    TextButton(
                                        onClick = { viewModel.clearAllWikiEntries() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF5350))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (aiLanguage == "ja") "すべての記憶を消去" else "Clear All Memories",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- BOTTOM CAPSULE INPUT AREA ---
            AnimatedVisibility(visible = activeTab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp, top = 8.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0x2BFFFFFF))
                        .border(1.dp, Color(0x3DFFFFFF), RoundedCornerShape(28.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Microphone Dictation Button
                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, aiLanguage)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, if (aiLanguage == "ja") "AIアシスタントに指示をどうぞ" else "Say something to the AI Assistant")
                            }
                            try {
                                speechRecognizerLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Voice input unsupported", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color(0x1BFFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Text Field
                    BasicTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_assistant_input_field"),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (textInput.isEmpty()) {
                                    Text(
                                        text = if (aiLanguage == "ja") "AIへの指示・アプリ検索..." else "Ask AI or search apps...",
                                        color = Color(0x80FFFFFF),
                                        fontSize = 15.sp
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )

                    // Clear query text input
                    if (textInput.isNotEmpty()) {
                        IconButton(
                            onClick = { textInput = "" },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Send Button
                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.askAiAssistant(textInput)
                                textInput = ""
                            }
                        },
                        enabled = textInput.isNotBlank() && !isAssistantLoading,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (textInput.isNotBlank() && !isAssistantLoading) Color(0xFF90CAF9) else Color(0x15FFFFFF)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (textInput.isNotBlank() && !isAssistantLoading) Color.Black else Color(0x4DFFFFFF),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var content by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Preference") }
        val categories = listOf("Preference", "Instruction", "Fact", "General")
        
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (aiLanguage == "ja") "記憶を追加" else "Add Memory", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(if (aiLanguage == "ja") "タイトル" else "Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF5350),
                            unfocusedBorderColor = Color(0x40FFFFFF),
                            focusedLabelColor = Color(0xFFEF5350),
                            unfocusedLabelColor = Color(0x80FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(if (aiLanguage == "ja") "記憶内容" else "Memory Content") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF5350),
                            unfocusedBorderColor = Color(0x40FFFFFF),
                            focusedLabelColor = Color(0xFFEF5350),
                            unfocusedLabelColor = Color(0x80FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Text(text = if (aiLanguage == "ja") "カテゴリー" else "Category", color = Color(0xCCFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categories.forEach { cat ->
                            val isSelected = category == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFFEF5350) else Color(0x1BFFFFFF))
                                    .clickable { category = cat }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color.White else Color(0xCCFFFFFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (title.isNotBlank() && content.isNotBlank()) {
                            viewModel.saveWikiEntry(
                                com.example.data.LlmWikiEntry(
                                    title = title,
                                    content = content,
                                    category = category
                                )
                            )
                            showAddDialog = false
                        }
                    }
                ) {
                    Text(if (aiLanguage == "ja") "保存" else "Save", color = Color(0xFFEF5350))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(if (aiLanguage == "ja") "キャンセル" else "Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF212121),
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (editingEntry != null) {
        var title by remember { mutableStateOf(editingEntry!!.title) }
        var content by remember { mutableStateOf(editingEntry!!.content) }
        var category by remember { mutableStateOf(editingEntry!!.category) }
        val categories = listOf("Preference", "Instruction", "Fact", "General")
        
        AlertDialog(
            onDismissRequest = { editingEntry = null },
            title = { Text(if (aiLanguage == "ja") "記憶を編集" else "Edit Memory", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(if (aiLanguage == "ja") "タイトル" else "Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF5350),
                            unfocusedBorderColor = Color(0x40FFFFFF),
                            focusedLabelColor = Color(0xFFEF5350),
                            unfocusedLabelColor = Color(0x80FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(if (aiLanguage == "ja") "記憶内容" else "Memory Content") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF5350),
                            unfocusedBorderColor = Color(0x40FFFFFF),
                            focusedLabelColor = Color(0xFFEF5350),
                            unfocusedLabelColor = Color(0x80FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Text(text = if (aiLanguage == "ja") "カテゴリー" else "Category", color = Color(0xCCFFFFFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categories.forEach { cat ->
                            val isSelected = category == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFFEF5350) else Color(0x1BFFFFFF))
                                    .clickable { category = cat }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color.White else Color(0xCCFFFFFF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (title.isNotBlank() && content.isNotBlank()) {
                            viewModel.saveWikiEntry(
                                editingEntry!!.copy(
                                    title = title,
                                    content = content,
                                    category = category,
                                    lastUpdated = System.currentTimeMillis()
                                )
                            )
                            editingEntry = null
                        }
                    }
                ) {
                    Text(if (aiLanguage == "ja") "保存" else "Save", color = Color(0xFFEF5350))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEntry = null }) {
                    Text(if (aiLanguage == "ja") "キャンセル" else "Cancel", color = Color.White)
                }
            },
            containerColor = Color(0xFF212121),
            shape = RoundedCornerShape(20.dp)
        )
    }
}
}
