package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.model.A4Layout
import com.example.model.AppLanguage
import com.example.model.PointFNorm
import com.example.model.ProcessProgress
import com.example.model.ScanFilter
import com.example.model.ScanPageItem
import com.example.ui.components.ImageLightboxDialog
import com.example.ui.components.ProgressBarCard
import com.example.ui.components.UploadCard
import com.example.ui.theme.EduBorder
import com.example.ui.theme.EduCream
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduRed
import com.example.ui.theme.EduSlate
import com.example.ui.theme.EduSurface
import com.example.ui.theme.EduTeal
import com.example.ui.theme.EduText
import com.example.ui.theme.EduTint
import com.example.ui.theme.EduYellow
import com.example.util.PdfEngine
import com.example.util.ScanEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun ScanScreen(
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Temporary photo file & uri for camera capture
    var tempCameraFile by remember { mutableStateOf<File?>(null) }

    // State for image editing: either a new capture or an existing page from scannedPages
    var editingPageIndex by remember { mutableIntStateOf(-1) } // -1 means new capture, >= 0 means editing scannedPages[editingPageIndex]
    var activeCapture by remember { mutableStateOf<Bitmap?>(null) }

    val corners = remember {
        mutableStateListOf(
            PointFNorm(0.08f, 0.08f), // 0: TL
            PointFNorm(0.92f, 0.08f), // 1: TR
            PointFNorm(0.92f, 0.92f), // 2: BR
            PointFNorm(0.08f, 0.92f)  // 3: BL
        )
    }

    var selectedCornerIndex by remember { mutableIntStateOf(0) } // For fine-tuning D-Pad
    var selectedFilter by remember { mutableStateOf(ScanFilter.AUTO) }
    var selectedLayout by remember { mutableStateOf(A4Layout.PORTRAIT) }

    // Scanned pages list
    val scannedPages = remember { mutableStateListOf<ScanPageItem>() }
    var progress by remember { mutableStateOf(ProcessProgress()) }
    var exportedPdf by remember { mutableStateOf<File?>(null) }

    // Fullscreen lightbox preview
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewTitle by remember { mutableStateOf("") }

    // High-resolution FileProvider camera launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val file = tempCameraFile
        if (success && file != null && file.exists() && file.length() > 0) {
            scope.launch {
                progress = ProcessProgress(
                    isProcessing = true,
                    percent = 30,
                    title = if (language == AppLanguage.VI) "Đang phân tích góc tài liệu..." else "Detecting document corners...",
                    description = "Samsung Galaxy S22 Ultra High-Res"
                )

                val loadedBmp = withContext(Dispatchers.IO) {
                    ScanEngine.loadAndCorrectOrientation(file, maxDimension = 2400)
                }

                if (loadedBmp != null) {
                    activeCapture = loadedBmp
                    val detected = withContext(Dispatchers.Default) {
                        ScanEngine.autoDetectCorners(loadedBmp)
                    }
                    corners.clear()
                    corners.addAll(detected)
                } else {
                    Toast.makeText(
                        context,
                        if (language == AppLanguage.VI) "Không thể nạp ảnh. Vui lòng thử lại!" else "Failed to load photo. Please retry!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                progress = ProcessProgress(isProcessing = false)
            }
        }
    }

    // Camera launcher helper
    val launchNativeCamera: () -> Unit = {
        try {
            val photoFile = File.createTempFile("edudoc_scan_", ".jpg", context.cacheDir)
            tempCameraFile = photoFile
            val photoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            takePictureLauncher.launch(photoUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                context,
                if (language == AppLanguage.VI) "Không thể mở máy ảnh: ${e.localizedMessage}" else "Cannot open camera: ${e.localizedMessage}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Runtime Camera Permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchNativeCamera()
        } else {
            Toast.makeText(
                context,
                if (language == AppLanguage.VI) "Cần quyền Camera để chụp tài liệu trực tiếp!" else "Camera permission required!",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val onStartCameraClick: () -> Unit = {
        val permissionState = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        )
        if (permissionState == PackageManager.PERMISSION_GRANTED) {
            launchNativeCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Function to open editor for an existing scanned page
    fun startEditingPage(index: Int) {
        if (index in scannedPages.indices) {
            val page = scannedPages[index]
            editingPageIndex = index
            activeCapture = page.originalBitmap
            corners.clear()
            corners.addAll(page.corners.map { PointFNorm(it.x, it.y) })
            selectedFilter = page.filter
            selectedCornerIndex = 0
        }
    }

    // Gallery import launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                progress = ProcessProgress(
                    isProcessing = true,
                    percent = 20,
                    title = if (language == AppLanguage.VI) "Đang nạp ảnh..." else "Importing photos...",
                    description = "${uris.size} ảnh"
                )

                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        try {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val bmp = BitmapFactory.decodeStream(stream)
                                if (bmp != null) {
                                    val detectedCorners = ScanEngine.autoDetectCorners(bmp)
                                    val warped = ScanEngine.warpQuad(bmp, detectedCorners)
                                    val filtered = ScanEngine.applyFilter(warped, selectedFilter)
                                    scannedPages.add(
                                        ScanPageItem(
                                            id = UUID.randomUUID().toString(),
                                            originalBitmap = bmp,
                                            corners = detectedCorners,
                                            filter = selectedFilter,
                                            bitmap = filtered,
                                            rotation = 0
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                progress = ProcessProgress(isProcessing = false)
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        ProgressBarCard(progress = progress)

        // Top Action Bar: Camera / Gallery buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    editingPageIndex = -1 // new capture
                    onStartCameraClick()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EduTeal,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("scan_camera_button")
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = EduYellow, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (language == AppLanguage.VI) "Chụp Camera" else "Camera Scan",
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { galleryLauncher.launch("image/*") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = EduTint,
                    contentColor = EduTeal
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = EduTeal, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (language == AppLanguage.VI) "Thêm Ảnh" else "Import Photos",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Active Capture / Edit Page Corner Adjustment Area
        val currentBmp = activeCapture
        if (currentBmp != null) {
            val imgAspect = (currentBmp.width.toFloat() / currentBmp.height.toFloat()).coerceIn(0.4f, 2.5f)
            val isEditingExisting = editingPageIndex >= 0

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0F172A))
                    .border(2.5.dp, if (isEditingExisting) EduGreen else EduYellow, RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isEditingExisting) {
                                        if (language == AppLanguage.VI) "Sửa lại Trang ${editingPageIndex + 1}" else "Editing Page ${editingPageIndex + 1}"
                                    } else {
                                        if (language == AppLanguage.VI) "Căn chỉnh 4 góc tài liệu" else "Adjust 4 document corners"
                                    },
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black
                                )
                                if (isEditingExisting) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(EduGreen)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (language == AppLanguage.VI) "Đang sửa" else "Editing",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (language == AppLanguage.VI) "Kéo 4 góc trực tiếp hoặc dùng nút mũi tên tinh chỉnh" else "Drag corners directly or use arrow buttons",
                                color = EduYellow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row {
                            if (isEditingExisting) {
                                IconButton(
                                    onClick = {
                                        // Option to retake this page photo
                                        onStartCameraClick()
                                    }
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Retake photo", tint = EduYellow)
                                }
                            }
                            IconButton(onClick = {
                                activeCapture = null
                                editingPageIndex = -1
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Cancel", tint = EduRed)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Preset Quick Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val detected = ScanEngine.autoDetectCorners(currentBmp)
                                corners.clear()
                                corners.addAll(detected)
                                Toast.makeText(
                                    context,
                                    if (language == AppLanguage.VI) "Đã tự động căn lại góc giấy!" else "Auto-aligned corners!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EduYellow,
                                contentColor = EduTeal
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (language == AppLanguage.VI) "Tự nhận diện" else "Auto-Detect",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        Button(
                            onClick = {
                                corners[0] = PointFNorm(0.02f, 0.02f)
                                corners[1] = PointFNorm(0.98f, 0.02f)
                                corners[2] = PointFNorm(0.98f, 0.98f)
                                corners[3] = PointFNorm(0.02f, 0.98f)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Fullscreen, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (language == AppLanguage.VI) "Toàn bộ" else "Full",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                corners[0] = PointFNorm(0.12f, 0.08f)
                                corners[1] = PointFNorm(0.88f, 0.08f)
                                corners[2] = PointFNorm(0.88f, 0.92f)
                                corners[3] = PointFNorm(0.12f, 0.92f)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AspectRatio, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (language == AppLanguage.VI) "Chuẩn A4" else "A4 Ratio",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Dynamic Aspect Ratio Interactive Box
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(imgAspect)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        val boxW = constraints.maxWidth.toFloat()
                        val boxH = constraints.maxHeight.toFloat()

                        Image(
                            bitmap = currentBmp.asImageBitmap(),
                            contentDescription = "Capture",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Unified Touch & Gesture Handler
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(currentBmp) {
                                    var draggingCornerIdx = -1

                                    detectDragGestures(
                                        onDragStart = { startOffset ->
                                            var minDistance = Float.MAX_VALUE
                                            var closestIdx = 0
                                            for (i in corners.indices) {
                                                val cX = corners[i].x * boxW
                                                val cY = corners[i].y * boxH
                                                val dist = hypot(startOffset.x - cX, startOffset.y - cY)
                                                if (dist < minDistance) {
                                                    minDistance = dist
                                                    closestIdx = i
                                                }
                                            }
                                            draggingCornerIdx = closestIdx
                                            selectedCornerIndex = closestIdx
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            if (draggingCornerIdx in 0..3) {
                                                val cur = corners[draggingCornerIdx]
                                                val newX = ((cur.x * boxW + dragAmount.x) / boxW).coerceIn(0f, 1f)
                                                val newY = ((cur.y * boxH + dragAmount.y) / boxH).coerceIn(0f, 1f)
                                                corners[draggingCornerIdx] = PointFNorm(newX, newY)
                                            }
                                        },
                                        onDragEnd = {
                                            draggingCornerIdx = -1
                                        },
                                        onDragCancel = {
                                            draggingCornerIdx = -1
                                        }
                                    )
                                }
                        ) {
                            // Draw Polygon & Crosshair handles
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val p0 = Offset(corners[0].x * boxW, corners[0].y * boxH)
                                val p1 = Offset(corners[1].x * boxW, corners[1].y * boxH)
                                val p2 = Offset(corners[2].x * boxW, corners[2].y * boxH)
                                val p3 = Offset(corners[3].x * boxW, corners[3].y * boxH)

                                val poly = Path().apply {
                                    moveTo(p0.x, p0.y)
                                    lineTo(p1.x, p1.y)
                                    lineTo(p2.x, p2.y)
                                    lineTo(p3.x, p3.y)
                                    close()
                                }

                                drawPath(path = poly, color = Color(0x3520A653))
                                drawPath(path = poly, color = Color(0xFFF9DF68), style = Stroke(width = 5f))

                                val points = listOf(p0, p1, p2, p3)
                                points.forEachIndexed { i, pt ->
                                    val isSelected = i == selectedCornerIndex

                                    drawCircle(
                                        color = if (isSelected) Color(0x66F9DF68) else Color(0x3308665B),
                                        radius = if (isSelected) 36f else 28f,
                                        center = pt
                                    )

                                    drawCircle(
                                        color = if (isSelected) Color(0xFFF9DF68) else Color.White,
                                        radius = 22f,
                                        center = pt
                                    )

                                    drawCircle(
                                        color = Color(0xFF08665B),
                                        radius = 22f,
                                        center = pt,
                                        style = Stroke(width = 4f)
                                    )

                                    drawCircle(
                                        color = Color(0xFF08665B),
                                        radius = 7f,
                                        center = pt
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Micro-Adjustment D-Pad & Corner Selector
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(8.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (language == AppLanguage.VI) "Tinh chỉnh góc đang chọn:" else "Fine-tune corner:",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                val cornerNames = if (language == AppLanguage.VI)
                                    listOf("1.Trên-Trái", "2.Trên-Phải", "3.Dưới-Phải", "4.Dưới-Trái")
                                else
                                    listOf("1.Top-Left", "2.Top-Right", "3.Bottom-Right", "4.Bottom-Left")

                                Text(
                                    text = cornerNames.getOrElse(selectedCornerIndex) { "" },
                                    color = EduYellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 4 Corner selection chips
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    listOf(
                                        0 to "TL",
                                        1 to "TR",
                                        2 to "BR",
                                        3 to "BL"
                                    ).forEach { (idx, label) ->
                                        val isSel = selectedCornerIndex == idx
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSel) EduYellow else Color.White.copy(alpha = 0.15f))
                                                .clickable { selectedCornerIndex = idx }
                                                .padding(horizontal = 9.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSel) EduTeal else Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                }

                                // 4 Nudge Directional Arrows
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    val step = 0.006f // ~0.6% precision nudge

                                    IconButton(
                                        onClick = {
                                            val c = corners[selectedCornerIndex]
                                            corners[selectedCornerIndex] = PointFNorm((c.x - step).coerceIn(0f, 1f), c.y)
                                        },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "Left", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            val c = corners[selectedCornerIndex]
                                            corners[selectedCornerIndex] = PointFNorm(c.x, (c.y - step).coerceIn(0f, 1f))
                                        },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Icon(Icons.Default.ArrowUpward, contentDescription = "Up", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            val c = corners[selectedCornerIndex]
                                            corners[selectedCornerIndex] = PointFNorm(c.x, (c.y + step).coerceIn(0f, 1f))
                                        },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Icon(Icons.Default.ArrowDownward, contentDescription = "Down", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }

                                    IconButton(
                                        onClick = {
                                            val c = corners[selectedCornerIndex]
                                            corners[selectedCornerIndex] = PointFNorm((c.x + step).coerceIn(0f, 1f), c.y)
                                        },
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.White.copy(alpha = 0.2f))
                                    ) {
                                        Icon(Icons.Default.ArrowForward, contentDescription = "Right", tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Filter choices
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ScanFilter.values().forEach { flt ->
                            val label = if (language == AppLanguage.VI) flt.titleVi else flt.titleEn
                            FilterChip(
                                selected = selectedFilter == flt,
                                onClick = { selectedFilter = flt },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EduYellow,
                                    selectedLabelColor = EduTeal,
                                    containerColor = Color.White.copy(alpha = 0.2f),
                                    labelColor = Color.White
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Save / Update button
                    Button(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val warped = ScanEngine.warpQuad(currentBmp, corners)
                                val filtered = ScanEngine.applyFilter(warped, selectedFilter)

                                if (isEditingExisting && editingPageIndex in scannedPages.indices) {
                                    // Update existing page in place!
                                    val old = scannedPages[editingPageIndex]
                                    scannedPages[editingPageIndex] = old.copy(
                                        corners = corners.map { PointFNorm(it.x, it.y) },
                                        filter = selectedFilter,
                                        bitmap = filtered
                                    )
                                    withContext(Dispatchers.Main) {
                                        activeCapture = null
                                        editingPageIndex = -1
                                        Toast.makeText(
                                            context,
                                            if (language == AppLanguage.VI) "Đã cập nhật lại trang scan!" else "Page updated!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                } else {
                                    // Add new page
                                    scannedPages.add(
                                        ScanPageItem(
                                            id = UUID.randomUUID().toString(),
                                            originalBitmap = currentBmp,
                                            corners = corners.map { PointFNorm(it.x, it.y) },
                                            filter = selectedFilter,
                                            bitmap = filtered,
                                            rotation = 0
                                        )
                                    )
                                    withContext(Dispatchers.Main) {
                                        activeCapture = null
                                        editingPageIndex = -1
                                        Toast.makeText(
                                            context,
                                            if (language == AppLanguage.VI) "Đã lưu trang vào danh sách scan!" else "Page saved to scan list!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isEditingExisting) EduGreen else EduYellow,
                            contentColor = if (isEditingExisting) Color.White else EduTeal
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("crop_save_page_button")
                    ) {
                        Icon(
                            imageVector = if (isEditingExisting) Icons.Default.Check else Icons.Default.Crop,
                            contentDescription = null,
                            tint = if (isEditingExisting) Color.White else EduTeal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isEditingExisting) {
                                if (language == AppLanguage.VI) "Cập Nhật Lại Trang Này" else "Save Changes to Page"
                            } else {
                                if (language == AppLanguage.VI) "Cắt & Lưu Trang Này" else "Crop & Save Page"
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Scanned Pages List & Export section
        if (scannedPages.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(EduSurface)
                    .border(2.dp, EduGreen, RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (language == AppLanguage.VI) "Đã scan ${scannedPages.size} trang" else "${scannedPages.size} pages scanned",
                                color = EduTeal,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = if (language == AppLanguage.VI) "Chạm vào trang bất kỳ để sửa lại góc hoặc bộ lọc" else "Tap any page to re-crop or change filter",
                                color = EduSlate,
                                fontSize = 11.sp
                            )
                        }

                        IconButton(onClick = { scannedPages.clear() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear all", tint = EduRed)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Horizontal preview list
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(scannedPages) { idx, page ->
                            val isCurrentlyEditing = editingPageIndex == idx

                            Box(
                                modifier = Modifier
                                    .width(115.dp)
                                    .aspectRatio(0.72f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(EduTint)
                                    .border(
                                        width = if (isCurrentlyEditing) 2.5.dp else 1.dp,
                                        color = if (isCurrentlyEditing) EduYellow else EduBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        // Tap on page opens editor to recrop and change filter!
                                        startEditingPage(idx)
                                    }
                            ) {
                                Image(
                                    bitmap = page.bitmap.asImageBitmap(),
                                    contentDescription = "Scan ${idx + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Top Page number badge
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(EduTeal.copy(alpha = 0.9f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("${idx + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    // Zoom icon
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(alpha = 0.85f))
                                            .clickable {
                                                previewBitmap = page.bitmap
                                                previewTitle = "Trang ${idx + 1} / Page ${idx + 1}"
                                            }
                                            .padding(3.dp)
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = "Zoom", tint = EduTeal, modifier = Modifier.size(14.dp))
                                    }
                                }

                                // Bottom action bar: Edit, Rotate, Delete
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.92f))
                                        .padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Sửa (Edit / Recrop)
                                    IconButton(
                                        onClick = { startEditingPage(idx) },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = EduTeal, modifier = Modifier.size(15.dp))
                                    }

                                    // Xoay 90° (Rotate)
                                    IconButton(
                                        onClick = {
                                            page.bitmap = ScanEngine.rotateBitmap(page.bitmap, 90)
                                            scannedPages[idx] = page.copy(bitmap = page.bitmap)
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.RotateRight, contentDescription = "Rotate", tint = EduTeal, modifier = Modifier.size(15.dp))
                                    }

                                    // Xóa (Delete)
                                    IconButton(
                                        onClick = {
                                            if (editingPageIndex == idx) {
                                                editingPageIndex = -1
                                                activeCapture = null
                                            }
                                            scannedPages.removeAt(idx)
                                        },
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = EduRed, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Export PDF Button
                    Button(
                        onClick = {
                            scope.launch {
                                progress = ProcessProgress(
                                    isProcessing = true,
                                    percent = 40,
                                    title = if (language == AppLanguage.VI) "Đang xuất tài liệu PDF A4..." else "Exporting A4 PDF...",
                                    description = "${scannedPages.size} trang scan"
                                )

                                withContext(Dispatchers.IO) {
                                    val outFile = File(context.cacheDir, "EduDoc_Scan_${System.currentTimeMillis()}.pdf")
                                    val pagesData = scannedPages.map { Pair(it.bitmap, it.rotation) }
                                    PdfEngine.createPdfFromBitmaps(pagesData, selectedLayout, outFile)
                                    exportedPdf = outFile

                                    withContext(Dispatchers.Main) {
                                        progress = ProcessProgress(isProcessing = false)
                                        PdfEngine.shareFile(
                                            context,
                                            outFile,
                                            "application/pdf",
                                            title = if (language == AppLanguage.VI) "Chia sẻ file PDF đã scan" else "Share scanned PDF"
                                        )
                                        Toast.makeText(context, "Xuất PDF thành công!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EduTeal,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_scan_pdf_button")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = EduYellow, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == AppLanguage.VI) "Xuất Thành File PDF A4" else "Export to A4 PDF",
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        } else if (activeCapture == null) {
            UploadCard(
                title = if (language == AppLanguage.VI) "Scan Tài Liệu Bằng Camera" else "Scan Documents with Camera",
                description = if (language == AppLanguage.VI)
                    "Chụp ảnh trang sách, bài tập, giấy khen. Tự động tìm góc và làm phẳng phối cảnh (Perspective Warp), lọc nét chữ như Adobe Scan, xuất PDF A4 sắc nét."
                else
                    "Capture book pages, assignments, or documents. Auto-detect edges, perspective warp, enhance clarity, and export print-ready A4 PDF.",
                buttonText = if (language == AppLanguage.VI) "Bật Camera Chụp Ngay" else "Start Camera Scan",
                icon = Icons.Default.CameraAlt,
                onPickFile = {
                    editingPageIndex = -1
                    onStartCameraClick()
                }
            )
        }
    }

    // Lightbox Modal for zooming in on a page
    if (previewBitmap != null) {
        ImageLightboxDialog(
            bitmap = previewBitmap,
            title = previewTitle,
            onDismiss = { previewBitmap = null },
            onShare = {
                val bmp = previewBitmap ?: return@ImageLightboxDialog
                scope.launch(Dispatchers.IO) {
                    val pageFile = File(context.cacheDir, "EduDoc_Scan_Page.png")
                    FileOutputStream(pageFile).use { out ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    withContext(Dispatchers.Main) {
                        PdfEngine.shareFile(context, pageFile, "image/png")
                    }
                }
            }
        )
    }
}
