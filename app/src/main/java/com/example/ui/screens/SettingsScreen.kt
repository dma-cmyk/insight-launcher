package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextDecoration
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
    val currentBgUrl by viewModel.currentBgUrl.collectAsState()
    val aiLanguage by viewModel.settingsManager.aiLanguage.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()

    var customUrlInput by remember { mutableStateOf("") }
    var showCustomUrlDialog by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val file = File(context.filesDir, "custom_background.jpg")
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
                title = { Text(Localization.get("settings_title", aiLanguage), color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = Localization.get("back_btn", aiLanguage), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0x900B0B1A)
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
            // 1. AI Model Configuration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x3CFFFFFF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
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
                        Text(Localization.get("ai_model_title", aiLanguage), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    HorizontalDivider(color = Color(0x20FFFFFF))

                    // Primary model
                    Column {
                        Text(Localization.get("primary_model_label", aiLanguage), fontSize = 12.sp, color = Color(0xB2FFFFFF))
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = primaryModel,
                            onValueChange = {
                                primaryModel = it
                                viewModel.settingsManager.setPrimaryModel(it)
                            },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF81C784),
                                unfocusedBorderColor = Color(0x40FFFFFF),
                                focusedLabelColor = Color(0xFF81C784),
                                unfocusedLabelColor = Color(0xB2FFFFFF)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("primary_model_input")
                        )
                    }

                    // Backup model
                    Column {
                        Text(Localization.get("backup_model_label", aiLanguage), fontSize = 12.sp, color = Color(0xB2FFFFFF))
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = backupModel,
                            onValueChange = {
                                backupModel = it
                                viewModel.settingsManager.setBackupModel(it)
                            },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF64B5F6),
                                unfocusedBorderColor = Color(0x40FFFFFF),
                                focusedLabelColor = Color(0xFF64B5F6),
                                unfocusedLabelColor = Color(0xB2FFFFFF)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Embedding model
                    Column {
                        Text(Localization.get("embedding_model_label", aiLanguage), fontSize = 12.sp, color = Color(0xB2FFFFFF))
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = embeddingModel,
                            onValueChange = {
                                embeddingModel = it
                                viewModel.settingsManager.setEmbeddingModel(it)
                            },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFB74D),
                                unfocusedBorderColor = Color(0x40FFFFFF),
                                focusedLabelColor = Color(0xFFFFB74D),
                                unfocusedLabelColor = Color(0xB2FFFFFF)
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
                            Text(Localization.get("gemini_api_key_label", aiLanguage), fontSize = 12.sp, color = Color(0xB2FFFFFF))
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
                            placeholder = { Text(Localization.get("gemini_api_key_placeholder", aiLanguage), color = Color(0x66FFFFFF), fontSize = 12.sp) },
                            textStyle = LocalTextStyle.current.copy(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFEF5350),
                                unfocusedBorderColor = Color(0x40FFFFFF),
                                focusedLabelColor = Color(0xFFEF5350),
                                unfocusedLabelColor = Color(0xB2FFFFFF)
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
                colors = CardDefaults.cardColors(containerColor = Color(0x3CFFFFFF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
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
                        Text(Localization.get("lang_title", aiLanguage), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    HorizontalDivider(color = Color(0x20FFFFFF))

                    Text(
                        Localization.get("lang_desc", aiLanguage),
                        fontSize = 12.sp,
                        color = Color(0xB2FFFFFF),
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
                            Text(name, color = Color.White, fontSize = 14.sp)
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFF81C784))
                            }
                        }
                    }
                }
            }

            // Display Layout Settings Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x3CFFFFFF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
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
                        Text(Localization.get("layout_mode_title", aiLanguage), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    HorizontalDivider(color = Color(0x20FFFFFF))

                    Text(
                        Localization.get("layout_mode_desc", aiLanguage),
                        fontSize = 12.sp,
                        color = Color(0xB2FFFFFF),
                        lineHeight = 16.sp
                    )

                    val modes = listOf(
                        "GRID" to Localization.get("layout_mode_grid", aiLanguage),
                        "LIST" to Localization.get("layout_mode_list", aiLanguage)
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
                                    imageVector = if (mode == "GRID") Icons.Default.GridView else Icons.Default.List,
                                    contentDescription = null,
                                    tint = if (isSelected) Color(0xFF64B5F6) else Color(0x80FFFFFF)
                                )
                                Text(label, color = Color.White, fontSize = 14.sp)
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
                colors = CardDefaults.cardColors(containerColor = Color(0x3CFFFFFF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
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
                        Text(titleText, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    HorizontalDivider(color = Color(0x20FFFFFF))

                    val descText = if (aiLanguage == "ja") {
                        "ランチャー全体のアプリ表示アイコンの形状をカスタマイズします。"
                    } else {
                        "Customize the shape of all application icons in the launcher."
                    }
                    Text(
                        descText,
                        fontSize = 12.sp,
                        color = Color(0xB2FFFFFF),
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
                                Text(shapeLabel, color = Color.White, fontSize = 14.sp)
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
                colors = CardDefaults.cardColors(containerColor = Color(0x3CFFFFFF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
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
                        Text(Localization.get("bg_preset_title", aiLanguage), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    HorizontalDivider(color = Color(0x20FFFFFF))

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
                            Text(preset.name, color = Color.White, fontSize = 14.sp)
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFFFF8A65))
                            }
                        }
                    }

                    // Album/Gallery Image picker option
                    val isCustomFile = currentBgUrl.startsWith("/") || currentBgUrl.contains("custom_background.jpg")
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
                            Text(Localization.get("album_select", aiLanguage), color = Color.White, fontSize = 14.sp)
                        }
                        if (isCustomFile) {
                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color(0xFFFF8A65))
                        }
                    }

                    HorizontalDivider(color = Color(0x14FFFFFF))

                    // Custom URL button
                    Button(
                        onClick = { showCustomUrlDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF8A65)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, contentDescription = "Custom URL", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(Localization.get("bg_custom_url", aiLanguage), color = Color.White)
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
                            color = Color.White,
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

            // AI Category Management Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x3CFFFFFF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
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
                        Text(Localization.get("merge_categories_title", aiLanguage), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(
                        Localization.get("merge_categories_desc", aiLanguage),
                        fontSize = 12.sp,
                        color = Color(0xB2FFFFFF),
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
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Merging...", color = Color.White)
                        } else {
                            Text(Localization.get("merge_categories_btn", aiLanguage), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Default Launcher Configuration Card
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x3CFFFFFF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(16.dp))
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
                        Text(Localization.get("set_default_home_title", aiLanguage), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(
                        Localization.get("set_default_home_desc", aiLanguage),
                        fontSize = 12.sp,
                        color = Color(0xB2FFFFFF),
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
                        Text(Localization.get("set_default_home_btn", aiLanguage), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // 4. Reset & Clear Cache Card (Battery and operation helper)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(16.dp))
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
                        Text(Localization.get("data_manage_title", aiLanguage), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(
                        Localization.get("data_manage_desc", aiLanguage),
                        fontSize = 12.sp,
                        color = Color(0xB2FFFFFF),
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
                        Text(Localization.get("reset_btn", aiLanguage), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }

    if (showCustomUrlDialog) {
        AlertDialog(
            onDismissRequest = { showCustomUrlDialog = false },
            title = { Text(Localization.get("dialog_custom_bg_title", aiLanguage)) },
            text = {
                Column {
                    Text(Localization.get("dialog_custom_bg_desc", aiLanguage))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customUrlInput,
                        onValueChange = { customUrlInput = it },
                        placeholder = { Text("https://example.com/nebula.jpg") },
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
                    Text(Localization.get("set", aiLanguage))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomUrlDialog = false }) {
                    Text(Localization.get("cancel", aiLanguage))
                }
            }
        )
    }
}
