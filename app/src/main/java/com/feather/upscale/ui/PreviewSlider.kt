package com.feather.upscale.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.FilterQuality
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
import com.feather.upscale.ui.theme.EmeraldGpu
import com.feather.upscale.ui.theme.VioletPrimary
import com.feather.upscale.ui.theme.VioletPrimaryLight
import kotlin.math.roundToInt

/**
 * Khung so sánh Before / After Chuẩn Điện Ảnh:
 *
 * 1. Aspect-Fit 100% Trung Thực: Giữ nguyên tỉ lệ ảnh gốc, không bị méo hay bóp méo khung hình.
 * 2. Cảm ứng Kéo Trượt Thông Minh & Pinch-to-Zoom (1.0x - 5.0x): Cho phép phóng to soi cận cảnh từng nét vẽ.
 * 3. Bộ lọc Độ nét Tương Phản Thật:
 *    - Nửa GỐC: Thể hiện trung thực độ mờ vỡ hạt ban đầu.
 *    - Nửa UPSCALE: Thể hiện độ sắc nét 4K/8K không tì vết.
 * 4. Nút Zoom Nhanh 2.5X / Reset 1.0X tiện lợi.
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
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    val haptic = LocalHapticFeedback.current

    val splitFraction by animateFloatAsState(
        targetValue = rawSplitFraction,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "splitFractionAnimation"
    )

    val animatedZoom by animateFloatAsState(
        targetValue = zoomScale,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "zoomAnimation"
    )

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

    val livePulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "livePulseAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(380.dp)
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF090D16))
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            VioletPrimaryLight.copy(alpha = 0.4f),
                            CyanAccent.copy(alpha = 0.25f),
                            Color.Transparent
                        )
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (beforeBitmap != null) {
                        zoomScale = (zoomScale * zoom).coerceIn(1f, 5f)
                        if (zoomScale > 1f) {
                            val maxPan = 400f * (zoomScale - 1f)
                            panOffset = Offset(
                                (panOffset.x + pan.x).coerceIn(-maxPan, maxPan),
                                (panOffset.y + pan.y).coerceIn(-maxPan, maxPan)
                            )
                        } else {
                            panOffset = Offset.Zero
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (zoomScale > 1.2f) {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        } else {
                            zoomScale = 2.5f
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onTap = { offset ->
                        val target = (offset.x / size.width.toFloat()).coerceIn(0.02f, 0.98f)
                        rawSplitFraction = target
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

                // 1. Tính toán Tỉ Lệ Khung Hình Chuẩn Xác (Aspect-Fit, không méo hình)
                val imageAspect = origW.toFloat() / origH.toFloat()
                val canvasAspect = canvasWidth / canvasHeight

                val baseDrawW: Float
                val baseDrawH: Float

                if (imageAspect > canvasAspect) {
                    baseDrawW = canvasWidth
                    baseDrawH = canvasWidth / imageAspect
                } else {
                    baseDrawH = canvasHeight
                    baseDrawW = canvasHeight * imageAspect
                }

                val finalDrawW = baseDrawW * animatedZoom
                val finalDrawH = baseDrawH * animatedZoom

                val baseOffsetX = (canvasWidth - finalDrawW) / 2f + (if (animatedZoom > 1f) panOffset.x else 0f)
                val baseOffsetY = (canvasHeight - finalDrawH) / 2f + (if (animatedZoom > 1f) panOffset.y else 0f)

                val dstOffset = IntOffset(baseOffsetX.roundToInt(), baseOffsetY.roundToInt())
                val dstSize = IntSize(finalDrawW.roundToInt(), finalDrawH.roundToInt())

                // 2. Nửa Trước (Gốc) - Bên trái Divider
                val leftClip = Path().apply {
                    addRect(Rect(0f, 0f, splitX, canvasHeight))
                }
                clipPath(leftClip) {
                    drawImage(
                        image = beforeImage,
                        srcOffset = IntOffset.Zero,
                        srcSize = IntSize(beforeImage.width, beforeImage.height),
                        dstOffset = dstOffset,
                        dstSize = dstSize,
                        filterQuality = FilterQuality.None // Giữ nguyên độ phân giải thấp gốc để phản ánh trung thực
                    )
                }

                // 3. Nửa Sau (Upscaled HD) - Bên phải Divider
                val rightClip = Path().apply {
                    addRect(Rect(splitX, 0f, canvasWidth, canvasHeight))
                }
                clipPath(rightClip) {
                    if (afterImage != null) {
                        drawImage(
                            image = afterImage,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(afterImage.width, afterImage.height),
                            dstOffset = dstOffset,
                            dstSize = dstSize,
                            filterQuality = FilterQuality.High // Lấy mẫu mịn bậc cao để show độ nét AI tối đa
                        )
                    } else {
                        drawImage(
                            image = beforeImage,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(beforeImage.width, beforeImage.height),
                            dstOffset = dstOffset,
                            dstSize = dstSize,
                            filterQuality = FilterQuality.None
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

                // 4. Thanh Divider phát sáng Neon
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
                    strokeWidth = 3.5.dp.toPx()
                )

                // 5. Nút gạt Glassmorphic ở tâm Divider
                val centerY = canvasHeight / 2f
                drawCircle(
                    color = Color.Black.copy(alpha = 0.5f),
                    radius = 22.dp.toPx(),
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

            // Thanh điều khiển Zoom ở góc trên bên phải (dưới nhãn)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                IconButton(
                    onClick = {
                        if (zoomScale > 1f) {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                        } else {
                            zoomScale = 2.5f
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (zoomScale > 1f) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                        contentDescription = "Zoom",
                        tint = if (zoomScale > 1f) CyanAccent else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Thanh thông tin độ phân giải ở góc dưới
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f),
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
                        text = "${upscaledW}×${upscaledH} (${scaleFactor}x HD)",
                        color = VioletPrimaryLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (zoomScale > 1f) {
                        Text(
                            text = "• ${(zoomScale * 100).toInt()}%",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                        text = "Khu vực so sánh ảnh Before / After",
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
            color = Color(0xFF0F172A).copy(alpha = 0.8f),
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

        // Nhãn UPSCALE (After / Live Rendering) - Glassmorphic Pill bên phải
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            shape = RoundedCornerShape(12.dp),
            color = if (isLoading && afterBitmap != null)
                CyanAccent.copy(alpha = 0.25f)
            else
                Color(0xFF4C1D95).copy(alpha = 0.85f),
            border = BorderStroke(
                1.dp,
                if (isLoading && afterBitmap != null) CyanAccent else VioletPrimaryLight.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            if (isLoading && afterBitmap != null)
                                CyanAccent.copy(alpha = livePulseAlpha)
                            else if (afterBitmap != null)
                                EmeraldGpu
                            else
                                CyanAccent
                        )
                )
                Text(
                    text = if (isLoading && afterBitmap != null)
                        "⚡ RENDERING LIVE"
                    else if (afterBitmap != null)
                        "UPSCALE ${scaleFactor}X"
                    else
                        "AI ENHANCED",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}
