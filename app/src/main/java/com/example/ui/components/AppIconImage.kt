package com.example.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.SettingsManager
import com.example.data.UsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File

@Composable
fun AppIconImage(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context.applicationContext) }
    val usageTracker = remember { UsageTracker(context.applicationContext) }

    val iconShape by settingsManager.iconShape.collectAsState()
    val customIcons by usageTracker.customIcons.collectAsState()

    val customIcon = customIcons[packageName]

    val shape = remember(iconShape) {
        when (iconShape) {
            "CIRCLE" -> CircleShape
            "SQUARE" -> RectangleShape
            "SQUIRCLE" -> RoundedCornerShape(percent = 38)
            else -> RoundedCornerShape(12.dp) // "ROUNDED_RECT"
        }
    }

    // Asynchronously load the app icon from PackageManager
    val iconState = produceState<Drawable?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (e: Exception) {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Color(0x1FFFFFFF)), // Translucent glass backdrop
        contentAlignment = Alignment.Center
    ) {
        if (!customIcon.isNullOrEmpty()) {
            if (customIcon.startsWith("image:")) {
                val path = customIcon.substringAfter("image:")
                val file = remember(path) { File(path) }
                AsyncImage(
                    model = file,
                    contentDescription = "Custom App Icon",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                // Render custom emoji or symbol
                Text(
                    text = customIcon,
                    fontSize = (size.value * 0.55f).sp
                )
            }
        } else {
            val drawable = iconState.value
            if (drawable != null) {
                // Convert to bitmap safely on main thread or convert directly
                val bitmap = remember(drawable) {
                    drawable.toBitmap(
                        width = size.value.toInt().coerceAtLeast(100),
                        height = size.value.toInt().coerceAtLeast(100)
                    ).asImageBitmap()
                }
                Image(
                    bitmap = bitmap,
                    contentDescription = "App Icon",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback Android icon with high contrast styling
                Icon(
                    imageVector = Icons.Default.Android,
                    contentDescription = "Default App Icon",
                    tint = Color(0xFFA5D6A7),
                    modifier = Modifier.size(size * 0.6f)
                )
            }
        }
    }
}
