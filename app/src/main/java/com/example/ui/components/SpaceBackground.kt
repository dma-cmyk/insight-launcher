package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import kotlin.random.Random

import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun SpaceBackground(
    bgUrl: String,
    modifier: Modifier = Modifier,
    scrollOffsetX: Float = 0f,
    scrollOffsetY: Float = 0f,
    bgLuminance: Float = 0.05f,
    autoContrast: Boolean = true
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (bgUrl == "procedural_nebula") {
            // Draw a gorgeous offline procedural cosmic nebula background matching the High Density theme
            val stars = remember {
                val r = Random(42) // Fixed seed for stable stars
                List(80) {
                    StarData(
                        x = r.nextFloat(),
                        y = r.nextFloat(),
                        size = r.nextFloat() * 3f + 1f,
                        alpha = r.nextFloat() * 0.7f + 0.3f
                    )
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                // Base background: center #0a0a0f to #000000
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF0A0A0F),
                            Color(0xFF000000)
                        ),
                        center = center,
                        radius = size.minDimension * 1.5f
                    )
                )

                // Top-left radial purple glow (circle at 20% 30% with #2c1a4d) shifting with parallax
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2C1A4D),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.2f - scrollOffsetX * 80f, size.height * 0.3f - scrollOffsetY * 40f),
                        radius = size.width * 0.7f
                    )
                )

                // Bottom-right radial dark indigo glow (circle at 80% 70% with #1a3c4d) shifting with parallax
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A3C4D),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.8f - scrollOffsetX * 80f, size.height * 0.7f - scrollOffsetY * 40f),
                        radius = size.width * 0.7f
                    )
                )

                // Render repeating stardust grid overlay shifting horizontally and vertically
                val spacing = 40f
                var xStart = -(scrollOffsetX * 20f) % spacing
                if (xStart > 0) {
                    xStart -= spacing
                }
                val yStartInitial = -(scrollOffsetY * 10f) % spacing
                val adjustedYStart = if (yStartInitial > 0) yStartInitial - spacing else yStartInitial

                var currX = xStart
                while (currX < size.width) {
                    var currY = adjustedYStart
                    while (currY < size.height) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.20f),
                            radius = 1.5f,
                            center = Offset(currX, currY)
                        )
                        currY += spacing
                    }
                    currX += spacing
                }

                // Render our beautiful twinkling stars with high-density parallax speed wrapping
                stars.forEach { star ->
                    val parallaxFactor = star.size * 30f + 10f
                    val starX = (star.x * size.width - scrollOffsetX * parallaxFactor) % size.width
                    val adjustedX = if (starX < 0) starX + size.width else starX
                    
                    val starY = (star.y * size.height - scrollOffsetY * (parallaxFactor * 0.5f)) % size.height
                    val adjustedY = if (starY < 0) starY + size.height else starY

                    drawCircle(
                        color = Color.White.copy(alpha = star.alpha),
                        radius = star.size,
                        center = Offset(
                            adjustedX,
                            adjustedY
                        )
                    )
                }
            }
        } else {
            // Load selected NASA/Unsplash or Local gallery image using Coil with a dark dimming layer
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = bgUrl,
                    contentDescription = "Cosmic Background",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // Parallax shifting with subtle zoom scaling to prevent black edge reveals
                            // The user suggested a full rotation horizontally instead of panning.
                            // To prevent black edges during rotation, scale up to cover the diagonal.
                            scaleX = 2.2f
                            scaleY = 2.2f
                            
                            // Rotate horizontally (e.g. 60 degrees per page)
                            rotationZ = (scrollOffsetX * 45f) % 360f
                            
                            // Vertical translation remains subtle
                            val maxTransY = size.height * 0.2f
                            translationY = (-scrollOffsetY * 30f).coerceIn(-maxTransY, maxTransY)
                        }
                )
                // Add high-contrast background overlay + repeating stardust grid overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Dynamic dim alpha based on background image luminance (higher luminance -> heavier dimming)
                    val baseLuminance = if (autoContrast) bgLuminance else 0.15f
                    // If luminance is high, make the center and edge overlays darker
                    val centerAlpha = (0.35f + baseLuminance * 0.5f).coerceIn(0.35f, 0.88f)
                    val edgeAlpha = (0.55f + baseLuminance * 0.4f).coerceIn(0.55f, 0.97f)

                    val centerColor = Color(0, 0, 0, (centerAlpha * 255).toInt())
                    val edgeColor = Color(0, 0, 0, (edgeAlpha * 255).toInt())

                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                centerColor,
                                edgeColor
                            ),
                            center = center,
                            radius = size.minDimension * 1.5f
                        )
                    )

                    // Top-left radial purple glow for aesthetic cohesion
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0x402C1A4D),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.2f - scrollOffsetX * 80f, size.height * 0.3f - scrollOffsetY * 40f),
                            radius = size.width * 0.7f
                        )
                    )

                    // Repeating stardust grid overlay shifting with parallax
                    val spacing = 40f
                    var xStart = -(scrollOffsetX * 20f) % spacing
                    if (xStart > 0) {
                        xStart -= spacing
                    }
                    val yStartInitial = -(scrollOffsetY * 10f) % spacing
                    val adjustedYStart = if (yStartInitial > 0) yStartInitial - spacing else yStartInitial

                    var currX = xStart
                    while (currX < size.width) {
                        var currY = adjustedYStart
                        while (currY < size.height) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.15f),
                                radius = 1.5f,
                                center = Offset(currX, currY)
                            )
                            currY += spacing
                        }
                        currX += spacing
                    }
                }
            }
        }
    }
}

private data class StarData(
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float
)
