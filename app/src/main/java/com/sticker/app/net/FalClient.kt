package com.sticker.app.net

import android.content.Context
import android.net.Uri
import android.os.Environment
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okio.sink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class FalClient(private val apiKey: String) {
    private val ok = OkHttpClient.Builder()
    .callTimeout(300, TimeUnit.SECONDS)
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(300, TimeUnit.SECONDS)
    .build()

// POST a JSON body to a FAL model endpoint and return the JSON response
private fun postJson(url: String, body: JSONObject): JSONObject {
    val reqBody = body.toString().toRequestBody("application/json".toMediaType())
    val req = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Key $apiKey")
        .addHeader("Content-Type", "application/json")
        .post(reqBody)
        .build()

    ok.newCall(req).execute().use { resp ->
        val txt = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) {
            val human = when (resp.code) {
                401 -> "Unauthorized (check FAL_KEY)"
                403 -> "Balance exhausted / account locked. Top up at fal.ai/dashboard/billing"
                else -> "HTTP ${resp.code}"
            }
            error("FAL error ${resp.code}: ${if (txt.isNotBlank()) txt else human}")
        }
        return JSONObject(if (txt.isNotBlank()) txt else "{}")
    }
}

// Convert a selected image to a data URL most FAL img2img models accept under "image_url"
fun imageUriToDataUrl(ctx: Context, uri: Uri): String {
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Cannot read image")
    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    return "data:image/jpeg;base64,$b64"
}

// Extract a single image URL from a variety of FAL response shapes
private fun extractImageUrl(json: JSONObject): String {
    // Try common top-level keys in order
    val candidates = listOf("images", "image", "output", "result")

    for (key in candidates) {
        if (!json.has(key)) continue
        val v = json.get(key)
        when (v) {
            is String -> return v
            is JSONObject -> {
                // {"image":{"url":"..."}} or {"output":{"url":"..."}}
                v.optString("url")?.takeIf { it.isNotBlank() }?.let { return it }
                // Sometimes nested again: {"output": {"images":[{"url":...}]}}
                v.optJSONArray("images")?.let { arr ->
                    extractFromArray(arr)?.let { return it }
                }
            }
            is JSONArray -> {
                extractFromArray(v)?.let { return it }
            }
        }
    }
    // Try to look deeper (defensive)
    json.optJSONObject("data")?.let { inner ->
        inner.optString("url")?.takeIf { it.isNotBlank() }?.let { return it }
        inner.optJSONArray("images")?.let { arr -> extractFromArray(arr)?.let { return it } }
    }
    error("Unknown FAL output format: $json")
}

private fun extractFromArray(arr: JSONArray): String? {
    if (arr.length() == 0) return null
    val first = arr.get(0)
    return when (first) {
        is String -> first
        is JSONObject -> first.optString("url").takeIf { it.isNotBlank() }
        else -> null
    }
}

// Preview (1024). modelSlug example: "fal-ai/flux-schnell"
fun runPreview(modelSlug: String, imageDataUrl: String, prompt: String): String {
    val url = "https://fal.run/$modelSlug"
    val input = JSONObject().apply {
        put("prompt", prompt)
        put("image_url", imageDataUrl)
        put("width", 1024)
        put("height", 1024)
        put("num_inference_steps", 16)   // a little cheaper/faster than 20
        put("guidance_scale", 7.0)
    }
    val body = JSONObject().put("input", input)
    val json = postJson(url, body)
    return extractImageUrl(json)
}

// Final (attempt 4K — many endpoints tile internally)
// modelSlug example: "fal-ai/flux-pro"
fun runFinal(modelSlug: String, imageDataUrl: String, prompt: String): String {
    val url = "https://fal.run/$modelSlug"
    val input = JSONObject().apply {
        put("prompt", prompt)
        put("image_url", imageDataUrl)
        put("width", 4096)
        put("height", 4096)
        put("num_inference_steps", 28)
        put("guidance_scale", 7.0)
        put("strength", 0.55)
        // If your chosen model supports explicit tiling parameters, add them here:
        // put("tile_size", 1024)
        // put("overlap", 128)
    }
    val body = JSONObject().put("input", input)
    val json = postJson(url, body)
    return extractImageUrl(json)
}

// Save a URL response to the public Downloads folder
fun downloadToDownloads(url: String, fileName: String): String {
    val req = Request.Builder().url(url).build()
    ok.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) error("Download failed ${resp.code}")
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val out = File(dir, fileName)
        resp.body?.source()?.use { src ->
            out.sink().buffer().use { sink -> sink.writeAll(src) }
        }
        return out.absolutePath
    }
}
}
