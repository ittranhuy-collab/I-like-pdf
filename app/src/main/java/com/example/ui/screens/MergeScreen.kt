package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.A4Layout
import com.example.model.AppLanguage
import com.example.model.DocPageItem
import com.example.model.ProcessProgress
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
import com.example.ui.theme.EduYellow
import com.example.ui.theme.EduTint
import com.example.util.PdfEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun MergeScreen(
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pageList = remember { mutableStateListOf<DocPageItem>() }
    var selectedLayout by remember { mutableStateOf(A4Layout.PORTRAIT) }
    var outputFileName by remember { mutableStateOf("Ghep_Noi_EduDoc") }
    var progress by remember { mutableStateOf(ProcessProgress()) }
    var lastExportedFile by remember { mutableStateOf<File?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                progress = ProcessProgress(
                    isProcessing = true,
                    percent = 20,
                    title = if (language == AppLanguage.VI) "Đang nạp file & giải nén trang..." else "Loading & rendering pages...",
                    description = "${uris.size} tệp đã chọn"
                )

                withContext(Dispatchers.IO) {
                    uris.forEachIndexed { fIdx, uri ->
                        val mime = context.contentResolver.getType(uri) ?: ""
                        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "File_${fIdx + 1}"

                        if (mime.contains("pdf") || name.endsWith(".pdf", ignoreCase = true)) {
                            val rendered = PdfEngine.renderPdfPages(context, uri, scale = 1.4f)
                            rendered.forEachIndexed { pIdx, bmp ->
                                pageList.add(
                                    DocPageItem(
                                        id = UUID.randomUUID().toString(),
                                        pageNumber = pIdx + 1,
                                        sourceName = name,
                                        bitmap = bmp,
                                        rotation = 0,
                                        isSelected = true
                                    )
                                )
                            }
                        } else {
                            // Image file
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                val bmp = BitmapFactory.decodeStream(stream)
                                if (bmp != null) {
                                    pageList.add(
                                        DocPageItem(
                                            id = UUID.randomUUID().toString(),
                                            pageNumber = 1,
                                            sourceName = name,
                                            bitmap = bmp,
                                            rotation = 0,
                                            isSelected = true
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                progress = ProcessProgress(isProcessing = false)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ProgressBarCard(progress = progress)

        if (pageList.isEmpty()) {
            UploadCard(
                title = if (language == AppLanguage.VI) "Thêm Các File PDF & Ảnh Cần Ghép" else "Add PDF & Image Files to Merge",
                description = if (language == AppLanguage.VI)
                    "Chọn đồng thời nhiều file PDF, JPG, PNG. Bạn có thể tự do kéo đổi thứ tự trang, xoay góc 90 độ và xuất khổ A4 Dọc hoặc Ngang chuẩn in ấn."
                else
                    "Select multiple PDF and image files. Reorder, rotate, select pages and export standard A4 print-ready PDF.",
                buttonText = if (language == AppLanguage.VI) "Chọn Nhiều Tệp PDF & Ảnh" else "Select PDF & Image Files",
                icon = Icons.Default.MergeType,
                onPickFile = { filePicker.launch(arrayOf("application/pdf", "image/*")) }
            )
        } else {
            // Control panel
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
                                text = if (language == AppLanguage.VI) "Tổng số trang: ${pageList.size}" else "Total Pages: ${pageList.size}",
                                color = EduTeal,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            val activeCount = pageList.count { it.isSelected }
                            Text(
                                text = if (language == AppLanguage.VI) "$activeCount trang được chọn xuất" else "$activeCount pages selected",
                                color = EduSlate,
                                fontSize = 12.sp
                            )
                        }

                        Row {
                            Button(
                                onClick = { filePicker.launch(arrayOf("application/pdf", "image/*")) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EduTint,
                                    contentColor = EduTeal
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = EduTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Thêm file", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(onClick = { pageList.clear() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Clear all", tint = EduRed)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Layout selector
                    Text(
                        text = if (language == AppLanguage.VI) "Khổ giấy xuất:" else "Page Layout:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = EduText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        A4Layout.values().forEach { layout ->
                            val label = when (layout) {
                                A4Layout.PORTRAIT -> if (language == AppLanguage.VI) "A4 Dọc" else "Portrait"
                                A4Layout.LANDSCAPE -> if (language == AppLanguage.VI) "A4 Ngang" else "Landscape"
                                A4Layout.FIT -> if (language == AppLanguage.VI) "Tự động" else "Auto Fit"
                            }
                            FilterChip(
                                selected = selectedLayout == layout,
                                onClick = { selectedLayout = layout },
                                label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = EduYellow,
                                    selectedLabelColor = EduTeal
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Merge Button
                    Button(
                        onClick = {
                            val activePages = pageList.filter { it.isSelected }
                            if (activePages.isEmpty()) {
                                Toast.makeText(context, "Vui lòng chọn ít nhất 1 trang!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            scope.launch {
                                progress = ProcessProgress(
                                    isProcessing = true,
                                    percent = 40,
                                    title = if (language == AppLanguage.VI) "Đang xuất PDF ghép..." else "Exporting merged PDF...",
                                    description = "${activePages.size} trang"
                                )

                                withContext(Dispatchers.IO) {
                                    val safeName = PdfEngine.sanitizeFilename(outputFileName, "Ghep_Noi_EduDoc")
                                    val outFile = File(context.cacheDir, "$safeName.pdf")

                                    val pagesToRender = activePages.map { Pair(it.bitmap, it.rotation) }
                                    PdfEngine.createPdfFromBitmaps(pagesToRender, selectedLayout, outFile)
                                    lastExportedFile = outFile

                                    withContext(Dispatchers.Main) {
                                        progress = ProcessProgress(isProcessing = false)
                                        PdfEngine.shareFile(
                                            context,
                                            outFile,
                                            "application/pdf",
                                            title = if (language == AppLanguage.VI) "Chia sẻ PDF đã ghép" else "Share Merged PDF"
                                        )
                                        Toast.makeText(
                                            context,
                                            if (language == AppLanguage.VI) "Đã ghép PDF thành công!" else "PDF merged successfully!",
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
                            .testTag("execute_merge_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MergeType,
                            contentDescription = null,
                            tint = EduYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == AppLanguage.VI) "Tạo File PDF Đã Ghép" else "Create Merged PDF",
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Grid of pages with reorder & rotate controls
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(pageList) { index, item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(EduSurface)
                            .border(
                                width = if (item.isSelected) 2.dp else 1.dp,
                                color = if (item.isSelected) EduGreen else EduBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Image(
                            bitmap = item.bitmap.asImageBitmap(),
                            contentDescription = "Trang ${index + 1}",
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(item.rotation.toFloat())
                        )

                        // Top bar: Checkbox and Page index
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(EduTeal.copy(alpha = 0.85f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = EduYellow,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }

                            Checkbox(
                                checked = item.isSelected,
                                onCheckedChange = { checked ->
                                    pageList[index] = item.copy(isSelected = checked)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = EduGreen,
                                    checkmarkColor = Color.White
                                )
                            )
                        }

                        // Bottom action buttons: Shift left, Rotate 90, Shift right, Delete
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.92f))
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    if (index > 0) {
                                        val prev = pageList[index - 1]
                                        pageList[index - 1] = item
                                        pageList[index] = prev
                                    }
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Move Left",
                                    tint = if (index > 0) EduTeal else EduSlate.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    pageList[index] = item.copy(rotation = (item.rotation + 90) % 360)
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RotateRight,
                                    contentDescription = "Rotate",
                                    tint = EduTeal,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (index < pageList.size - 1) {
                                        val next = pageList[index + 1]
                                        pageList[index + 1] = item
                                        pageList[index] = next
                                    }
                                },
                                enabled = index < pageList.size - 1,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Move Right",
                                    tint = if (index < pageList.size - 1) EduTeal else EduSlate.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = { pageList.removeAt(index) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = EduRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
