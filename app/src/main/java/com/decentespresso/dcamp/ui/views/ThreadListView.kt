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
    val rows = remember { mutableStateListOf<MessageRow>() }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var statusFilter by remember { mutableStateOf("unsolved") }
    val isProblems = board.name == "Problems"

    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true
        try {
            val offset = if (reset) 0 else rows.size
            val params = buildMap {
                put("project_id", board.id.toString()); put("offset", offset.toString()); put("limit", "30")
                if (isProblems && statusFilter != "all") put("status", statusFilter)
            }
            val page = runCatching { Services.api.messagesPage(params) }.getOrNull() ?: return
            if (reset) { rows.clear(); rows.addAll(page.messages) } else rows.addAll(page.messages)
            total = page.total ?: rows.size
        } finally { loading = false }
    }

    androidx.compose.runtime.LaunchedEffect(board.id, statusFilter) { load(reset = true) }

    LazyColumn(Modifier.fillMaxWidth().background(c.bg), contentPadding = androidx.compose.foundation.layout.PaddingValues(18.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(hexColor(board.color) ?: c.accent),
                    contentAlignment = Alignment.Center) {
                    Icon(forumIconPublic(board), contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(10.dp))
                Text(T(board.name), fontSize = 26.sp, fontWeight = FontWeight.Black, color = c.ink)
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
                if (idx == rows.lastIndex && rows.size < total) {
                    androidx.compose.runtime.LaunchedEffect(rows.size) { load(reset = false) }
                }
            }
            if (loading && rows.size < total) {
                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.accent) } }
            }
        }
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
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        val thumb = row.thumbUrl
        if (thumb != null) {
            AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(c.line))
        } else {
            Avatar(person = row.author, size = 52.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (row.isPinned) Icon(Icons.Filled.PushPin, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(14.dp))
                Text(row.displaySubject, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.ink,
                    modifier = Modifier.weight(1f, fill = false))
                row.categoryName?.takeIf { it.isNotEmpty() }?.let { CategoryPill(it) }
            }
            if (row.displayPreview.isNotEmpty()) {
                Text(row.displayPreview, fontSize = 15.sp, color = c.inkSoft, maxLines = 1)
            }
            row.recentCommentText?.let { PlainText.strip(it) }?.takeIf { it.isNotEmpty() }?.let { rc ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ReplyAll, contentDescription = null, tint = c.muted, modifier = Modifier.size(13.dp))
                    Text(rc, fontSize = 13.sp, color = c.muted, maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthorLine(person = row.author, date = row.createdAt)
                val n = row.commentCount ?: 0
                if (n > 0) Text("· $n 💬", fontSize = 13.sp, color = c.muted)
            }
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
