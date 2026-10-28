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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.api.DcampRoute
import com.decentespresso.dcamp.api.Notif
import com.decentespresso.dcamp.api.PlainText
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.api.SessionStore
import com.decentespresso.dcamp.ui.Dest
import com.decentespresso.dcamp.ui.LocalRouter
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.theme.Avatar
import com.decentespresso.dcamp.ui.theme.DcampTheme
import com.decentespresso.dcamp.ui.theme.RelativeTime
import kotlinx.coroutines.launch

private fun notifIcon(kind: String?): ImageVector = when (kind) {
    "mention" -> Icons.Filled.AlternateEmail
    "dm" -> Icons.Filled.Email
    "comment_own", "comment_followed" -> Icons.AutoMirrored.Filled.Comment
    "moved" -> Icons.Filled.Build
    else -> Icons.Filled.Notifications
}

private fun notifKindLabel(kind: String?): String = when (kind) {
    "mention" -> T("mentioned you")
    "dm" -> T("sent you a message")
    "comment_own" -> T("replied to your thread")
    "comment_followed" -> T("commented on a thread you follow")
    "moved" -> T("moved your post to its own thread")
    else -> T("new activity")
}

@Composable
fun NotificationsView(session: SessionStore.State) {
    val c = DcampTheme.colors
    val router = LocalRouter.current
    val scope = rememberCoroutineScope()
    // Retained so going back from an opened notification keeps the scroll position; the poll
    // below still runs and drops the item just opened (it was marked seen).
    val notifs = com.decentespresso.dcamp.ui.rememberRetained("notifs") { mutableStateListOf<Notif>() }
    val listState = com.decentespresso.dcamp.ui.rememberRetained("list") { androidx.compose.foundation.lazy.LazyListState() }

    LaunchedEffect(Unit) {
        runCatching { Services.api.notifPoll() }.getOrNull()?.let { notifs.clear(); notifs.addAll(it) }
    }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(T("Notifications"), fontSize = 22.sp, fontWeight = FontWeight.Black, color = c.ink)
            Spacer(Modifier.weight(1f))
            if (notifs.isNotEmpty()) {
                Text(T("Mark all read"), color = c.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        scope.launch { Services.api.notifSeen(notifs.map { it.id }); notifs.clear() }
                    })
            }
        }
        if (notifs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(T("You’re all caught up"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = c.ink)
                    Text(T("New mentions, replies and messages will appear here."), fontSize = 13.sp, color = c.muted)
                }
            }
        } else {
            LazyColumn(state = listState) {
                items(notifs, key = { it.id }) { notif ->
                    NotifRow(notif) {
                        scope.launch { Services.api.notifSeen(listOf(notif.id)) }
                        DcampRoute.messageID(notif.route)?.let { router.push(Dest.Thread(it)) }
                            ?: DcampRoute.dmID(notif.route)?.let { router.push(Dest.Dm(it)) }
                    }
                    Divider(color = c.line)
                }
            }
        }
    }
}

@Composable
private fun NotifRow(notif: Notif, onClick: () -> Unit) {
    val c = DcampTheme.colors
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box {
            Avatar(person = notif.actor, size = 40.dp)
            Box(Modifier.align(Alignment.BottomEnd).size(18.dp).background(c.accent, CircleShape)
                .border(2.dp, c.bg, CircleShape), contentAlignment = Alignment.Center) {
                Icon(notifIcon(notif.kind), contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.ink)) { append(notif.actor?.name ?: T("Someone")) }
                withStyle(SpanStyle(color = c.inkSoft)) { append(" ${notifKindLabel(notif.kind)}") }
            }, fontSize = 14.sp)
            notif.preview?.takeIf { it.isNotEmpty() }?.let {
                Text(PlainText.strip(it), fontSize = 13.sp, color = c.inkSoft, maxLines = 2)
            }
            Text(RelativeTime.string(notif.createdAt), fontSize = 11.sp, color = c.muted)
        }
    }
}
