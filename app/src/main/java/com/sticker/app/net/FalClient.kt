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
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class FalClient(private val apiKey: String) {
private val ok = OkHttpClient.Builder()
.callTimeout(300, TimeUnit.SECONDS)
.connectTimeout(30, TimeUnit.SECONDS)
.readTimeout(300, TimeUnit.SECONDS)
.build()

private fun postJson(url: String, body: JSONObject): JSONObject {
    val reqBody = body.toString().toRequestBody("application/json".toMediaType())
    val req = Request.Builder()
        .url(url)
        .addHeader("Authorization", "Key $apiKey")
        .addHeader("Content-Type", "application/json")
        .post(reqBody)
        .build()
    ok.newCall(req).execute().use { resp ->
        if (!resp.isSuccessful) error("FAL error ${resp.code}: ${resp.body?.string()}")
        val txt = resp.body?.string() ?: "{}"
        return JSONObject(txt)
    }
}

// Convert a picked image to a data URL that FAL accepts as "image_url"
fun imageUriToDataUrl(ctx: Context, uri: Uri): String {
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Cannot read image")
    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    return "data:image/jpeg;base64,$b64"
}

// Preview run (1024)
fun runPreview(modelSlug: String, imageDataUrl: String, prompt: String): String {
    val url = "https://fal.run/$modelSlug"
    val input = JSONObject().apply {
        put("prompt", prompt)
        put("image_url", imageDataUrl)
        put("width", 1024)
        put("height", 1024)
        put("num_inference_steps", 20)
        put("guidance_scale", 7.0)
    }
    val body = JSONObject().put("input", input)
    val json = postJson(url, body)
    val out = json.opt("images") ?: json.opt("image") ?: json.opt("output")
    return when (out) {
        is String -> out
        is org.json.JSONArray -> out.optString(0)
        is JSONObject -> out.optString("url")
        else -> error("Unknown FAL output format")
    }
}

// Final run (attempt 4K; model may tile internally)
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
    }
    val body = JSONObject().put("input", input)
    val json = postJson(url, body)
    val out = json.opt("images") ?: json.opt("image") ?: json.opt("output")
    return when (out) {
        is String -> out
        is org.json.JSONArray -> out.optString(0)
        is JSONObject -> out.optString("url")
        else -> error("Unknown FAL output format")
    }
}

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
