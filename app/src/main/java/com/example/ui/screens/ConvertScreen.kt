package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppLanguage
import com.example.model.ProcessProgress
import com.example.ui.components.ImageLightboxDialog
import com.example.ui.components.ProgressBarCard
import com.example.ui.components.UploadCard
import com.example.ui.theme.EduBorder
import com.example.ui.theme.EduCream
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduSlate
import com.example.ui.theme.EduSurface
import com.example.ui.theme.EduTeal
import com.example.ui.theme.EduText
import com.example.ui.theme.EduTint
import com.example.ui.theme.EduYellow
import com.example.util.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun ConvertScreen(
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var progress by remember { mutableStateOf(ProcessProgress()) }

    var selectedFormat by remember { mutableStateOf("PNG") } // "PNG" or "JPEG"
    var selectedDpiScale by remember { mutableFloatStateOf(2.0f) } // 1.5f, 2.0f, 2.5f

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewTitle by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Tai_lieu.pdf"
            pages = emptyList()

            scope.launch {
                progress = ProcessProgress(
                    isProcessing = true,
                    percent = 10,
                    title = if (language == AppLanguage.VI) "Đang đọc tệp PDF..." else "Reading PDF...",
                    description = fileName
                )

                try {
                    val rendered = withContext(Dispatchers.IO) {
                        PdfEngine.renderPdfPages(
                            context = context,
                            uri = uri,
                            scale = selectedDpiScale,
                            maxPages = 60
                        ) { cur, total ->
                            val pct = (cur * 100 / total)
                            progress = ProcessProgress(
                                isProcessing = true,
                                percent = pct,
                                title = if (language == AppLanguage.VI) "Đang kết xuất trang $cur/$total" else "Rendering page $cur/$total",
                                description = fileName
                            )
                        }
                    }
                    pages = rendered
                    progress = ProcessProgress(isProcessing = false)
                } catch (e: Exception) {
                    e.printStackTrace()
                    progress = ProcessProgress(isProcessing = false)
                    Toast.makeText(context, "Lỗi đọc file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ProgressBarCard(progress = progress)

        if (selectedUri == null || pages.isEmpty()) {
            UploadCard(
                title = if (language == AppLanguage.VI) "Chọn File PDF Hoặc Văn Bản" else "Select PDF or Document",
                description = if (language == AppLanguage.VI)
                    "Chuyển đổi từng trang thành ảnh sắc nét (PNG/JPEG) với độ phân giải tùy chỉnh. Tương thích tải về file nén ZIP mở được trên Windows & WinRAR."
                else
                    "Convert each page into ultra sharp PNG/JPEG images. 100% compatible with Windows & WinRAR ZIP archives.",
                buttonText = if (language == AppLanguage.VI) "Chọn Tệp Từ Thiết Bị" else "Select File From Device",
                icon = Icons.Default.Image,
                onPickFile = { filePicker.launch(arrayOf("application/pdf")) }
            )
        } else {
            // Options & Export Header
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = fileName,
                                color = EduTeal,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = if (language == AppLanguage.VI) "Tổng cộng ${pages.size} trang đã kết xuất" else "${pages.size} pages rendered",
                                color = EduSlate,
                                fontSize = 12.sp
                            )
                        }

                        IconButton(
                            onClick = { filePicker.launch(arrayOf("application/pdf")) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Change file",
                                tint = EduTeal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Format & DPI Selectors
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (language == AppLanguage.VI) "Định dạng:" else "Format:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EduText
                        )

                        listOf("PNG", "JPEG").forEach { fmt ->
                            FilterChip(
                                selected = selectedFormat == fmt,
                                onClick = { selectedFormat = fmt },
                                label = { Text(fmt, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EduYellow,
                                    selectedLabelColor = EduTeal
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "DPI:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = EduText
                        )

                        listOf(
                            1.5f to "1.5x",
                            2.0f to "2.0x ★",
                            2.5f to "2.5x In"
                        ).forEach { (scale, label) ->
                            FilterChip(
                                selected = selectedDpiScale == scale,
                                onClick = { selectedDpiScale = scale },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EduYellow,
                                    selectedLabelColor = EduTeal
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Download ZIP button
                    Button(
                        onClick = {
                            scope.launch {
                                progress = ProcessProgress(
                                    isProcessing = true,
                                    percent = 20,
                                    title = if (language == AppLanguage.VI) "Đang nén file ZIP..." else "Compressing ZIP...",
                                    description = "WinRAR 100% Compatible"
                                )

                                withContext(Dispatchers.IO) {
                                    val safeName = PdfEngine.sanitizeFilename(fileName.removeSuffix(".pdf"))
                                    val ext = if (selectedFormat == "PNG") "png" else "jpg"
                                    val zipFile = File(context.cacheDir, "${safeName}_Anh_$selectedFormat.zip")

                                    val items = pages.mapIndexed { idx, bmp ->
                                        Pair("Trang_${idx + 1}.$ext", bmp)
                                    }

                                    PdfEngine.exportToZip(items, zipFile, isPng = selectedFormat == "PNG")

                                    withContext(Dispatchers.Main) {
                                        progress = ProcessProgress(isProcessing = false)
                                        PdfEngine.shareFile(
                                            context,
                                            zipFile,
                                            "application/zip",
                                            title = if (language == AppLanguage.VI) "Tải file ZIP ảnh" else "Download Images ZIP"
                                        )
                                        Toast.makeText(
                                            context,
                                            if (language == AppLanguage.VI) "Đã tạo ZIP thành công! Đang mở chia sẻ..." else "ZIP created successfully!",
                                            Toast.LENGTH_SHORT
                                        ).show()
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
                            .testTag("download_zip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Archive,
                            contentDescription = null,
                            tint = EduYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == AppLanguage.VI) "Tải Tất Cả Trang (Tệp ZIP)" else "Download All Pages (ZIP)",
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pages Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(pages) { index, bitmap ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(EduSurface)
                            .border(1.5.dp, EduBorder, RoundedCornerShape(16.dp))
                            .clickable {
                                previewBitmap = bitmap
                                previewTitle = "Trang ${index + 1} / Page ${index + 1}"
                            }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Trang ${index + 1}",
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top page badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(EduTeal.copy(alpha = 0.85f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = EduYellow,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // Bottom action icons
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.9f))
                                .padding(2.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    previewBitmap = bitmap
                                    previewTitle = "Trang ${index + 1}"
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomIn,
                                    contentDescription = "Zoom",
                                    tint = EduTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val ext = if (selectedFormat == "PNG") "png" else "jpg"
                                        val pageFile = File(context.cacheDir, "Trang_${index + 1}.$ext")
                                        FileOutputStream(pageFile).use { out ->
                                            if (selectedFormat == "PNG") {
                                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                            } else {
                                                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            PdfEngine.shareFile(
                                                context,
                                                pageFile,
                                                if (selectedFormat == "PNG") "image/png" else "image/jpeg",
                                                title = "Chia sẻ Trang ${index + 1}"
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = EduTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Lightbox modal
    if (previewBitmap != null) {
        ImageLightboxDialog(
            bitmap = previewBitmap,
            title = previewTitle,
            onDismiss = { previewBitmap = null },
            onShare = {
                val bmp = previewBitmap ?: return@ImageLightboxDialog
                scope.launch(Dispatchers.IO) {
                    val pageFile = File(context.cacheDir, "EduDoc_Preview.png")
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
