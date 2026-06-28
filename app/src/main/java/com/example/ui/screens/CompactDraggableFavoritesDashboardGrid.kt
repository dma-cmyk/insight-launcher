package com.example.ui.screens

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.data.InstalledApp
import com.example.ui.AppLauncherViewModel
import kotlin.math.roundToInt

@Composable
fun CompactDraggableFavoritesDashboardGrid(
    favoriteApps: List<InstalledApp>,
    viewModel: AppLauncherViewModel
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
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
                                        val temp = mutable[draggingIndex!!]
                                        mutable[draggingIndex!!] = mutable[targetIndex]
                                        mutable[targetIndex] = temp
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
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
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
    }
}
