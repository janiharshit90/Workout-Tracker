package com.example.mentzertracker.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.mentzertracker.BuildConfig
import com.example.mentzertracker.util.fileProviderAuthority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ReleaseInfo(
    val versionName: String,
    val tagName: String,
    val notes: String,
    val apkUrl: String,
    val apkSize: Long,
    val pageUrl: String
)

sealed interface CheckResult {
    data class Available(val release: ReleaseInfo) : CheckResult
    data object UpToDate : CheckResult
    data object NotConfigured : CheckResult
    data class Error(val message: String) : CheckResult
}

data class VerifyProblem(val message: String, val signatureMismatch: Boolean = false)

/**
 * Self-updater backed by GitHub Releases.
 *
 * Flow: check /releases/latest -> compare tag to BuildConfig.VERSION_NAME -> download the
 * attached .apk (with progress) -> verify it's the same app, newer, and signed with the
 * same key -> hand it to the system installer.
 *
 * The repo comes from BuildConfig.UPDATE_REPO ("owner/repo"), set in app/build.gradle.kts.
 */
object UpdateManager {

    private const val USER_AGENT = "MentzerTracker-Updater"
    private const val APK_MIME = "application/vnd.android.package-archive"

    val repo: String get() = BuildConfig.UPDATE_REPO
    val isConfigured: Boolean get() = Regex("^[\\w.-]+/[\\w.-]+$").matches(repo)

    suspend fun check(currentVersion: String): CheckResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext CheckResult.NotConfigured
        val conn = (URL("https://api.github.com/repos/$repo/releases/latest").openConnection() as HttpURLConnection)
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", USER_AGENT)

            when (val code = conn.responseCode) {
                in 200..299 -> Unit
                404 -> return@withContext CheckResult.Error("No published releases found for $repo (is the repo public?).")
                403, 429 -> return@withContext CheckResult.Error("GitHub rate limit reached. Try again in an hour.")
                else -> return@withContext CheckResult.Error("GitHub returned HTTP $code.")
            }

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name")
            val version = tag.trim().removePrefix("v").removePrefix("V")
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkSize = 0L
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        apkSize = a.optLong("size", 0L)
                        break
                    }
                }
            }
            if (!isNewer(currentVersion, version)) return@withContext CheckResult.UpToDate
            if (apkUrl.isNullOrBlank()) return@withContext CheckResult.Error("Release $tag has no .apk attached.")

            CheckResult.Available(ReleaseInfo(
                versionName = version, tagName = tag,
                notes = json.optString("body").trim(),
                apkUrl = apkUrl, apkSize = apkSize,
                pageUrl = json.optString("html_url")
            ))
        } catch (e: IOException) {
            CheckResult.Error("Couldn't reach GitHub. Check your connection.")
        } catch (e: JSONException) {
            CheckResult.Error("Unexpected response from GitHub.")
        } finally {
            conn.disconnect()
        }
    }

    /** Numeric, segment-by-segment compare: 1.10.0 > 1.9.3, "v1.2" == "1.2.0". */
    fun isNewer(current: String, candidate: String): Boolean {
        val a = parseVersion(current)
        val b = parseVersion(candidate)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return y > x
        }
        return false
    }

    private fun parseVersion(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
            .split('.')
            .map { part -> part.filter(Char::isDigit).toIntOrNull() ?: 0 }

    /** Downloads the APK into cacheDir/updates. [onProgress] gets 0..1, or -1 if size is unknown. */
    suspend fun download(ctx: Context, release: ReleaseInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val tmp = File(dir, "download.part")
            val out = File(dir, "MentzerTracker-${release.versionName}.apk")

            val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true  // GitHub redirects to its CDN
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/octet-stream")
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("HTTP $code")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                var done = 0L
                var lastPct = -1
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            ensureActive()  // makes Cancel actually stop the download
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) { lastPct = pct; onProgress(pct / 100f) }
                            } else if (lastPct != -2) { lastPct = -2; onProgress(-1f) }
                        }
                    }
                }
                if (total > 0 && done != total) throw IOException("Download incomplete ($done of $total bytes)")
            } catch (e: Exception) {
                tmp.delete()
                throw e
            } finally {
                conn.disconnect()
            }
            if (!tmp.renameTo(out)) throw IOException("Couldn't save the downloaded file")
            out
        }

    /**
     * Catches the classic "App not installed" failures BEFORE the installer does, with a
     * readable reason. Returns null when the APK looks good.
     */
    fun verify(ctx: Context, apk: File): VerifyProblem? {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
                    else PackageManager.GET_SIGNATURES

        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
            ?: return VerifyProblem("The downloaded file isn't a valid APK.")
        if (archive.packageName != ctx.packageName) {
            return VerifyProblem("The downloaded APK is for a different app (${archive.packageName}).")
        }
        @Suppress("DEPRECATION")
        val installed = pm.getPackageInfo(ctx.packageName, flags)
        if (versionCodeOf(archive) <= versionCodeOf(installed)) {
            return VerifyProblem(
                "The release's versionCode (${versionCodeOf(archive)}) isn't higher than the installed one " +
                "(${versionCodeOf(installed)}), so Android would refuse it. Publish it with a higher version tag."
            )
        }
        val newSigs = signatureDigests(archive)
        val oldSigs = signatureDigests(installed)
        if (newSigs.isNotEmpty() && oldSigs.isNotEmpty() && newSigs.intersect(oldSigs).isEmpty()) {
            return VerifyProblem(
                "This update is signed with a different key than the installed app, so Android " +
                "can't install it over the top.",
                signatureMismatch = true
            )
        }
        return null
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        ctx.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun launchInstaller(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, fileProviderAuthority(ctx), apk)
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openUrl(ctx: Context, url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Suppress("DEPRECATION")
    private fun versionCodeOf(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signatureDigests(info: PackageInfo): Set<String> {
        val sigs: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
            val si = info.signingInfo ?: return emptySet()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory
        } else info.signatures
        val md = MessageDigest.getInstance("SHA-256")
        return sigs.orEmpty().map { sig -> md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.toSet()
    }
}
