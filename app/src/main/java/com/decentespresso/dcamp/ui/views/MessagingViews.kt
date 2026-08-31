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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.api.ChatLine
import com.decentespresso.dcamp.api.DMConversation
import com.decentespresso.dcamp.api.DMMessage
import com.decentespresso.dcamp.api.DMThread
import com.decentespresso.dcamp.api.Person
import com.decentespresso.dcamp.api.PlainText
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.api.SessionStore
import com.decentespresso.dcamp.ui.Dest
import com.decentespresso.dcamp.ui.LocalRouter
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.theme.Avatar
import com.decentespresso.dcamp.ui.theme.AuthorLine
import com.decentespresso.dcamp.ui.theme.DcampTheme
import com.decentespresso.dcamp.ui.theme.dcCard
import com.decentespresso.dcamp.ui.theme.hexColor
import com.decentespresso.dcamp.ui.trix.TranslatableText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Flat author-headed message row (chat), matching the web: avatar + name/badges + body. */
@Composable
private fun LineRow(author: Person?, html: String, createdAt: Int?, translated: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Avatar(person = author, size = 36.dp, onClick = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            AuthorLine(person = author, date = createdAt)
            TranslatableText(original = html, translated = translated)
        }
    }
}

/** Read-only composer placeholder used until Phase 5 wires the real rich composer. */
@Composable
private fun ComposerPlaceholder(canPost: Boolean, roleNotice: String, placeholder: String) {
    val c = DcampTheme.colors
    if (canPost) {
        Box(Modifier.fillMaxWidth().background(c.bg).padding(14.dp)) {
            Box(Modifier.fillMaxWidth().background(c.panel, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 12.dp)) {
                Text(placeholder, color = c.muted, fontSize = 15.sp)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth().background(c.bg).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
            Text(roleNotice, fontSize = 13.sp, color = c.muted)
        }
    }
}

// Chat ----------------------------------------------------------------------

@Composable
fun ChatView(session: SessionStore.State) {
    val c = DcampTheme.colors
    val lines = remember { mutableStateListOf<ChatLine>() }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        runCatching { Services.api.chatLines() }.getOrNull()?.let { lines.clear(); lines.addAll(it.lines) }
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
        while (isActive) {
            delay(3000)
            val streamingMin = lines.filter { (it.streaming ?: 0) != 0 }.minOfOrNull { it.id }
            val since = streamingMin?.minus(1) ?: (lines.lastOrNull()?.id ?: 0)
            val r = runCatching { Services.api.chatLines(since = since) }.getOrNull() ?: continue
            if (r.lines.isNotEmpty()) {
                val byId = lines.associateBy { it.id }.toMutableMap()
                r.lines.forEach { byId[it.id] = it }
                val merged = byId.values.sortedBy { it.id }
                lines.clear(); lines.addAll(merged)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
            item { Column(Modifier.fillMaxWidth().dcCard().padding(8.dp)) { } }
            items(lines, key = { it.id }) { line ->
                LineRow(line.author, line.html, line.createdAt, line.bodyTr)
                Divider(color = c.line)
            }
        }
        ComposerPlaceholder(session.canPost,
            T("You have read-only access — chat is for current Decent machine owners."),
            T("Message the room… (rich text, @mention)"))
    }
}

// Direct messages -----------------------------------------------------------

@Composable
fun DMListView(session: SessionStore.State) {
    val c = DcampTheme.colors
    val router = LocalRouter.current
    val convos = remember { mutableStateListOf<DMConversation>() }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var offset by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }

    suspend fun reload() {
        loading = true
        try {
            val r = runCatching { Services.api.dmConversations(0, 30, query.trim()) }.getOrNull()
            convos.clear(); convos.addAll(r?.conversations ?: emptyList())
            offset = convos.size; hasMore = r?.hasMore ?: false
        } finally { loading = false }
    }
    suspend fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        try {
            val r = runCatching { Services.api.dmConversations(offset, 30, query.trim()) }.getOrNull() ?: return
            convos.addAll(r.conversations); offset += r.conversations.size; hasMore = r.hasMore
        } finally { loading = false }
    }

    LaunchedEffect(Unit) { reload() }
    LaunchedEffect(query) { delay(300); reload() }

    LazyColumn(Modifier.fillMaxSize().background(c.bg), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        item {
            Text(T("Direct Messages"), fontSize = 22.sp, fontWeight = FontWeight.Black, color = c.ink,
                modifier = Modifier.padding(bottom = 12.dp))
        }
        item {
            Row(Modifier.fillMaxWidth().background(c.panel, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(T("Filter by name"), color = c.muted, fontSize = 15.sp)
                    BasicTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = c.ink, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent))
                }
            }
            Spacer(Modifier.size(14.dp))
        }
        if (convos.isEmpty() && !loading) {
            item { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                Text(if (query.isEmpty()) T("No messages yet") else T("No matches"), color = c.muted) } }
        } else {
            itemsIndexed(convos) { idx, convo ->
                DMConversationRow(convo) { router.push(Dest.Dm(convo.id)) }
                Divider(color = c.line)
                if (idx == convos.lastIndex && hasMore) LaunchedEffect(convos.size) { loadMore() }
            }
        }
    }
}

@Composable
private fun DMConversationRow(convo: DMConversation, onClick: () -> Unit) {
    val c = DcampTheme.colors
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(person = convo.lead, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(convo.title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1)
            Text(PlainText.strip(convo.last ?: ""), fontSize = 14.sp, color = c.inkSoft, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(com.decentespresso.dcamp.ui.theme.RelativeTime.string(convo.lastAt), fontSize = 12.sp, color = c.muted)
            if (convo.unreadCount > 0) {
                Text("${convo.unreadCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.background(c.accent, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
fun DMThreadView(convoID: Int, session: SessionStore.State) {
    val c = DcampTheme.colors
    var thread by remember(convoID) { mutableStateOf(DMThread()) }
    val listState = rememberLazyListState()

    LaunchedEffect(convoID) {
        runCatching { Services.api.dmThread(convoID) }.getOrNull()?.let { thread = it }
        if (thread.messages.isNotEmpty()) listState.scrollToItem(thread.messages.lastIndex)
        while (isActive) {
            delay(6000)
            runCatching { Services.api.dmThread(convoID) }.getOrNull()?.let { thread = it }
        }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(state = listState, modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(thread.messages, key = { it.id }) { m ->
                DMBubble(m, showSender = thread.members.size > 2)
            }
        }
        ComposerPlaceholder(session.canPost, "", T("Write a direct message… (rich text, @mention)"))
    }
}

/** iMessage-style DM bubble (own = blue/right, others = grey/left). */
@Composable
private fun DMBubble(message: DMMessage, showSender: Boolean) {
    val c = DcampTheme.colors
    val mine = message.isMine
    val senderTint = hexColor(message.sender?.avatarColor) ?: c.accent

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom) {
        if (mine) Spacer(Modifier.widthIn(min = 44.dp))
        else { Avatar(person = message.sender, size = 28.dp); Spacer(Modifier.size(8.dp)) }

        Column(
            Modifier.widthIn(max = 280.dp)
                .background(if (mine) c.link else if (showSender) senderTint.copy(alpha = 0.14f) else c.bubbleOther,
                    RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (showSender && !mine) {
                message.sender?.name?.takeIf { it.isNotEmpty() }?.let {
                    Text(it, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = senderTint)
                }
            }
            // "mine" bubbles render white text on the blue background.
            TranslatableText(original = message.html, translated = message.bodyTr,
                textColor = if (mine) Color.White else c.ink)
            Text(com.decentespresso.dcamp.ui.theme.RelativeTime.string(message.createdAt),
                fontSize = 11.sp, color = if (mine) Color.White.copy(alpha = 0.75f) else c.muted)
        }

        if (mine) { Spacer(Modifier.size(8.dp)); Avatar(person = message.sender, size = 28.dp) }
        else Spacer(Modifier.widthIn(min = 44.dp))
    }
}
