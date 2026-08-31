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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.api.Comment
import com.decentespresso.dcamp.api.MessageDetail
import com.decentespresso.dcamp.api.Boosts
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.api.SessionStore
import com.decentespresso.dcamp.api.messageDetail
import com.decentespresso.dcamp.api.messagePin
import com.decentespresso.dcamp.api.messageSetStatus
import com.decentespresso.dcamp.api.messageStatus
import com.decentespresso.dcamp.api.commentDelete
import com.decentespresso.dcamp.ui.LocalRouter
import com.decentespresso.dcamp.ui.T
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
    var currentID by remember(messageID) { mutableIntStateOf(messageID) }
    var message by remember { mutableStateOf<MessageDetail?>(null) }
    val comments = remember { androidx.compose.runtime.mutableStateListOf<Comment>() }
    var lastVisit by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }

    suspend fun load() {
        loading = true
        try {
            val resp = runCatching { Services.api.messageDetail(currentID) }.getOrNull() ?: return
            message = resp.message
            comments.clear(); comments.addAll(resp.comments ?: emptyList())
            lastVisit = resp.lastVisit
        } finally { loading = false }
    }

    // Load + live poll (new/streaming comments).
    androidx.compose.runtime.LaunchedEffect(currentID) {
        message = null; comments.clear()
        load()
        while (isActive) {
            delay(3000)
            val resp = runCatching { Services.api.messageDetail(currentID) }.getOrNull() ?: continue
            val fresh = resp.comments ?: emptyList()
            val changed = fresh.size != comments.size ||
                fresh.zip(comments).any { (a, b) -> a.body != b.body || a.streaming != b.streaming }
            if (changed) { comments.clear(); comments.addAll(fresh) }
            if (resp.message.boosts?.list?.size != message?.boosts?.list?.size) message = resp.message
        }
    }

    // Prev/next across the board's ordered thread list.
    val siblings = router.threadSiblings
    val sibIdx = siblings.indexOf(currentID).takeIf { it >= 0 }
    val firstUnread = lastVisit?.takeIf { it > 0 }?.let { lv -> comments.firstOrNull { (it.createdAt ?: 0) > lv }?.id }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { SummarizeBar(SummaryKind.Thread(currentID)) }
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
                }
                item { MessageCard(m, session, onChange = { scope.launch { load() } }, onDeleted = { router.pop() }) }
                if (comments.isNotEmpty()) {
                    item {
                        Text(T(if (comments.size == 1) "{n} comment" else "{n} comments", "n" to "${comments.size}"),
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    }
                    item {
                        Column(Modifier.fillMaxWidth().dcCard().padding(4.dp)) {
                            comments.forEach { cm ->
                                if (cm.id == firstUnread) SinceLastVisitDivider()
                                CommentRow(cm, session, onChange = { scope.launch { load() } })
                                Divider(color = c.line)
                            }
                        }
                    }
                }
                item {
                    if (session.canPost) {
                        val model = remember(currentID) { ComposerModel() }
                        Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(T("Add a comment"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                            InlineComposer(T("Write a comment… (rich text, @mention)"), model) { html ->
                                val r = runCatching { Services.api.createComment(currentID, html) }.getOrNull()
                                if (r?.id != null) { scope.launch { load() }; true } else false
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
}

@Composable
private fun MessageCard(m: MessageDetail, session: SessionStore.State, onChange: () -> Unit, onDeleted: () -> Unit) {
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
            MessageMenu(m, session, onChange, onDeleted)
        }
        TranslatableText(original = m.html, translated = m.bodyTr)
        BoostBar("message", m.id, m.boosts ?: Boosts()) { onChange() }
    }
}

@Composable
private fun CommentRow(cm: Comment, session: SessionStore.State, onChange: () -> Unit) {
    val c = DcampTheme.colors
    val router = LocalRouter.current
    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(person = cm.author, size = 34.dp, onClick = { cm.author?.id?.let { router.personID = it } })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { AuthorLine(person = cm.author, date = cm.createdAt) }
                CommentMenu(cm, session, onChange)
            }
            TranslatableText(original = cm.html, translated = cm.bodyTr)
            BoostBar("comment", cm.id, cm.boosts ?: Boosts()) { onChange() }
        }
    }
}

@Composable
private fun MessageMenu(m: MessageDetail, session: SessionStore.State, onChange: () -> Unit, onDeleted: () -> Unit) {
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
            if (canModify) {
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
private fun CommentMenu(cm: Comment, session: SessionStore.State, onChange: () -> Unit) {
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
            if (canModify) {
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
