package com.example.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import com.example.model.PointFNorm
import com.example.model.ScanFilter
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ScanEngine {

    /**
     * Safely loads an image file from disk (e.g. from camera),
     * downsamples if excessively large (e.g. 108MP on Galaxy S22 Ultra) to avoid OOM,
     * and corrects orientation according to EXIF metadata.
     */
    fun loadAndCorrectOrientation(file: File, maxDimension: Int = 2400): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null

        try {
            // First decode bounds
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)

            var inSampleSize = 1
            val maxOriginal = max(options.outWidth, options.outHeight)
            while (maxOriginal / inSampleSize > maxDimension) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val rawBitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

            // Read EXIF orientation
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotationDegrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }

            return if (rotationDegrees != 0) {
                rotateBitmap(rawBitmap, rotationDegrees)
            } else {
                rawBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Warps a 4-corner quadrilateral from source bitmap to a rectified rectangular bitmap.
     * Uses Android's Skia native Matrix.setPolyToPoly for high performance.
     */
    fun warpQuad(
        srcBitmap: Bitmap,
        corners: List<PointFNorm> // [TL, TR, BR, BL] in normalized 0..1 coordinates
    ): Bitmap {
        val bw = srcBitmap.width.toFloat()
        val bh = srcBitmap.height.toFloat()

        val tlX = corners[0].x.coerceIn(0f, 1f) * bw
        val tlY = corners[0].y.coerceIn(0f, 1f) * bh
        val trX = corners[1].x.coerceIn(0f, 1f) * bw
        val trY = corners[1].y.coerceIn(0f, 1f) * bh
        val brX = corners[2].x.coerceIn(0f, 1f) * bw
        val brY = corners[2].y.coerceIn(0f, 1f) * bh
        val blX = corners[3].x.coerceIn(0f, 1f) * bw
        val blY = corners[3].y.coerceIn(0f, 1f) * bh

        // Calculate destination width and height based on edge lengths
        val widthTop = hypot(trX - tlX, trY - tlY)
        val widthBottom = hypot(brX - blX, brY - blY)
        val heightLeft = hypot(blX - tlX, blY - tlY)
        val heightRight = hypot(brX - trX, brY - trY)

        var outW = max(widthTop, widthBottom).roundToInt()
        var outH = max(heightLeft, heightRight).roundToInt()

        // Clamp to max 2400 to prevent OOM while keeping ultra-sharp resolution
        val maxDim = 2400
        val largest = max(outW, outH)
        if (largest > maxDim) {
            val scale = maxDim.toFloat() / largest
            outW = max(200, (outW * scale).roundToInt())
            outH = max(200, (outH * scale).roundToInt())
        }
        outW = max(100, outW)
        outH = max(100, outH)

        val srcPoints = floatArrayOf(
            tlX, tlY,
            trX, trY,
            brX, brY,
            blX, blY
        )

        val dstPoints = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )

        val matrix = Matrix()
        matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)

        val resultBitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(srcBitmap, matrix, paint)

        return resultBitmap
    }

    /**
     * Highly robust auto-detection of document boundaries.
     * Uses directional contrast gradient scanning to find paper edges against laptop/table surfaces.
     */
    fun autoDetectCorners(bitmap: Bitmap): List<PointFNorm> {
        val sampleW = 200
        val sampleH = ((sampleW.toFloat() / bitmap.width) * bitmap.height).roundToInt().coerceIn(120, 360)
        val scaled = Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)

        val pixels = IntArray(sampleW * sampleH)
        scaled.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        scaled.recycle()

        // 1. Compute grayscale and brightness map
        val lum = FloatArray(sampleW * sampleH)
        var totalLum = 0.0
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            lum[i] = l
            totalLum += l
        }
        val avgLum = (totalLum / pixels.size).toFloat()

        // 2. Compute vertical & horizontal gradient (Sobel-like edge energy)
        fun getLum(x: Int, y: Int): Float {
            val cx = x.coerceIn(0, sampleW - 1)
            val cy = y.coerceIn(0, sampleH - 1)
            return lum[cy * sampleW + cx]
        }

        // Find document bounds from center outward
        val centerX = sampleW / 2
        val centerY = sampleH / 2

        // Scan Top boundary (from top downwards along multiple vertical columns in the middle 60%)
        val topCandidates = mutableListOf<Int>()
        for (x in (sampleW * 0.25).roundToInt()..(sampleW * 0.75).roundToInt() step 6) {
            for (y in 4 until centerY) {
                val diff = getLum(x, y + 2) - getLum(x, y - 2)
                // Paper is typically brighter than background table/keyboard
                if (diff > 18f || (getLum(x, y) > avgLum * 1.08f && getLum(x, y - 4) < avgLum * 0.95f)) {
                    topCandidates.add(y)
                    break
                }
            }
        }

        // Scan Bottom boundary (from bottom upwards)
        val bottomCandidates = mutableListOf<Int>()
        for (x in (sampleW * 0.25).roundToInt()..(sampleW * 0.75).roundToInt() step 6) {
            for (y in (sampleH - 5) downTo centerY) {
                val diff = getLum(x, y - 2) - getLum(x, y + 2)
                if (diff > 18f || (getLum(x, y) > avgLum * 1.08f && getLum(x, y + 4) < avgLum * 0.95f)) {
                    bottomCandidates.add(y)
                    break
                }
            }
        }

        // Scan Left boundary (from left towards center)
        val leftCandidates = mutableListOf<Int>()
        for (y in (sampleH * 0.25).roundToInt()..(sampleH * 0.75).roundToInt() step 6) {
            for (x in 4 until centerX) {
                val diff = getLum(x + 2, y) - getLum(x - 2, y)
                if (diff > 18f || (getLum(x, y) > avgLum * 1.08f && getLum(x - 4, y) < avgLum * 0.95f)) {
                    leftCandidates.add(x)
                    break
                }
            }
        }

        // Scan Right boundary (from right towards center)
        val rightCandidates = mutableListOf<Int>()
        for (y in (sampleH * 0.25).roundToInt()..(sampleH * 0.75).roundToInt() step 6) {
            for (x in (sampleW - 5) downTo centerX) {
                val diff = getLum(x - 2, y) - getLum(x + 2, y)
                if (diff > 18f || (getLum(x, y) > avgLum * 1.08f && getLum(x + 4, y) < avgLum * 0.95f)) {
                    rightCandidates.add(x)
                    break
                }
            }
        }

        // Calculate median edges
        fun median(list: List<Int>, fallback: Int): Int {
            if (list.isEmpty()) return fallback
            val sorted = list.sorted()
            return sorted[sorted.size / 2]
        }

        val topEdge = median(topCandidates, (sampleH * 0.12).roundToInt()).coerceIn(4, (sampleH * 0.35).roundToInt())
        val bottomEdge = median(bottomCandidates, (sampleH * 0.88).roundToInt()).coerceIn((sampleH * 0.65).roundToInt(), sampleH - 4)
        val leftEdge = median(leftCandidates, (sampleW * 0.12).roundToInt()).coerceIn(4, (sampleW * 0.35).roundToInt())
        val rightEdge = median(rightCandidates, (sampleW * 0.88).roundToInt()).coerceIn((sampleW * 0.65).roundToInt(), sampleW - 4)

        // Verify valid document size
        val docWidth = rightEdge - leftEdge
        val docHeight = bottomEdge - topEdge

        if (docWidth < sampleW * 0.3f || docHeight < sampleH * 0.3f) {
            // Document taking up most of photo -> provide standard 8% border
            return listOf(
                PointFNorm(0.06f, 0.06f),
                PointFNorm(0.94f, 0.06f),
                PointFNorm(0.94f, 0.94f),
                PointFNorm(0.06f, 0.94f)
            )
        }

        val fx1 = (leftEdge.toFloat() / sampleW).coerceIn(0.02f, 0.40f)
        val fx2 = (rightEdge.toFloat() / sampleW).coerceIn(0.60f, 0.98f)
        val fy1 = (topEdge.toFloat() / sampleH).coerceIn(0.02f, 0.40f)
        val fy2 = (bottomEdge.toFloat() / sampleH).coerceIn(0.60f, 0.98f)

        return listOf(
            PointFNorm(fx1, fy1), // TL
            PointFNorm(fx2, fy1), // TR
            PointFNorm(fx2, fy2), // BR
            PointFNorm(fx1, fy2)  // BL
        )
    }

    /**
     * Applies enhancement filter to clean up scanned documents.
     */
    fun applyFilter(bitmap: Bitmap, filter: ScanFilter): Bitmap {
        return when (filter) {
            ScanFilter.ORIGINAL -> bitmap
            ScanFilter.GRAYSCALE -> applyGrayscale(bitmap)
            ScanFilter.BLACK_WHITE -> applyOtsuBinary(bitmap)
            ScanFilter.ENHANCE -> applyEnhance(bitmap, contrast = 1.35f, brightness = 20f)
            ScanFilter.AUTO -> applyAutoDoc(bitmap)
        }
    }

    private fun applyGrayscale(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix()
        cm.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun applyEnhance(src: Bitmap, contrast: Float, brightness: Float): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val cm = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun applyAutoDoc(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val sat = ColorMatrix()
        sat.setSaturation(0.5f)

        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                1.25f, 0f, 0f, 0f, 15f,
                0f, 1.25f, 0f, 0f, 15f,
                0f, 0f, 1.25f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        sat.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(sat)
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun applyOtsuBinary(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val hist = IntArray(256)
        val lums = IntArray(pixels.size)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val l = (0.299f * r + 0.587f * g + 0.114f * b).roundToInt().coerceIn(0, 255)
            lums[i] = l
            hist[l]++
        }

        val total = w * h
        var sum = 0.0
        for (t in 0..255) sum += t * hist[t]

        var sumB = 0.0
        var wB = 0
        var maxVariance = 0.0
        var threshold = 128

        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break

            sumB += t * hist[t]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val variance = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)

            if (variance > maxVariance) {
                maxVariance = variance
                threshold = t
            }
        }

        val outPixels = IntArray(pixels.size)
        val white = -0x1
        val black = -0x1000000
        for (i in pixels.indices) {
            outPixels[i] = if (lums[i] > threshold) white else black
        }

        return Bitmap.createBitmap(outPixels, w, h, Bitmap.Config.ARGB_8888)
    }

    fun rotateBitmap(src: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return src
        val matrix = Matrix()
        matrix.postRotate(degrees.toFloat())
        val rotated = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (rotated != src) {
            src.recycle()
        }
        return rotated
    }
}
