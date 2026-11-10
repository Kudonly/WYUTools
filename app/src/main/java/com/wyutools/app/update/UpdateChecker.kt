package com.wyutools.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val version: String,
    val tag: String,
    val name: String,
    val notes: String,
    /** APK 直链（Release 资产的 browser_download_url）；没有 apk 资产时回退到发布页。 */
    val url: String,
)

sealed interface UpdateResult {
    data class Available(val release: ReleaseInfo) : UpdateResult
    data class Latest(val version: String) : UpdateResult
    data object NoRelease : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

/** 通过 GitHub Releases 检查更新；仓库没有 Release 时返回 [UpdateResult.NoRelease]。 */
object UpdateChecker {
    private const val REPO = "Kudonly/WYUTools"
    private const val RELEASES_API = "https://api.github.com/repos/$REPO/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/$REPO/releases"

    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(RELEASES_API)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "WYUTools-Android")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 404) return@withContext UpdateResult.NoRelease
                if (!response.isSuccessful) {
                    val message = if (response.code == 403) {
                        "GitHub 接口访问受限，请稍后再试。"
                    } else {
                        "检查更新失败（HTTP ${response.code}）。"
                    }
                    return@withContext UpdateResult.Failed(message)
                }
                val body = response.body?.string().orEmpty()
                val root = runCatching { JSONObject(body) }.getOrNull()
                    ?: return@withContext UpdateResult.Failed("更新信息格式异常，请稍后重试。")
                val tag = root.optString("tag_name").trim()
                if (tag.isEmpty()) {
                    return@withContext UpdateResult.Failed("更新信息缺少版本号。")
                }
                val version = tag.removePrefix("v").removePrefix("V")
                val page = root.optString("html_url").trim().ifEmpty { RELEASES_PAGE }
                val release = ReleaseInfo(
                    version = version,
                    tag = tag,
                    name = root.optString("name").trim().ifEmpty { tag },
                    notes = root.optString("body").trim(),
                    // Hand the browser the asset itself instead of the release
                    // page: "前往下载" then starts the download straight away
                    // and nobody has to hunt for the apk in the asset list.
                    url = pickApkDownloadUrl(root, page),
                )
                if (compareVersions(version, currentVersion) > 0) {
                    UpdateResult.Available(release)
                } else {
                    UpdateResult.Latest(version)
                }
            }
        } catch (error: IOException) {
            UpdateResult.Failed("网络连接失败，请检查网络后重试。")
        } catch (error: Exception) {
            UpdateResult.Failed("检查更新失败，请稍后重试。")
        }
    }

    /**
     * Release 里的 apk 资产直链。优先名字带 release 的正式包，跳过 debug 包；
     * 只有 debug 包时也退而取它，一个 apk 都没有才回落到发布页。
     */
    private fun pickApkDownloadUrl(root: JSONObject, fallback: String): String {
        val assets = root.optJSONArray("assets") ?: return fallback
        var anyApk: String? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val fileName = asset.optString("name").trim().lowercase()
            val downloadUrl = asset.optString("browser_download_url").trim()
            if (downloadUrl.isEmpty() || !fileName.endsWith(".apk")) continue
            if ("debug" in fileName) continue
            if (anyApk == null) anyApk = downloadUrl
            if ("release" in fileName) return downloadUrl
        }
        return anyApk ?: fallback
    }

    private fun compareVersions(latest: String, current: String): Int {
        val latestParts = versionParts(latest)
        val currentParts = versionParts(current)
        val size = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until size) {
            val left = latestParts.getOrElse(index) { 0 }
            val right = currentParts.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private fun versionParts(raw: String): List<Int> =
        raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .split(Regex("[^0-9]+"))
            .mapNotNull { it.toIntOrNull() }
}
