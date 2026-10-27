package com.example.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppLanguage
import com.example.model.ProcessProgress
import com.example.model.WatermarkOptions
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

@Composable
fun WatermarkScreen(
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var pageCount by remember { mutableIntStateOf(0) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var watermarkText by remember { mutableStateOf("BẢN SAO - CHỈ DÙNG HỌC TẬP") }
    var opacity by remember { mutableFloatStateOf(0.28f) }
    var angle by remember { mutableFloatStateOf(-45f) }
    var selectedColor by remember { mutableStateOf("#888888") }

    var progress by remember { mutableStateOf(ProcessProgress()) }
    var watermarkedFile by remember { mutableStateOf<File?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Tai_lieu.pdf"
            watermarkedFile = null

            scope.launch {
                progress = ProcessProgress(
                    isProcessing = true,
                    percent = 20,
                    title = if (language == AppLanguage.VI) "Đang nạp PDF..." else "Loading PDF...",
                    description = fileName
                )

                withContext(Dispatchers.IO) {
                    pageCount = PdfEngine.getPdfPageCount(context, uri)
                    val rendered = PdfEngine.renderPdfPages(context, uri, scale = 1.2f, maxPages = 1)
                    previewBitmap = rendered.firstOrNull()
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

        if (selectedUri == null) {
            UploadCard(
                title = if (language == AppLanguage.VI) "Đóng Dấu Bản Quyền Lên File PDF" else "Watermark Your PDF",
                description = if (language == AppLanguage.VI)
                    "Chèn dấu mờ chữ chéo hoặc ngang lên toàn bộ trang PDF: 'BẢN SAO', 'TUYỆT MẬT', tên giáo viên hoặc thương hiệu để bảo vệ tài liệu."
                else
                    "Stamp diagonal or horizontal watermark text across all pages to protect documents, exams, and lecture notes.",
                buttonText = if (language == AppLanguage.VI) "Chọn File PDF Cần Đóng Dấu" else "Select PDF to Watermark",
                icon = Icons.Default.WaterDrop,
                onPickFile = { filePicker.launch(arrayOf("application/pdf")) }
            )
        } else {
            // Document info
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(EduSurface)
                    .border(2.dp, EduGreen, RoundedCornerShape(20.dp))
                    .padding(14.dp)
            ) {
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
                            text = if (language == AppLanguage.VI) "Tổng cộng $pageCount trang" else "$pageCount pages",
                            color = EduSlate,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(onClick = { filePicker.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Change file", tint = EduTeal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Configuration Form
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(EduSurface)
                    .border(2.dp, EduGreen, RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = if (language == AppLanguage.VI) "Nội dung đóng dấu:" else "Watermark Text:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = EduTeal
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = EduGreen,
                            focusedLabelColor = EduTeal
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Quick Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("BẢN SAO", "TUYỆT MẬT", "BẢN THẢO", "TRẦN HUY").forEach { chip ->
                            FilterChip(
                                selected = watermarkText == chip,
                                onClick = { watermarkText = chip },
                                label = { Text(chip, fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EduYellow,
                                    selectedLabelColor = EduTeal
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Opacity slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (language == AppLanguage.VI) "Độ mờ (Opacity):" else "Opacity:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = EduTeal
                        )
                        Text(
                            text = "${(opacity * 100).roundToInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = EduGreen
                        )
                    }
                    Slider(
                        value = opacity,
                        onValueChange = { opacity = it },
                        valueRange = 0.10f..0.70f,
                        colors = SliderDefaults.colors(
                            thumbColor = EduTeal,
                            activeTrackColor = EduGreen
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Angle selector
                    Text(
                        text = if (language == AppLanguage.VI) "Góc nghiêng:" else "Angle:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = EduTeal
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            -45f to if (language == AppLanguage.VI) "Chéo -45°" else "-45° Diag",
                            0f to if (language == AppLanguage.VI) "Ngang 0°" else "0° Horiz",
                            -30f to "-30°",
                            45f to "+45°"
                        ).forEach { (a, label) ->
                            FilterChip(
                                selected = angle == a,
                                onClick = { angle = a },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EduYellow,
                                    selectedLabelColor = EduTeal
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Color picker
                    Text(
                        text = if (language == AppLanguage.VI) "Màu chữ dấu:" else "Color:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = EduTeal
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "#888888" to "Xám",
                            "#E94B4B" to "Đỏ",
                            "#08665B" to "Teal",
                            "#1E40AF" to "Xanh"
                        ).forEach { (hex, name) ->
                            val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { Color.Gray }
                            val isSelected = selectedColor == hex

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) EduYellow else EduTint)
                                    .border(if (isSelected) 2.dp else 1.dp, if (isSelected) EduGreen else EduBorder, RoundedCornerShape(10.dp))
                                    .clickable { selectedColor = hex }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EduTeal)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Execute Button
                    Button(
                        onClick = {
                            val uri = selectedUri ?: return@Button
                            scope.launch {
                                progress = ProcessProgress(
                                    isProcessing = true,
                                    percent = 15,
                                    title = if (language == AppLanguage.VI) "Đang đóng dấu watermark..." else "Stamping watermark...",
                                    description = "$pageCount trang"
                                )

                                withContext(Dispatchers.IO) {
                                    val safeName = PdfEngine.sanitizeFilename(fileName.removeSuffix(".pdf"))
                                    val outFile = File(context.cacheDir, "${safeName}_Watermark.pdf")

                                    val opts = WatermarkOptions(
                                        text = watermarkText.ifEmpty { "WATERMARK" },
                                        opacity = opacity,
                                        angle = angle,
                                        colorHex = selectedColor
                                    )

                                    PdfEngine.watermarkPdf(
                                        context = context,
                                        uri = uri,
                                        options = opts,
                                        outputFile = outFile
                                    ) { cur, total ->
                                        progress = ProcessProgress(
                                            isProcessing = true,
                                            percent = (cur * 100 / total),
                                            title = if (language == AppLanguage.VI) "Đang xử lý trang $cur/$total" else "Processing page $cur/$total",
                                            description = watermarkText
                                        )
                                    }

                                    watermarkedFile = outFile

                                    withContext(Dispatchers.Main) {
                                        progress = ProcessProgress(isProcessing = false)
                                        PdfEngine.shareFile(
                                            context,
                                            outFile,
                                            "application/pdf",
                                            title = if (language == AppLanguage.VI) "Chia sẻ PDF đã đóng dấu" else "Share Watermarked PDF"
                                        )
                                        Toast.makeText(context, "Đóng dấu thành công!", Toast.LENGTH_SHORT).show()
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
                            .testTag("execute_watermark_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = EduYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == AppLanguage.VI) "Đóng Dấu Toàn Bộ File PDF" else "Stamp & Export PDF",
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }
    }
}
