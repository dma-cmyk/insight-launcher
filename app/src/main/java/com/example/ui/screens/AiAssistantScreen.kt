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
import androidx.compose.ui.window.Dialog
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
    val fdroidRepos by viewModel.fdroidRepos.collectAsState()
    val isFDroidLoading by viewModel.isFDroidLoading.collectAsState()
    val githubRepoDetail by viewModel.githubRepoDetail.collectAsState()
    val fdroidAppDetail by viewModel.fdroidAppDetail.collectAsState()
    val isDetailLoading by viewModel.isDetailLoading.collectAsState()
    val detailError by viewModel.detailError.collectAsState()

    val colorTheme by viewModel.colorTheme.collectAsState()
    val isLight = colorTheme.startsWith("light_")

    val textColor = if (isLight) Color(0xFF11111F) else Color.White
    val subTextColor = if (isLight) Color(0xFF454558) else Color(0xB2FFFFFF)
    val topBarBg = if (isLight) Color(0xFDF8F7FC) else Color(0x2B000000)
    val starTint = if (isLight) Color(0xFF1976D2) else Color(0xFF90CAF9)
    val wikiGreen = if (isLight) Color(0xFF2E7D32) else Color(0xFF81C784)
    val cardBgColor = if (isLight) Color(0xDDFFFFFF) else Color(0x12FFFFFF)
    val cardBorderColor = if (isLight) Color(0x2E000000) else Color(0x1BFFFFFF)
    val panelBgColor = if (isLight) Color(0xFDF8F7FC) else Color(0xCD0A0A12)
    val panelBorderColor = if (isLight) Color(0x1A000000) else Color(0x20FFFFFF)
    
    var textInput by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(0) } // 0 = Chat, 1 = LLM Wiki
    var pendingWikiEntry by remember { mutableStateOf<com.example.data.LlmWikiEntry?>(null) }
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
                // Correct voice input using AI before submitting
                viewModel.processAiAssistantVoiceInput(spokenText) { correctedText ->
                    textInput = correctedText
                    viewModel.askAiAssistant(correctedText)
                }
            }
        }
    }

    // Export Memory Markdown Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            viewModel.exportMemoriesAsMarkdown(context, uri) { success ->
                if (success) {
                    Toast.makeText(context, if (aiLanguage == "ja") "ダウンロード完了しました" else "Exported successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, if (aiLanguage == "ja") "ダウンロードに失敗しました" else "Export failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val isImportingWiki by viewModel.isImportingWiki.collectAsState()

    // Import Memory Launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importMemoriesFromUri(context, uri) { success, count ->
                if (success) {
                    val msg = if (aiLanguage == "ja") {
                        "アップロードが完了しました。AIが${count}件のWikiエントリーを構築して保存しました。"
                    } else {
                        "Import successful. AI processed and saved ${count} wiki entries."
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                } else {
                    val msg = if (aiLanguage == "ja") "アップロード、またはAIによるWiki構築に失敗しました" else "Import or AI reconstruction failed"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
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
                            tint = starTint,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (aiLanguage == "ja") "AI アシスタント" else "AI Assistant",
                            color = textColor,
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
                            tint = textColor
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
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = topBarBg
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
                        if (isDetailLoading) {
                            // --- DETAIL LOADING VIEW ---
                            Box(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFFEF5350),
                                        strokeWidth = 3.dp,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = if (aiLanguage == "ja") "詳細情報を取得して解析中..." else "Fetching and analyzing details...",
                                        color = textColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        } else if (githubRepoDetail != null) {
                            // --- GITHUB REPO DETAIL VIEW ---
                            val repo = githubRepoDetail!!
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { viewModel.clearDetailState() },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF90CAF9))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (aiLanguage == "ja") "検索結果に戻る" else "Back to Search Results",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0x1F90CAF9)),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0x3390CAF9), RoundedCornerShape(20.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            coil.compose.AsyncImage(
                                                model = repo.ownerAvatarUrl,
                                                contentDescription = "Avatar",
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(CircleShape)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = repo.name,
                                                    color = textColor,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "by ${repo.ownerLogin}",
                                                    color = subTextColor,
                                                    fontSize = 13.sp
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Star,
                                                            contentDescription = "Stars",
                                                            tint = Color(0xFFFFB300),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Text(
                                                            text = "${repo.stargazersCount} stars",
                                                            color = subTextColor,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0x2290CAF9))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "GitHub FOSS",
                                                            color = starTint,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, cardBorderColor, RoundedCornerShape(20.dp))
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = if (aiLanguage == "ja") "🤖 AI詳細解説" else "🤖 AI Explanation",
                                                color = starTint,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                            Text(
                                                text = repo.summaryExplanation,
                                                color = textColor,
                                                fontSize = 15.sp,
                                                lineHeight = 24.sp
                                            )
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                pendingWikiEntry = com.example.data.LlmWikiEntry(
                                                    title = repo.name,
                                                    content = "${repo.summaryExplanation}\n\n[Details]\n• Owner: ${repo.ownerLogin}\n• Stars: ${repo.stargazersCount}\n• URL: ${repo.htmlUrl}",
                                                    category = "Fact",
                                                    tags = listOf("GitHub", "Repo", "FOSS", repo.ownerLogin, repo.name.take(10).replace(" ", ""))
                                                )
                                                viewModel.clearDetailState()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.BookmarkAdd,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (aiLanguage == "ja") "LLM Wikiに追加" else "Add to LLM Wiki",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse(repo.htmlUrl)
                                                    ).apply {
                                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "Cannot open URL", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x2B90CAF9)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = null,
                                                tint = starTint,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (fdroidAppDetail != null) {
                            // --- F-DROID APP DETAIL VIEW ---
                            val app = fdroidAppDetail!!
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { viewModel.clearDetailState() },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF81C784))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ArrowBack,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = wikiGreen
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (aiLanguage == "ja") "検索結果に戻る" else "Back to Search Results",
                                                color = wikiGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0x1F81C784)),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color(0x3381C784), RoundedCornerShape(20.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                            coil.compose.AsyncImage(
                                                model = app.iconUrl,
                                                contentDescription = "Icon",
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.White)
                                                    .padding(2.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = app.name,
                                                    color = textColor,
                                                    fontSize = 20.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = app.packageName,
                                                    color = subTextColor,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0x2281C784))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = app.license,
                                                            color = wikiGreen,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(Color(0x2281C784))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(
                                                            text = "F-Droid FOSS",
                                                            color = wikiGreen,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, cardBorderColor, RoundedCornerShape(20.dp))
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Text(
                                                text = if (aiLanguage == "ja") "🤖 AI詳細解説" else "🤖 AI Explanation",
                                                color = wikiGreen,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(bottom = 8.dp)
                                            )
                                            Text(
                                                text = app.summaryExplanation,
                                                color = textColor,
                                                fontSize = 15.sp,
                                                lineHeight = 24.sp
                                            )
                                        }
                                    }
                                }

                                if (app.apkDownloadLinks.isNotEmpty()) {
                                    item {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                            shape = RoundedCornerShape(20.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(1.dp, Color(0x16FFFFFF), RoundedCornerShape(20.dp))
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text(
                                                    text = if (aiLanguage == "ja") "📥 APK 直接ダウンロードリンク" else "📥 Direct APK Downloads",
                                                    color = wikiGreen,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(bottom = 12.dp)
                                                )
                                                app.apkDownloadLinks.forEachIndexed { idx, url ->
                                                    Card(
                                                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                try {
                                                                    val intent = android.content.Intent(
                                                                        android.content.Intent.ACTION_VIEW,
                                                                        android.net.Uri.parse(url)
                                                                    ).apply {
                                                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                    }
                                                                    context.startActivity(intent)
                                                                } catch (e: Exception) {
                                                                    android.widget.Toast.makeText(context, "Cannot open link", android.widget.Toast.LENGTH_SHORT).show()
                                                                }
                                                            }
                                                            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp))
                                                            .padding(12.dp)
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Download,
                                                                contentDescription = null,
                                                                tint = wikiGreen,
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = url.substringAfterLast("/").ifEmpty { "Download APK #${idx + 1}" },
                                                                    color = textColor,
                                                                    fontSize = 13.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                            Icon(
                                                                imageVector = Icons.Default.ArrowForward,
                                                                contentDescription = null,
                                                                tint = Color(0x4DFFFFFF),
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }
                                                    if (idx < app.apkDownloadLinks.lastIndex) {
                                                        Spacer(modifier = Modifier.height(8.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                pendingWikiEntry = com.example.data.LlmWikiEntry(
                                                    title = app.name,
                                                    content = "${app.summaryExplanation}\n\n[Details]\n• Package: ${app.packageName}\n• License: ${app.license}\n• URL: https://f-droid.org/packages/${app.packageName}/\n• APK Links: ${app.apkDownloadLinks.joinToString(", ")}",
                                                    category = "Fact",
                                                    tags = listOf("FDroid", "App", "FOSS", app.packageName.split(".").lastOrNull() ?: "FOSS", app.name.take(10).replace(" ", ""))
                                                )
                                                viewModel.clearDetailState()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.weight(1f).height(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.BookmarkAdd,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (aiLanguage == "ja") "LLM Wikiに追加" else "Add to LLM Wiki",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                try {
                                                    val intent = android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse("https://f-droid.org/packages/${app.packageName}/")
                                                    ).apply {
                                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    android.widget.Toast.makeText(context, "Cannot open URL", android.widget.Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x2B81C784)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.height(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = null,
                                                tint = wikiGreen,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        } else if (assistantResponse == null && !isAssistantLoading) {
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
                                    tint = starTint,
                                    modifier = Modifier.size(56.dp)
                                )
                            }

                            Text(
                                text = if (aiLanguage == "ja") "何をお手伝いしましょうか？" else "How can I help you?",
                                color = textColor,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = if (aiLanguage == "ja") 
                                    "インストール済みのアプリから、指示に合うものをAIがインテリジェントに検索・提案します。" 
                                    else "The AI will intelligently search and recommend apps from your device based on your request.",
                                color = subTextColor,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Suggestions Title
                            Text(
                                text = if (aiLanguage == "ja") "クイックアクション" else "Quick Actions",
                                color = starTint,
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
                                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
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
                                                        color = textColor,
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
                                                color = starTint,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = response.headline,
                                                color = textColor,
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
                                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, cardBorderColor, RoundedCornerShape(20.dp))
                                    ) {
                                        Column {
                                            if (!response.headerImageUrl.isNullOrBlank()) {
                                                coil.compose.AsyncImage(
                                                    model = response.headerImageUrl,
                                                    contentDescription = "Header Image",
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(max = 200.dp)
                                                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                                )
                                            }
                                            Text(
                                                text = response.answer,
                                                color = textColor,
                                                fontSize = 16.sp,
                                                lineHeight = 26.sp,
                                                modifier = Modifier.padding(20.dp)
                                            )
                                        }
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
                                                    color = textColor,
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
                                                        color = textColor,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = if (aiLanguage == "ja") "会話内容をAIの記憶に登録し、次回以降の対話に反映します" else "Saves this detail to AI Memory so it won't forget",
                                                        color = subTextColor.copy(alpha = 0.5f),
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
                                            color = starTint,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                        )
                                    }

                                    items(matchedApps) { app ->
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = cardBgColor),
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
                                                        color = textColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 16.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = app.cachedInfo?.category ?: (if (aiLanguage == "ja") "未解析" else "Unanalyzed"),
                                                        color = subTextColor,
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
                                                        tint = textColor,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // 3.1 Recommended Play Store Apps
                                val recommendedStoreAppsRaw = response.recommendedStoreApps ?: emptyList()
                                val recommendedStoreApps = recommendedStoreAppsRaw.mapNotNull { item ->
                                    try {
                                        if (item is Map<*, *>) {
                                            com.example.data.RecommendedStoreApp(
                                                name = item["name"]?.toString() ?: "",
                                                packageName = item["packageName"]?.toString() ?: "",
                                                description = item["description"]?.toString() ?: "",
                                                playStoreUrl = item["playStoreUrl"]?.toString() ?: "",
                                                category = item["category"]?.toString() ?: "General"
                                            )
                                        } else {
                                            null
                                        }
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
                                                            color = textColor,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 16.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(Color(0x33EF5350))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = storeApp.category,
                                                                color = Color(0xFFEF5350),
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = storeApp.packageName,
                                                        color = subTextColor,
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

                                                Column {
                                                    IconButton(
                                                        onClick = {
                                                            pendingWikiEntry = com.example.data.LlmWikiEntry(
                                                                title = storeApp.name.ifEmpty { "Play Store App" },
                                                                content = "${storeApp.description}\n\n[Details]\n• Package: ${storeApp.packageName}\n• Category: ${storeApp.category}\n• URL: ${storeApp.playStoreUrl}",
                                                                category = "Fact",
                                                                tags = listOf("App", "PlayStore", storeApp.category, "Recommendation", storeApp.name.take(10).replace(" ", ""))
                                                            )
                                                        },
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(Color(0x33EF5350))
                                                            .size(36.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.BookmarkAdd,
                                                            contentDescription = "Save to Wiki",
                                                            tint = Color(0xFFEF5350),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    IconButton(
                                                        onClick = {
                                                            val query = if (aiLanguage == "ja") "Playストアアプリ「${storeApp.name}」(${storeApp.packageName}) の詳細を教えてください" else "Tell me more about Play Store app '${storeApp.name}' (${storeApp.packageName})"
                                                            viewModel.askAiAssistant(query)
                                                        },
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .background(Color(0x33EF5350))
                                                            .size(36.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Search,
                                                            contentDescription = "Search details",
                                                            tint = Color(0xFFEF5350),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
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
                                            color = starTint,
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
                                                        color = starTint,
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.5.dp
                                                    )
                                                    Text(
                                                        text = if (aiLanguage == "ja") "GitHubから最新のリポジトリを検索中..." else "Searching GitHub for live repositories...",
                                                        color = subTextColor,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    } else if (githubRepos.isEmpty()) {
                                        item {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                                            ) {
                                                Text(
                                                    text = if (aiLanguage == "ja") "「$ghQuery」に一致するリポジトリが見つかりませんでした。" else "No repositories found for '$ghQuery'.",
                                                    color = subTextColor.copy(alpha = 0.5f),
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
                                                        try {
                                                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(repo.htmlUrl)).apply {
                                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            android.widget.Toast.makeText(context, "Cannot open URL", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
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
                                                                    color = starTint,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Black
                                                                )
                                                            }
                                                            Text(
                                                                text = repo.owner.login,
                                                                color = subTextColor,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }

                                                        // Stars badge and actions
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
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
                                                            IconButton(
                                                                onClick = {
                                                                    pendingWikiEntry = com.example.data.LlmWikiEntry(
                                                                        title = repo.name,
                                                                        content = "${repo.description ?: "No description"}\n\n[Details]\n• Stars: ${repo.stargazersCount}\n• URL: ${repo.htmlUrl}",
                                                                        category = "Fact",
                                                                        tags = listOf("GitHub", "Code", "Repository", "OpenSource", repo.name.take(10).replace(" ", ""))
                                                                    )
                                                                },
                                                                modifier = Modifier
                                                                    .clip(CircleShape)
                                                                    .background(Color(0x3390CAF9))
                                                                    .size(24.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.BookmarkAdd,
                                                                    contentDescription = "Save to Wiki",
                                                                    tint = starTint,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    viewModel.loadGitHubRepoDetails(repo)
                                                                },
                                                                modifier = Modifier
                                                                    .clip(CircleShape)
                                                                    .background(Color(0x3390CAF9))
                                                                    .size(24.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Search,
                                                                    contentDescription = "Search details",
                                                                    tint = starTint,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    Text(
                                                        text = repo.name,
                                                        color = textColor,
                                                        fontSize = 15.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    if (!repo.description.isNullOrBlank()) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = repo.description,
                                                            color = subTextColor,
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

                                 // 3.3 F-Droid Search Results
                                val fdQuery = response.fdroidSearchQuery
                                if (!fdQuery.isNullOrBlank() || isFDroidLoading || fdroidRepos.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = (if (aiLanguage == "ja") "🤖 F-Droid リアルタイム検索" else "🤖 F-Droid Real-time Search").uppercase(),
                                            color = wikiGreen,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                                        )
                                    }

                                    if (isFDroidLoading) {
                                        item {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0x1181C784)),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, Color(0x2281C784), RoundedCornerShape(16.dp))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(16.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                ) {
                                                    CircularProgressIndicator(
                                                        color = wikiGreen,
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.5.dp
                                                    )
                                                    Text(
                                                        text = if (aiLanguage == "ja") "F-Droidから最新のアプリを検索中..." else "Searching F-Droid for live apps...",
                                                        color = subTextColor,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    } else if (fdroidRepos.isEmpty()) {
                                        item {
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
                                            ) {
                                                Text(
                                                    text = if (aiLanguage == "ja") "「$fdQuery」に一致するアプリが見つかりませんでした。" else "No apps found for '$fdQuery'.",
                                                    color = subTextColor.copy(alpha = 0.5f),
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(16.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        items(fdroidRepos) { pkg ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0x1181C784)),
                                                shape = RoundedCornerShape(16.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, Color(0x2281C784), RoundedCornerShape(16.dp))
                                                    .clickable {
                                                        try {
                                                            val intent = android.content.Intent(
                                                                android.content.Intent.ACTION_VIEW,
                                                                android.net.Uri.parse("https://f-droid.org/packages/${pkg.packageName}/")
                                                            ).apply {
                                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            android.widget.Toast.makeText(context, "Cannot open URL", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
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
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(24.dp)
                                                                    .clip(CircleShape)
                                                                    .background(Color(0x22FFFFFF)),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = "FD",
                                                                    color = wikiGreen,
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Black
                                                                )
                                                            }
                                                            Text(
                                                                text = "F-Droid FOSS",
                                                                color = subTextColor,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Medium,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }

                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                        ) {
                                                            IconButton(
                                                                onClick = {
                                                                    pendingWikiEntry = com.example.data.LlmWikiEntry(
                                                                        title = pkg.name,
                                                                        content = "${pkg.summary}\n\n[Details]\n• License: ${pkg.license}\n• Package: ${pkg.packageName}\n• URL: https://f-droid.org/packages/${pkg.packageName}/",
                                                                        category = "Fact",
                                                                        tags = listOf("FDroid", "App", "FOSS", pkg.name.take(10).replace(" ", ""))
                                                                    )
                                                                },
                                                                modifier = Modifier
                                                                    .clip(CircleShape)
                                                                    .background(Color(0x3381C784))
                                                                    .size(24.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.BookmarkAdd,
                                                                    contentDescription = "Save to Wiki",
                                                                    tint = wikiGreen,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                            IconButton(
                                                                onClick = {
                                                                    viewModel.loadFDroidAppDetails(pkg)
                                                                },
                                                                modifier = Modifier
                                                                    .clip(CircleShape)
                                                                    .background(Color(0x3381C784))
                                                                    .size(24.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Search,
                                                                    contentDescription = "Search details",
                                                                    tint = wikiGreen,
                                                                    modifier = Modifier.size(14.dp)
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(10.dp))

                                                    Row(
                                                        verticalAlignment = Alignment.Top,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        coil.compose.AsyncImage(
                                                            model = pkg.iconUrl,
                                                            contentDescription = "Icon",
                                                            modifier = Modifier
                                                                .size(48.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(Color.White)
                                                        )
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                Text(
                                                                    text = pkg.name,
                                                                    color = textColor,
                                                                    fontSize = 15.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Icon(
                                                                    imageVector = Icons.Default.OpenInNew,
                                                                    contentDescription = "Open",
                                                                    tint = Color(0x80FFFFFF),
                                                                    modifier = Modifier.size(12.dp)
                                                                )
                                                            }
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = pkg.summary,
                                                                color = subTextColor,
                                                                fontSize = 13.sp,
                                                                lineHeight = 18.sp
                                                            )
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Box(
                                                                    modifier = Modifier
                                                                        .clip(RoundedCornerShape(4.dp))
                                                                        .background(Color(0x2281C784))
                                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text(
                                                                        text = pkg.license,
                                                                        color = wikiGreen,
                                                                        fontSize = 10.sp,
                                                                        fontWeight = FontWeight.SemiBold
                                                                    )
                                                                }
                                                                Text(
                                                                    text = pkg.packageName,
                                                                    color = subTextColor.copy(alpha = 0.5f),
                                                                    fontSize = 10.sp
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                        item {
                                            Text(
                                                text = if (aiLanguage == "ja") "F-Droidでさらに検索する" else "Search more on F-Droid",
                                                color = wikiGreen,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier
                                                    .padding(top = 4.dp, bottom = 8.dp)
                                                    .clickable {
                                                        try {
                                                            val intent = android.content.Intent(
                                                                android.content.Intent.ACTION_VIEW,
                                                                android.net.Uri.parse("https://search.f-droid.org/?q=${android.net.Uri.encode(fdQuery)}")
                                                            ).apply {
                                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                            context.startActivity(intent)
                                                        } catch (e: Exception) {
                                                            // ignore
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }

                                // 4. AI Follow-up suggestions
                                val suggestions = response.suggestions ?: emptyList()
                                if (suggestions.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = if (aiLanguage == "ja") "次の質問候補" else "Suggested follow-ups",
                                            color = starTint,
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
                                                    colors = CardDefaults.cardColors(containerColor = cardBgColor),
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
                                                        color = textColor,
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
                                .background(panelBgColor),
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
                                        color = starTint,
                                        strokeWidth = 3.dp
                                    )
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = starTint,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .scale(1f)
                                    )
                                }

                                Text(
                                    text = if (aiLanguage == "ja") "AIが探索・分析中..." else "AI is searching & analyzing...",
                                    color = textColor,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = if (aiLanguage == "ja") "インストールされたアプリから最適なものを検索しています" else "Scanning your installed apps list for matches",
                                    color = subTextColor.copy(alpha = 0.5f),
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
                                    color = starTint,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (aiLanguage == "ja") 
                                        "ここに保存された事実や設定・指示は、AIアシスタントが会話をする際に常に前提知識として参照されます。" 
                                        else "Facts, preferences, and instructions saved here are automatically fed to the AI as context, so it never forgets them.",
                                    color = subTextColor,
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
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor
                                )
                            )

                            // Download Button
                            IconButton(
                                onClick = { 
                                    exportLauncher.launch("ai_launcher_memory.md")
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x22FFFFFF))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = "Download Memories",
                                    tint = textColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            // Upload Button
                            IconButton(
                                onClick = { 
                                    importLauncher.launch("*/*")
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x22FFFFFF))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Upload,
                                    contentDescription = "Upload Memories",
                                    tint = textColor,
                                    modifier = Modifier.size(20.dp)
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
                                            "チャットのカードにある「💡 記憶に登録」を押すか、上部のアップロードボタンからメディア（画像、音声、文書、動画など）をインポートしてAIによる再構築を行ってください。" 
                                            else "Click '💡 Save to Wiki' on cards, or use the upload button above to import media (images, audio, documents, video) for AI reconstruction.",
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
                                        colors = CardDefaults.cardColors(containerColor = cardBgColor),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
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
                                                color = textColor,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Spacer(modifier = Modifier.height(4.dp))

                                            Text(
                                                text = entry.content,
                                                color = subTextColor,
                                                fontSize = 13.sp,
                                                lineHeight = 20.sp
                                            )
                                            
                                            if (entry.tags.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                @OptIn(ExperimentalLayoutApi::class)
                                                FlowRow(
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    entry.tags.forEach { tag ->
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(Color(0x22FFFFFF))
                                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Text(text = "#$tag", color = Color(0xAAFFFFFF), fontSize = 11.sp)
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            if (entry.relatedLinkIds.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = if (aiLanguage == "ja") "関連リンク (${entry.relatedLinkIds.size})" else "Related Links (${entry.relatedLinkIds.size})",
                                                    color = Color(0xFF64B5F6),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                val relatedEntries = wikiEntries.filter { entry.relatedLinkIds.contains(it.id) }
                                                Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    relatedEntries.forEach { rel ->
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF64B5F6), modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(text = rel.title, color = subTextColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Auto-link memories button
                                item {
                                    val isAutoLinking by viewModel.isAutoLinking.collectAsState()
                                    TextButton(
                                        onClick = { viewModel.autoLinkWikiEntries() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF64B5F6)),
                                        enabled = !isAutoLinking
                                    ) {
                                        if (isAutoLinking) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = Color(0xFF64B5F6),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (aiLanguage == "ja") "自動整理中..." else "Auto-linking...",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.AutoFixHigh,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (aiLanguage == "ja") "AIで関連リンクを自動整理" else "Auto-link Memories with AI",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Bulk-organize memories button
                                item {
                                    val isBulkOrganizing by viewModel.isBulkOrganizing.collectAsState()
                                    TextButton(
                                        onClick = { viewModel.bulkOrganizeWikiEntries() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF81C784)),
                                        enabled = !isBulkOrganizing
                                    ) {
                                        if (isBulkOrganizing) {
                                            androidx.compose.material3.CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                color = wikiGreen,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (aiLanguage == "ja") "一括整理中..." else "Organizing...",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Category,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (aiLanguage == "ja") "AIでカテゴリーとタグを一括整理" else "Bulk Organize Tags & Categories",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
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
                            .background(cardBorderColor)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Input",
                            tint = textColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Text Field
                    BasicTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        textStyle = LocalTextStyle.current.copy(color = textColor, fontSize = 15.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_assistant_input_field"),
                        decorationBox = { innerTextField ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (textInput.isEmpty()) {
                                    Text(
                                        text = if (aiLanguage == "ja") "AIへの指示・アプリ検索..." else "Ask AI or search apps...",
                                        color = subTextColor.copy(alpha = 0.5f),
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
                                tint = textColor,
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

    if (pendingWikiEntry != null) {
        var title by remember { mutableStateOf(pendingWikiEntry!!.title) }
        var content by remember { mutableStateOf(pendingWikiEntry!!.content) }
        var category by remember { mutableStateOf(pendingWikiEntry!!.category) }
        var tagsString by remember { mutableStateOf(pendingWikiEntry!!.tags.joinToString(", ")) }
        val selectedRelatedIds = remember { mutableStateListOf(*pendingWikiEntry!!.relatedLinkIds.toTypedArray()) }
        val categories = listOf("Preference", "Instruction", "Fact", "General")
        
        AlertDialog(containerColor = panelBgColor, 
            onDismissRequest = { pendingWikiEntry = null },
            title = { Text(if (aiLanguage == "ja") "記憶を追加" else "Add Memory", color = textColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(if (aiLanguage == "ja") "タイトル" else "Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF5350), unfocusedBorderColor = if (isLight) Color(0x20000000) else Color(0x40FFFFFF),
                            focusedLabelColor = Color(0xFFEF5350), unfocusedLabelColor = if (isLight) Color(0x80000000) else Color(0x80FFFFFF),
                            focusedTextColor = textColor, unfocusedTextColor = textColor
                        )
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(if (aiLanguage == "ja") "記憶内容" else "Memory Content") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF5350), unfocusedBorderColor = if (isLight) Color(0x20000000) else Color(0x40FFFFFF),
                            focusedLabelColor = Color(0xFFEF5350), unfocusedLabelColor = if (isLight) Color(0x80000000) else Color(0x80FFFFFF),
                            focusedTextColor = textColor, unfocusedTextColor = textColor
                        )
                    )
                    OutlinedTextField(
                        value = tagsString,
                        onValueChange = { tagsString = it },
                        label = { Text(if (aiLanguage == "ja") "タグ (カンマ区切り, 最低5つ)" else "Tags (comma separated, min 5)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF5350), unfocusedBorderColor = if (isLight) Color(0x20000000) else Color(0x40FFFFFF),
                            focusedLabelColor = Color(0xFFEF5350), unfocusedLabelColor = if (isLight) Color(0x80000000) else Color(0x80FFFFFF),
                            focusedTextColor = textColor, unfocusedTextColor = textColor
                        )
                    )
                    Text(text = if (aiLanguage == "ja") "カテゴリー" else "Category", color = subTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categories.forEach { cat ->
                            val isSelected = category == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFFEF5350) else cardBorderColor)
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
                    if (wikiEntries.isNotEmpty()) {
                        Text(text = if (aiLanguage == "ja") "関連リンク" else "Related Links", color = subTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyColumn(modifier = Modifier.heightIn(max = 120.dp).fillMaxWidth()) {
                            items(wikiEntries) { w ->
                                val isSelected = selectedRelatedIds.contains(w.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            if (isSelected) selectedRelatedIds.remove(w.id) else selectedRelatedIds.add(w.id) 
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { if (it) selectedRelatedIds.add(w.id) else selectedRelatedIds.remove(w.id) }
                                    )
                                    Text(text = w.title, color = textColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsedTags = tagsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        if (title.isNotBlank() && content.isNotBlank() && parsedTags.size >= 5) {
                            viewModel.saveWikiEntry(
                                com.example.data.LlmWikiEntry(
                                    title = title,
                                    content = content,
                                    category = category,
                                    tags = parsedTags,
                                    relatedLinkIds = selectedRelatedIds.toList()
                                )
                            )
                            pendingWikiEntry = null
                        } else {
                            android.widget.Toast.makeText(context, if (aiLanguage == "ja") "タイトル、内容、および5つ以上のタグが必要です" else "Title, content, and at least 5 tags required", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(if (aiLanguage == "ja") "保存" else "Save", color = Color(0xFFEF5350))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingWikiEntry = null }) {
                    Text(if (aiLanguage == "ja") "キャンセル" else "Cancel", color = textColor)
                }
            },
            
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (editingEntry != null) {
        var title by remember { mutableStateOf(editingEntry!!.title) }
        var content by remember { mutableStateOf(editingEntry!!.content) }
        var category by remember { mutableStateOf(editingEntry!!.category) }
        var tagsString by remember { mutableStateOf(editingEntry!!.tags.joinToString(", ")) }
        val selectedRelatedIds = remember { mutableStateListOf(*editingEntry!!.relatedLinkIds.toTypedArray()) }
        val categories = listOf("Preference", "Instruction", "Fact", "General")
        
        AlertDialog(containerColor = panelBgColor, 
            onDismissRequest = { editingEntry = null },
            title = { Text(if (aiLanguage == "ja") "記憶を編集" else "Edit Memory", color = textColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(if (aiLanguage == "ja") "タイトル" else "Title") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF5350), unfocusedBorderColor = if (isLight) Color(0x20000000) else Color(0x40FFFFFF),
                            focusedLabelColor = Color(0xFFEF5350), unfocusedLabelColor = if (isLight) Color(0x80000000) else Color(0x80FFFFFF),
                            focusedTextColor = textColor, unfocusedTextColor = textColor
                        )
                    )
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(if (aiLanguage == "ja") "記憶内容" else "Memory Content") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF5350), unfocusedBorderColor = if (isLight) Color(0x20000000) else Color(0x40FFFFFF),
                            focusedLabelColor = Color(0xFFEF5350), unfocusedLabelColor = if (isLight) Color(0x80000000) else Color(0x80FFFFFF),
                            focusedTextColor = textColor, unfocusedTextColor = textColor
                        )
                    )
                    OutlinedTextField(
                        value = tagsString,
                        onValueChange = { tagsString = it },
                        label = { Text(if (aiLanguage == "ja") "タグ (カンマ区切り, 最低5つ)" else "Tags (comma separated, min 5)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFEF5350), unfocusedBorderColor = if (isLight) Color(0x20000000) else Color(0x40FFFFFF),
                            focusedLabelColor = Color(0xFFEF5350), unfocusedLabelColor = if (isLight) Color(0x80000000) else Color(0x80FFFFFF),
                            focusedTextColor = textColor, unfocusedTextColor = textColor
                        )
                    )
                    Text(text = if (aiLanguage == "ja") "カテゴリー" else "Category", color = subTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        categories.forEach { cat ->
                            val isSelected = category == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) Color(0xFFEF5350) else cardBorderColor)
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
                    val otherWikis = wikiEntries.filter { it.id != editingEntry!!.id }
                    if (otherWikis.isNotEmpty()) {
                        Text(text = if (aiLanguage == "ja") "関連リンク" else "Related Links", color = subTextColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyColumn(modifier = Modifier.heightIn(max = 120.dp).fillMaxWidth()) {
                            items(otherWikis) { w ->
                                val isSelected = selectedRelatedIds.contains(w.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            if (isSelected) selectedRelatedIds.remove(w.id) else selectedRelatedIds.add(w.id) 
                                        }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { if (it) selectedRelatedIds.add(w.id) else selectedRelatedIds.remove(w.id) }
                                    )
                                    Text(text = w.title, color = textColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val parsedTags = tagsString.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        if (title.isNotBlank() && content.isNotBlank() && parsedTags.size >= 5) {
                            viewModel.saveWikiEntry(
                                editingEntry!!.copy(
                                    title = title,
                                    content = content,
                                    category = category,
                                    tags = parsedTags,
                                    relatedLinkIds = selectedRelatedIds.toList(),
                                    lastUpdated = System.currentTimeMillis()
                                )
                            )
                            editingEntry = null
                        } else {
                            android.widget.Toast.makeText(context, if (aiLanguage == "ja") "タイトル、内容、および5つ以上のタグが必要です" else "Title, content, and at least 5 tags required", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(if (aiLanguage == "ja") "保存" else "Save", color = Color(0xFFEF5350))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingEntry = null }) {
                    Text(if (aiLanguage == "ja") "キャンセル" else "Cancel", color = textColor)
                }
            },
            
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (detailError != null) {
        AlertDialog(containerColor = panelBgColor, 
            onDismissRequest = { viewModel.clearDetailState() },
            title = { Text(if (aiLanguage == "ja") "エラーが発生しました" else "Error Occurred", color = textColor) },
            text = { Text(detailError ?: "", color = subTextColor) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearDetailState() }) {
                    Text(if (aiLanguage == "ja") "閉じる" else "Close", color = Color(0xFFEF5350))
                }
            },
            
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (isImportingWiki) {
        Dialog(onDismissRequest = {}) {
            Card(
                colors = CardDefaults.cardColors(containerColor = panelBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFFEF5350))
                    Text(
                        text = if (aiLanguage == "ja") "AIがWikiを構築・再編中..." else "AI is constructing & structuring Wiki...",
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
}

