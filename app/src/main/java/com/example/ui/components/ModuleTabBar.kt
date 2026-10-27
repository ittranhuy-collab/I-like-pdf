package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppLanguage
import com.example.model.EduModule
import com.example.ui.theme.EduBorder
import com.example.ui.theme.EduGreen
import com.example.ui.theme.EduSlate
import com.example.ui.theme.EduSurface
import com.example.ui.theme.EduTeal
import com.example.ui.theme.EduText
import com.example.ui.theme.EduYellow

@Composable
fun ModuleTabBar(
    selectedModule: EduModule,
    onSelectModule: (EduModule) -> Unit,
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EduModule.values().forEach { module ->
            val isSelected = module == selectedModule
            val title = if (language == AppLanguage.VI) module.titleVi else module.titleEn
            val icon = getModuleIcon(module)

            Box(
                modifier = Modifier
                    .testTag("tab_${module.id}")
                    .shadow(if (isSelected) 3.dp else 0.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) EduYellow else EduSurface)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) EduGreen else EduBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectModule(module) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isSelected) EduTeal else EduSlate,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = if (isSelected) EduTeal else EduText,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

fun getModuleIcon(module: EduModule): ImageVector {
    return when (module) {
        EduModule.CONVERT -> Icons.Default.Image
        EduModule.MERGE -> Icons.Default.MergeType
        EduModule.COMPRESS -> Icons.Default.Compress
        EduModule.SPLIT -> Icons.Default.CallSplit
        EduModule.WATERMARK -> Icons.Default.WaterDrop
        EduModule.SCAN -> Icons.Default.CameraAlt
    }
}
