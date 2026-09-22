package com.example.mentzertracker.update

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mentzertracker.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class NeedsPermission(val release: ReleaseInfo, val file: File) : UpdateState
    data class InstallerLaunched(val release: ReleaseInfo, val file: File) : UpdateState
    data class Failed(
        val message: String,
        val release: ReleaseInfo? = null,
        val signatureMismatch: Boolean = false
    ) : UpdateState
}

class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Context get() = getApplication<Application>()
    private val prefs = app.getSharedPreferences("mentzer_update", Context.MODE_PRIVATE)

    val currentVersion: String = BuildConfig.VERSION_NAME
    val isConfigured: Boolean = UpdateManager.isConfigured
    val repo: String = UpdateManager.repo

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialogVisible

    private val _autoCheck = MutableStateFlow(prefs.getBoolean(KEY_AUTO, true))
    val autoCheck: StateFlow<Boolean> = _autoCheck

    private val _lastChecked = MutableStateFlow(prefs.getLong(KEY_LAST_CHECK, 0L))
    val lastChecked: StateFlow<Long> = _lastChecked

    private var job: Job? = null
    private var userIsWaiting = false

    fun setAutoCheck(on: Boolean) {
        _autoCheck.value = on
        prefs.edit().putBoolean(KEY_AUTO, on).apply()
    }

    /** Silent, throttled check on app start. Only surfaces a dialog if there's an update. */
    fun checkOnLaunch() {
        if (!_autoCheck.value || !isConfigured) return
        if (System.currentTimeMillis() - _lastChecked.value < AUTO_CHECK_INTERVAL_MS) return
        check(manual = false)
    }

    /** From the Settings button: always shows a result, and ignores "skip this version". */
    fun checkNow() = check(manual = true)

    private fun check(manual: Boolean) {
        if (manual) { userIsWaiting = true; _dialogVisible.value = true }
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = UpdateState.Checking
            val result = UpdateManager.check(currentVersion)
            val showResult = userIsWaiting
            userIsWaiting = false

            if (result !is CheckResult.Error) {
                val now = System.currentTimeMillis()
                _lastChecked.value = now
                prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
            }

            _state.value = when (result) {
                is CheckResult.Available -> {
                    val skipped = prefs.getString(KEY_SKIPPED, null) == result.release.versionName
                    if (skipped && !showResult) UpdateState.Idle
                    else UpdateState.Available(result.release).also { _dialogVisible.value = true }
                }
                CheckResult.UpToDate -> if (showResult) UpdateState.UpToDate else UpdateState.Idle
                CheckResult.NotConfigured ->
                    if (showResult) UpdateState.Failed("Updates aren't configured for this build (no GitHub repo set). See README.")
                    else UpdateState.Idle
                is CheckResult.Error -> if (showResult) UpdateState.Failed(result.message) else UpdateState.Idle
            }
            if (_state.value == UpdateState.Idle) _dialogVisible.value = false
        }
    }

    fun download(release: ReleaseInfo) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            try {
                val file = UpdateManager.download(ctx, release) { p ->
                    _state.value = UpdateState.Downloading(release, p)
                }
                val problem = UpdateManager.verify(ctx, file)
                if (problem != null) {
                    _state.value = UpdateState.Failed(problem.message, release, problem.signatureMismatch)
                    return@launch
                }
                install(release, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("Download failed: ${e.message ?: e.javaClass.simpleName}", release)
            }
        }
    }

    private fun install(release: ReleaseInfo, file: File) {
        if (!UpdateManager.canInstall(ctx)) {
            _state.value = UpdateState.NeedsPermission(release, file)
            return
        }
        _state.value = try {
            UpdateManager.launchInstaller(ctx, file)
            UpdateState.InstallerLaunched(release, file)
        } catch (e: Exception) {
            UpdateState.Failed("Couldn't open the installer: ${e.message}", release)
        }
    }

    /** "Continue" after granting install permission, or "Install again". */
    fun retryInstall() {
        when (val s = _state.value) {
            is UpdateState.NeedsPermission -> install(s.release, s.file)
            is UpdateState.InstallerLaunched -> install(s.release, s.file)
            else -> Unit
        }
    }

    fun openPermissionSettings() = UpdateManager.openInstallPermissionSettings(ctx)

    fun openReleasePage(release: ReleaseInfo?) {
        val url = release?.pageUrl?.takeIf { it.isNotBlank() } ?: "https://github.com/$repo/releases/latest"
        runCatching { UpdateManager.openUrl(ctx, url) }
    }

    fun cancelDownload() {
        job?.cancel()
        _state.value = UpdateState.Idle
        _dialogVisible.value = false
    }

    fun skip(release: ReleaseInfo) {
        prefs.edit().putString(KEY_SKIPPED, release.versionName).apply()
        dismiss()
    }

    fun dismiss() {
        if (_state.value is UpdateState.Downloading) return  // use Cancel instead
        _dialogVisible.value = false
        _state.value = UpdateState.Idle
    }

    companion object {
        private const val KEY_AUTO = "auto_check"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_SKIPPED = "skipped_version"
        private const val AUTO_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000  // at most every 6h
    }
}
