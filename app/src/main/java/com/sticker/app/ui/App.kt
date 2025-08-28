package com.sticker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sticker.app.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
MaterialTheme(colorScheme = neonScheme()) {
Box(Modifier.fillMaxSize()) {
NeonBackground()
Scaffold(
containerColor = Color.Transparent,
topBar = {
TopAppBar(
title = { Text("4K Sticker Generator") },
colors = TopAppBarDefaults.topAppBarColors(
containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
titleContentColor = MaterialTheme.colorScheme.onPrimary
)
)
}
) { pad ->
val api = BuildConfig.FAL_KEY
val previewSlug = BuildConfig.PREVIEW_MODEL_SLUG
val finalSlug = BuildConfig.FINAL_MODEL_SLUG
                Column(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(12.dp)
            ) {
                if (api.isBlank()) {
                    AssistChip(onClick = {}, label = { Text("Set FAL_KEY in GitHub Secrets and rebuild") })
                    Spacer(Modifier.height(8.dp))
                }
                ElevatedCard(
                    Modifier.fillMaxWidth(),
                    elevation = CardDefaults.elevatedCardElevation(10.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)
                    )
                ) {
                    GeneratorScreen(
                        modifier = Modifier.padding(12.dp),
                        api = api,
                        previewVersion = previewSlug,
                        finalVersion = finalSlug
                    )
                }
            }
        }
    }
}
}

@Composable
private fun neonScheme() = lightColorScheme(
primary = Color(0xFF7C4DFF),
onPrimary = Color.White,
secondary = Color(0xFF00D2FF),
background = Color(0xFF0D0F1A),
onBackground = Color(0xFFE6E9F7),
surface = Color(0xFF111326),
onSurface = Color(0xFFE6E9F7),
tertiary = Color(0xFFFF4081)
)

@Composable
private fun NeonBackground() {
Canvas(Modifier.fillMaxSize()) {
val w = size.width
val h = size.height
// three soft radial glows
drawCircle(
brush = Brush.radialGradient(
colors = listOf(Color(0x5540C4FF), Color.Transparent),
center = Offset(w * 0.2f, h * 0.25f),
radius = w * 0.5f
),
radius = w * 0.5f,
center = Offset(w * 0.2f, h * 0.25f)
)
drawCircle(
brush = Brush.radialGradient(
colors = listOf(Color(0x55FF4081), Color.Transparent),
center = Offset(w * 0.8f, h * 0.7f),
radius = w * 0.45f
),
radius = w * 0.45f,
center = Offset(w * 0.8f, h * 0.7f)
)
drawCircle(
brush = Brush.radialGradient(
colors = listOf(Color(0x557C4DFF), Color.Transparent),
center = Offset(w * 0.4f, h * 0.85f),
radius = w * 0.4f
),
radius = w * 0.4f,
center = Offset(w * 0.4f, h * 0.85f)
)
}
}
