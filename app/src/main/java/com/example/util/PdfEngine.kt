package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.model.A4Layout
import com.example.model.CompressionLevel
import com.example.model.WatermarkOptions
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PdfEngine {

    const val A4_PORTRAIT_W = 595
    const val A4_PORTRAIT_H = 842
    const val A4_LANDSCAPE_W = 842
    const val A4_LANDSCAPE_H = 595

    fun copyUriToTempFile(context: Context, uri: Uri, prefix: String = "edudoc_temp"): File {
        val tempFile = File.createTempFile(prefix, ".pdf", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    fun getPdfPageCount(context: Context, uri: Uri): Int {
        val tempFile = copyUriToTempFile(context, uri)
        var count = 0
        try {
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    count = renderer.pageCount
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            tempFile.delete()
        }
        return count
    }

    fun renderPdfPages(
        context: Context,
        uri: Uri,
        scale: Float = 2.0f,
        maxPages: Int = 100,
        onProgress: ((Int, Int) -> Unit)? = null
    ): List<Bitmap> {
        val tempFile = copyUriToTempFile(context, uri)
        val result = mutableListOf<Bitmap>()
        try {
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = min(renderer.pageCount, maxPages)
                    for (i in 0 until total) {
                        onProgress?.invoke(i + 1, total)
                        renderer.openPage(i).use { page ->
                            val width = (page.width * scale).roundToInt().coerceAtLeast(100)
                            val height = (page.height * scale).roundToInt().coerceAtLeast(100)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            result.add(bitmap)
                        }
                    }
                }
            }
        } finally {
            tempFile.delete()
        }
        return result
    }

    fun createPdfFromBitmaps(
        pages: List<Pair<Bitmap, Int>>, // Bitmap and rotation degrees
        layout: A4Layout,
        outputFile: File
    ) {
        val pdfDocument = PdfDocument()

        for (i in pages.indices) {
            val (origBitmap, rotation) = pages[i]
            val bitmap = if (rotation % 360 != 0) {
                ScanEngine.rotateBitmap(origBitmap, rotation)
            } else {
                origBitmap
            }

            val isBitmapWide = bitmap.width > bitmap.height
            val (pageW, pageH) = when (layout) {
                A4Layout.PORTRAIT -> Pair(A4_PORTRAIT_W, A4_PORTRAIT_H)
                A4Layout.LANDSCAPE -> Pair(A4_LANDSCAPE_W, A4_LANDSCAPE_H)
                A4Layout.FIT -> if (isBitmapWide) Pair(A4_LANDSCAPE_W, A4_LANDSCAPE_H) else Pair(A4_PORTRAIT_W, A4_PORTRAIT_H)
            }

            val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)

            // Fit bitmap with aspect ratio
            val bmpRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val pageRatio = pageW.toFloat() / pageH.toFloat()

            var drawW = pageW.toFloat()
            var drawH = pageH.toFloat()
            var drawX = 0f
            var drawY = 0f

            if (bmpRatio > pageRatio) {
                drawH = pageW / bmpRatio
                drawY = (pageH - drawH) / 2f
            } else {
                drawW = pageH * bmpRatio
                drawX = (pageW - drawW) / 2f
            }

            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val dstRect = Rect(drawX.roundToInt(), drawY.roundToInt(), (drawX + drawW).roundToInt(), (drawY + drawH).roundToInt())
            canvas.drawBitmap(bitmap, null, dstRect, paint)

            pdfDocument.finishPage(page)
        }

        FileOutputStream(outputFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()
    }

    fun compressPdf(
        context: Context,
        uri: Uri,
        level: CompressionLevel,
        outputFile: File,
        onProgress: (Int, Int) -> Unit
    ): Long {
        val tempFile = copyUriToTempFile(context, uri)
        val pdfDocument = PdfDocument()

        try {
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount
                    for (i in 0 until total) {
                        onProgress(i + 1, total)
                        renderer.openPage(i).use { page ->
                            val origW = page.width
                            val origH = page.height

                            val renderW = (origW * level.scale).roundToInt().coerceAtLeast(80)
                            val renderH = (origH * level.scale).roundToInt().coerceAtLeast(80)

                            val uncompressed = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                            val cv = Canvas(uncompressed)
                            cv.drawColor(Color.WHITE)
                            page.render(uncompressed, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                            // JPEG compress
                            val baos = ByteArrayOutputStream()
                            uncompressed.compress(Bitmap.CompressFormat.JPEG, level.quality, baos)
                            uncompressed.recycle()

                            val jpegBytes = baos.toByteArray()
                            val compressedBmp = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

                            val pageInfo = PdfDocument.PageInfo.Builder(origW, origH, i + 1).create()
                            val docPage = pdfDocument.startPage(pageInfo)
                            val canvas = docPage.canvas
                            canvas.drawColor(Color.WHITE)

                            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            val dstRect = Rect(0, 0, origW, origH)
                            canvas.drawBitmap(compressedBmp, null, dstRect, paint)
                            compressedBmp.recycle()

                            pdfDocument.finishPage(docPage)
                        }
                    }
                }
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
            tempFile.delete()
        }

        return outputFile.length()
    }

    fun watermarkPdf(
        context: Context,
        uri: Uri,
        options: WatermarkOptions,
        outputFile: File,
        onProgress: (Int, Int) -> Unit
    ) {
        val tempFile = copyUriToTempFile(context, uri)
        val pdfDocument = PdfDocument()

        try {
            ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount
                    for (i in 0 until total) {
                        onProgress(i + 1, total)
                        renderer.openPage(i).use { page ->
                            val origW = page.width
                            val origH = page.height

                            val renderW = (origW * 1.8f).roundToInt()
                            val renderH = (origH * 1.8f).roundToInt()

                            val bmp = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                            val cv = Canvas(bmp)
                            cv.drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            // Watermark paint
                            val wmColor = try {
                                Color.parseColor(options.colorHex)
                            } catch (e: Exception) {
                                Color.GRAY
                            }
                            val alpha = (options.opacity.coerceIn(0.05f, 0.9f) * 255).roundToInt()

                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = wmColor
                                this.alpha = alpha
                                textSize = max(24f, min(renderW, renderH) * 0.055f)
                                isFakeBoldText = true
                                textAlign = Paint.Align.CENTER
                            }

                            cv.save()
                            cv.translate(renderW / 2f, renderH / 2f)
                            cv.rotate(options.angle)

                            val step = paint.textSize * 4.5f
                            var y = -renderH.toFloat()
                            while (y < renderH) {
                                var x = -renderW.toFloat()
                                while (x < renderW) {
                                    cv.drawText(options.text, x, y, paint)
                                    x += step * 2.2f
                                }
                                y += step
                            }
                            cv.restore()

                            val pageInfo = PdfDocument.PageInfo.Builder(origW, origH, i + 1).create()
                            val docPage = pdfDocument.startPage(pageInfo)
                            val canvas = docPage.canvas
                            canvas.drawColor(Color.WHITE)

                            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                            canvas.drawBitmap(bmp, null, Rect(0, 0, origW, origH), p)
                            bmp.recycle()

                            pdfDocument.finishPage(docPage)
                        }
                    }
                }
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
        } finally {
            pdfDocument.close()
            tempFile.delete()
        }
    }

    fun exportToZip(
        items: List<Pair<String, Bitmap>>,
        outputFile: File,
        isPng: Boolean = true
    ) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
            for ((filename, bitmap) in items) {
                val entry = ZipEntry(filename)
                zos.putNextEntry(entry)
                val baos = ByteArrayOutputStream()
                if (isPng) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, baos)
                }
                zos.write(baos.toByteArray())
                zos.closeEntry()
            }
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String = "Chia sẻ tệp từ EduDoc Studio") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun saveToDownloads(context: Context, file: File, mimeType: String): Uri? {
        val fileName = file.name
        val resolver = context.contentResolver

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/EduDocStudio")
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                return uri
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val targetFolder = File(downloadsDir, "EduDocStudio").apply { mkdirs() }
            val targetFile = File(targetFolder, fileName)
            file.copyTo(targetFile, overwrite = true)
            return Uri.fromFile(targetFile)
        }
        return null
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        if (bytes < 1024 * 1024) return "${(bytes / 1024f).let { "%.1f".format(it) }} KB"
        return "${(bytes / (1024f * 1024f)).let { "%.2f".format(it) }} MB"
    }

    fun sanitizeFilename(raw: String, fallback: String = "Tai_lieu"): String {
        var str = raw.trim().replace(Regex("[\\\\/:*?\"<>|\\r\\n\\t]"), "_")
        str = str.replace(Regex("\\s+"), "_")
        str = str.replace(Regex("_+"), "_")
        if (str.length > 50) str = str.substring(0, 50)
        str = str.trim('_', '.')
        return if (str.isEmpty()) fallback else str
    }
}
