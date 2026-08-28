package com.feather.upscale.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.feather.upscale.NcnnUpscaler
import com.feather.upscale.ui.theme.AmberWarning
import com.feather.upscale.ui.theme.CyanAccent
import com.feather.upscale.ui.theme.EmeraldGpu
import com.feather.upscale.ui.theme.EmeraldGpuLight
import com.feather.upscale.ui.theme.RoseError
import com.feather.upscale.ui.theme.VioletPrimary
import com.feather.upscale.ui.theme.VioletPrimaryLight
import com.feather.upscale.util.StorageHelper
import com.feather.upscale.worker.UpscaleState
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpscaleScreen(
    viewModel: UpscaleViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val selectedUri by viewModel.selectedUri.collectAsState()
    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val customOutputDir by viewModel.customOutputDir.collectAsState()
    val isBatchZip by viewModel.isBatchZip.collectAsState()
    val isVideo by viewModel.isVideo.collectAsState()
    val beforeBitmap by viewModel.beforeBitmap.collectAsState()
    val afterBitmap by viewModel.afterBitmap.collectAsState()
    val selectedPreset by viewModel.selectedPreset.collectAsState()
    val isAutoDetected by viewModel.isAutoDetected.collectAsState()
    val scale by viewModel.scale.collectAsState()
    val useFp16 by viewModel.useFp16.collectAsState()
    val mobileModel by viewModel.mobileModel.collectAsState()
    val quantizationMode by viewModel.quantizationMode.collectAsState()
    val forceLowRam by viewModel.forceLowRam.collectAsState()
    val upscaleState by viewModel.upscaleState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()

    var showBackDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onImageSelected(uri)
    }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onComicSelected(uri)
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onVideoSelected(uri)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Throwable) {}
            viewModel.setCustomOutputDir(uri.toString())
        }
    }

    val isProcessing = upscaleState is UpscaleState.Processing
    val isPaused = upscaleState is UpscaleState.Paused

    // Chặn sự kiện Back khi đang upscale để hiển thị Dialog lựa chọn
    BackHandler(enabled = isProcessing || isPaused) {
        showBackDialog = true
    }

    // Hộp thoại xác nhận khi người dùng nhấn Back trong lúc đang upscale
    if (showBackDialog) {
        AlertDialog(
            onDismissRequest = { showBackDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Tiến trình Upscale đang chạy",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Bạn đang thực hiện upscale ảnh/truyện. Bạn muốn xử lý thế nào trước khi thoát?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "💡 Mẹo: Nếu bạn nhấn nút Home hoặc chuyển app khác, tiến trình vẫn tiếp tục chạy ngầm trên thanh thông báo.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBackDialog = false
                        viewModel.saveDraftAndExit {
                            (context as? Activity)?.finish()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lưu tạm & Thoát")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showBackDialog = false
                            viewModel.cancelAndCleanup {
                                (context as? Activity)?.finish()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hủy tiến trình")
                    }

                    OutlinedButton(
                        onClick = { showBackDialog = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Tiếp tục")
                    }
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Hiệu ứng nhấp nháy cho GPU status dot
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = VioletPrimary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = "UpScale",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.titleLarge,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Manga & Comic AI Enhancer",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    // Badge trạng thái NCNN Vulkan
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = EmeraldGpu.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, EmeraldGpu.copy(alpha = 0.4f)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldGpu.copy(alpha = pulseAlpha))
                            )
                            Text(
                                text = "Vulkan GPU",
                                color = EmeraldGpu,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Preview Split Slider (Hero Component - Hỗ trợ Live Runtime Rendering)
            PreviewSlider(
                beforeBitmap = beforeBitmap,
                afterBitmap = afterBitmap,
                isLoading = isProcessing,
                scaleFactor = scale
            )

            // 2. Thẻ Chọn Nguồn Ảnh / Truyện / Video (Glassmorphic Deck)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: Chọn 1 ảnh
                Card(
                    onClick = {
                        imagePickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing && !isPaused,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedUri != null && !isBatchZip && !isVideo)
                            VioletPrimary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (selectedUri != null && !isBatchZip && !isVideo) VioletPrimary else Color.Transparent
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = VioletPrimary.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = null,
                                    tint = VioletPrimaryLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "Ảnh Đơn",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "JPG, PNG, WebP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                // Card 2: Chọn Tập Truyện / Sách (ZIP / CBZ / MOBI / PRC)
                Card(
                    onClick = {
                        zipPickerLauncher.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-cbz",
                                "application/x-mobipocket-ebook",
                                "application/octet-stream",
                                "*/*"
                            )
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing && !isPaused,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedUri != null && isBatchZip)
                            CyanAccent.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (selectedUri != null && isBatchZip) CyanAccent else Color.Transparent
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = CyanAccent.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.FolderZip,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "Tập Truyện",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "CBZ, MOBI, ZIP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }

                // Card 3: Chọn Video AI
                Card(
                    onClick = {
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !isProcessing && !isPaused,
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedUri != null && isVideo)
                            EmeraldGpu.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (selectedUri != null && isVideo) EmeraldGpu else Color.Transparent
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = EmeraldGpu.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = EmeraldGpuLight,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = "Video AI",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "MP4, MKV, WebM",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Thanh thông tin file đã chọn
            if (selectedFileName != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isVideo -> Icons.Default.PlayArrow
                                isBatchZip -> Icons.Default.FolderZip
                                else -> Icons.Default.Image
                            },
                            contentDescription = null,
                            tint = when {
                                isVideo -> EmeraldGpu
                                isBatchZip -> CyanAccent
                                else -> VioletPrimaryLight
                            },
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFileName ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = when {
                                    isVideo -> "Video Siêu Phân Giải AI (Bảo toàn âm thanh gốc)"
                                    selectedFileName?.endsWith(".mobi", true) == true || selectedFileName?.endsWith(".prc", true) == true -> "Sách truyện MOBI / PRC (PalmDOC)"
                                    isBatchZip -> "Tập tin nén truyện tranh (CBZ / ZIP)"
                                    else -> "Đã nạp vào bộ nhớ đệm"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 3. Preset Gợi ý Thể loại (Tự động nhận diện AI + Bộ tính năng Real-ESRGAN Suite)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Bộ Tính Năng AI Real-ESRGAN",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    if (isAutoDetected) {
                        Surface(
                            shape = CircleShape,
                            color = CyanAccent.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "✨ Tự động nhận diện",
                                color = CyanAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Anime & Cartoons",
                        "Face Recovery",
                        "Fix Pixelation",
                        "Auto Denoise",
                        "Lossless Zoom",
                        "Manga B&W"
                    ).forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = { viewModel.setPreset(preset) },
                            label = {
                                Text(
                                    text = preset,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedPreset == preset) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            enabled = !isProcessing && !isPaused,
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VioletPrimary.copy(alpha = 0.2f),
                                selectedLabelColor = VioletPrimaryLight
                            )
                        )
                    }
                }
            }

            // 4. Bảng Cấu hình Kỹ thuật On-Device Mobile AI (Model, Quantization, Scale, Tiling)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "On-Device Mobile AI Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldGpu.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "100% Offline",
                                color = EmeraldGpu,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // 1. Lightweight Models (Mô hình rút gọn cho Di động)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Mô hình rút gọn (Mobile Models)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Surface(shape = CircleShape, color = VioletPrimary.copy(alpha = 0.15f)) {
                                Text("NPU/GPU", color = VioletPrimaryLight, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val modelOptions: List<Pair<String, String>> = listOf(
                                NcnnUpscaler.MODEL_ANIME_6B to "Anime 6B (Manga)",
                                NcnnUpscaler.MODEL_VIDEO_V3 to "AnimeVideoV3 (Siêu nhẹ)",
                                NcnnUpscaler.MODEL_MOBILE_SR to "MobileSR Fast (NPU)",
                                NcnnUpscaler.MODEL_PHOTO_X4 to "Photo x4 (Chân dung)"
                            )
                            modelOptions.forEach { (modelId, label) ->
                                FilterChip(
                                    selected = mobileModel == modelId,
                                    onClick = { viewModel.setMobileModel(modelId) },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = if (mobileModel == modelId) FontWeight.Bold else FontWeight.Normal) },
                                    enabled = !isProcessing && !isPaused,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = EmeraldGpu.copy(alpha = 0.2f),
                                        selectedLabelColor = EmeraldGpu
                                    )
                                )
                            }
                        }
                    }

                    // 2. Lượng tử hóa (Model Quantization: INT8 w8a8 vs FP16)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Lượng tử hóa INT8 (w8a8)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Surface(shape = CircleShape, color = CyanAccent.copy(alpha = 0.15f)) {
                                    Text("RAM -75%", color = CyanAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Text("Nén mô hình 4x và tăng tốc suy luận tối đa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = quantizationMode == NcnnUpscaler.PRECISION_INT8,
                            onCheckedChange = { isInt8 ->
                                viewModel.setQuantizationMode(if (isInt8) NcnnUpscaler.PRECISION_INT8 else NcnnUpscaler.PRECISION_FP16)
                            },
                            enabled = !isProcessing && !isPaused,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CyanAccent)
                        )
                    }

                    // 3. Segmented Scale Button (2x, 4x, 8x, 10x)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Tỉ lệ phóng to", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Độ phân giải đầu ra", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Row(modifier = Modifier.padding(3.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (scale == 2) VioletPrimary else Color.Transparent,
                                    modifier = Modifier.clickable(enabled = !isProcessing && !isPaused) { viewModel.setScale(2) }
                                ) {
                                    Text(
                                        text = "2x",
                                        color = if (scale == 2) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (scale == 4) VioletPrimary else Color.Transparent,
                                    modifier = Modifier.clickable(enabled = !isProcessing && !isPaused) { viewModel.setScale(4) }
                                ) {
                                    Text(
                                        text = "4x",
                                        color = if (scale == 4) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (scale == 8) VioletPrimary else Color.Transparent,
                                    modifier = Modifier.clickable(enabled = !isProcessing && !isPaused) { viewModel.setScale(8) }
                                ) {
                                    Text(
                                        text = "8x Max",
                                        color = if (scale == 8) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (scale == 10) VioletPrimary else Color.Transparent,
                                    modifier = Modifier.clickable(enabled = !isProcessing && !isPaused) { viewModel.setScale(10) }
                                ) {
                                    Text(
                                        text = "10x (1000%)",
                                        color = if (scale == 10) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 4. OOM Guard Switch (Zero-OOM Tiling / Chunking)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("OOM Guard 4GB RAM (Tiling)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Surface(shape = CircleShape, color = EmeraldGpu.copy(alpha = 0.15f)) {
                                    Text("An Toàn", color = EmeraldGpu, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                            Text("Cắt nhỏ 128px + Ghép liền mạch C^1 Raised-Cosine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = forceLowRam,
                            onCheckedChange = { viewModel.setForceLowRam(it) },
                            enabled = !isProcessing && !isPaused,
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EmeraldGpu)
                        )
                    }

                    // Thư mục lưu đầu ra tùy chọn
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = VioletPrimaryLight, modifier = Modifier.size(18.dp))
                                Text("Thư mục lưu đầu ra", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                text = StorageHelper.getDisplayPathFromTreeUri(customOutputDir)?.let { "Tùy chỉnh: $it" } ?: "Mặc định (Pictures / Download/UpScale)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (customOutputDir != null) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (customOutputDir != null) {
                                OutlinedButton(
                                    onClick = { viewModel.setCustomOutputDir(null) },
                                    enabled = !isProcessing && !isPaused,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Đặt lại", fontSize = 11.sp, color = RoseError)
                                }
                            }
                            Button(
                                onClick = { folderPickerLauncher.launch(null) },
                                enabled = !isProcessing && !isPaused,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary.copy(alpha = 0.8f))
                            ) {
                                Text("Chọn", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // 5. Card Tiến độ Thời gian thực & Thẻ Kết Quả Lưu Tệp Mới
            AnimatedVisibility(
                visible = isProcessing || isPaused || upscaleState is UpscaleState.Completed || upscaleState is UpscaleState.Error,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when (upscaleState) {
                            is UpscaleState.Completed -> EmeraldGpu.copy(alpha = 0.12f)
                            is UpscaleState.Error -> RoseError.copy(alpha = 0.12f)
                            is UpscaleState.Paused -> AmberWarning.copy(alpha = 0.12f)
                            else -> VioletPrimary.copy(alpha = 0.15f)
                        }
                    ),
                    border = BorderStroke(
                        1.dp,
                        when (upscaleState) {
                            is UpscaleState.Completed -> EmeraldGpu.copy(alpha = 0.5f)
                            is UpscaleState.Error -> RoseError.copy(alpha = 0.5f)
                            is UpscaleState.Paused -> AmberWarning.copy(alpha = 0.5f)
                            else -> VioletPrimaryLight.copy(alpha = 0.4f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        when (val state = upscaleState) {
                            is UpscaleState.Processing -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(VioletPrimaryLight)
                                        )
                                        Text(
                                            text = state.statusMessage.ifEmpty { "Đang xử lý GPU Vulkan..." },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "${(state.progressFraction * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = VioletPrimaryLight
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { state.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = VioletPrimaryLight
                                )

                                // Telemetry Grid 3 ô
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("TILES", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                            Text("${state.completedTiles}/${state.totalTiles}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("TILE SIZE", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                            Text("${state.currentTileSize}px", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = CyanAccent)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text("TRANG", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                            Text("${state.currentPage}/${state.totalPages}", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }

                            is UpscaleState.Paused -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(imageVector = Icons.Default.Pause, contentDescription = null, tint = AmberWarning)
                                    Text(
                                        text = "Đã tạm dừng (Trang ${state.currentPage}, Tile ${state.completedTiles}/${state.totalTiles})",
                                        fontWeight = FontWeight.Bold,
                                        color = AmberWarning
                                    )
                                }
                            }

                            is UpscaleState.Completed -> {
                                // KHU VỰC THÔNG TIN TỆP MỚI VÀ NÚT MỞ / CHIA SẺ
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGpu, modifier = Modifier.size(28.dp))
                                        Column {
                                            Text(
                                                text = "🎉 Upscale Hoàn Tất & Đã Lưu!",
                                                fontWeight = FontWeight.ExtraBold,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = EmeraldGpu
                                            )
                                            Text(
                                                text = "Đã tự động lưu vào thư mục UpScale • ${state.totalDurationMs / 1000}s",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Hộp ghi chú bảo toàn ảnh gốc & trạng thái thư mục
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        border = BorderStroke(1.dp, EmeraldGpu.copy(alpha = 0.3f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldGpu, modifier = Modifier.size(16.dp))
                                                Text(
                                                    text = if (state.isVerified) "Đã xác minh tệp hợp lệ trong thư viện thiết bị" else "Đang hoàn tất tệp",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmeraldGpu
                                                )
                                            }

                                            Text(
                                                text = "📄 Tên tệp: ${state.outputFileName.ifEmpty { "upscaled_result.png" }}",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "📐 Độ phân giải: ${state.outputResolution.ifEmpty { "4K UHD" }} • Dung lượng: ${state.outputFileSize.ifEmpty { "Đã tối ưu" }}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "📁 Thư mục: ${state.outputDirectory ?: (state.outputPath?.let { File(it).parent } ?: "UpScale")}",
                                                fontSize = 11.sp,
                                                color = CyanAccent,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    // Hàng nút Thao tác: Xem ảnh & Mở thư mục & Chia sẻ
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { openFile(context, state.outputPath) },
                                            modifier = Modifier.weight(1.2f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGpu)
                                        ) {
                                            Icon(imageVector = Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (isBatchZip) "Mở tập" else "Xem ảnh", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { openFolder(context, state.outputDirectory, state.outputPath) },
                                            modifier = Modifier.weight(1.1f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                                            border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.6f))
                                        ) {
                                            Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Thư mục", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }

                                        OutlinedButton(
                                            onClick = { shareFile(context, state.outputPath) },
                                            modifier = Modifier.weight(0.8f),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                        ) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }

                            is UpscaleState.Error -> {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = RoseError, modifier = Modifier.size(28.dp))
                                    Column {
                                        Text(
                                            text = if (state.isOom) "Cảnh Báo Áp Lực RAM (OOM)" else "Lỗi Xử Lý",
                                            fontWeight = FontWeight.Bold,
                                            color = RoseError
                                        )
                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }

                        // Điều khiển khi đang chạy (Pause / Resume / Cancel)
                        if (isProcessing || isPaused) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (isProcessing) {
                                    OutlinedButton(
                                        onClick = { viewModel.pauseUpscale() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Pause, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tạm dừng")
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.resumeUpscale() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                                    ) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Tiếp tục")
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.cancelUpscale() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RoseError)
                                ) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Hủy")
                                }
                            }
                        }
                    }
                }
            }

            // 6. Nút Hành Động Thích Ứng Thông Minh (Adaptive Action CTA)
            if (upscaleState is UpscaleState.Completed) {
                val completedState = upscaleState as UpscaleState.Completed
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { openFile(context, completedState.outputPath) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .shadow(elevation = 10.dp, shape = RoundedCornerShape(18.dp), ambientColor = EmeraldGpu, spotColor = EmeraldGpu),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldGpu)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Text(
                                text = if (isBatchZip) "MỞ TẬP TRUYỆN ĐÃ UPSCALE" else "XEM ẢNH ĐÃ UPSCALE (4K UHD)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { openFolder(context, completedState.outputDirectory, completedState.outputPath) },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                            border = BorderStroke(1.5.dp, CyanAccent.copy(alpha = 0.6f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("THƯ MỤC ĐẦU RA", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.reset() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VioletPrimaryLight),
                            border = BorderStroke(1.5.dp, VioletPrimary.copy(alpha = 0.6f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("TỆP MỚI", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            } else {
                Button(
                    onClick = { viewModel.startUpscale() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(18.dp), ambientColor = VioletPrimary, spotColor = VioletPrimary),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VioletPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    enabled = selectedUri != null && !isProcessing && !isPaused
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (isBatchZip)
                                "BẮT ĐẦU UPSCALE TẬP TRUYỆN (${scale}X)"
                            else
                                "BẮT ĐẦU UPSCALE ẢNH (${scale}X)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Tiện ích mở tệp bằng Intent an toàn qua FileProvider hoặc Document URI
 */
private fun openFile(context: Context, filePath: String?) {
    if (filePath == null) return
    if (filePath.startsWith("content://")) {
        try {
            val uri = Uri.parse(filePath)
            val mimeType = when {
                filePath.contains(".mp4", true) || filePath.contains(".mkv", true) -> "video/*"
                filePath.contains(".cbz", true) || filePath.contains(".zip", true) -> "application/zip"
                else -> "image/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (_: Throwable) {
            try {
                val uri = Uri.parse(filePath)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Mở tệp bằng"))
                return
            } catch (_: Throwable) {}
        }
    }

    val file = File(filePath)
    if (!file.exists()) return
    try {
        val uri = FileProvider.getUriForFile(context, "com.feather.upscale.fileprovider", file)
        val mimeType = when {
            file.extension.equals("mp4", true) || file.extension.equals("mkv", true) -> "video/*"
            file.extension.equals("cbz", true) || file.extension.equals("zip", true) -> "application/zip"
            else -> "image/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Throwable) {
        try {
            val uri = FileProvider.getUriForFile(context, "com.feather.upscale.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Mở tệp bằng"))
        } catch (_: Throwable) {}
    }
}

/**
 * Tiện ích mở thư mục chứa kết quả lưu đầu ra qua File Manager
 */
private fun openFolder(context: Context, dirPath: String?, filePath: String?) {
    // 1. Thư mục Tree URI tùy chỉnh (content://...):
    if (dirPath != null && dirPath.startsWith("content://")) {
        try {
            val treeUri = Uri.parse(dirPath)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(treeUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Mở thư mục lưu đầu ra"))
            return
        } catch (_: Throwable) {
            try {
                val treeUri = Uri.parse(dirPath)
                val intent = Intent(Intent.ACTION_VIEW, treeUri).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Mở thư mục lưu đầu ra"))
                return
            } catch (_: Throwable) {}
        }
    }

    // 2. Thư mục cục bộ (Pictures/UpScale, Movies/UpScale, Download/UpScale):
    val targetDir = if (dirPath != null && !dirPath.startsWith("content://")) {
        File(dirPath)
    } else if (filePath != null && !filePath.startsWith("content://")) {
        File(filePath).parentFile
    } else {
        null
    }

    if (targetDir != null && targetDir.exists()) {
        try {
            val uri = FileProvider.getUriForFile(context, "com.feather.upscale.fileprovider", targetDir)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Mở thư mục lưu đầu ra"))
            return
        } catch (_: Throwable) {
            try {
                val uri = FileProvider.getUriForFile(context, "com.feather.upscale.fileprovider", targetDir)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(Intent.createChooser(intent, "Mở thư mục lưu đầu ra"))
                return
            } catch (_: Throwable) {}
        }
    }

    // 3. Mở Media Gallery theo loại tệp
    try {
        val isVid = filePath?.endsWith(".mp4", true) == true || filePath?.endsWith(".mkv", true) == true
        val mediaUri = if (isVid) Uri.parse("content://media/external/video/media") else Uri.parse("content://media/external/images/media")
        val mime = if (isVid) "vnd.android.cursor.dir/video" else "vnd.android.cursor.dir/image"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(mediaUri, mime)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Mở thư mục lưu đầu ra"))
        return
    } catch (_: Throwable) {}

    // 4. Fallback: Mở trực tiếp tệp kết quả
    if (filePath != null) {
        openFile(context, filePath)
    }
}

/**
 * Tiện ích chia sẻ tệp qua FileProvider
 */
private fun shareFile(context: Context, filePath: String?) {
    if (filePath == null) return
    if (filePath.startsWith("content://")) {
        try {
            val uri = Uri.parse(filePath)
            val mimeType = when {
                filePath.contains(".mp4", true) || filePath.contains(".mkv", true) -> "video/mp4"
                filePath.contains(".cbz", true) || filePath.contains(".zip", true) -> "application/zip"
                else -> "image/png"
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Chia sẻ kết quả Upscale"))
            return
        } catch (_: Throwable) {}
    }

    val file = File(filePath)
    if (!file.exists()) return
    try {
        val uri = FileProvider.getUriForFile(context, "com.feather.upscale.fileprovider", file)
        val mimeType = when {
            file.extension.equals("mp4", true) || file.extension.equals("mkv", true) -> "video/mp4"
            file.extension.equals("cbz", true) || file.extension.equals("zip", true) -> "application/zip"
            else -> "image/png"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Chia sẻ kết quả Upscale"))
    } catch (_: Throwable) {}
}
