package com.decentespresso.dcamp.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.api.SessionStore
import com.decentespresso.dcamp.api.SummaryResult
import com.decentespresso.dcamp.api.activitySummary
import com.decentespresso.dcamp.api.chatSummary
import com.decentespresso.dcamp.api.forumSummary
import com.decentespresso.dcamp.api.homeSummary
import com.decentespresso.dcamp.api.summaryEmailSample
import com.decentespresso.dcamp.api.summaryPrefsGet
import com.decentespresso.dcamp.api.summaryPrefsSave
import com.decentespresso.dcamp.api.threadSummary
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.theme.DC
import com.decentespresso.dcamp.ui.theme.DCPrimaryButton
import com.decentespresso.dcamp.ui.theme.DcampTheme
import com.decentespresso.dcamp.ui.trix.TrixContentView
import kotlinx.coroutines.launch

/** Summary target, shared by the summarize bar. Ported from iOS SummaryKind. */
sealed class SummaryKind {
    data object Home : SummaryKind()
    data class Forum(val id: Int, val name: String) : SummaryKind()
    data class Thread(val id: Int) : SummaryKind()
    data object Chat : SummaryKind()
}

fun SummaryKind.label(): String = when (this) {
    is SummaryKind.Home -> T("Diaspora summary")
    is SummaryKind.Forum -> name
    is SummaryKind.Thread -> T("Thread summary")
    is SummaryKind.Chat -> T("Chat summary")
}

/** The summary pinned to the tablet side panel (the web's "Show in sidebar"). */
class SummaryPin {
    var kind by mutableStateOf<SummaryKind?>(null)
    var days by mutableStateOf(7)
    var title by mutableStateOf("Summary")
    val visible: Boolean get() = kind != null
    fun clear() { kind = null }
}

val LocalSummaryPin = androidx.compose.runtime.staticCompositionLocalOf { SummaryPin() }

object SummaryLoader {
    val windows = listOf(1, 2, 3, 5, 7, 10, 14, 21, 30)

    /** Peek the shared day-cache first (cheap), only generate on a miss. Returns (html, mine?). */
    suspend fun load(kind: SummaryKind, days: Int): Pair<String, String?> {
        val cached = runCatching { fetch(kind, days, peek = true) }.getOrNull()
        if (!cached?.summary.isNullOrEmpty()) {
            return cached!!.summary!! to cached.mine?.takeIf { it.isNotEmpty() }
        }
        val r = runCatching { fetch(kind, days, peek = false) }.getOrNull()
        return (r?.summary?.takeIf { it.isNotEmpty() } ?: "<p>No activity in this window.</p>") to r?.mine?.takeIf { it.isNotEmpty() }
    }

    private suspend fun fetch(kind: SummaryKind, days: Int, peek: Boolean): SummaryResult = when (kind) {
        is SummaryKind.Home -> Services.api.homeSummary(days, peek)
        is SummaryKind.Forum -> Services.api.forumSummary(kind.id, days, peek)
        is SummaryKind.Thread -> Services.api.threadSummary(kind.id, days, peek)
        is SummaryKind.Chat -> Services.api.chatSummary(days, "main", peek)
    }
}

private fun daysLabel(d: Int): String = T(if (d == 1) "past {n} day" else "past {n} days", "n" to "$d")

/** ✨ Summarize bar: pick a window, hit Summarize, the AI summary expands inline. */
@Composable
fun SummarizeBar(kind: SummaryKind) {
    val c = DcampTheme.colors
    val scope = rememberCoroutineScope()
    var days by remember { mutableIntStateOf(7) }
    var expanded by remember { mutableStateOf(false) }
    var html by remember { mutableStateOf<String?>(null) }
    var mine by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    fun reload() {
        loading = true
        scope.launch { val r = SummaryLoader.load(kind, days); html = r.first; mine = r.second; loading = false }
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("✨", fontSize = 16.sp)
            Box {
                Row(Modifier.background(c.panel, CircleShape).border(1.dp, c.lineStrong, CircleShape)
                    .clickable { menuOpen = true }.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(daysLabel(days), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.inkSoft)
                    Text("▾", fontSize = 10.sp, color = c.inkSoft)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    SummaryLoader.windows.forEach { d ->
                        DropdownMenuItem(text = { Text(daysLabel(d)) }, onClick = {
                            days = d; menuOpen = false; if (expanded) reload()
                        })
                    }
                }
            }
            DCPrimaryButton(onClick = { if (expanded) expanded = false else { expanded = true; reload() } }) {
                Text(if (expanded) T("Hide") else T("Summarize"), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
            val pin = LocalSummaryPin.current
            if (expanded && wide) {
                Text("⇤ ${T("Show in sidebar")}", color = c.link, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { pin.kind = kind; pin.days = days; pin.title = kind.label(); expanded = false })
            }
            Spacer(Modifier.weight(1f))
        }

        if (expanded) {
            Column(Modifier.fillMaxWidth().background(c.panel, RoundedCornerShape(DC.radius))
                .border(1.dp, c.line, RoundedCornerShape(DC.radius)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                mine?.let { m ->
                    Column(Modifier.fillMaxWidth().background(c.accent.copy(alpha = 0.10f), RoundedCornerShape(10.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("@ ${T("You were mentioned")}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.accentInk)
                        TrixContentView(m)
                    }
                }
                if (loading) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = c.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(T("Summarizing…"), fontSize = 13.sp, color = c.muted)
                    }
                } else {
                    html?.let { TrixContentView(it) }
                }
            }
        }
    }
}

/** The "Summaries by email" page: choose sources + frequency, preview, email a sample, subscribe. */
@Composable
fun EmailSummariesView(state: SessionStore.State) {
    val c = DcampTheme.colors
    val scope = rememberCoroutineScope()
    var period by remember { mutableStateOf("week") }
    var emailEnabled by remember { mutableStateOf(false) }
    val selected = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var digest by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    val sourceOptions = state.boards.filter { it.kind == null || it.kind == "forum" }
        .map { it.id.toString() to it.name } + ("chat" to T("Chat Room"))
    fun sourcesCsv() = selected.sorted().joinToString(",")

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (loaded) return@LaunchedEffect
        val p = Services.api.summaryPrefsGet()
        period = p.period ?: "week"; emailEnabled = (p.emailEnabled ?: 0) != 0
        selected.clear(); selected.addAll((p.sources ?: "").split(",").filter { it.isNotEmpty() })
        loaded = true
    }

    Column(Modifier.fillMaxSize().background(c.bg)
        .verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(T("Summaries by email"), fontSize = 24.sp, fontWeight = FontWeight.Black, color = c.ink)

        SummarySection(T("Frequency")) {
            SummarySegmented(listOf("day" to T("Daily"), "week" to T("Weekly"), "month" to T("Monthly")), period) { period = it; digest = null }
            SummaryToggle(T("Email me summaries"), emailEnabled) { emailEnabled = it }
        }
        SummarySection(T("Include")) {
            sourceOptions.forEach { (id, name) ->
                SummaryToggle(name, selected.contains(id)) { on -> if (on) selected.add(id) else selected.remove(id) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DCPrimaryButton(onClick = { scope.launch { Services.api.summaryPrefsSave(sourcesCsv(), period, emailEnabled) } }) {
                Text(T("Save summary settings"), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(T("Email me a sample"), color = c.link, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { scope.launch { Services.api.summaryEmailSample(sourcesCsv(), period) } }.padding(10.dp))
        }
        SummarySection(T("Preview")) {
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(strokeWidth = 2.dp, color = c.accent, modifier = Modifier.size(16.dp)); Text(T("Generating…"), color = c.muted) }
                digest != null -> TrixContentView(digest!!)
                else -> Text(T("Preview my digest"), color = c.link, fontSize = 14.sp,
                    modifier = Modifier.clickable { loading = true; scope.launch { digest = Services.api.activitySummary(sourcesCsv(), period).summary; loading = false } })
            }
        }
    }
}

@Composable
private fun SummarySection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val c = DcampTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.muted)
        Column(Modifier.fillMaxWidth().background(c.panel, RoundedCornerShape(14.dp)).border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun SummaryToggle(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    val c = DcampTheme.colors
    Row(Modifier.fillMaxWidth().clickable { onChange(!on) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = on, onCheckedChange = onChange,
            colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.accent))
    }
}

@Composable
private fun SummarySegmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    val c = DcampTheme.colors
    Row(Modifier.fillMaxWidth().background(c.bg, RoundedCornerShape(10.dp)).border(1.dp, c.lineStrong, RoundedCornerShape(10.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Text(label, fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) Color.White else c.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).background(if (active) c.accent else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(value) }.padding(vertical = 8.dp))
        }
    }
}

/** Persistent tablet side panel showing the pinned summary. */
@Composable
fun SummarySidebarView(pin: SummaryPin) {
    val c = DcampTheme.colors
    var html by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val key = "${pin.kind}-${pin.days}"
    androidx.compose.runtime.LaunchedEffect(key) {
        val k = pin.kind ?: return@LaunchedEffect
        loading = true
        html = SummaryLoader.load(k, pin.days).first
        loading = false
    }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("✨", fontSize = 15.sp)
            Text(pin.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1, modifier = Modifier.weight(1f))
            Text("✕", color = c.muted, fontSize = 16.sp, modifier = Modifier.clickable { pin.clear() }.padding(4.dp))
        }
        androidx.compose.material3.Divider(color = c.line)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)) {
            if (loading) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = c.accent, modifier = Modifier.size(20.dp).padding(top = 20.dp))
            } else {
                html?.let { TrixContentView(it) }
            }
        }
    }
}
