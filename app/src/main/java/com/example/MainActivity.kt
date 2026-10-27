package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.model.AppLanguage
import com.example.model.EduModule
import com.example.ui.components.EduTopBar
import com.example.ui.components.ModuleTabBar
import com.example.ui.screens.CompressScreen
import com.example.ui.screens.ConvertScreen
import com.example.ui.screens.MergeScreen
import com.example.ui.screens.ScanScreen
import com.example.ui.screens.SplitScreen
import com.example.ui.screens.WatermarkScreen
import com.example.ui.theme.EduWarmBg
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                EduDocMainScreen()
            }
        }
    }
}

@Composable
fun EduDocMainScreen() {
    var selectedModule by remember { mutableStateOf(EduModule.CONVERT) }
    var language by remember { mutableStateOf(AppLanguage.VI) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                EduTopBar(
                    currentLanguage = language,
                    onLanguageChange = { language = it }
                )
                ModuleTabBar(
                    selectedModule = selectedModule,
                    onSelectModule = { selectedModule = it },
                    language = language
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(EduWarmBg)
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedModule,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "ModuleSwitch"
            ) { target ->
                when (target) {
                    EduModule.CONVERT -> ConvertScreen(language = language)
                    EduModule.MERGE -> MergeScreen(language = language)
                    EduModule.COMPRESS -> CompressScreen(language = language)
                    EduModule.SPLIT -> SplitScreen(language = language)
                    EduModule.WATERMARK -> WatermarkScreen(language = language)
                    EduModule.SCAN -> ScanScreen(language = language)
                }
            }
        }
    }
}
