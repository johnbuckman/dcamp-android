package com.decentespresso.dcamp.ui.views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.api.Board
import com.decentespresso.dcamp.api.SessionStore
import com.decentespresso.dcamp.ui.Dest
import com.decentespresso.dcamp.ui.LocalRouter
import com.decentespresso.dcamp.ui.Router
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.theme.Avatar
import com.decentespresso.dcamp.ui.theme.DCSectionLabel
import com.decentespresso.dcamp.ui.theme.DcampTheme
import com.decentespresso.dcamp.ui.theme.dcCard
import com.decentespresso.dcamp.ui.theme.hexColor

/** App shell: warm-paper forums home under a persistent top bar, matching the web/iOS layout. */
@Composable
fun MainView(session: SessionStore, state: SessionStore.State) {
    val c = DcampTheme.colors
    val router = remember { Router() }
    val top = router.backStack.lastOrNull()

    BackHandler(enabled = router.backStack.isNotEmpty()) { router.pop() }

    androidx.compose.runtime.CompositionLocalProvider(LocalRouter provides router) {
        Column(Modifier.fillMaxSize().background(c.bg)) {
            TopBar(session, state, showBack = router.backStack.isNotEmpty(), onBack = { router.pop() })
            Box(Modifier.fillMaxSize()) {
                when (val d = top) {
                    null -> ForumsHomeView(session, state)
                    is Dest.Board -> PlaceholderScreen("Forum #${d.id}")
                    is Dest.Thread -> PlaceholderScreen("Thread #${d.id}")
                    Dest.Chat -> PlaceholderScreen("Chat Room")
                    Dest.Dms -> PlaceholderScreen("Direct Messages")
                    is Dest.Dm -> PlaceholderScreen("DM #${d.id}")
                    Dest.Search -> PlaceholderScreen("Search")
                    Dest.Find -> PlaceholderScreen("Find forums")
                }
            }
        }
    }

    // Person card sheet
    router.personID?.let { pid ->
        // PersonCardView built in Phase 7; placeholder dismiss for now.
        PersonCardSheetPlaceholder(pid) { router.personID = null }
    }
}

@Composable
private fun TopBar(session: SessionStore, state: SessionStore.State, showBack: Boolean, onBack: () -> Unit) {
    val c = DcampTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.bg).statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = c.ink,
                modifier = Modifier.size(26.dp).clickable { onBack() })
        }
        Text("dcamp", fontSize = 18.sp, fontWeight = FontWeight.Black, color = c.ink)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = c.inkSoft,
            modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Avatar(person = state.me, size = 28.dp)
    }
}

@Composable
private fun ForumsHomeView(session: SessionStore, state: SessionStore.State) {
    val c = DcampTheme.colors
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 240.dp),
        contentPadding = PaddingValues(18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().background(c.bg),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(T("Forums"), fontSize = 32.sp, fontWeight = FontWeight.Black, color = c.ink)
                Text("${T("Forums")}: ${state.boards.size}", fontSize = 15.sp, color = c.muted)
            }
        }
        items(state.boards, key = { it.id }) { board ->
            ForumCard(board)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            DCSectionLabel(T("Other"), Modifier.padding(top = 6.dp))
        }
        item {
            OtherCard(Icons.AutoMirrored.Filled.Chat, c.accent, T("Chat Room"),
                T("Live community chat for Decent espresso owners."), badge = state.chatUnread)
        }
        item {
            OtherCard(Icons.Filled.Email, Color(0xFF8C5CDB), T("Direct Messages"), T("Private conversations."))
        }
        if (state.showRegions || state.showRoasters) {
            item {
                OtherCard(Icons.Filled.Map, Color(0xFF3399DB), T("Find forums"),
                    T("Discover regional & roaster forums."))
            }
        }
    }
}

private fun forumIcon(board: Board): ImageVector = when (board.slug) {
    "general-forum" -> Icons.Filled.Forum
    "most-popular-topics" -> Icons.Filled.LocalFireDepartment
    "problems" -> Icons.Filled.Build
    "programmer-s-forum" -> Icons.Filled.Code
    else -> Icons.Filled.QuestionAnswer
}

@Composable
private fun ForumCard(board: Board) {
    val c = DcampTheme.colors
    val router = LocalRouter.current
    val tint = hexColor(board.color) ?: c.accent
    Column(
        Modifier.fillMaxWidth().heightIn(min = 180.dp).dcCard()
            .clickable { router.push(Dest.Board(board.id)) }.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(tint), contentAlignment = Alignment.Center) {
            Icon(forumIcon(board), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(T(board.name), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.ink)
            val ns = board.newSince ?: 0
            if (ns > 0) {
                Text("$ns", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.background(c.accent, CircleShape).padding(horizontal = 6.dp, vertical = 1.dp))
            }
        }
        board.description?.takeIf { it.isNotEmpty() }?.let {
            Text(T(it), fontSize = 15.sp, color = c.inkSoft)
        }
        board.messageCount?.let {
            Text("${T("Messages")}: $it", fontSize = 14.sp, color = c.muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun OtherCard(icon: ImageVector, tint: Color, title: String, subtitle: String, badge: Int = 0) {
    val c = DcampTheme.colors
    val router = LocalRouter.current
    val dest = when (title) {
        T("Chat Room") -> Dest.Chat
        T("Direct Messages") -> Dest.Dms
        else -> Dest.Find
    }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 120.dp).dcCard().clickable { router.push(dest) }.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.weight(1f))
            if (badge > 0) {
                Text("$badge", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.background(Color(0xFFDC2626), CircleShape).padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = c.ink)
        Text(subtitle, fontSize = 15.sp, color = c.inkSoft)
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    val c = DcampTheme.colors
    Box(Modifier.fillMaxSize().background(c.bg), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink)
            Text("Coming in a later build phase", fontSize = 14.sp, color = c.muted)
        }
    }
}

@Composable
private fun PersonCardSheetPlaceholder(personID: Int, onDismiss: () -> Unit) {
    // Real PersonCardView arrives in Phase 7.
}
