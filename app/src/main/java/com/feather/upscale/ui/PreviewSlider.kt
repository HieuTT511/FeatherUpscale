package com.feather.upscale.ui

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feather.upscale.ui.theme.CyanAccent
import com.feather.upscale.ui.theme.VioletPrimary
import com.feather.upscale.ui.theme.VioletPrimaryLight
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Thành phần so sánh Before / After đạt chuẩn UX Modern Android 2026.
 *
 * Tính năng cao cấp:
 * - Kéo trượt ngang với vật lý mượt mà, hỗ trợ Double-Tap để căn giữa 50%.
 * - Rung Haptic phản hồi khi lướt qua mốc 50%.
 * - Hiển thị kích thước độ phân giải thực tế (ví dụ 800x1200 -> 3200x4800 4K).
 * - Nhãn kính mờ Glassmorphic với viền gradient.
 * - Placeholder chuyển động mượt mà khi chưa có ảnh.
 */
@Composable
fun PreviewSlider(
    beforeBitmap: Bitmap?,
    afterBitmap: Bitmap?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    scaleFactor: Int = 4,
) {
    var rawSplitFraction by remember { mutableFloatStateOf(0.5f) }
    var isHoldingToOriginal by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    // Hoạt ảnh chuyển đổi mượt mà
    val splitFraction by animateFloatAsState(
        targetValue = if (isHoldingToOriginal) 0.001f else rawSplitFraction,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "splitFractionAnimation"
    )

    // Hiệu ứng Shimmering
    val infiniteTransition = rememberInfiniteTransition(label = "shimmerTransition")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerOffset"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            VioletPrimaryLight.copy(alpha = 0.35f),
                            CyanAccent.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        rawSplitFraction = 0.5f
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onTap = { offset ->
                        val target = (offset.x / size.width.toFloat()).coerceIn(0.02f, 0.98f)
                        rawSplitFraction = target
                    },
                    onPress = {
                        // Nhấn giữ để xem toàn bộ ảnh gốc
                    }
                )
            }
            .pointerInput(Unit) {
                var lastMidCrossed = false
                detectHorizontalDragGestures(
                    onDragStart = { },
                    onDragEnd = { },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val newFraction = (rawSplitFraction + (dragAmount / size.width.toFloat())).coerceIn(0.02f, 0.98f)
                        val crossedMid = (rawSplitFraction < 0.5f && newFraction >= 0.5f) || (rawSplitFraction > 0.5f && newFraction <= 0.5f)
                        if (crossedMid && !lastMidCrossed) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        lastMidCrossed = crossedMid
                        rawSplitFraction = newFraction
                    }
                )
            }
    ) {
        if (beforeBitmap != null) {
            val beforeImage = remember(beforeBitmap) { beforeBitmap.asImageBitmap() }
            val afterImage = remember(afterBitmap) { afterBitmap?.asImageBitmap() }

            val origW = beforeBitmap.width
            val origH = beforeBitmap.height
            val upscaledW = origW * scaleFactor
            val upscaledH = origH * scaleFactor

            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val splitX = canvasWidth * splitFraction

                // 1. Nửa Trước (Gốc) - Phía bên trái
                val leftClip = Path().apply {
                    addRect(Rect(0f, 0f, splitX, canvasHeight))
                }
                clipPath(leftClip) {
                    drawImage(
                        image = beforeImage,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(beforeImage.width, beforeImage.height),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(canvasWidth.roundToInt(), canvasHeight.roundToInt())
                    )
                }

                // 2. Nửa Sau (Upscaled) - Phía bên phải
                val rightClip = Path().apply {
                    addRect(Rect(splitX, 0f, canvasWidth, canvasHeight))
                }
                clipPath(rightClip) {
                    if (afterImage != null) {
                        drawImage(
                            image = afterImage,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(afterImage.width, afterImage.height),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(canvasWidth.roundToInt(), canvasHeight.roundToInt())
                        )
                    } else {
                        // Khi chưa có kết quả sau: hiển thị ảnh gốc với dải shimmer
                        drawImage(
                            image = beforeImage,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(beforeImage.width, beforeImage.height),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(canvasWidth.roundToInt(), canvasHeight.roundToInt())
                        )
                        if (isLoading) {
                            val shimmerX = canvasWidth * shimmerOffset
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        VioletPrimaryLight.copy(alpha = 0.35f),
                                        CyanAccent.copy(alpha = 0.35f),
                                        Color.Transparent
                                    ),
                                    start = Offset(shimmerX - 200f, 0f),
                                    end = Offset(shimmerX + 200f, canvasHeight)
                                ),
                                topLeft = Offset(splitX, 0f),
                                size = Size(canvasWidth - splitX, canvasHeight)
                            )
                        }
                    }
                }

                // 3. Đường Divider phát sáng Neon
                drawLine(
                    brush = Brush.verticalGradient(
                        listOf(
                            CyanAccent,
                            Color.White,
                            VioletPrimaryLight
                        )
                    ),
                    start = Offset(splitX, 0f),
                    end = Offset(splitX, canvasHeight),
                    strokeWidth = 3.dp.toPx()
                )

                // 4. Vòng tay cầm kéo trượt Glassmorphic ở giữa
                val centerY = canvasHeight / 2f
                drawCircle(
                    color = Color.Black.copy(alpha = 0.4f),
                    radius = 20.dp.toPx(),
                    center = Offset(splitX, centerY)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, VioletPrimary),
                        center = Offset(splitX, centerY),
                        radius = 16.dp.toPx()
                    ),
                    radius = 16.dp.toPx(),
                    center = Offset(splitX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = Offset(splitX, centerY)
                )
            }

            // Thanh thông tin độ phân giải ở góc dưới
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${origW}×${origH}",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "→",
                        color = CyanAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${upscaledW}×${upscaledH} (${scaleFactor}x Ultra-HD)",
                        color = VioletPrimaryLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

        } else {
            // Placeholder trang nhã khi chưa tải ảnh
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            listOf(
                                VioletPrimary.copy(alpha = 0.15f),
                                MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = VioletPrimary.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, VioletPrimaryLight.copy(alpha = 0.3f)),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = VioletPrimaryLight,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    Text(
                        text = "Khu vực xem trước ảnh Before / After",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Chọn 1 ảnh hoặc tập tin truyện ZIP/CBZ bên dưới",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Nhãn GỐC (Before) - Glassmorphic Pill bên trái
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.75f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF94A3B8))
                )
                Text(
                    text = "GỐC",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }

        // Nhãn UPSCALE (After) - Glassmorphic Pill bên phải
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF4C1D95).copy(alpha = 0.8f),
            border = BorderStroke(1.dp, VioletPrimaryLight.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(CyanAccent)
                )
                Text(
                    text = if (afterBitmap != null) "UPSCALE ${scaleFactor}X" else "AI ENHANCED",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}
