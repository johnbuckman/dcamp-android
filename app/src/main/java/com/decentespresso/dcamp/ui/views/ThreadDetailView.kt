package com.decentespresso.dcamp.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.api.Comment
import com.decentespresso.dcamp.api.MessageDetail
import com.decentespresso.dcamp.api.PlainText
import com.decentespresso.dcamp.api.Boosts
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.api.SessionStore
import com.decentespresso.dcamp.api.messageDetail
import com.decentespresso.dcamp.api.messagePin
import com.decentespresso.dcamp.api.messageSetStatus
import com.decentespresso.dcamp.api.messageStatus
import com.decentespresso.dcamp.api.offtopicMove
import com.decentespresso.dcamp.api.offtopicSuggest
import com.decentespresso.dcamp.api.commentDelete
import com.decentespresso.dcamp.ui.LocalRouter
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.rememberRetained
import com.decentespresso.dcamp.ui.theme.Avatar
import com.decentespresso.dcamp.ui.theme.AuthorLine
import com.decentespresso.dcamp.ui.theme.DcampTheme
import com.decentespresso.dcamp.ui.theme.dcCard
import com.decentespresso.dcamp.ui.trix.TranslatableText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Thread view: message card + author-headed comments with reactions and ⋯ menus. */
@Composable
fun ThreadDetailView(messageID: Int, session: SessionStore.State) {
    val c = DcampTheme.colors
    val router = LocalRouter.current
    val scope = rememberCoroutineScope()
    // Retained (not plain remember) so going back to this thread from a screen pushed over it
    // finds its comments and scroll position as they were, and doesn't re-jump to first-unread.
    var currentID by rememberRetained("cur") { mutableIntStateOf(messageID) }
    var message by rememberRetained("msg") { mutableStateOf<MessageDetail?>(null) }
    val comments = rememberRetained("comments") { androidx.compose.runtime.mutableStateListOf<Comment>() }
    var lastVisit by rememberRetained("lastVisit") { mutableStateOf<Int?>(null) }
    // Thread whose lastVisit we hold: every `message` fetch (incl. the 3s poll) stamps a new
    // visit, so only the FIRST response for a thread carries the real previous visit.
    var lastVisitFor by rememberRetained("lastVisitFor") { mutableStateOf<Int?>(null) }
    // Long-thread collapse (>20 comments: server sends head + tail, total = real count).
    var showAll by rememberRetained("showAll") { mutableStateOf(false) }
    var collapsedTotal by rememberRetained("collapsedTotal") { mutableStateOf<Int?>(null) }
    var collapsedHead by rememberRetained("collapsedHead") { mutableIntStateOf(10) }
    var loadingAll by remember { mutableStateOf(false) }
    // Thread we've already opened at its first unread comment (only once per thread).
    var jumpedFor by rememberRetained("jumpedFor") { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    val composerModel = remember(currentID) { ComposerModel() }
    var editMode by remember { mutableStateOf<ComposeMode?>(null) }
    var offtopicCommentID by remember { mutableStateOf<Int?>(null) }
    val listState = rememberRetained("list") { androidx.compose.foundation.lazy.LazyListState() }
    // Scroll-to-first-unread plumbing: lazy index of the comments card (set while building the
    // list) and the layout coordinates of the card and of the "New since your last visit" divider.
    val commentsCardIndex = remember { intArrayOf(-1) }
    val cardCoords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val unreadCoords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    // Quote a message/comment: buffer the web-parity quote into the composer and scroll it into
    // view (the composer is the last lazy item, so quoting the top-of-thread message must bring
    // it on-screen). src is the source permalink so "wrote:" links back to the original.
    fun quoteInto(rawHtml: String, aid: Int?, aname: String?, src: String?) {
        composerModel.insertQuote(quoteHtml(rawHtml, aid, aname, src))
        scope.launch { listState.animateScrollToItem(Int.MAX_VALUE) }
    }

    fun applyCollapse(resp: com.decentespresso.dcamp.api.MessageResponse) {
        if (resp.isCollapsed) { collapsedTotal = resp.total; collapsedHead = resp.head ?: 10 }
        else collapsedTotal = null
    }

    /** True when a collapsed response's first unread comment could be in the hidden middle:
     *  every head comment predates the last visit but the first tail comment doesn't. */
    fun unreadInGap(resp: com.decentespresso.dcamp.api.MessageResponse, lv: Int?): Boolean {
        if (!resp.isCollapsed || lv == null || lv <= 0) return false
        val cs = resp.comments ?: return false
        val head = resp.head ?: 10
        if (head <= 0 || cs.size <= head || (resp.total ?: 0) <= cs.size) return false
        return (cs[head - 1].createdAt ?: 0) <= lv && (cs[head].createdAt ?: 0) > lv
    }

    suspend fun load(): Boolean {
        loading = true
        try {
            var resp = runCatching { Services.api.messageDetail(currentID, showAll) }.getOrNull() ?: return false
            val lv = if (lastVisitFor == currentID) lastVisit else resp.lastVisit
            // Opening a long thread whose first unread comment would be hidden in the collapsed
            // middle: fetch every comment instead, so the divider and the jump land on it.
            if (unreadInGap(resp, lv)) {
                runCatching { Services.api.messageDetail(currentID, all = true) }.getOrNull()?.let {
                    resp = it; showAll = true
                }
            }
            message = resp.message
            comments.clear(); comments.addAll(resp.comments ?: emptyList())
            if (lastVisitFor != currentID) { lastVisit = lv; lastVisitFor = currentID }   // lv: first response's
            applyCollapse(resp)
            return true
        } finally { loading = false }
    }

    /** Poll for new/streaming comments. A collapsed thread merges by id (append-only): the
     *  fresh response is head+tail again, so a plain replace would shuffle the window. */
    suspend fun poll() {
        val resp = runCatching { Services.api.messageDetail(currentID, showAll) }.getOrNull() ?: return
        val fresh = resp.comments ?: emptyList()
        if (resp.isCollapsed || collapsedTotal != null) {
            val index = comments.withIndex().associate { (i, cm) -> cm.id to i }
            for (cm in fresh) {
                val i = index[cm.id]
                if (i == null) comments.add(cm)
                else if (comments[i].body != cm.body || comments[i].streaming != cm.streaming) comments[i] = cm
            }
            if (resp.isCollapsed) collapsedTotal = maxOf(resp.total ?: 0, comments.size)
        } else {
            val changed = fresh.size != comments.size ||
                fresh.zip(comments).any { (a, b) -> a.body != b.body || a.streaming != b.streaming }
            if (changed) { comments.clear(); comments.addAll(fresh) }
        }
        if (resp.message.boosts?.list?.size != message?.boosts?.list?.size) message = resp.message
    }

    // Prev/next across the board's ordered thread list.
    val siblings = router.threadSiblings
    val sibIdx = siblings.indexOf(currentID).takeIf { it >= 0 }
    // Forum Country filter: when on with a country selection, hide comments whose author
    // isn't in the chosen countries (blank/unknown counts as non-matching), like the web.
    val forumFilter = com.decentespresso.dcamp.ui.LocalForumFilter.current
    val visibleComments = if (forumFilter.active && forumFilter.countries.isNotEmpty()) {
        val set = forumFilter.countries.map { it.uppercase() }.toSet()
        comments.filter { set.contains((it.authorCountry ?: "").uppercase()) }
    } else comments.toList()
    val firstUnread = lastVisit?.takeIf { it > 0 }?.let { lv -> visibleComments.firstOrNull { (it.createdAt ?: 0) > lv }?.id }
    val firstUnreadNow by androidx.compose.runtime.rememberUpdatedState(firstUnread)
    // Comments the server left out of a collapsed thread (0 when uncollapsed / all loaded).
    val hiddenMiddle = maxOf(0, (collapsedTotal ?: 0) - comments.size)
    val headBoundaryID = if (hiddenMiddle > 0 && collapsedHead > 0 && comments.size >= collapsedHead)
        comments[collapsedHead - 1].id else null

    /** Bring the "New since your last visit" divider to the top of the list (like iOS, which
     *  scrolls to the first unread with anchor .top). All comments live in ONE lazy item (the
     *  card), so the target is (card index, divider's y inside the card), read from layout
     *  coordinates. Images above the divider load after the first layout and push it down, so
     *  for a short while keep re-anchoring it, until the layout has been still for a moment or
     *  the user starts dragging. */
    suspend fun jumpToFirstUnread() = kotlinx.coroutines.coroutineScope {
        if (firstUnreadNow == null) return@coroutineScope
        val idx = commentsCardIndex[0].takeIf { it >= 0 } ?: return@coroutineScope
        var userDragged = false
        val watcher = launch {
            listState.interactionSource.interactions.collect {
                if (it is androidx.compose.foundation.interaction.DragInteraction.Start) userDragged = true
            }
        }
        // Divider's distance below the top of the list content (null = not composed/visible).
        fun dividerOffset(): Float? {
            val card = cardCoords[0]; val div = unreadCoords[0]
            if (card == null || div == null || !card.isAttached || !div.isAttached) return null
            val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == idx } ?: return null
            return item.offset + card.localPositionOf(div, androidx.compose.ui.geometry.Offset.Zero).y
        }
        listState.scrollToItem(idx)   // compose the card so the divider gets laid out
        val start = System.currentTimeMillis()
        var lastMove = start
        while (!userDragged && firstUnreadNow != null) {
            androidx.compose.runtime.withFrameNanos { }
            val now = System.currentTimeMillis()
            if (now - lastMove > 1500 || now - start > 8000) break
            val off = dividerOffset()
            if (off == null) {
                // Divider scrolled out of the composed window (big layout jump): re-measure.
                val card = cardCoords[0]; val div = unreadCoords[0]
                if (card != null && div != null && card.isAttached && div.isAttached) {
                    listState.scrollToItem(idx, card.localPositionOf(div, androidx.compose.ui.geometry.Offset.Zero).y.toInt())
                    lastMove = now
                }
            } else if (kotlin.math.abs(off) > 1f) {
                // (near the end of the thread the list can't scroll that far: nothing consumed)
                if (kotlin.math.abs(listState.scrollBy(off)) > 0.5f) lastMove = now
            }
        }
        watcher.cancel()
    }

    // Load + live poll (new/streaming comments). Opening a thread (first time, or prev/next to
    // another) scrolls to its first unread comment once; coming back to a retained thread
    // just refreshes in place and keeps the scroll position.
    androidx.compose.runtime.LaunchedEffect(currentID) {
        if (message?.id != currentID) {
            message = null; comments.clear()
            showAll = false; collapsedTotal = null; collapsedHead = 10
            listState.scrollToItem(0)
        }
        if (load() && jumpedFor != currentID) {
            jumpedFor = currentID
            delay(350)
            jumpToFirstUnread()
        }
        while (isActive) {
            delay(3000)
            poll()
        }
    }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            var itemCount = 0   // lazy index of the next item, for commentsCardIndex
            item { SummarizeBar(SummaryKind.Thread(currentID)) }; itemCount++
            commentsCardIndex[0] = -1
            message?.let { m ->
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.weight(1f))
                        if (sibIdx != null) {
                            val prev = sibIdx.takeIf { it > 0 }?.let { siblings[it - 1] }
                            val next = sibIdx.takeIf { it < siblings.lastIndex }?.let { siblings[it + 1] }
                            Text("▲", fontSize = 18.sp, color = if (prev != null) c.inkSoft else c.line,
                                modifier = Modifier.padding(8.dp).clickable(enabled = prev != null) { prev?.let { currentID = it } })
                            Text("▼", fontSize = 18.sp, color = if (next != null) c.inkSoft else c.line,
                                modifier = Modifier.padding(8.dp).clickable(enabled = next != null) { next?.let { currentID = it } })
                        }
                    }
                }; itemCount++
                item { MessageCard(m, session, onChange = { scope.launch { load() } }, onDeleted = { router.pop() },
                    onEdit = { editMode = ComposeMode.EditThread(m) },
                    onQuote = { quoteInto(m.html, m.author?.id, m.author?.name, "#/message/$currentID") }) }; itemCount++
                if (visibleComments.isNotEmpty()) {
                    item {
                        val shown = visibleComments.size + hiddenMiddle
                        Text(T(if (shown == 1) "{n} comment" else "{n} comments", "n" to "$shown"),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    }; itemCount++
                    commentsCardIndex[0] = itemCount
                    item(key = "comments") {
                        Column(Modifier.onPlaced { cardCoords[0] = it }.fillMaxWidth().dcCard().padding(4.dp)) {
                            visibleComments.forEach { cm ->
                                if (cm.id == firstUnread) {
                                    Box(Modifier.onPlaced { unreadCoords[0] = it }) { SinceLastVisitDivider() }
                                }
                                CommentRow(cm, session, onChange = { scope.launch { load() } },
                                    onEdit = { editMode = ComposeMode.EditComment(cm) },
                                    onQuote = { quoteInto(cm.html, cm.author?.id, cm.author?.name, "#/message/$currentID?c=${cm.id}") })
                                Divider(color = c.line)
                                if (cm.id == headBoundaryID) {
                                    LoadAllRow(hiddenMiddle, loadingAll) {
                                        loadingAll = true; showAll = true
                                        scope.launch { load(); loadingAll = false }
                                    }
                                }
                            }
                        }
                    }; itemCount++
                }
                item {
                    if (session.canPost) {
                        Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InlineComposer(T("Write a comment… (rich text, @mention)"), composerModel, draftKey = "comment:$currentID",
                                title = T("Add a comment")) { html ->
                                val r = runCatching { Services.api.createComment(currentID, html) }.getOrNull()
                                if (r?.id != null) {
                                    scope.launch { load() }
                                    scope.launch { if (Services.api.offtopicSuggest(r.id).suggested) offtopicCommentID = r.id }
                                    true
                                } else false
                            }
                        }
                    } else {
                        ReadOnlyNotice()
                    }
                }
            }
        }
        if (loading && message == null) {
            CircularProgressIndicator(color = c.accent, modifier = Modifier.align(Alignment.Center))
        }
    }

    editMode?.let { mode ->
        ComposeView(mode, session, onPosted = { editMode = null; scope.launch { load() } }, onDismiss = { editMode = null })
    }

    offtopicCommentID?.let { cid ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { offtopicCommentID = null },
            title = { Text(T("Move to its own thread?")) },
            text = { Text(T("This comment looks like it’s about something else. Move it to its own thread?")) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val id = cid; offtopicCommentID = null
                    scope.launch { val r = Services.api.offtopicMove(id); if (r.id != null) currentID = r.id!! else load() }
                }) { Text(T("Move it")) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { offtopicCommentID = null }) { Text(T("Keep it here")) } },
        )
    }
}

@Composable
private fun MessageCard(m: MessageDetail, session: SessionStore.State, onChange: () -> Unit, onDeleted: () -> Unit,
                        onEdit: () -> Unit, onQuote: () -> Unit) {
    val c = DcampTheme.colors
    val router = LocalRouter.current
    Column(Modifier.fillMaxWidth().dcCard().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(person = m.author, size = 46.dp, onClick = { m.author?.id?.let { router.personID = it } })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (m.pinned == 1) Icon(Icons.Filled.PushPin, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                    Text(m.displaySubject, fontSize = 24.sp, fontWeight = FontWeight.Black, color = c.ink)
                }
                AuthorLine(person = m.author, date = m.createdAt)
            }
            MessageMenu(m, session, onChange, onDeleted, onEdit, onQuote)
        }
        TranslatableText(original = m.html, translated = m.bodyTr)
        BoostBar("message", m.id, m.boosts ?: Boosts()) { onChange() }
    }
}

/** Wrap a post's content as a blockquote for the reply composer (plain-text, escaped). */
private fun escQuote(s: String): String = s
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    .replace("\"", "&quot;").replace("'", "&#39;")

/** Build a quote matching the web (dcamp.js insertQuote): an "@Author wrote:" attribution line —
 *  the author as a real mention link, "wrote:" linking back to the source — followed by a
 *  blockquote of the plain-text selection. */
fun quoteHtml(rawHtml: String, aid: Int?, aname: String?, src: String?): String {
    val body = "<blockquote>" + escQuote(PlainText.strip(rawHtml)).replace(Regex("\\n+"), "<br>") + "</blockquote>"
    if (aid == null || aname.isNullOrEmpty()) return body
    val wrote = if (!src.isNullOrEmpty()) "<a href=\"${escQuote(src)}\">${escQuote(T("wrote"))}:</a>"
                else "${escQuote(T("wrote"))}:"
    return "<a href=\"#/p/$aid\">@${escQuote(aname)}</a> $wrote<br>$body"
}

@Composable
private fun CommentRow(cm: Comment, session: SessionStore.State, onChange: () -> Unit, onEdit: () -> Unit, onQuote: () -> Unit) {
    val c = DcampTheme.colors
    val router = LocalRouter.current
    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(person = cm.author, size = 34.dp, onClick = { cm.author?.id?.let { router.personID = it } })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { AuthorLine(person = cm.author, date = cm.createdAt) }
                CommentMenu(cm, session, onChange, onEdit, onQuote)
            }
            TranslatableText(original = cm.html, translated = cm.bodyTr)
            BoostBar("comment", cm.id, cm.boosts ?: Boosts()) { onChange() }
        }
    }
}

@Composable
private fun MessageMenu(m: MessageDetail, session: SessionStore.State, onChange: () -> Unit, onDeleted: () -> Unit,
                        onEdit: () -> Unit, onQuote: () -> Unit) {
    val c = DcampTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var open by remember { mutableStateOf(false) }
    val canModify = m.author?.id != null && (session.me?.id == m.author?.id || session.isAdmin)
    val isProblems = session.boards.firstOrNull { it.id == m.projectId }?.name == "Problems"

    Box {
        Icon(Icons.Filled.MoreHoriz, contentDescription = "More", tint = c.muted,
            modifier = Modifier.size(28.dp).clickable { open = true }.padding(2.dp))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(T("Copy link")) }, onClick = {
                clipboard.setText(AnnotatedString("https://decentespresso.com/support/dcamp/#/message/${m.id}"))
                open = false
            })
            if (session.canPost) {
                DropdownMenuItem(text = { Text(T("Quote & reply")) }, onClick = { open = false; onQuote() })
            }
            if (canModify) {
                DropdownMenuItem(text = { Text(T("Edit")) }, onClick = { open = false; onEdit() })
                if (isProblems) {
                    DropdownMenuItem(text = { Text(T("Mark: Unsolved")) }, onClick = { open = false; scope.launch { Services.api.messageSetStatus(m.id, "unsolved"); onChange() } })
                    DropdownMenuItem(text = { Text(T("Mark: Solved")) }, onClick = { open = false; scope.launch { Services.api.messageSetStatus(m.id, "solved"); onChange() } })
                    DropdownMenuItem(text = { Text(T("Mark: Closed")) }, onClick = { open = false; scope.launch { Services.api.messageSetStatus(m.id, "notaproblem"); onChange() } })
                }
                if (session.isAdmin) {
                    DropdownMenuItem(text = { Text(if (m.pinned == 1) T("Unpin") else T("Pin")) }, onClick = { open = false; scope.launch { Services.api.messagePin(m.id, m.pinned != 1); onChange() } })
                }
                DropdownMenuItem(text = { Text(T("Delete")) }, onClick = { open = false; scope.launch { Services.api.messageStatus(m.id, "deleted"); onDeleted() } })
            }
        }
    }
}

@Composable
private fun CommentMenu(cm: Comment, session: SessionStore.State, onChange: () -> Unit, onEdit: () -> Unit, onQuote: () -> Unit) {
    val c = DcampTheme.colors
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var open by remember { mutableStateOf(false) }
    val canModify = cm.author?.id != null && (session.me?.id == cm.author?.id || session.isAdmin)
    Box {
        Icon(Icons.Filled.MoreHoriz, contentDescription = "More", tint = c.muted,
            modifier = Modifier.size(22.dp).clickable { open = true }.padding(2.dp))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(T("Copy link")) }, onClick = {
                clipboard.setText(AnnotatedString("https://decentespresso.com/support/dcamp/#/message/${cm.messageId ?: 0}?c=${cm.id}"))
                open = false
            })
            if (session.canPost) {
                DropdownMenuItem(text = { Text(T("Quote & reply")) }, onClick = { open = false; onQuote() })
            }
            if (canModify) {
                DropdownMenuItem(text = { Text(T("Edit")) }, onClick = { open = false; onEdit() })
                DropdownMenuItem(text = { Text(T("Delete")) }, onClick = { open = false; scope.launch { Services.api.commentDelete(cm.id); onChange() } })
            }
        }
    }
}

@Composable
private fun ReadOnlyNotice() {
    val c = DcampTheme.colors
    Row(Modifier.fillMaxWidth().dcCard().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
        Text(T("You have read-only access — posting is for current Decent machine owners."), fontSize = 13.sp, color = c.muted)
    }
}

/** Between the head and tail of a collapsed long thread: fetch every comment (all=1). */
@Composable
private fun LoadAllRow(hidden: Int, busy: Boolean, onClick: () -> Unit) {
    val c = DcampTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { onClick() }.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                Spacer(Modifier.size(8.dp))
            }
            Text(T("Load all messages") + " ($hidden)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accentInk)
        }
        Divider(color = c.line)
    }
}

/** "New since your last visit" divider before the first unread comment. */
@Composable
fun SinceLastVisitDivider() {
    val c = DcampTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f).height(1.dp).background(c.accent.copy(alpha = 0.35f)))
        Text(T("New since your last visit"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.accentInk)
        Box(Modifier.weight(1f).height(1.dp).background(c.accent.copy(alpha = 0.35f)))
    }
}
