package com.sticker.app.net

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.squareup.moshi.JsonClass
import okhttp3.*
import okio.buffer
import okio.sink
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.*
import java.io.File
import java.util.concurrent.TimeUnit

interface ReplicateApi {
    @Multipart
    @POST("v1/files")
    suspend fun uploadFile(@Part file: MultipartBody.Part): UploadResp

    @POST("v1/predictions")
    suspend fun createPrediction(@Body body: CreatePredictionReq): PredictionResp

    @GET("v1/predictions/{id}")
    suspend fun getPrediction(@Path("id") id: String): PredictionResp
}

@JsonClass(generateAdapter = true) data class UploadResp(val url: String?)
@JsonClass(generateAdapter = true) data class CreatePredictionReq(val version: String, val input: Map<String, @JvmSuppressWildcards Any>)
@JsonClass(generateAdapter = true) data class PredictionResp(val id: String, val status: String, val output: Any?, val error: String?)

class ReplicateClient(private val token: String) {
    private val ok = OkHttpClient.Builder()
        .callTimeout(300, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    private val api: ReplicateApi = Retrofit.Builder()
        .baseUrl("https://api.replicate.com/")
        .client(ok.newBuilder().addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("Authorization", "Token $token")
                .addHeader("Content-Type", "application/json")
                .build()
            chain.proceed(req)
        }.build())
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(ReplicateApi::class.java)

    suspend fun uploadImage(ctx: Context, uri: Uri): String {
        val stream = ctx.contentResolver.openInputStream(uri) ?: error("Cannot read image")
        val bytes = stream.readBytes(); stream.close()
        val body = RequestBody.create("application/octet-stream".toMediaType(), bytes)
        val part = MultipartBody.Part.createFormData("file", "upload.jpg", body)
        val resp = api.uploadFile(part)
        return resp.url ?: error("Upload failed")
    }

    suspend fun generateImage(version: String, input: Map<String, Any>): String {
        val create = api.createPrediction(CreatePredictionReq(version, input))
        var cur = create
        while (cur.status in listOf("starting","processing","queued","running")) {
            Thread.sleep(2000)
            cur = api.getPrediction(cur.id)
        }
        if (cur.status != "succeeded") throw RuntimeException("Generation failed: ${cur.error ?: cur.status}")
        val out = cur.output
        return when (out) {
            is String -> out
            is List<*> -> (out.firstOrNull() as? String) ?: error("Empty output")
            else -> error("Unknown output format")
        }
    }

    fun downloadToDownloads(ctx: Context, url: String, fileName: String): String {
        val req = Request.Builder().url(url).build()
        ok.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("Download failed ${resp.code}")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val out = File(dir, fileName)
            resp.body?.source()?.use { src -> out.sink().buffer().use { sink -> sink.writeAll(src) } }
            return out.absolutePath
        }
    }
}
