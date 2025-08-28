package com.sticker.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sticker.app.BuildConfig
import com.sticker.app.net.FalClient
import com.sticker.app.prompts.CATEGORIES
import com.sticker.app.prompts.PROMPTS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun GeneratorScreen(
modifier: Modifier = Modifier,
api: String,
previewVersion: String,
finalVersion: String
) {
val ctx = LocalContext.current
val scope = rememberCoroutineScope()
val client = remember { FalClient(api) }
val previewSlug = if (previewVersion.isNotBlank()) previewVersion else BuildConfig.PREVIEW_MODEL_SLUG
val finalSlug = if (finalVersion.isNotBlank()) finalVersion else BuildConfig.FINAL_MODEL_SLUG

var imageUri by remember { mutableStateOf<Uri?>(null) }
var previewUrls by remember { mutableStateOf(listOf<String>()) }
var selectedPromptIndex by remember { mutableStateOf(0) }
var generating by remember { mutableStateOf(false) }
var status by remember { mutableStateOf("") }

val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
    imageUri = it
    previewUrls = emptyList()
}

// Cost control
val categoryKeys = remember { CATEGORIES.keys.toList() }
var runAll by remember { mutableStateOf(false) } // false = single category (20)
var selectedCategory by remember { mutableStateOf(categoryKeys.first()) }
var categoryMenu by remember { mutableStateOf(false) }

Column(modifier = modifier.padding(16.dp)) {
    // Upload + thumb
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { pickImage.launch("image/*") }) {
            Text(if (imageUri == null) "Upload photo" else "Change photo")
        }
        Spacer(Modifier.width(12.dp))
        imageUri?.let { uri ->
            loadThumb(ctx, uri)?.let { bm ->
                Image(bm.asImageBitmap(), null, Modifier.size(64.dp))
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // Generate previews
    Button(
        onClick = {
            if (imageUri == null) return@Button
            generating = true; status = "Uploading image…"
            scope.launch(Dispatchers.IO) {
                try {
                    val dataUrl = client.imageUriToDataUrl(ctx, imageUri!!)
                    val urls = mutableListOf<String>()
                    for ((i, p) in PROMPTS.withIndex()) {
                        status = "Preview ${i + 1}/5…"
                        val out = client.runPreview(
                            modelSlug = previewSlug,
                            imageDataUrl = dataUrl,
                            prompt = p + ", head-only portrait, clean white background"
                        )
                        urls.add(out)
                    }
                    previewUrls = urls
                    status = "Previews ready. Select a prompt, then generate."
                } catch (e: Exception) {
                    status = "Error: ${e.message}"
                } finally { generating = false }
            }
        },
        enabled = imageUri != null && !generating
    ) { Text("Generate 5 previews") }

    if (previewUrls.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text("Select one of the 5 prompts:")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { i ->
                FilterChip(
                    selected = selectedPromptIndex == i,
                    onClick = { selectedPromptIndex = i },
                    label = { Text("Prompt ${i + 1}") }
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        // Mode switch + simple dropdown
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = runAll, onCheckedChange = { runAll = it })
            Spacer(Modifier.width(8.dp))
            Text(if (runAll) "All 5 categories (100)" else "Single category (20)")
            Spacer(Modifier.width(12.dp))
            if (!runAll) {
                Box {
                    Button(onClick = { categoryMenu = true }) { Text("Category: $selectedCategory") }
                    DropdownMenu(expanded = categoryMenu, onDismissRequest = { categoryMenu = false }) {
                        categoryKeys.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    selectedCategory = c
                                    categoryMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        // Previews gallery
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(220.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(previewUrls) { url -> PreviewImage(url) }
        }
    }

    Spacer(Modifier.height(12.dp))

    // Generate finals
    Button(
        onClick = {
            if (imageUri == null) return@Button
            generating = true
            status = if (runAll) "Starting 100× 4K…" else "Starting 20× 4K…"
            scope.launch(Dispatchers.IO) {
                try {
                    val dataUrl = client.imageUriToDataUrl(ctx, imageUri!!)
                    val basePrompt = PROMPTS[selectedPromptIndex]
                    val catsToRun = if (runAll) CATEGORIES.entries.toList()
                    else listOf(CATEGORIES.entries.first { it.key == selectedCategory })

                    var done = 0
                    val total = catsToRun.sumOf { it.value.size }

                    for ((cat, list) in catsToRun) {
                        for ((k, variant) in list.withIndex()) {
                            status = "[$cat] ${k + 1}/${list.size} (${++done}/$total)"
                            val prompt = "$basePrompt, $variant, sticker, white background, head-only, no neck"
                            val out = client.runFinal(
                                modelSlug = finalSlug,
                                imageDataUrl = dataUrl,
                                prompt = prompt
                            )
                            client.downloadToDownloads(out, "${cat}_${k + 1}.jpg")
                        }
                    }
                    status = "Done. Saved $total images in Downloads."
                } catch (e: Exception) {
                    status = "Error: ${e.message}"
                } finally { generating = false }
            }
        },
        enabled = imageUri != null && previewUrls.isNotEmpty() && !generating
    ) { Text(if (runAll) "Generate 100 at 4K" else "Generate 20 at 4K") }

    Spacer(Modifier.height(8.dp))
    if (generating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Text(status, style = MaterialTheme.typography.bodySmall)
}
}

@Composable
private fun PreviewImage(url: String) {
var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
LaunchedEffect(url) {
try {
java.net.URL(url).openStream().use { s ->
bmp = BitmapFactory.decodeStream(s)
}
} catch (_: Throwable) {}
}
bmp?.let {
Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(120.dp))
}
}

private fun loadThumb(ctx: Context, uri: Uri): android.graphics.Bitmap? =
ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
