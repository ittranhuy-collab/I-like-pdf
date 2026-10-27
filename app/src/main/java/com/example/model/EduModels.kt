package com.example.model

import android.graphics.Bitmap

enum class AppLanguage {
    VI, EN
}

enum class EduModule(
    val id: String,
    val titleVi: String,
    val titleEn: String,
    val subtitleVi: String,
    val subtitleEn: String
) {
    CONVERT(
        "convert",
        "1. Chuyển Word/PDF → Ảnh",
        "1. Word/PDF → Images",
        "Kết xuất từng trang thành ảnh nét & tải ZIP",
        "Export pages as sharp images & ZIP"
    ),
    MERGE(
        "merge",
        "2. Ghép Nối PDF & Ảnh",
        "2. Merge PDF & Images",
        "Sắp xếp thứ tự, xoay góc & chuẩn khổ A4",
        "Reorder, rotate & A4 output"
    ),
    COMPRESS(
        "compress",
        "3. Nén File PDF",
        "3. Compress PDF",
        "8 mức nén thông minh & ước tính dung lượng",
        "8 smart compression levels & size preview"
    ),
    SPLIT(
        "split",
        "4. Tách File PDF Đa Năng",
        "4. Split PDF",
        "Tách lẻ từng trang, theo dải hoặc chọn trực quan",
        "Extract single pages, ranges or visually"
    ),
    WATERMARK(
        "watermark",
        "5. Đóng Dấu Watermark",
        "5. Watermark PDF",
        "Thêm chữ chéo hoặc ngang bảo vệ bản quyền",
        "Stamp custom watermark on all pages"
    ),
    SCAN(
        "scan",
        "6. Scan Camera → PDF",
        "6. Scan Camera → PDF",
        "Chụp tài liệu, căn chỉnh 4 góc & bộ lọc nét",
        "Capture document, 4-corner warp & filters"
    )
}

enum class A4Layout(val titleVi: String, val titleEn: String) {
    PORTRAIT("A4 Dọc (Portrait)", "A4 Portrait"),
    LANDSCAPE("A4 Ngang (Landscape)", "A4 Landscape"),
    FIT("Tự động theo trang gốc (Fit)", "Auto Fit")
}

data class DocPageItem(
    val id: String,
    val pageNumber: Int,
    val sourceName: String,
    val bitmap: Bitmap,
    val widthPt: Float = 595.28f,
    val heightPt: Float = 841.89f,
    var rotation: Int = 0,
    var isSelected: Boolean = true
)

data class ScanPageItem(
    val id: String,
    val originalBitmap: Bitmap,
    var corners: List<PointFNorm>,
    var filter: ScanFilter = ScanFilter.AUTO,
    var bitmap: Bitmap,
    var rotation: Int = 0
)

enum class ScanFilter(val titleVi: String, val titleEn: String) {
    AUTO("Tự động", "Auto"),
    ORIGINAL("Gốc", "Original"),
    GRAYSCALE("Xám", "Grayscale"),
    BLACK_WHITE("Trắng đen", "B&W"),
    ENHANCE("Tăng sáng", "Brighten")
}

data class PointFNorm(var x: Float, var y: Float)

data class CompressionLevel(
    val level: Int,
    val scale: Float,
    val quality: Int,
    val titleVi: String,
    val descVi: String,
    val titleEn: String,
    val descEn: String
)

val COMPRESSION_PRESETS = listOf(
    CompressionLevel(1, 2.00f, 92, "Mức 1", "Siêu nét", "Level 1", "Ultra sharp"),
    CompressionLevel(2, 1.75f, 88, "Mức 2", "Nét cao", "Level 2", "High quality"),
    CompressionLevel(3, 1.55f, 84, "Mức 3", "Chuẩn+", "Level 3", "Standard+"),
    CompressionLevel(4, 1.40f, 80, "Mức 4", "Khuyên dùng", "Level 4", "Recommended"),
    CompressionLevel(5, 1.22f, 74, "Mức 5", "Thu gọn", "Level 5", "Compact"),
    CompressionLevel(6, 1.08f, 66, "Mức 6", "Nhẹ", "Level 6", "Light"),
    CompressionLevel(7, 0.92f, 55, "Mức 7", "Rất nhỏ", "Level 7", "Very small"),
    CompressionLevel(8, 0.78f, 42, "Mức 8", "Cực nhỏ", "Level 8", "Minimal")
)

data class WatermarkOptions(
    var text: String = "BẢN SAO - CHỈ DÙNG HỌC TẬP",
    var opacity: Float = 0.28f,
    var angle: Float = -45f,
    var colorHex: String = "#888888"
)

data class ProcessProgress(
    val isProcessing: Boolean = false,
    val percent: Int = 0,
    val title: String = "",
    val description: String = ""
)
