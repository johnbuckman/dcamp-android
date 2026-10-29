package com.decentespresso.dcamp.ui.views

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.api.AppConfig
import com.decentespresso.dcamp.api.UpdateInfo
import com.decentespresso.dcamp.api.Updater
import com.decentespresso.dcamp.ui.T
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private const val IDLE = 0
private const val PROMPT = 1
private const val DOWNLOADING = 2
private const val FAILED = 3
private const val CHECKING = 4
private const val UP_TO_DATE = 5
private const val UNREACHABLE = 6
private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * On launch, checks GitHub for a newer release; if found (and not already dismissed), offers to
 * download + install it. "Later" suppresses that version until a newer one appears. "Check for
 * update" in the account menu runs the same check on demand and also says when you're up to date.
 * The sideload counterpart of the iOS SideStep auto-update.
 */
@Composable
fun UpdateGate() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var phase by remember { mutableIntStateOf(IDLE) }
    var progress by remember { mutableFloatStateOf(0f) }
    var pendingApk by remember { mutableStateOf<java.io.File?>(null) }

    // Returns from the per-app "Allow from this source" screen; install if now permitted.
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val f = pendingApk
        if (f != null && Updater.canInstall(ctx)) { Updater.install(ctx, f); pendingApk = null; phase = IDLE }
        else phase = FAILED
    }

    fun installOrRequest(f: java.io.File) {
        if (Updater.canInstall(ctx)) { Updater.install(ctx, f); phase = IDLE }
        else { pendingApk = f; permLauncher.launch(Updater.unknownSourcesSettings(ctx)) }
    }

    // Check when the app comes to the foreground, throttled to at most once a day. Only a check
    // GitHub actually answered counts (Updater.check records it), so being offline or a failed
    // request retries on the next resume rather than burning the day.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && phase == IDLE) {
                scope.launch {
                    val now = System.currentTimeMillis()
                    if (com.decentespresso.dcamp.api.Services.connectivity.isOnline.value &&
                        now - AppConfig.lastUpdateCheck >= DAY_MS) {
                        Updater.check()?.let { info = it; phase = PROMPT }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Manual check from the account menu: ignores the daily throttle and a dismissed version.
    LaunchedEffect(Unit) {
        Updater.manualRequests.drop(1).collect {
            if (phase == DOWNLOADING) return@collect
            phase = CHECKING
            val latest = Updater.latest()
            info = latest
            phase = when {
                latest == null -> UNREACHABLE
                Updater.isNewer(latest) -> PROMPT
                else -> UP_TO_DATE
            }
        }
    }

    if (phase == IDLE) return
    if (phase == CHECKING || phase == UP_TO_DATE || phase == UNREACHABLE) {
        AlertDialog(
            onDismissRequest = { phase = IDLE },
            title = { Text(T("Check for update")) },
            text = {
                Text(when (phase) {
                    CHECKING -> T("Checking…")
                    UP_TO_DATE -> T("You have the latest version ({cur}).",
                        "cur" to com.decentespresso.dcamp.BuildConfig.VERSION_NAME)
                    else -> T("Couldn’t check for updates. Please try again later.")
                })
            },
            confirmButton = {
                if (phase != CHECKING) TextButton(onClick = { phase = IDLE }) { Text(T("OK")) }
            },
        )
        return
    }
    val i = info ?: return

    AlertDialog(
        onDismissRequest = { if (phase != DOWNLOADING) phase = IDLE },
        title = { Text(T("Update available")) },
        text = {
            Column {
                Text(T("You have version {cur}, version {new} is available.",
                    "cur" to com.decentespresso.dcamp.BuildConfig.VERSION_NAME, "new" to i.version))
                if (phase == DOWNLOADING) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                if (phase == FAILED) {
                    Spacer(Modifier.height(8.dp))
                    Text(T("Download failed. Please try again later."), fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            if (phase == PROMPT || phase == FAILED) {
                TextButton(onClick = {
                    phase = DOWNLOADING; progress = 0f
                    scope.launch {
                        val f = Updater.download(ctx, i) { progress = it }
                        if (f != null) installOrRequest(f) else phase = FAILED
                    }
                }) { Text(if (phase == FAILED) T("Retry") else T("Update")) }
            }
        },
        dismissButton = {
            if (phase != DOWNLOADING) {
                TextButton(onClick = { AppConfig.dismissedUpdate = i.version; phase = IDLE }) {
                    Text(T("Later"))
                }
            }
        },
    )
}
