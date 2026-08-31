package com.decentespresso.dcamp.ui.views

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
import kotlinx.coroutines.launch

private const val IDLE = 0
private const val PROMPT = 1
private const val DOWNLOADING = 2
private const val FAILED = 3

/**
 * On launch, checks GitHub for a newer release; if found (and not already dismissed), offers to
 * download + install it. "Later" suppresses that version until a newer one appears. The sideload
 * counterpart of the iOS SideStep auto-update.
 */
@Composable
fun UpdateGate() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<UpdateInfo?>(null) }
    var phase by remember { mutableIntStateOf(IDLE) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        Updater.check()?.let { info = it; phase = PROMPT }
    }

    val i = info
    if (i == null || phase == IDLE) return

    AlertDialog(
        onDismissRequest = { if (phase != DOWNLOADING) phase = IDLE },
        title = { Text(T("Update available")) },
        text = {
            Column {
                Text(T("Version {v} is available.", "v" to i.version))
                if (i.notes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(i.notes, fontSize = 13.sp, maxLines = 6)
                }
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
                        if (f != null) { Updater.install(ctx, f); phase = IDLE } else phase = FAILED
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
