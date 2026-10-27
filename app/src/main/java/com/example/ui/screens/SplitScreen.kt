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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.A4Layout
import com.example.model.AppLanguage
import com.example.model.ProcessProgress
import com.example.ui.components.ProgressBarCard
import com.example.ui.components.UploadCard
import com.example.ui.theme.EduBorder
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
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class SplitMode {
    ALL_SINGLE,
    RANGE,
    VISUAL
}

@Composable
fun SplitScreen(
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var pages by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var selectedPages = remember { mutableStateListOf<Boolean>() }

    var splitMode by remember { mutableStateOf(SplitMode.VISUAL) }
    var rangeInput by remember { mutableStateOf("1-2") }
    var progress by remember { mutableStateOf(ProcessProgress()) }
    var exportedFile by remember { mutableStateOf<File?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Tai_lieu.pdf"
            pages = emptyList()
            selectedPages.clear()
            exportedFile = null

            scope.launch {
                progress = ProcessProgress(
                    isProcessing = true,
                    percent = 20,
                    title = if (language == AppLanguage.VI) "Đang tải trang PDF..." else "Loading PDF pages...",
                    description = fileName
                )

                withContext(Dispatchers.IO) {
                    val rendered = PdfEngine.renderPdfPages(context, uri, scale = 1.3f)
                    pages = rendered
                    selectedPages.addAll(List(rendered.size) { false })
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

        if (selectedUri == null || pages.isEmpty()) {
            UploadCard(
                title = if (language == AppLanguage.VI) "Chọn File PDF Cần Tách" else "Select PDF to Split",
                description = if (language == AppLanguage.VI)
                    "Hỗ trợ 3 chế độ: Tách toàn bộ thành từng trang đơn (ZIP), Tách theo dải trang (ví dụ: 1-3, 5) hoặc chọn trực quan trên lưới xem trước."
                else
                    "Supports 3 split modes: Extract all individual pages (ZIP), Split by custom range (e.g. 1-3, 5), or visually select on grid.",
                buttonText = if (language == AppLanguage.VI) "Chọn Tệp PDF" else "Select PDF File",
                icon = Icons.Default.CallSplit,
                onPickFile = { filePicker.launch(arrayOf("application/pdf")) }
            )
        } else {
            // Header panel
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
                                text = if (language == AppLanguage.VI) "Tổng số ${pages.size} trang" else "${pages.size} pages total",
                                color = EduSlate,
                                fontSize = 12.sp
                            )
                        }

                        IconButton(onClick = { filePicker.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Change file", tint = EduTeal)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Split Mode Selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = splitMode == SplitMode.VISUAL,
                            onClick = { splitMode = SplitMode.VISUAL },
                            label = { Text(if (language == AppLanguage.VI) "Chọn trên lưới" else "Visual Pick", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EduYellow, selectedLabelColor = EduTeal)
                        )
                        FilterChip(
                            selected = splitMode == SplitMode.RANGE,
                            onClick = { splitMode = SplitMode.RANGE },
                            label = { Text(if (language == AppLanguage.VI) "Theo dải trang" else "By Range", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EduYellow, selectedLabelColor = EduTeal)
                        )
                        FilterChip(
                            selected = splitMode == SplitMode.ALL_SINGLE,
                            onClick = { splitMode = SplitMode.ALL_SINGLE },
                            label = { Text(if (language == AppLanguage.VI) "Tất cả (ZIP)" else "All Pages", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = EduYellow, selectedLabelColor = EduTeal)
                        )
                    }

                    if (splitMode == SplitMode.RANGE) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = rangeInput,
                            onValueChange = { rangeInput = it },
                            label = { Text(if (language == AppLanguage.VI) "Nhập dải trang (VD: 1-3, 5)" else "Enter page range (e.g. 1-3, 5)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EduGreen,
                                focusedLabelColor = EduTeal
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Execute Split Button
                    Button(
                        onClick = {
                            val uri = selectedUri ?: return@Button
                            scope.launch {
                                progress = ProcessProgress(
                                    isProcessing = true,
                                    percent = 25,
                                    title = if (language == AppLanguage.VI) "Đang thực hiện tách PDF..." else "Splitting PDF...",
                                    description = fileName
                                )

                                withContext(Dispatchers.IO) {
                                    val safeName = PdfEngine.sanitizeFilename(fileName.removeSuffix(".pdf"))

                                    when (splitMode) {
                                        SplitMode.VISUAL -> {
                                            val selectedIndices = selectedPages.indices.filter { selectedPages[it] }
                                            if (selectedIndices.isEmpty()) {
                                                withContext(Dispatchers.Main) {
                                                    progress = ProcessProgress(isProcessing = false)
                                                    Toast.makeText(context, "Vui lòng tick chọn ít nhất 1 trang!", Toast.LENGTH_SHORT).show()
                                                }
                                                return@withContext
                                            }

                                            val outFile = File(context.cacheDir, "${safeName}_Tach_${selectedIndices.size}Trang.pdf")
                                            val pagesToExport = selectedIndices.map { Pair(pages[it], 0) }
                                            PdfEngine.createPdfFromBitmaps(pagesToExport, A4Layout.PORTRAIT, outFile)
                                            exportedFile = outFile
                                        }
                                        SplitMode.RANGE -> {
                                            val indices = parseRangeInput(rangeInput, pages.size)
                                            if (indices.isEmpty()) {
                                                withContext(Dispatchers.Main) {
                                                    progress = ProcessProgress(isProcessing = false)
                                                    Toast.makeText(context, "Dải trang không hợp lệ!", Toast.LENGTH_SHORT).show()
                                                }
                                                return@withContext
                                            }

                                            val outFile = File(context.cacheDir, "${safeName}_Tach_Dai_${rangeInput.replace(" ", "")}.pdf")
                                            val pagesToExport = indices.map { Pair(pages[it], 0) }
                                            PdfEngine.createPdfFromBitmaps(pagesToExport, A4Layout.PORTRAIT, outFile)
                                            exportedFile = outFile
                                        }
                                        SplitMode.ALL_SINGLE -> {
                                            val zipFile = File(context.cacheDir, "${safeName}_TatCaTrang_PDF.zip")
                                            ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                                                pages.forEachIndexed { idx, bmp ->
                                                    val singlePdf = File(context.cacheDir, "temp_p_${idx + 1}.pdf")
                                                    PdfEngine.createPdfFromBitmaps(listOf(Pair(bmp, 0)), A4Layout.PORTRAIT, singlePdf)

                                                    val entry = ZipEntry("Trang_${idx + 1}.pdf")
                                                    zos.putNextEntry(entry)
                                                    singlePdf.inputStream().use { input -> input.copyTo(zos) }
                                                    zos.closeEntry()
                                                    singlePdf.delete()
                                                }
                                            }
                                            exportedFile = zipFile
                                        }
                                    }

                                    withContext(Dispatchers.Main) {
                                        progress = ProcessProgress(isProcessing = false)
                                        val f = exportedFile
                                        if (f != null) {
                                            val mime = if (f.name.endsWith(".zip")) "application/zip" else "application/pdf"
                                            PdfEngine.shareFile(context, f, mime, title = "Chia sẻ tệp đã tách")
                                            Toast.makeText(context, "Tách thành công!", Toast.LENGTH_SHORT).show()
                                        }
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
                            .testTag("execute_split_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallSplit,
                            contentDescription = null,
                            tint = EduYellow,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (language == AppLanguage.VI) "Thực Hiện Tách File" else "Extract & Split File",
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Grid of pages
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(pages) { index, bitmap ->
                    val isChecked = selectedPages.getOrNull(index) ?: false

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(EduSurface)
                            .border(
                                width = if (isChecked) 2.dp else 1.dp,
                                color = if (isChecked) EduGreen else EduBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                if (index < selectedPages.size) {
                                    selectedPages[index] = !isChecked
                                }
                            }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Trang ${index + 1}",
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(6.dp),
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

                            if (splitMode == SplitMode.VISUAL) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (index < selectedPages.size) {
                                            selectedPages[index] = checked
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = EduGreen,
                                        checkmarkColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

fun parseRangeInput(input: String, totalPages: Int): List<Int> {
    val result = mutableSetOf<Int>()
    val parts = input.split(",", ";")
    for (part in parts) {
        val trimmed = part.trim()
        if (trimmed.contains("-")) {
            val range = trimmed.split("-")
            val start = range.getOrNull(0)?.trim()?.toIntOrNull()
            val end = range.getOrNull(1)?.trim()?.toIntOrNull()
            if (start != null && end != null) {
                for (p in start..end) {
                    if (p in 1..totalPages) {
                        result.add(p - 1)
                    }
                }
            }
        } else {
            val single = trimmed.toIntOrNull()
            if (single != null && single in 1..totalPages) {
                result.add(single - 1)
            }
        }
    }
    return result.sorted()
}
