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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.decentespresso.dcamp.api.Board
import com.decentespresso.dcamp.api.MessageRow
import com.decentespresso.dcamp.api.PlainText
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.api.SessionStore
import com.decentespresso.dcamp.api.messagesPage
import com.decentespresso.dcamp.ui.Dest
import com.decentespresso.dcamp.ui.LocalRouter
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.rememberRetained
import com.decentespresso.dcamp.ui.theme.Avatar
import com.decentespresso.dcamp.ui.theme.AuthorLine
import com.decentespresso.dcamp.ui.theme.DcampTheme
import com.decentespresso.dcamp.ui.theme.hexColor
import kotlinx.coroutines.launch

/** Forum thread list: header, (Problems) status pills, and rich thread rows. */
@Composable
fun ThreadListView(board: Board, session: SessionStore.State) {
    val c = DcampTheme.colors
    val router = LocalRouter.current
    val scope = rememberCoroutineScope()
    // Retained (not remembered) so reading a thread and going back keeps the loaded pages
    // and the scroll position instead of reloading the list at the top.
    val rows = rememberRetained("rows") { mutableStateListOf<MessageRow>() }
    var total by rememberRetained("total") { mutableIntStateOf(0) }
    var fetched by rememberRetained("fetched") { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var statusFilter by rememberRetained("status") { mutableStateOf("unsolved") }
    var loadedFilter by rememberRetained("loadedFor") { mutableStateOf<String?>(null) }
    val listState = rememberRetained("list") { LazyListState() }
    var composing by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    val forumFilter = com.decentespresso.dcamp.ui.LocalForumFilter.current
    val isProblems = board.name == "Problems"

    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true
        try {
            val offset = if (reset) 0 else fetched
            val params = buildMap {
                put("project_id", board.id.toString()); put("offset", offset.toString()); put("limit", "30")
                if (isProblems && statusFilter != "all") put("status", statusFilter)
                putAll(forumFilter.messageParams)
            }
            val page = runCatching { Services.api.messagesPage(params) }.getOrNull() ?: return
            // The list is in latest-activity order, so a thread that got a reply since the last
            // page shifts the rest down and the next page repeats a row: skip those (duplicate
            // LazyColumn keys crash), but count them in the server offset.
            if (reset) { rows.clear(); rows.addAll(page.messages); listState.scrollToItem(0) }
            else { val have = rows.mapTo(HashSet()) { it.id }; rows.addAll(page.messages.filter { it.id !in have }) }
            fetched = offset + page.messages.size
            total = page.total ?: rows.size
            loadedFilter = "$statusFilter ${forumFilter.messageParams}"
        } finally { loading = false }
    }

    /** Back from a thread: update the newest page's rows (reply counts, latest reply) in place,
     *  without adding or removing rows, so the scroll position and paging offset stay put. */
    suspend fun refreshInPlace() {
        val params = buildMap {
            put("project_id", board.id.toString()); put("offset", "0"); put("limit", "30")
            if (isProblems && statusFilter != "all") put("status", statusFilter)
            putAll(forumFilter.messageParams)
        }
        val page = runCatching { Services.api.messagesPage(params) }.getOrNull() ?: return
        val fresh = page.messages.associateBy { it.id }
        rows.forEachIndexed { i, r -> fresh[r.id]?.let { if (it != r) rows[i] = it } }
    }

    androidx.compose.runtime.LaunchedEffect(board.id, statusFilter) {
        if (rows.isNotEmpty() && loadedFilter == "$statusFilter ${forumFilter.messageParams}") refreshInPlace() else load(reset = true)
    }

    LazyColumn(Modifier.fillMaxWidth().background(c.bg), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(hexColor(board.color) ?: c.accent),
                    contentAlignment = Alignment.Center) {
                    Icon(forumIconPublic(board), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(10.dp))
                Text(T(board.name), fontSize = 26.sp, fontWeight = FontWeight.Black, color = c.ink,
                    modifier = Modifier.weight(1f))
                if (session.canPost) {
                    com.decentespresso.dcamp.ui.theme.DCPrimaryButton(onClick = { composing = true }) {
                        Text("+ ${T("New message")}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
        item { SummarizeBar(SummaryKind.Forum(board.id, board.name)); Spacer(Modifier.size(12.dp)) }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill("≡ ${if (forumFilter.selected) forumFilter.label else T("Filter")}", active = forumFilter.on) { showFilter = true }
                if (forumFilter.selected) {
                    StatusPill(if (forumFilter.on) T("Filter on") else T("Filter off"), active = forumFilter.on) {
                        forumFilter.toggleOn(); scope.launch { load(reset = true) }
                    }
                }
            }
        }
        if (isProblems) {
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("unsolved" to "Unsolved", "solved" to "Solved", "notaproblem" to "Closed", "all" to "All").forEach { (k, label) ->
                        StatusPill(T(label), active = statusFilter == k) { statusFilter = k }
                    }
                }
            }
        }
        if (loading && rows.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) } }
        } else if (rows.isEmpty()) {
            item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { Text(T("No threads yet"), color = c.muted) } }
        } else {
            itemsIndexed(rows, key = { _, r -> r.id }) { idx, row ->
                ThreadRowView(row) {
                    router.threadSiblings = rows.map { it.id }
                    router.push(Dest.Thread(row.id))
                }
                Divider(color = c.line)
                if (idx == rows.lastIndex && fetched < total) {
                    androidx.compose.runtime.LaunchedEffect(rows.size) { load(reset = false) }
                }
            }
            if (loading && fetched < total) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) } }
            }
        }
    }

    if (composing) {
        ComposeView(ComposeMode.NewThread(board), session,
            onPosted = { newID -> composing = false; router.threadSiblings = rows.map { it.id }; router.push(Dest.Thread(newID)) },
            onDismiss = { composing = false })
    }
    if (showFilter) {
        FilterSheet(forumFilter, onApply = { scope.launch { load(reset = true) } }, onDismiss = { showFilter = false })
    }
}

@Composable
private fun StatusPill(title: String, active: Boolean, onClick: () -> Unit) {
    val c = DcampTheme.colors
    Text(
        title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        color = if (active) c.accentInk else c.inkSoft,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (active) c.accent.copy(alpha = 0.12f) else c.panel)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

@Composable
private fun ThreadRowView(row: MessageRow, onClick: () -> Unit) {
    val c = DcampTheme.colors
    val thumbSize = 52.dp
    val gap = 12.dp
    val pic: @Composable () -> Unit = {
        val thumb = row.thumbUrl
        if (thumb != null) {
            AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(thumbSize).clip(RoundedCornerShape(8.dp)).background(c.line))
        } else {
            Avatar(person = row.author, size = thumbSize)
        }
    }
    val details: List<@Composable () -> Unit> = buildList {
        row.categoryName?.takeIf { it.isNotEmpty() }?.let { add { CategoryPill(it) } }
        if (row.displayPreview.isNotEmpty()) {
            add { Text(row.displayPreview, fontSize = 15.sp, color = c.inkSoft, maxLines = 1) }
        }
        row.recentCommentText?.let { PlainText.strip(it) }?.takeIf { it.isNotEmpty() }?.let { rc ->
            add {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ReplyAll, contentDescription = null, tint = c.muted, modifier = Modifier.size(13.dp))
                    Text(rc, fontSize = 13.sp, color = c.muted, maxLines = 1)
                }
            }
        }
        add {
            val n = row.commentCount ?: 0
            AuthorLine(person = row.author, date = row.createdAt) {
                if (n > 0) Text("· $n 💬", fontSize = 13.sp, color = c.muted, softWrap = false)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 14.dp)) {
        if (maxWidth >= FLOAT_THUMB_BELOW) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.Top) {
                pic()
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThreadTitle(row, narrowWidth = null, beside = 0.dp)
                    details.forEach { it() }
                }
            }
        } else {
            // Phones: the picture floats top-right and the title wraps around it, so the
            // title isn't confined to a slim column beside the picture.
            FloatRightLayout(thumbSize, gap, 6.dp, pic) {
                ThreadTitle(row, narrowWidth = maxWidth - thumbSize - gap, beside = thumbSize)
                details.forEach { it() }
            }
        }
    }
}

private val FLOAT_THUMB_BELOW = 560.dp

/** Thread title. With [narrowWidth], the lines that start beside the floated picture
 *  (the top [beside] of the block) are laid out at that width, the rest at full width. */
@Composable
private fun ThreadTitle(row: MessageRow, narrowWidth: Dp?, beside: Dp) {
    val c = DcampTheme.colors
    val style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.ink)
    val pinId = "pin"
    val pinSize = 14.sp
    val text = buildAnnotatedString {
        if (row.isPinned) { appendInlineContent(pinId, "📌"); append(" ") }
        append(row.displaySubject)
    }
    val placeholders = if (row.isPinned)
        listOf(AnnotatedString.Range(Placeholder(pinSize, pinSize, PlaceholderVerticalAlign.TextCenter), 0, 1))
    else emptyList()
    val inline = mapOf(pinId to InlineTextContent(Placeholder(pinSize, pinSize, PlaceholderVerticalAlign.TextCenter)) {
        Icon(Icons.Filled.PushPin, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.fillMaxSize())
    })
    if (narrowWidth == null) {
        Text(text, style = style, inlineContent = inline)
        return
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val split = remember(text, narrowWidth, beside, density) {
        val layout = measurer.measure(text, style, placeholders = placeholders,
            constraints = Constraints(maxWidth = with(density) { narrowWidth.roundToPx() }), density = density)
        val besidePx = with(density) { beside.toPx() }
        val n = (0 until layout.lineCount).count { layout.getLineTop(it) < besidePx }.coerceAtLeast(1)
        if (n >= layout.lineCount) text.length else layout.getLineEnd(n - 1)
    }
    Column {
        Text(text.subSequence(0, split), style = style, inlineContent = inline, modifier = Modifier.width(narrowWidth))
        val rest = text.text.substring(split).trimStart()
        if (rest.isNotEmpty()) Text(rest, style = style)
    }
}

/** [pic] pinned top-right; [content] children stack with [spacing], narrowed to clear the
 *  picture while they start beside it. The first child (the title) handles its own split. */
@Composable
private fun FloatRightLayout(picSize: Dp, gap: Dp, spacing: Dp, pic: @Composable () -> Unit, content: @Composable () -> Unit) {
    Layout(contents = listOf(pic, content)) { (picM, items), constraints ->
        val full = constraints.maxWidth
        val picPx = picSize.roundToPx()
        val narrow = full - picPx - gap.roundToPx()
        val p = picM.map { it.measure(Constraints.fixed(picPx, picPx)) }
        var y = 0
        val placed = items.mapIndexed { i, m ->
            val w = if (i > 0 && y < picPx) narrow else full
            val pl = m.measure(Constraints(maxWidth = w))
            val at = y
            y += pl.height + spacing.roundToPx()
            pl to at
        }
        val h = maxOf(y - spacing.roundToPx(), picPx)
        layout(full, h) {
            p.forEach { it.place(full - picPx, 0) }
            placed.forEach { (pl, at) -> pl.place(0, at) }
        }
    }
}

@Composable
fun CategoryPill(name: String, emoji: String? = null) {
    val c = DcampTheme.colors
    Text(
        name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.accentInk,
        maxLines = 1, softWrap = false,
        modifier = Modifier.background(c.accent.copy(alpha = 0.12f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
