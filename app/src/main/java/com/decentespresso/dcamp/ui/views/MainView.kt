package com.decentespresso.dcamp.ui.views

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.api.Board
import com.decentespresso.dcamp.api.Services
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
    val forumFilter = remember { com.decentespresso.dcamp.ui.ForumFilterStore() }
    val summaryPin = remember { SummaryPin() }
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val online by com.decentespresso.dcamp.api.Services.connectivity.isOnline.collectAsState()
    // Reconnecting after an offline launch (or a dropped connection that left us with no data)
    // reloads the bootstrap so the forum fills in behind the dismissing offline panel.
    androidx.compose.runtime.LaunchedEffect(online) { if (online && state.boards.isEmpty()) session.refresh() }
    val top = router.backStack.lastOrNull()

    BackHandler(enabled = router.backStack.isNotEmpty()) { router.pop() }

    // Foreground notification toast: poll for new notifications and surface the newest.
    var toast by remember { androidx.compose.runtime.mutableStateOf<com.decentespresso.dcamp.api.Notif?>(null) }
    var lastNotifMax by remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            val items = runCatching { Services.api.notifPoll() }.getOrDefault(emptyList())
            val newest = items.maxByOrNull { it.id }
            // This loop keeps running after the app is backgrounded: post to the phone then,
            // or the items would be acked below without the user ever seeing them.
            val background = !com.decentespresso.dcamp.push.PhoneNotifications.inForeground
            if (background && lastNotifMax >= 0) {
                items.filter { it.id > lastNotifMax }.forEach { com.decentespresso.dcamp.push.PhoneNotifications.post(appContext, it) }
            } else if (newest != null && lastNotifMax >= 0 && newest.id > lastNotifMax) {
                toast = newest
                val id = newest.id
                launch { kotlinx.coroutines.delay(4000); if (toast?.id == id) toast = null }
            }
            if (newest != null) lastNotifMax = maxOf(lastNotifMax, newest.id)
            else if (lastNotifMax < 0) lastNotifMax = 0
            // Ack like web/iOS: notif_poll returns the OLDEST 25 unseen, so never acking
            // stalled the toast (and would replay them as phone notifications later).
            if (items.isNotEmpty()) Services.api.notifSeen(items.map { it.id })
            kotlinx.coroutines.delay(30000)
        }
    }

    // A tapped phone notification opens its thread / DM.
    val pendingRoute by com.decentespresso.dcamp.push.PhoneNotifications.pendingRoute.collectAsState()
    androidx.compose.runtime.LaunchedEffect(pendingRoute) {
        pendingRoute?.let { r ->
            com.decentespresso.dcamp.push.PhoneNotifications.pendingRoute.value = null
            router.open(r)
        }
    }

    NotificationPermissionPrompt()

    androidx.compose.runtime.CompositionLocalProvider(LocalRouter provides router,
        com.decentespresso.dcamp.ui.LocalForumFilter provides forumFilter,
        LocalSummaryPin provides summaryPin) {
        // Edge-to-edge is enforced on Android 15+, so the manifest's adjustResize no longer
        // shrinks the window for the keyboard: pad for the nav bar + IME ourselves, or the
        // keyboard covers the chat/DM/comment composers.
        Box(Modifier.fillMaxSize().background(c.bg).navigationBarsPadding().imePadding()) {
        Column(Modifier.fillMaxSize().background(c.bg)) {
            TopBar(session, state, showBack = router.backStack.isNotEmpty(), onBack = { router.pop() })
            toast?.let { n ->
                NotifToast(n) {
                    toast = null
                    com.decentespresso.dcamp.api.DcampRoute.messageID(n.route)?.let { router.push(Dest.Thread(it)) }
                        ?: com.decentespresso.dcamp.api.DcampRoute.dmID(n.route)?.let { router.push(Dest.Dm(it)) }
                }
            }
            Row(Modifier.fillMaxSize()) {
                if (wide && summaryPin.visible) {
                    Box(Modifier.width(340.dp).fillMaxSize()) { SummarySidebarView(summaryPin) }
                    androidx.compose.material3.Divider(Modifier.fillMaxHeight().width(1.dp), color = c.line)
                }
            Box(Modifier.weight(1f).fillMaxSize()) {
                // One composition per stack entry, each with its own retained state, so a list
                // under an open thread comes back with its rows and scroll position.
                val depth = router.backStack.size
                androidx.compose.runtime.key(depth, top) {
                androidx.compose.runtime.CompositionLocalProvider(com.decentespresso.dcamp.ui.LocalRetained provides router.retainedAt(depth)) {
                when (val d = top) {
                    null -> ForumsHomeView(session, state)
                    is Dest.Board -> {
                        val board = state.boards.firstOrNull { it.id == d.id }
                        if (board != null) ThreadListView(board, state) else PlaceholderScreen("Forum #${d.id}")
                    }
                    is Dest.Thread -> ThreadDetailView(d.id, state)
                    Dest.Chat -> ChatView(state)
                    Dest.Dms -> DMListView(state)
                    is Dest.Dm -> DMThreadView(d.id, state)
                    Dest.Search -> SearchView()
                    Dest.Find -> PlaceholderScreen("Find forums")
                    Dest.Notifications -> NotificationsView(state)
                    Dest.Settings -> SettingsView(session, state)
                    Dest.EmailSummaries -> EmailSummariesView(state)
                    Dest.Help -> HelpView()
                }
                }
                }
            }
            }
        }
        if (!online) OfflineView(onRetry = {
            com.decentespresso.dcamp.api.Services.connectivity.recheck()
            scope.launch { session.refresh() }
        })
        UpdateGate()
        }
    }

    // Person card sheet
    router.personID?.let { pid ->
        androidx.compose.runtime.CompositionLocalProvider(LocalRouter provides router) {
            PersonCardView(pid) { router.personID = null }
        }
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
        val router = LocalRouter.current
        Icon(Icons.Filled.Search, contentDescription = "Search", tint = c.inkSoft,
            modifier = Modifier.size(24.dp).clickable { router.push(Dest.Search) })
        Spacer(Modifier.width(14.dp))
        Box {
            Icon(Icons.Filled.Notifications, contentDescription = "Notifications", tint = c.inkSoft,
                modifier = Modifier.size(24.dp).clickable { router.push(Dest.Notifications) })
            if (state.pingUnread > 0) {
                Text(if (state.pingUnread > 99) "99+" else "${state.pingUnread}", fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp)
                        .background(Color(0xFFDC2626), CircleShape).padding(horizontal = 4.dp, vertical = 1.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        // Account menu, like the web's picture menu: My settings, Check for update, About.
        var accountMenu by remember { androidx.compose.runtime.mutableStateOf(false) }
        var showAbout by remember { androidx.compose.runtime.mutableStateOf(false) }
        Box {
            Avatar(person = state.me, size = 28.dp, onClick = { accountMenu = true })
            androidx.compose.material3.DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text(T("My settings")) },
                    onClick = { accountMenu = false; router.push(Dest.Settings) })
                androidx.compose.material3.DropdownMenuItem(text = { Text(T("Check for update")) },
                    onClick = { accountMenu = false; com.decentespresso.dcamp.api.Updater.manualRequests.value++ })
                androidx.compose.material3.DropdownMenuItem(text = { Text(T("About")) },
                    onClick = { accountMenu = false; showAbout = true })
            }
        }
        if (showAbout) AboutDialog { showAbout = false }
    }
}

private const val SOURCE_URL = "https://github.com/johnbuckman/dcamp-android"

/** Avatar menu → About: app name, version, source link and a shortcut to Check for update. */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val c = DcampTheme.colors
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.panel,
        title = { Text(T("dcamp"), color = c.ink, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(T("Version {v} ({code})",
                    "v" to com.decentespresso.dcamp.BuildConfig.VERSION_NAME,
                    "code" to com.decentespresso.dcamp.BuildConfig.VERSION_CODE.toString()),
                    color = c.inkSoft, fontSize = 15.sp)
                Text(SOURCE_URL.removePrefix("https://"), color = c.link, fontSize = 15.sp,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier.clickable { runCatching { uriHandler.openUri(SOURCE_URL) } })
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(T("Close"), color = c.accent) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                onDismiss(); com.decentespresso.dcamp.api.Updater.manualRequests.value++
            }) { Text(T("Check for update"), color = c.inkSoft) }
        },
    )
}

@Composable
private fun ForumsHomeView(session: SessionStore, state: SessionStore.State) {
    val c = DcampTheme.colors
    DcampPull(onRefresh = { session.refresh() }) {
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
        item(span = { GridItemSpan(maxLineSpan) }) { SummarizeBar(SummaryKind.Home) }
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
}

fun forumIconPublic(board: Board): ImageVector = when (board.slug) {
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
            Icon(forumIconPublic(board), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
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

/** Foreground toast card for a newly-arrived notification (tap to open). */
@Composable
private fun NotifToast(notif: com.decentespresso.dcamp.api.Notif, onTap: () -> Unit) {
    val c = DcampTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            .background(c.panel, RoundedCornerShape(12.dp))
            .border(1.dp, c.line, RoundedCornerShape(12.dp)).clickable { onTap() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Avatar(person = notif.actor, size = 32.dp)
        Column(Modifier.weight(1f)) {
            Text(notif.actor?.name ?: "dcamp", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.ink, maxLines = 1)
            Text(notif.kindLabel, fontSize = 13.sp, color = c.inkSoft, maxLines = 1)
        }
        Text("›", color = c.muted, fontSize = 18.sp)
    }
}

/**
 * Ask once (Android 13+) for permission to post phone notifications, with a short explanation
 * first. Declining is remembered; Settings → Notifications has a way back in.
 */
@Composable
private fun NotificationPermissionPrompt() {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val c = DcampTheme.colors
    var show by remember {
        androidx.compose.runtime.mutableStateOf(
            !com.decentespresso.dcamp.push.PhoneNotifications.hasPermission(ctx) &&
                !com.decentespresso.dcamp.api.AppConfig.askedNotifPermission)
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    if (!show) return
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { show = false; com.decentespresso.dcamp.api.AppConfig.askedNotifPermission = true },
        containerColor = c.panel,
        title = { Text(T("Get notified?"), color = c.ink) },
        text = {
            Text(T("dcamp can notify you about direct messages, @mentions and replies, even when the app is closed. Choose which kinds in Settings."),
                color = c.inkSoft)
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                show = false
                com.decentespresso.dcamp.api.AppConfig.askedNotifPermission = true
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }) { Text(T("Continue"), color = c.accent) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = {
                show = false; com.decentespresso.dcamp.api.AppConfig.askedNotifPermission = true
            }) { Text(T("Not now"), color = c.inkSoft) }
        },
    )
}
