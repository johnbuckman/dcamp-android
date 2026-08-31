package com.decentespresso.dcamp.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.decentespresso.dcamp.api.Recipient
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.theme.Avatar
import com.decentespresso.dcamp.ui.theme.DCPrimaryButton
import com.decentespresso.dcamp.ui.theme.DcampTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    val c = DcampTheme.colors
    Box(Modifier.fillMaxWidth().background(c.panel, RoundedCornerShape(10.dp)).border(1.dp, c.lineStrong, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        if (query.isEmpty()) Text(T("Search people"), color = c.muted, fontSize = 15.sp)
        BasicTextField(query, onChange, singleLine = true, textStyle = TextStyle(color = c.ink, fontSize = 15.sp), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
    }
}

/** New DM — multi-select people (chips) then start a 1:1 or group conversation. */
@Composable
fun NewDMView(onCreated: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = DcampTheme.colors
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<Recipient>() }
    val selected = remember { mutableStateListOf<Recipient>() }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { results.clear(); return@LaunchedEffect }
        delay(250)
        val r = runCatching { Services.api.pingRecipients(query.trim()) }.getOrDefault(emptyList())
        results.clear(); results.addAll(r)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.95f).heightIn(max = 640.dp).background(c.bg, RoundedCornerShape(16.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(T("Cancel"), color = c.inkSoft, fontSize = 15.sp, modifier = Modifier.clickable { onDismiss() })
                Spacer(Modifier.weight(1f))
                Text(if (selected.size > 1) T("New group message") else T("New message"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Spacer(Modifier.weight(1f))
                val canStart = selected.isNotEmpty() && !busy
                Text(T("Start"), color = if (canStart) c.accent else c.muted, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.clickable(enabled = canStart) {
                        busy = true
                        scope.launch {
                            val id = runCatching { Services.api.dmCreate(selected.map { it.id }) }.getOrNull()
                            busy = false
                            if (id != null) { onCreated(id); onDismiss() }
                        }
                    })
            }
            if (selected.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    selected.toList().forEach { r ->
                        Row(Modifier.background(c.accent.copy(alpha = 0.12f), CircleShape).clickable { selected.remove(r) }.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(r.name, color = c.accentInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("✕", color = c.accentInk, fontSize = 12.sp)
                        }
                    }
                }
            }
            SearchBox(query) { query = it }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(results, key = { it.id }) { r ->
                    val isSel = selected.any { it.id == r.id }
                    Row(Modifier.fillMaxWidth().clickable { if (isSel) selected.removeAll { it.id == r.id } else selected.add(r) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (isSel) "☑" else "☐", color = if (isSel) c.accent else c.muted, fontSize = 18.sp)
                        Avatar(person = r.person, size = 30.dp)
                        Text(r.name, color = c.ink, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/** Reusable people search sheet — [onPick] does the async work (e.g. invite) then dismisses. */
@Composable
fun PeopleSearchSheet(title: String, onPick: suspend (Recipient) -> Unit, onDismiss: () -> Unit) {
    val c = DcampTheme.colors
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val results = remember { mutableStateListOf<Recipient>() }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { results.clear(); return@LaunchedEffect }
        delay(250)
        results.clear(); results.addAll(runCatching { Services.api.pingRecipients(query.trim()) }.getOrDefault(emptyList()))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.95f).heightIn(max = 560.dp).background(c.bg, RoundedCornerShape(16.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
            SearchBox(query) { query = it }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(results, key = { it.id }) { r ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                        busy = true; scope.launch { onPick(r); busy = false; onDismiss() }
                    }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(person = r.person, size = 30.dp)
                        Text(r.name, color = c.ink, fontSize = 15.sp)
                    }
                }
            }
        }
    }
}
