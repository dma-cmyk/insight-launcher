package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.InstalledApp
import com.example.ui.AppLauncherViewModel
import com.example.ui.Localization
import kotlin.math.roundToInt

@Composable
fun CompactDraggableFavoritesGrid(
    favoriteApps: List<InstalledApp>,
    viewModel: AppLauncherViewModel,
    aiLanguage: String,
    modifier: Modifier = Modifier
) {
    var dragList by remember { mutableStateOf(favoriteApps) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var lastReorderTime by remember { mutableStateOf(0L) }

    LaunchedEffect(favoriteApps) {
        if (draggingIndex == null) {
            dragList = favoriteApps
        }
    }

    val itemPositions = remember { mutableStateMapOf<Int, Rect>() }
    var containerCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { containerCoords = it }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        val rootOffset = containerCoords?.localToWindow(startOffset) ?: startOffset
                        val matchedIndex = itemPositions.entries.firstOrNull { (_, rect) ->
                            rect.contains(rootOffset)
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
                                val now = System.currentTimeMillis()
                                if (now - lastReorderTime > 150L) {
                                    val targetIndex = itemPositions.entries.firstOrNull { (index, bounds) ->
                                        index != draggingIndex && bounds.contains(currentCenter)
                                    }?.key
                                    if (targetIndex != null && targetIndex < dragList.size) {
                                        val mutable = dragList.toMutableList()
                                        val temp = mutable.removeAt(draggingIndex!!)
                                        mutable.add(targetIndex, temp)
                                        dragList = mutable

                                        val targetBounds = itemPositions[targetIndex]
                                        if (targetBounds != null) {
                                            dragOffset += originalBounds.center - targetBounds.center
                                        }
                                        draggingIndex = targetIndex
                                        lastReorderTime = now
                                    }
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
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            // Header
            CompactSectionHeader(
                title = getCategoryDisplayName("FAVORITE", aiLanguage),
                icon = Icons.Default.Star,
                iconColor = Color(0xFFFFD700),
                badgeText = "${dragList.size}"
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (dragList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = Localization.get("no_apps", aiLanguage),
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            } else {
                val rows = dragList.chunked(2)
                rows.forEachIndexed { rowIndex, rowApps ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (colIndex in 0 until 2) {
                            if (colIndex < rowApps.size) {
                                val itemIndex = rowIndex * 2 + colIndex
                                val app = rowApps[colIndex]
                                val isCurrentDragging = draggingIndex == itemIndex

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .onGloballyPositioned { coords ->
                                            if (!isCurrentDragging) {
                                                itemPositions[itemIndex] = coords.boundsInWindow()
                                            }
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
                                    CompactAppItemWithSummary(
                                        app = app,
                                        onClick = { viewModel.selectApp(app) },
                                        onLongClick = null,
                                        isFavorite = true,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Bottom space to avoid overlap with floating dock
            Spacer(modifier = Modifier.height(180.dp))
        }
    }
}
