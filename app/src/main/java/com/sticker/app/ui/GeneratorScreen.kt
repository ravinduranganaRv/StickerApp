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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sticker.app.net.ReplicateClient
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
    val client = remember { ReplicateClient(api) }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var previewUrls by remember { mutableStateOf(listOf<String>()) }
    var selectedPromptIndex by remember { mutableStateOf(0) }
    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        imageUri = it; previewUrls = emptyList()
    }

    Column(modifier = modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { pickImage.launch("image/*") }) { Text(if (imageUri == null) "Upload photo" else "Change photo") }
            Spacer(Modifier.width(12.dp))
            imageUri?.let {
                val bm = loadThumb(ctx, it)
                if (bm != null) Image(bm.asImageBitmap(), null, Modifier.size(64.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (imageUri == null) return@Button
                generating = true; status = "Uploading image…"
                scope.launch(Dispatchers.IO) {
                    try {
                        val fileUrl = client.uploadImage(ctx, imageUri!!)
                        val urls = mutableListOf<String>()
                        for ((i, p) in PROMPTS.withIndex()) {
                            status = "Preview ${i+1}/5…"
                            val out = client.generateImage(
                                version = previewVersion,
                                input = mapOf(
                                    "image" to fileUrl,
                                    "prompt" to (p + ", head-only portrait, clean white background"),
                                    "width" to 1024, "height" to 1024,
                                    "num_inference_steps" to 25, "guidance_scale" to 7.5
                                )
                            )
                            urls.add(out)
                        }
                        previewUrls = urls
                        status = "Previews ready. Select a prompt, then generate 100."
                    } catch (e: Exception) { status = "Error: ${e.message}" }
                    finally { generating = false }
                }
            },
            enabled = imageUri != null && !generating
        ) { Text("Generate 5 previews") }

        if (previewUrls.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Select one of the 5 prompts:")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(5) { i ->
                    FilterChip(selected = selectedPromptIndex == i, onClick = { selectedPromptIndex = i }, label = { Text("Prompt ${i+1}") })
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(220.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(previewUrls.size) { idx -> PreviewImage(previewUrls[idx]) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (imageUri == null) return@Button
                generating = true; status = "Starting 100× 4K generation…"
                scope.launch(Dispatchers.IO) {
                    try {
                        val fileUrl = client.uploadImage(ctx, imageUri!!)
                        val basePrompt = PROMPTS[selectedPromptIndex]
                        var done = 0
                        val total = CATEGORIES.values.sumOf { it.size }
                        for ((cat, list) in CATEGORIES) {
                            for ((k, variant) in list.withIndex()) {
                                status = "[$cat] ${k+1}/${list.size} (${++done}/$total)"
                                val prompt = "$basePrompt, $variant, sticker, white background, head-only, no neck"
                                val out = client.generateImage(
                                    version = finalVersion,
                                    input = mapOf(
                                        "image" to fileUrl,
                                        "prompt" to prompt,
                                        "width" to 4096, "height" to 4096,
                                        "tile_size" to 1024, "overlap" to 128,
                                        "num_inference_steps" to 28, "guidance_scale" to 7.0, "strength" to 0.55
                                    )
                                )
                                client.downloadToDownloads(ctx, out, "${cat}_${k+1}.jpg")
                            }
                        }
                        status = "Done. Saved 100 images in Downloads."
                    } catch (e: Exception) { status = "Error: ${e.message}" }
                    finally { generating = false }
                }
            },
            enabled = imageUri != null && previewUrls.isNotEmpty() && !generating
        ) { Text("Generate 100 at 4K") }

        Spacer(Modifier.height(8.dp))
        if (generating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PreviewImage(url: String) {
    var bmp by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        try { java.net.URL(url).openStream().use { s -> bmp = BitmapFactory.decodeStream(s) } } catch (_: Throwable) {}
    }
    bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().height(120.dp)) }
}

private fun loadThumb(ctx: Context, uri: Uri): android.graphics.Bitmap? =
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
