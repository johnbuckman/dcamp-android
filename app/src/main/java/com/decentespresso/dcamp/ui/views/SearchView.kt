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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.api.Person
import com.decentespresso.dcamp.api.PlainText
import com.decentespresso.dcamp.api.SearchHit
import com.decentespresso.dcamp.api.SearchResponse
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.api.DcampRoute
import com.decentespresso.dcamp.api.derekSearchPoll
import com.decentespresso.dcamp.api.derekSearchStart
import com.decentespresso.dcamp.ui.Dest
import com.decentespresso.dcamp.ui.LocalRouter
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.theme.Avatar
import com.decentespresso.dcamp.ui.theme.MachineBadge
import com.decentespresso.dcamp.ui.theme.DcampTheme
import com.decentespresso.dcamp.ui.theme.RelativeTime
import com.decentespresso.dcamp.ui.trix.TrixContentView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val searchTypes = listOf("messages" to "Threads", "comments" to "Comments", "chat" to "Chat", "pings" to "DMs")

@Composable
fun SearchView() {
    val c = DcampTheme.colors
    val router = LocalRouter.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var response by remember { mutableStateOf(SearchResponse()) }
    var searching by remember { mutableStateOf(false) }
    var types by remember { mutableStateOf(setOf("messages", "comments", "chat", "pings")) }
    var derekAnswer by remember { mutableStateOf<String?>(null) }
    var derekLoading by remember { mutableStateOf(false) }

    var sortNewest by remember { mutableStateOf(false) }
    var showWeak by remember { mutableStateOf(false) }        // weak-relevance tail expanded ("Show N more results…")
    var selectedForum by remember { mutableStateOf<Int?>(null) }
    var expertsOnly by remember { mutableStateOf(false) }     // all members by default; experts is opt-in
    var showAllPeople by remember { mutableStateOf(false) }   // People capped at PEOPLE_CAP rows so results stay in view
    var sortMenu by remember { mutableStateOf(false) }

    val terms = remember(query) { query.lowercase().split(" ").filter { it.length >= 2 } }

    // Debounced search on query/types change.
    LaunchedEffect(query, types) {
        derekAnswer = null; derekLoading = false
        if (query.trim().length < 2) { response = SearchResponse(); return@LaunchedEffect }
        delay(300)
        searching = true
        val t = if (types.isEmpty()) "messages,comments,chat,pings" else types.joinToString(",")
        response = runCatching { Services.api.search(query, t, 0) }.getOrDefault(SearchResponse())
        selectedForum = null; showWeak = false; showAllPeople = false
        searching = false
    }

    fun open(hit: SearchHit) {
        when (hit.type) {
            "chat" -> router.push(Dest.Chat)
            "pings", "dm" -> (DcampRoute.dmID(hit.link) ?: hit.id)?.let { router.push(Dest.Dm(it)) }
            else -> (DcampRoute.messageID(hit.link) ?: hit.id.takeIf { hit.type == "message" })?.let { router.push(Dest.Thread(it)) }
        }
    }

    fun askDerek() {
        derekAnswer = null; derekLoading = true
        val q = query
        scope.launch {
            val start = Services.api.derekSearchStart(q)
            val token = start.token
            if (token.isNullOrEmpty()) { derekLoading = false; return@launch }
            repeat(40) {
                delay(700)
                val poll = Services.api.derekSearchPoll(token)
                if (poll.isDone) {
                    derekAnswer = poll.answer?.takeIf { it.isNotEmpty() } ?: "<p>No answer found.</p>"
                    derekLoading = false; return@launch
                }
            }
            derekLoading = false
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        // Search field
        Row(Modifier.fillMaxWidth().padding(16.dp).background(c.panel, RoundedCornerShape(12.dp))
            .border(1.dp, c.lineStrong, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(T("Search dcamp"), color = c.muted, fontSize = 15.sp)
                BasicTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = c.ink, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth())
            }
            if (searching) CircularProgressIndicator(strokeWidth = 2.dp, color = c.accent, modifier = Modifier.size(16.dp))
        }

        // Type filter pills
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            searchTypes.forEach { (key, label) ->
                FilterPill(T(label), active = types.contains(key)) {
                    types = if (types.contains(key)) types - key else types + key
                }
            }
        }

        // Sort / experts-only controls
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                FilterPill(if (sortNewest) T("Newest") else T("Relevance"), active = false) { sortMenu = true }
                androidx.compose.material3.DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text(T("Relevance")) }, onClick = { sortNewest = false; sortMenu = false })
                    androidx.compose.material3.DropdownMenuItem(text = { Text(T("Newest")) }, onClick = { sortNewest = true; sortMenu = false })
                }
            }
            if (response.people.isNotEmpty() && !response.experts.isNullOrEmpty()) {
                FilterPill(T("Experts only"), active = expertsOnly) { expertsOnly = !expertsOnly }
            }
        }
        // Forum facet
        response.forums?.takeIf { it.size > 1 }?.let { forums ->
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill(T("All forums"), active = selectedForum == null) { selectedForum = null; showWeak = false }
                forums.forEach { f -> FilterPill(f.name, active = selectedForum == f.id) { selectedForum = if (selectedForum == f.id) null else f.id; showWeak = false } }
            }
        }

        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
            if (query.length >= 2) {
                item {
                    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        if (derekAnswer != null) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                                Text(T("Derek says"), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink)
                            }
                            TrixContentView(derekAnswer!!)
                        } else {
                            Row(Modifier.clickable(enabled = !derekLoading) { askDerek() },
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (derekLoading) CircularProgressIndicator(strokeWidth = 2.dp, color = c.accent, modifier = Modifier.size(16.dp))
                                else Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = c.accentInk, modifier = Modifier.size(16.dp))
                                Text(if (derekLoading) T("Derek is thinking…") else T("Ask Derek about “{q}”", "q" to query),
                                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.accentInk)
                            }
                        }
                    }
                }
            }
            val people = run {
                val ex = response.experts
                if (expertsOnly && !ex.isNullOrEmpty()) {
                    val set = ex.toSet()
                    response.people.filter { set.contains(it.id) }.ifEmpty { response.people }
                } else response.people
            }
            if (people.isNotEmpty()) {
                item { SectionHeader("${T("People")} (${people.size})") }
                items(if (showAllPeople) people else people.take(PEOPLE_CAP), key = { "p-${it.id}" }) { p ->
                    PersonResultRow(p, isExpert = response.experts?.contains(p.id) == true) { router.personID = p.id }
                    Divider(color = c.line)
                }
                if (!showAllPeople && people.size > PEOPLE_CAP) {
                    item { MoreRow(T("Show all {n} people", "n" to "${people.size}")) { showAllPeople = true } }
                }
            }
            // Good matches first (score >= half the top); the weak tail waits behind "Show N more results…".
            val forumResults = selectedForum?.let { f -> response.results.filter { it.forumId == f } } ?: response.results
            val results = run {
                var list = forumResults
                if (!showWeak) { val top = list.mapNotNull { it.score }.maxOrNull() ?: 0.0; if (top > 0) list = list.filter { (it.score ?: 0.0) >= top * 0.5 } }
                if (sortNewest) list.sortedByDescending { it.createdAt ?: 0 } else list.sortedByDescending { it.score ?: 0.0 }
            }
            val weak = forumResults.size - results.size
            if (results.isNotEmpty()) {
                item { SectionHeader("${T("Results")} (${results.size})") }
                items(results, key = { it.uid }) { hit ->
                    SearchHitRow(hit, terms) { open(hit) }
                    Divider(color = c.line)
                }
                if (weak > 0) {
                    item { MoreRow(if (weak == 1) T("Show 1 more result…") else T("Show {n} more results…", "n" to "$weak")) { showWeak = true } }
                }
            }
            if (query.length >= 2 && !searching && results.isEmpty() && people.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    Text(T("No results"), color = c.muted) } }
            } else if (query.length < 2) {
                item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                    Text(T("Find threads, comments, chat, DMs and people."), color = c.muted) } }
            }
        }
    }
}

private const val PEOPLE_CAP = 5

@Composable
private fun MoreRow(text: String, onClick: () -> Unit) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = DcampTheme.colors.accentInk,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp))
}

@Composable
private fun SectionHeader(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DcampTheme.colors.muted,
        modifier = Modifier.padding(vertical = 8.dp))
}

@Composable
private fun FilterPill(title: String, active: Boolean, onClick: () -> Unit) {
    val c = DcampTheme.colors
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (active) c.accentInk else c.inkSoft,
        modifier = Modifier.border(1.dp, if (active) c.accent.copy(alpha = 0.5f) else c.lineStrong, CircleShape)
            .background(if (active) c.accent.copy(alpha = 0.12f) else c.panel, CircleShape)
            .clickable { onClick() }.padding(horizontal = 11.dp, vertical = 6.dp))
}

@Composable
private fun PersonResultRow(person: Person, isExpert: Boolean, onClick: () -> Unit) {
    val c = DcampTheme.colors
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Avatar(person = person, size = 32.dp)
        Text(person.name, color = c.ink, fontSize = 15.sp)
        MachineBadge(person)
        Spacer(Modifier.weight(1f))
        if (isExpert) Icon(Icons.Filled.Star, contentDescription = null, tint = c.accent, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun SearchHitRow(hit: SearchHit, terms: List<String>, onClick: () -> Unit) {
    val c = DcampTheme.colors
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TypePill(hit.type)
            Text(highlight(hit.title ?: T("Untitled"), terms, c.accentInk), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                color = c.ink, maxLines = 1)
        }
        hit.snippet?.takeIf { it.isNotEmpty() }?.let {
            Text(highlight(PlainText.strip(it), terms, c.accentInk), fontSize = 13.sp, color = c.inkSoft, maxLines = 2)
        }
        val bits = listOfNotNull(hit.author?.name, hit.forum, hit.createdAt?.let { RelativeTime.string(it) }).filter { it.isNotEmpty() }
        if (bits.isNotEmpty()) Text(bits.joinToString(" · "), fontSize = 11.sp, color = c.muted, maxLines = 1)
    }
}

@Composable
private fun TypePill(type: String?) {
    val c = DcampTheme.colors
    val (label, tint) = when (type) {
        "comment" -> T("COMMENT") to c.link
        "chat" -> T("CHAT") to c.accent
        "pings", "dm" -> T("DM") to c.dm
        else -> T("THREAD") to c.inkSoft
    }
    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tint,
        modifier = Modifier.background(tint.copy(alpha = 0.12f), CircleShape).padding(horizontal = 6.dp, vertical = 1.dp))
}

private fun highlight(text: String, terms: List<String>, accent: Color): AnnotatedString {
    if (terms.isEmpty()) return AnnotatedString(text)
    val lower = text.lowercase()
    val ranges = mutableListOf<IntRange>()
    for (term in terms) {
        var start = 0
        while (true) {
            val idx = lower.indexOf(term, start)
            if (idx < 0) break
            ranges.add(idx until (idx + term.length)); start = idx + term.length
        }
    }
    if (ranges.isEmpty()) return AnnotatedString(text)
    val sorted = ranges.sortedBy { it.first }
    return buildAnnotatedString {
        var pos = 0
        for (r in sorted) {
            if (r.first < pos) continue
            append(text.substring(pos, r.first))
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Bold)) { append(text.substring(r.first, r.last + 1)) }
            pos = r.last + 1
        }
        append(text.substring(pos))
    }
}
