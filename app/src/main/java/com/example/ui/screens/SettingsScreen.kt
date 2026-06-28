package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import android.widget.Toast
import android.content.Intent
import android.provider.Settings
import com.example.data.SettingsManager
import com.example.ui.AppLauncherViewModel
import com.example.ui.Localization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppLauncherViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    var primaryModel by remember { mutableStateOf(viewModel.settingsManager.getPrimaryModel()) }
    var backupModel by remember { mutableStateOf(viewModel.settingsManager.getBackupModel()) }
    var embeddingModel by remember { mutableStateOf(viewModel.settingsManager.getEmbeddingModel()) }
    var geminiApiKey by remember { mutableStateOf(viewModel.settingsManager.getGeminiApiKey()) }
    var isApiKeyVisible by remember { mutableStateOf(false) }
    val currentBgUrl by viewModel.currentBgUrl.collectAsState()
    val autoContrast by viewModel.autoContrast.collectAsState()
    val bgLuminance by viewModel.bgLuminance.collectAsState()
    val aiLanguage by viewModel.settingsManager.aiLanguage.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val includeIconlessSystemApps by viewModel.settingsManager.includeIconlessSystemApps.collectAsState()
    val customCategorizationPrompt by viewModel.settingsManager.customCategorizationPrompt.collectAsState()
    val colorTheme by viewModel.colorTheme.collectAsState()

    val isLight = colorTheme.startsWith("light_")
    val textColor = if (isLight) Color(0xFF11111F) else Color.White
    val subTextColor = if (isLight) Color(0xFF454558) else Color(0xB2FFFFFF)
    val cardBgColor = if (isLight) Color(0xDDFFFFFF) else Color(0x3CFFFFFF)
    val borderColor = if (isLight) Color(0x2E000000) else Color(0x24FFFFFF)
    val dividerColor = if (isLight) Color(0x1B000000) else Color(0x20FFFFFF)
    val topBarBgColor = if (isLight) Color(0xFDF8F7FC) else Color(0x900B0B1A)
    val panelBgColor = if (isLight) Color(0xFDF8F7FC) else Color(0xCD0A0A12)

    var customUrlInput by remember { mutableStateOf("") }
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var showAddMcpServerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(showCustomUrlDialog) {
        if (showCustomUrlDialog) {
            customUrlInput = if (currentBgUrl.startsWith("http")) currentBgUrl else ""
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                // Delete previous custom background files to save storage space
                context.filesDir.listFiles()?.forEach { f ->
                    if (f.name.startsWith("custom_background_") && f.name.endsWith(".jpg")) {
                        f.delete()
                    }
                }
                // Also delete the old legacy non-timestamped file if it exists
                val legacyFile = File(context.filesDir, "custom_background.jpg")
                if (legacyFile.exists()) {
                    legacyFile.delete()
                }

                // Generate a unique filename using timestamp
                val file = File(context.filesDir, "custom_background_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                viewModel.settingsManager.setBgImageUrl(file.absolutePath)
                Toast.makeText(context, Localization.get("custom_bg_success", aiLanguage), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, Localization.get("custom_bg_fail", aiLanguage, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(Localization.get("settings_title", aiLanguage), color = textColor, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = Localization.get("back_btn", aiLanguage), tint = textColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarBgColor
                )
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 0. Color Theme Selection Card (Adaptive and Legible)
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .testTag("color_theme_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = "Color Theme", tint = if (isLight) MaterialTheme.colorScheme.primary else Color(0xFF81C784))
                        val titleText = if (aiLanguage == "ja") "カラーテーマ設定" else "Color Theme Settings"
                        Text(titleText, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }

                    HorizontalDivider(color = dividerColor)

                    val darkThemes = listOf(
                        "dark_charcoal" to (if (aiLanguage == "ja") "コズミック・チャコール (標準)" else "Cosmic Charcoal (Default)"),
                        "dark_indigo" to (if (aiLanguage == "ja") "ミッドナイト・インディゴ" else "Midnight Indigo"),
                        "dark_emerald" to (if (aiLanguage == "ja") "フォレスト・エメラルド" else "Forest Emerald"),
                        "dark_obsidian" to (if (aiLanguage == "ja") "サンセット・オブシディアン" else "Sunset Obsidian")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        darkThemes.forEach { (themeKey, themeName) ->
                            val isSelected = colorTheme == themeKey
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) (if (isLight) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color(0xFF81C784).copy(alpha = 0.25f))
                                        else if (isLight) Color(0x0A000000) else cardBgColor
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) (if (isLight) MaterialTheme.colorScheme.primary else Color(0xFF81C784)) else borderColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { viewModel.setColorTheme(themeKey) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Theme color mini indicator dot
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when (themeKey) {
                                                    "dark_charcoal" -> Color(0xFF80DEEA)
                                                    "dark_indigo" -> Color(0xFF38BDF8)
                                                    "dark_emerald" -> Color(0xFF34D399)
                                                    "dark_obsidian" -> Color(0xFFF59E0B)
                                                    else -> Color.White
                                                }
                                            )
                                    )
                                    Text(
                                        text = themeName,
                                        color = if (isSelected) textColor else subTextColor,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1. AI Model Configuration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Model", tint = Color(0xFF81C784))
                        Text(Localization.get("ai_model_title", aiLanguage), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }

                    HorizontalDivider(color = dividerColor)

                    // Primary model
                    Column {
                        Text(Localization.get("primary_model_label", aiLanguage), fontSize = 12.sp, color = subTextColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = primaryModel,
                            onValueChange = {
                                primaryModel = it
                                viewModel.settingsManager.setPrimaryModel(it)
                            },
                            textStyle = LocalTextStyle.current.copy(color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF81C784),
                                unfocusedBorderColor = borderColor,
                                focusedLabelColor = Color(0xFF81C784),
                                unfocusedLabelColor = subTextColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("primary_model_input")
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                "gemini-flash-latest",
                                "gemini-flash-lite-latest"
                            ).forEach { modelOption ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (primaryModel == modelOption) Color(0xFF81C784).copy(alpha = 0.25f)
                                            else cardBgColor
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (primaryModel == modelOption) Color(0xFF81C784)
                                                    else Color(0x1FFFFFFF),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            primaryModel = modelOption
                                            viewModel.settingsManager.setPrimaryModel(modelOption)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = modelOption.removePrefix("models/"),
                                        color = if (primaryModel == modelOption) Color(0xFF81C784) else Color(0xB2FFFFFF),
                                        fontSize = 11.sp,
                                        fontWeight = if (primaryModel == modelOption) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Backup model
                    Column {
                        Text(Localization.get("backup_model_label", aiLanguage), fontSize = 12.sp, color = subTextColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = backupModel,
                            onValueChange = {
                                backupModel = it
                                viewModel.settingsManager.setBackupModel(it)
                            },
                            textStyle = LocalTextStyle.current.copy(color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF64B5F6),
                                unfocusedBorderColor = borderColor,
                                focusedLabelColor = Color(0xFF64B5F6),
                                unfocusedLabelColor = subTextColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            listOf(
                                "gemini-flash-latest",
                                "gemini-flash-lite-latest"
                            ).forEach { modelOption ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (backupModel == modelOption) Color(0xFF64B5F6).copy(alpha = 0.25f)
                                            else cardBgColor
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (backupModel == modelOption) Color(0xFF64B5F6)
                                                    else Color(0x1FFFFFFF),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            backupModel = modelOption
                                            viewModel.settingsManager.setBackupModel(modelOption)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = modelOption.removePrefix("models/"),
                                        color = if (backupModel == modelOption) Color(0xFF64B5F6) else Color(0xB2FFFFFF),
                                        fontSize = 11.sp,
                                        fontWeight = if (backupModel == modelOption) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    // Embedding model
                    Column {
                        Text(Localization.get("embedding_model_label", aiLanguage), fontSize = 12.sp, color = subTextColor)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = embeddingModel,
                            onValueChange = {
                                embeddingModel = it
                                viewModel.settingsManager.setEmbeddingModel(it)
                            },
                            textStyle = LocalTextStyle.current.copy(color = textColor),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFB74D),
                                unfocusedBorderColor = borderColor,
                                focusedLabelColor = Color(0xFFFFB74D),
                                unfocusedLabelColor = subTextColor
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Gemini API Key (Custom)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(Localization.get("gemini_api_key_label", aiLanguage), fontSize = 12.sp, color = subTextColor)
                            Text(
                                text = Localization.get("get_api_key_link_text", aiLanguage),
                                fontSize = 11.sp,
                                color = Color(0xFF60A5FA),
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline,
                                modifier = Modifier
                                    .clickable {
                                        try {
                                            uriHandler.openUri("https://aistudio.google.com/app/apikey")
                                        } catch (e: Exception) {
                                            Toast.makeText(context, Localization.get("link_error", aiLanguage).format(e.message ?: ""), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .padding(vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = geminiApiKey,
                            onValueChange = {
                                geminiApiKey = it
                                viewModel.settingsManager.setGeminiApiKey(it)
                            },
                            placeholder = { Text(Localization.get("gemini_api_key_placeholder", aiLanguage), color = subTextColor.copy(alpha = 0.4f), fontSize = 12.sp) },
                            textStyle = LocalTextStyle.current.copy(color = textColor),
                            visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image = if (isApiKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                                val description = if (isApiKeyVisible) "Hide API Key" else "Show API Key"
                                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                    Icon(
                                        imageVector = image,
                                        contentDescription = description,
                                        tint = Color(0xB2FFFFFF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFEF5350),
                                unfocusedBorderColor = borderColor,
                                focusedLabelColor = Color(0xFFEF5350),
                                unfocusedLabelColor = subTextColor
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gemini_api_key_input")
                        )
                    }
                }
            }

            // AI Language Selection Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .testTag("language_selection_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Language, contentDescription = "Language", tint = Color(0xFF81C784))
                        Text(Localization.get("lang_title", aiLanguage), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }

                    HorizontalDivider(color = dividerColor)

                    Text(
                        Localization.get("lang_desc", aiLanguage),
                        fontSize = 12.sp,
                        color = subTextColor,
                        lineHeight = 16.sp
                    )

                    val languages = listOf(
                        "ja" to "日本語 (Japanese)",
                        "en" to "English",
                        "ko" to "한국어 (Korean)",
                        "zh" to "中文 (Chinese)"
                    )

                    languages.forEach { (code, name) ->
                        val isSelected = aiLanguage == code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x3D81C784) else Color.Transparent)
                                .clickable { viewModel.settingsManager.setAiLanguage(code) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(name, color = textColor, fontSize = 14.sp)
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFF81C784))
                            }
                        }
                    }
                }
            }

            // Display Layout Settings Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .testTag("layout_mode_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.GridView, contentDescription = "Layout", tint = Color(0xFF64B5F6))
                        Text(Localization.get("layout_mode_title", aiLanguage), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }

                    HorizontalDivider(color = dividerColor)

                    Text(
                        Localization.get("layout_mode_desc", aiLanguage),
                        fontSize = 12.sp,
                        color = subTextColor,
                        lineHeight = 16.sp
                    )

                    val modes = listOf(
                        "GRID" to Localization.get("layout_mode_grid", aiLanguage),
                        "LIST" to Localization.get("layout_mode_list", aiLanguage),
                        "COMPACT" to Localization.get("layout_mode_compact", aiLanguage)
                    )

                    modes.forEach { (mode, label) ->
                        val isSelected = viewMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x3D64B5F6) else Color.Transparent)
                                .clickable { viewModel.setViewMode(mode) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        "GRID" -> Icons.Default.GridView
                                        "COMPACT" -> Icons.Default.Dashboard
                                        else -> Icons.Default.List
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF64B5F6) else Color(0x80FFFFFF)
                                )
                                Text(label, color = textColor, fontSize = 14.sp)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFF64B5F6))
                            }
                        }
                    }
                }
            }

            // Icon Shape Settings Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .testTag("icon_shape_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = "Icon Shape", tint = Color(0xFFA5D6A7))
                        val titleText = if (aiLanguage == "ja") "表示アイコンの形状" else "Icon Shape"
                        Text(titleText, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }

                    HorizontalDivider(color = dividerColor)

                    val descText = if (aiLanguage == "ja") {
                        "ランチャー全体のアプリ表示アイコンの形状をカスタマイズします。"
                    } else {
                        "Customize the shape of all application icons in the launcher."
                    }
                    Text(
                        descText,
                        fontSize = 12.sp,
                        color = subTextColor,
                        lineHeight = 16.sp
                    )

                    val shapes = listOf(
                        "ROUNDED_RECT" to if (aiLanguage == "ja") "角丸長方形 (標準)" else "Rounded Rectangle (Default)",
                        "CIRCLE" to if (aiLanguage == "ja") "真円" else "Circle",
                        "SQUARE" to if (aiLanguage == "ja") "正方形" else "Square",
                        "SQUIRCLE" to if (aiLanguage == "ja") "スクアクル (ソフトな角丸)" else "Squircle"
                    )

                    val activeIconShape by viewModel.iconShape.collectAsState()

                    shapes.forEach { (shapeKey, shapeLabel) ->
                        val isSelected = activeIconShape == shapeKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x3DA5D6A7) else Color.Transparent)
                                .clickable { viewModel.setIconShape(shapeKey) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Mini visual preview of the shape
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(
                                            when (shapeKey) {
                                                "CIRCLE" -> CircleShape
                                                "SQUARE" -> RectangleShape
                                                "SQUIRCLE" -> RoundedCornerShape(percent = 38)
                                                else -> RoundedCornerShape(4.dp)
                                            }
                                        )
                                        .background(if (isSelected) Color(0xFFA5D6A7) else Color(0x80FFFFFF))
                                )
                                Text(shapeLabel, color = textColor, fontSize = 14.sp)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFFA5D6A7))
                            }
                        }
                    }
                }
            }

            // 2. Space Background Wallpaper Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Wallpaper, contentDescription = "Background", tint = Color(0xFFFF8A65))
                        Text(Localization.get("bg_preset_title", aiLanguage), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }

                    HorizontalDivider(color = dividerColor)

                    // Preset list
                    SettingsManager.SPACE_PRESETS.forEach { preset ->
                        val isSelected = currentBgUrl == preset.value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x3DFF8A65) else Color.Transparent)
                                .clickable { viewModel.settingsManager.setBgImageUrl(preset.value) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(preset.name, color = textColor, fontSize = 14.sp)
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFFFF8A65))
                            }
                        }
                    }

                    // Album/Gallery Image picker option
                    val isCustomFile = currentBgUrl.startsWith(context.filesDir.absolutePath) || currentBgUrl.contains("custom_background")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isCustomFile) Color(0x3DFF8A65) else Color.Transparent)
                            .clickable { galleryLauncher.launch("image/*") }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery", tint = Color(0xFFFF8A65), modifier = Modifier.size(20.dp))
                            Text(Localization.get("album_select", aiLanguage), color = textColor, fontSize = 14.sp)
                        }
                        if (isCustomFile) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFFFF8A65))
                        }
                    }

                    HorizontalDivider(color = borderColor)

                    // Custom URL button
                    Button(
                        onClick = { showCustomUrlDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF8A65)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Custom URL", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Localization.get("bg_custom_url", aiLanguage), color = textColor)
                    }

                    HorizontalDivider(color = borderColor)

                    // Auto Contrast Adjustment option
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Brightness6,
                                    contentDescription = "Contrast",
                                    tint = Color(0xFFFF8A65),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        Localization.get("auto_contrast_title", aiLanguage),
                                        color = textColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        Localization.get("auto_contrast_desc", aiLanguage),
                                        color = textColor.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                            Switch(
                                checked = autoContrast,
                                onCheckedChange = { viewModel.settingsManager.setAutoContrast(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFF8A65),
                                    checkedTrackColor = Color(0x66FF8A65),
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = Color(0x33FFFFFF)
                                )
                            )
                        }

                        // Include Iconless System Apps toggle
                        HorizontalDivider(color = borderColor, modifier = Modifier.padding(vertical = 4.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Apps,
                                    contentDescription = "Apps",
                                    tint = Color(0xFF64B5F6),
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        Localization.get("include_iconless_system_apps_title", aiLanguage),
                                        color = textColor,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        Localization.get("include_iconless_system_apps_desc", aiLanguage),
                                        color = textColor.copy(alpha = 0.6f),
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                            Switch(
                                checked = includeIconlessSystemApps,
                                onCheckedChange = { viewModel.settingsManager.setIncludeIconlessSystemApps(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFF64B5F6),
                                    checkedTrackColor = Color(0x6664B5F6),
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = Color(0x33FFFFFF)
                                )
                            )
                        }

                        // Display the detected brightness and state
                        if (currentBgUrl != "procedural_nebula") {
                            val brightnessPercent = (bgLuminance * 100).toInt()
                            val brightnessLabel = if (brightnessPercent > 50) {
                                if (aiLanguage == "ja") "明るい (自動調光強)" else "Bright (High Dimming)"
                            } else {
                                if (aiLanguage == "ja") "暗い (自動調光弱)" else "Dark (Low Dimming)"
                            }
                            Text(
                                text = String.format(Localization.get("bg_luminance_detected", aiLanguage), brightnessPercent, brightnessLabel),
                                color = Color(0xFFFF8A65).copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 30.dp)
                            )
                        }
                    }
                }
            }

            // AI Analysis Instructions & Bulk Re-analyze Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .testTag("ai_instructions_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "AI Instructions", tint = Color(0xFFFFB74D))
                        Text(Localization.get("custom_prompt_title", aiLanguage), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Text(
                        Localization.get("custom_prompt_desc", aiLanguage),
                        fontSize = 12.sp,
                        color = subTextColor,
                        lineHeight = 16.sp
                    )

                    OutlinedTextField(
                        value = customCategorizationPrompt,
                        onValueChange = { viewModel.settingsManager.setCustomCategorizationPrompt(it) },
                        placeholder = { Text(Localization.get("custom_prompt_hint", aiLanguage), color = Color(0x99FFFFFF)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0x11FFFFFF),
                            unfocusedContainerColor = Color(0x11FFFFFF),
                            focusedBorderColor = Color(0xFF64B5F6),
                            unfocusedBorderColor = Color(0x33FFFFFF),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Examples row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable { viewModel.settingsManager.setCustomCategorizationPrompt(Localization.get("custom_prompt_example1", aiLanguage)) }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(Localization.get("custom_prompt_example1", aiLanguage), color = subTextColor, fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22FFFFFF))
                                .clickable { viewModel.settingsManager.setCustomCategorizationPrompt(Localization.get("custom_prompt_example2", aiLanguage)) }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(Localization.get("custom_prompt_example2", aiLanguage), color = subTextColor, fontSize = 10.sp, textAlign = TextAlign.Center)
                        }
                    }

                    HorizontalDivider(color = borderColor, modifier = Modifier.padding(vertical = 4.dp))

                    // Re-analyze all action
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { 
                                viewModel.reanalyzeAllBulk() 
                                Toast.makeText(context, Localization.get("analyzing", aiLanguage, "", 1, 1).replace("1/1", ""), Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Re-analyze All Bulk", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Localization.get("bulk_analyze_option", aiLanguage), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                        Button(
                            onClick = { 
                                viewModel.reanalyzeAllSequential()
                                Toast.makeText(context, Localization.get("analyzing", aiLanguage, "", 1, 1).replace("1/1", ""), Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Re-analyze All Sequential", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(Localization.get("sequential_analyze_option", aiLanguage), fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center)
                        }
                    }
                }
            }

            // 3. Security Warning Notice (MANDATORY per secret management skill guidelines)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x2BFF5252)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x4DFF5252), RoundedCornerShape(16.dp))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Warning, contentDescription = "Security Notice", tint = Color(0xFFFF5252))
                    Column {
                        Text(
                            text = Localization.get("security_warning_title", aiLanguage),
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = Localization.get("security_warning_desc", aiLanguage),
                            color = Color(0xE6FFFFFF),
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Model Context Protocol (MCP) Configuration Card
            val mcpServers by viewModel.mcpServers.collectAsState()

            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                    .testTag("mcp_settings_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = "MCP Settings", tint = Color(0xFF4CAF50))
                        Text(Localization.get("mcp_settings_title", aiLanguage), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Text(
                        Localization.get("mcp_settings_desc", aiLanguage),
                        fontSize = 12.sp,
                        color = subTextColor,
                        lineHeight = 16.sp
                    )
                    
                    HorizontalDivider(color = dividerColor)

                    // 1. Built-in MCP Tools Indicators (Active)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0x224CAF50), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(Localization.get("mcp_builtin_title", aiLanguage), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                            Text(Localization.get("mcp_builtin_desc", aiLanguage), fontSize = 11.sp, color = Color(0x99FFFFFF))
                        }
                    }

                    HorizontalDivider(color = dividerColor)

                    // 2. Custom MCP Servers list
                    Text(Localization.get("mcp_custom_servers", aiLanguage), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)

                    if (mcpServers.isEmpty()) {
                        Text(
                            Localization.get("mcp_no_custom_servers", aiLanguage),
                            fontSize = 12.sp,
                            color = subTextColor.copy(alpha = 0.4f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            mcpServers.forEach { server ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0x12FFFFFF), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(server.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
                                        if (server.description.isNotBlank()) {
                                            Text(server.description, fontSize = 11.sp, color = subTextColor.copy(alpha = 0.5f))
                                        }
                                        Text(server.endpointUrl, fontSize = 10.sp, color = subTextColor.copy(alpha = 0.4f), maxLines = 1)
                                    }
                                    
                                    // Enabled toggle switch
                                    Switch(
                                        checked = server.isEnabled,
                                        onCheckedChange = { isEnabled ->
                                            viewModel.toggleMcpServer(server, isEnabled)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color(0xFF4CAF50),
                                            checkedTrackColor = Color(0x4D4CAF50)
                                        )
                                    )

                                    // Delete button
                                    IconButton(
                                        onClick = { viewModel.deleteMcpServer(server.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Server",
                                            tint = Color(0xFFEF5350),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Button to open dialog
                    Button(
                        onClick = { showAddMcpServerDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1BFFFFFF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x33FFFFFF)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_mcp_server_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Server", modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Localization.get("mcp_add_server_btn", aiLanguage), color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // AI Category Management Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = "Category", tint = Color(0xFF9575CD))
                        Text(Localization.get("merge_categories_title", aiLanguage), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Text(
                        Localization.get("merge_categories_desc", aiLanguage),
                        fontSize = 12.sp,
                        color = subTextColor,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    val isMerging by viewModel.isMergingCategories.collectAsState()
                    Button(
                        onClick = { viewModel.mergeCategories() },
                        enabled = !isMerging,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF673AB7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isMerging) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = textColor, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Merging...", color = textColor)
                        } else {
                            Text(Localization.get("merge_categories_btn", aiLanguage), fontWeight = FontWeight.Bold, color = textColor)
                        }
                    }
                }
            }

            // Default Launcher Configuration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Default Home", tint = Color(0xFF64B5F6))
                        Text(Localization.get("set_default_home_title", aiLanguage), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Text(
                        Localization.get("set_default_home_desc", aiLanguage),
                        fontSize = 12.sp,
                        color = subTextColor,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(Settings.ACTION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("set_default_home_button")
                    ) {
                        Text(Localization.get("set_default_home_btn", aiLanguage), fontWeight = FontWeight.Bold, color = textColor)
                    }
                }
            }

            // 4. Reset & Clear Cache Card (Battery and operation helper)
            Card(
                colors = CardDefaults.cardColors(containerColor = cardBgColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = "Cache Clean", tint = Color(0xFFEF5350))
                        Text(Localization.get("data_manage_title", aiLanguage), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = textColor)
                    }
                    Text(
                        Localization.get("data_manage_desc", aiLanguage),
                        fontSize = 12.sp,
                        color = subTextColor,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = { viewModel.clearCache() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_cache_button")
                    ) {
                        Text(Localization.get("reset_btn", aiLanguage), fontWeight = FontWeight.Bold, color = textColor)
                    }
                }
            }
        }
    }

    if (showCustomUrlDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            containerColor = panelBgColor,
            tonalElevation = 0.dp,
            title = { Text(Localization.get("dialog_custom_bg_title", aiLanguage), color = textColor) },
            text = {
                Column {
                    Text(Localization.get("dialog_custom_bg_desc", aiLanguage), color = subTextColor)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        placeholder = { Text("https://example.com/nebula.jpg") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = textColor),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customUrlInput.isNotBlank()) {
                            viewModel.settingsManager.setBgImageUrl(customUrlInput)
                        }
                        showCustomUrlDialog = false
                    }
                ) {
                    Text(Localization.get("set", aiLanguage), color = textColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUrlDialog = false }) {
                    Text(Localization.get("cancel", aiLanguage), color = subTextColor)
                }
            }
        )
    }

    if (showAddMcpServerDialog) {
        var serverNameInput by remember { mutableStateOf("") }
        var serverDescInput by remember { mutableStateOf("") }
        var serverUrlInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddMcpServerDialog = false },
            containerColor = panelBgColor,
            tonalElevation = 0.dp,
            title = { Text(Localization.get("mcp_add_dialog_title", aiLanguage), color = textColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = serverNameInput,
                        onValueChange = { serverNameInput = it },
                        label = { Text(Localization.get("mcp_server_name_label", aiLanguage), color = subTextColor) },
                        placeholder = { Text("My Remote MCP Server") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = textColor),
                        modifier = Modifier.fillMaxWidth().testTag("mcp_input_name")
                    )
                    OutlinedTextField(
                        value = serverDescInput,
                        onValueChange = { serverDescInput = it },
                        label = { Text(Localization.get("mcp_server_desc_label", aiLanguage), color = subTextColor) },
                        placeholder = { Text("Handles system files/scripts") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = textColor),
                        modifier = Modifier.fillMaxWidth().testTag("mcp_input_desc")
                    )
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        label = { Text(Localization.get("mcp_server_url_label", aiLanguage), color = subTextColor) },
                        placeholder = { Text("http://192.168.1.5:5000/api") },
                        textStyle = androidx.compose.ui.text.TextStyle(color = textColor),
                        modifier = Modifier.fillMaxWidth().testTag("mcp_input_url")
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (serverNameInput.isNotBlank() && serverUrlInput.isNotBlank()) {
                            viewModel.addMcpServer(serverNameInput, serverDescInput, serverUrlInput)
                        }
                        showAddMcpServerDialog = false
                    },
                    enabled = serverNameInput.isNotBlank() && serverUrlInput.isNotBlank()
                ) {
                    Text(Localization.get("set", aiLanguage), color = if (serverNameInput.isNotBlank() && serverUrlInput.isNotBlank()) textColor else subTextColor.copy(alpha = 0.5f))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMcpServerDialog = false }) {
                    Text(Localization.get("cancel", aiLanguage), color = subTextColor)
                }
            }
        )
    }
}
