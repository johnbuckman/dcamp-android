package com.decentespresso.dcamp.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.decentespresso.dcamp.api.PersonCard
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.api.dmDirect
import com.decentespresso.dcamp.api.personMute
import com.decentespresso.dcamp.api.personUnmute
import com.decentespresso.dcamp.api.reportPerson
import kotlinx.coroutines.launch
import com.decentespresso.dcamp.ui.Dest
import com.decentespresso.dcamp.ui.LocalRouter
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.theme.Avatar
import com.decentespresso.dcamp.ui.theme.DcampTheme
import com.decentespresso.dcamp.ui.theme.RelativeTime

@Composable
fun PersonCardView(personID: Int, onDismiss: () -> Unit) {
    val c = DcampTheme.colors
    val router = LocalRouter.current
    var card by remember(personID) { mutableStateOf<PersonCard?>(null) }
    var loading by remember(personID) { mutableStateOf(true) }
    var muted by remember(personID) { mutableStateOf(false) }
    var reporting by remember(personID) { mutableStateOf(false) }
    var reportReason by remember(personID) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(personID) {
        loading = true
        card = runCatching { Services.api.personCard(personID) }.getOrNull()
        muted = (card?.muted ?: 0) != 0
        loading = false
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.92f).heightIn(max = 640.dp).background(c.bg, RoundedCornerShape(16.dp)).padding(20.dp)
            .verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val cc = card
            when {
                cc != null -> {
                    // Header
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Avatar(person = cc.person, size = 64.dp)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(cc.person.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c.ink)
                            cc.person.machine?.takeIf { it.isNotEmpty() }?.let { IconLabel(Icons.Filled.Coffee, it) }
                            cc.city?.takeIf { it.isNotEmpty() }?.let { IconLabel(Icons.Filled.LocationOn, it) }
                            cc.person.about?.takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, color = c.inkSoft) }
                        }
                    }
                    Divider(color = c.line)
                    // Actions (write actions gated; present for parity)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        ActionButton(Icons.Filled.Email, T("Message")) {
                            scope.launch {
                                val convo = runCatching { Services.api.dmDirect(cc.person.id) }.getOrNull()
                                if (convo != null) { onDismiss(); router.push(Dest.Dm(convo)) }
                            }
                        }
                        ActionButton(Icons.Filled.VolumeOff, if (muted) T("Unmute") else T("Mute")) {
                            scope.launch {
                                if (muted) Services.api.personUnmute(cc.person.id) else Services.api.personMute(cc.person.id)
                                muted = !muted
                            }
                        }
                        ActionButton(Icons.Filled.Flag, T("Report")) { reporting = true }
                    }
                    if (cc.messages.isNotEmpty()) {
                        Divider(color = c.line)
                        Text(T("Threads").uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.muted)
                        cc.messages.forEach { m ->
                            ActivityRow(m.subject ?: T("Thread"), m.createdAt) { onDismiss(); router.push(Dest.Thread(m.id)) }
                        }
                    }
                    if (cc.comments.isNotEmpty()) {
                        Divider(color = c.line)
                        Text(T("Comments").uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.muted)
                        cc.comments.forEach { cm ->
                            ActivityRow(cm.messageSubject ?: T("Comment"), cm.createdAt) {
                                onDismiss(); router.push(Dest.Thread(cm.messageId ?: cm.id))
                            }
                        }
                    }
                }
                loading -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) }
                else -> Text(T("Couldn’t load profile"), color = c.muted)
            }
        }
    }

    if (reporting) {
        val cc = card
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { reporting = false },
            title = { Text(T("Report {name}?", "name" to (cc?.person?.name ?: ""))) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(T("This alerts the moderators."), fontSize = 13.sp, color = c.muted)
                    androidx.compose.material3.OutlinedTextField(value = reportReason, onValueChange = { reportReason = it },
                        label = { Text(T("Reason")) }, singleLine = false)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val id = cc?.person?.id; reporting = false
                    scope.launch { if (id != null) Services.api.reportPerson(id, reportReason, "app://person/$id"); reportReason = "" }
                }) { Text(T("Report")) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { reporting = false; reportReason = "" }) { Text(T("Cancel")) }
            },
        )
    }
}

@Composable
private fun IconLabel(icon: ImageVector, text: String) {
    val c = DcampTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = c.muted, modifier = Modifier.size(13.dp))
        Text(text, fontSize = 12.sp, color = c.inkSoft)
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = DcampTheme.colors
    Column(Modifier.clickable { onClick() }.padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = label, tint = c.accentInk, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 12.sp, color = c.accentInk, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ActivityRow(title: String, epoch: Int?, onClick: () -> Unit) {
    val c = DcampTheme.colors
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, fontSize = 14.sp, color = c.ink, maxLines = 1)
        Text(RelativeTime.string(epoch), fontSize = 11.sp, color = c.muted)
    }
}
