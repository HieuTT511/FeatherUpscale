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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
 * Khung so sánh Before / After Chuẩn Điện Ảnh (Cinematic Comparison Engine):
 *
 * 1. Tự động thích ứng tỉ lệ ảnh (Aspect-Adaptive Fill): Triệt tiêu hoàn toàn 2 vệt đen hai bên, ảnh luôn lấp đầy tự nhiên và chuẩn xác.
 * 2. Thanh trượt mượt mà tuyệt đối (Silky Drag): Kéo trượt ngang với phản hồi rung haptic tức thì.
 * 3. Chế độ Soi Chi Tiết Cận Cảnh (2X Detail Inspector): Phóng to 200% để kiểm tra từng đường nét mực, sợi tóc, mắt nhân vật.
 * 4. Phản ánh độ sắc nét tương phản thực tế giữa ảnh gốc và AI Super-Resolution.
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
    var isZoomed by remember { mutableStateOf(false) }

    val haptic = LocalHapticFeedback.current

    val splitFraction by animateFloatAsState(
        targetValue = rawSplitFraction,
        animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f),
        label = "splitFractionAnimation"
    )

    val zoomScale by animateFloatAsState(
        targetValue = if (isZoomed) 2.2f else 1.0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
        label = "zoomScaleAnimation"
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

    // Tính toán aspect ratio thích ứng để loại bỏ hoàn toàn 2 viền đen 2 bên
    val containerModifier = if (beforeBitmap != null) {
        val aspect = (beforeBitmap.width.toFloat() / beforeBitmap.height.toFloat()).coerceIn(0.72f, 1.6f)
        modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .heightIn(min = 280.dp, max = 460.dp)
    } else {
        modifier
            .fillMaxWidth()
            .height(340.dp)
    }

    Box(
        modifier = containerModifier
            .shadow(elevation = 10.dp, shape = RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF0F172A))
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
                detectTapGestures(
                    onDoubleTap = {
                        rawSplitFraction = 0.5f
                        isZoomed = !isZoomed
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onTap = { offset ->
                        rawSplitFraction = (offset.x / size.width.toFloat()).coerceIn(0.01f, 0.99f)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
                        val newFraction = (rawSplitFraction + (dragAmount / size.width.toFloat())).coerceIn(0.01f, 0.99f)
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

                // Căn chỉnh ảnh lấp đầy khung hình chuẩn xác (Fill Container không méo tỉ lệ)
                val imageAspect = origW.toFloat() / origH.toFloat()
                val canvasAspect = canvasWidth / canvasHeight

                val baseDrawW: Float
                val baseDrawH: Float

                if (imageAspect > canvasAspect) {
                    baseDrawH = canvasHeight
                    baseDrawW = canvasHeight * imageAspect
                } else {
                    baseDrawW = canvasWidth
                    baseDrawH = canvasWidth / imageAspect
                }

                val finalDrawW = baseDrawW * zoomScale
                val finalDrawH = baseDrawH * zoomScale

                val offsetX = (canvasWidth - finalDrawW) / 2f
                val offsetY = (canvasHeight - finalDrawH) / 2f

                val dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt())
                val dstSize = IntSize(finalDrawW.roundToInt(), finalDrawH.roundToInt())

                // 1. Nửa Trước (Gốc) - Bên trái Divider
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
                        filterQuality = FilterQuality.None // Giữ nguyên độ phân giải thấp để phản ánh trung thực
                    )
                }

                // 2. Nửa Sau (Upscaled HD) - Bên phải Divider
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
                            filterQuality = FilterQuality.High // Lấy mẫu siêu nét chuẩn HD
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

                // 3. Thanh Divider phát sáng Neon
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

                // 4. Tay nắm Glassmorphic tại tâm Divider
                val centerY = canvasHeight / 2f
                drawCircle(
                    color = Color.Black.copy(alpha = 0.5f),
                    radius = 20.dp.toPx(),
                    center = Offset(splitX, centerY)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, VioletPrimary),
                        center = Offset(splitX, centerY),
                        radius = 15.dp.toPx()
                    ),
                    radius = 15.dp.toPx(),
                    center = Offset(splitX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 4.5.dp.toPx(),
                    center = Offset(splitX, centerY)
                )
            }

            // Nút Soi Chi Tiết Cận Cảnh (Zoom Inspector)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
            ) {
                IconButton(
                    onClick = {
                        isZoomed = !isZoomed
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isZoomed) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                        contentDescription = "Soi chi tiết",
                        tint = if (isZoomed) CyanAccent else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Thanh thông tin độ phân giải ở góc dưới
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${origW}×${origH}",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "→",
                        color = CyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${upscaledW}×${upscaledH} (${scaleFactor}x HD)",
                        color = VioletPrimaryLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isZoomed) {
                        Text(
                            text = "• 200%",
                            color = CyanAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

        } else {
            // Placeholder khi chưa nạp ảnh
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

        // Nhãn GỐC (Before)
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF0F172A).copy(alpha = 0.8f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
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
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }

        // Nhãn UPSCALE (After)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            shape = RoundedCornerShape(10.dp),
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
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
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
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}
