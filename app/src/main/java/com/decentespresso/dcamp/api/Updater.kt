package com.decentespresso.dcamp.api

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.decentespresso.dcamp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val apkSize: Long)

/**
 * Self-updater for the sideloaded APK. Checks the GitHub repo's latest release, compares it to
 * the installed versionName, and — on the user's OK — downloads the release APK and hands it to
 * the system installer. This only makes sense for the sideloaded build (Play would forbid it);
 * it's the Android analogue of the iOS SideStep catalog auto-update.
 */
object Updater {
    private const val LATEST = "https://api.github.com/repos/johnbuckman/dcamp-android/releases/latest"
    private val client = OkHttpClient()

    /** Latest release info if it's newer than this build and not the version the user dismissed;
     *  null otherwise (including on any network/parse error). Safe on any dispatcher. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(LATEST)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "dcamp-android").build()
            val body = client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                r.body?.string()
            } ?: return@withContext null
            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            if (tag.isEmpty() || !isNewer(tag, BuildConfig.VERSION_NAME) || tag == AppConfig.dismissedUpdate) {
                return@withContext null
            }
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var url: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    url = a.optString("browser_download_url"); size = a.optLong("size"); break
                }
            }
            val apk = url ?: return@withContext null
            UpdateInfo(tag, json.optString("body").trim(), apk, size)
        } catch (e: Exception) {
            null
        }
    }

    /** Download the APK into cacheDir/updates, reporting 0f..1f progress. Returns the file or null. */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder().url(info.apkUrl).header("User-Agent", "dcamp-android").build()
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@withContext null
                    val stream = r.body?.byteStream() ?: return@withContext null
                    val total = if (info.apkSize > 0) info.apkSize else (r.body?.contentLength() ?: -1L)
                    val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
                    val out = File(dir, "dcamp-${info.version}.apk")
                    out.outputStream().use { fos ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var n: Int
                        while (stream.read(buf).also { n = it } >= 0) {
                            fos.write(buf, 0, n)
                            read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    onProgress(1f)
                    out
                }
            } catch (e: Exception) {
                null
            }
        }

    /** Launch the system package installer for a downloaded APK (needs REQUEST_INSTALL_PACKAGES). */
    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    /** True if `remote` semver > `local`, compared component-wise (missing components count as 0). */
    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".", "-").mapNotNull { it.toIntOrNull() }
        val l = local.split(".", "-").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
