package com.decentespresso.dcamp.ui.views

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.ui.theme.DcampColors
import com.decentespresso.dcamp.ui.theme.hex6
import androidx.compose.ui.viewinterop.AndroidView
import com.decentespresso.dcamp.api.Person
import com.decentespresso.dcamp.api.PlainText
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.theme.Avatar
import com.decentespresso.dcamp.ui.theme.MachineBadge
import com.decentespresso.dcamp.ui.theme.DcampTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Bridges the bundled Trix editor (assets/editor/editor.html) into Compose, mirroring
 * the iOS ComposerModel + ComposerWebView. Keeps html/contentHeight/mentionQuery current
 * via the JS "composer" bridge; posting reads dcampGetHTML() on demand (never gated on a
 * change event — the "Send does nothing" iOS bug).
 */
/** Writing area of an inline composer: at least 3 lines of text (+ editor padding). */
private const val MIN_TEXT_DP = 96

class ComposerModel {
    var webView: WebView? = null
    var ready by mutableStateOf(false)
    var contentHeight by mutableStateOf(0)
    var toolbarHeight by mutableStateOf(44)     // formatting toolbar; wraps to 2+ rows on a phone
    var focused by mutableStateOf(false)        // the editor WebView has input focus
    var growForModal by mutableStateOf(false)   // a toolbar modal (table) is open → give it room
    var mentionQuery by mutableStateOf<String?>(null)
    var submitRequested by mutableStateOf(0)
    var attachRequested by mutableStateOf(0)   // toolbar "Attach image" tapped → host opens the picker
    @Volatile var draftKey: String? = null   // per-context draft persistence (read on the JS bridge thread)
    private var lastHtml = ""
    // False until loadDraft() has run for the current page: trix-initialize posts an EMPTY
    // "change" right after "ready", and saving that would wipe the stored draft before it's restored.
    @Volatile private var draftRestored = false
    // Key the page itself pulled its draft for at trix-initialize (see pullDraft), so loadDraft()
    // doesn't push it a second time.
    @Volatile private var pulledKey: String? = null

    /** JS bridge thread: the editor asks for its draft once Trix has initialised and loads it
     *  itself before reporting "ready". A push via evaluateJavascript on "ready" sometimes never
     *  landed (editor empty while the pref still held the draft). */
    fun pullDraft(): String {
        val k = draftKey ?: return ""
        pulledKey = k; draftRestored = true
        val d = com.decentespresso.dcamp.api.AppConfig.getDraft(k)
        return if (PlainText.strip(d).isNotEmpty()) d else ""
    }
    private var pendingQuote: String? = null   // quote requested before the editor was ready

    /** Restore this context's saved draft into the editor (call once ready). */
    fun loadDraft() {
        draftRestored = true
        val k = draftKey ?: return
        if (pulledKey == k) return      // the page already loaded it (pullDraft)
        val d = com.decentespresso.dcamp.api.AppConfig.getDraft(k)
        if (d.isNotEmpty() && PlainText.strip(d).isNotEmpty()) loadHTML(d)
    }
    /** Drop the saved draft (call after a successful post/send). */
    fun clearDraft() { draftKey?.let { com.decentespresso.dcamp.api.AppConfig.clearDraft(it) } }

    private fun eval(js: String) { webView?.post { webView?.evaluateJavascript(js, null) } }
    private fun jsStr(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    fun setPlaceholder(text: String) = eval("dcampSetPlaceholder(${jsStr(text)})")
    fun setTheme(c: DcampColors) = eval("dcampSetTheme({dark:${c.dark},ink:'#${c.ink.hex6()}',soft:'#${c.inkSoft.hex6()}'," +
        "panel:'#${c.panel.hex6()}',line:'#${c.lineStrong.hex6()}',link:'#${c.link.hex6()}',accent:'#${c.accent.hex6()}'})")
    fun focus() = eval("dcampFocus()")
    fun insertMention(id: Int, name: String) { eval("dcampInsertMention($id, ${jsStr(name)})"); mentionQuery = null }
    fun insertImage(url: String) = eval("dcampInsertImage(${jsStr(url)})")
    fun insertFile(url: String, name: String, mime: String) = eval("dcampInsertFile(${jsStr(url)}, ${jsStr(name)}, ${jsStr(mime)})")
    /** Insert a blockquote. If the editor isn't composed/ready yet (its lazy item is
     *  off-screen), buffer it and apply once the editor reports ready. */
    fun insertQuote(html: String) {
        if (webView != null && ready) eval("dcampInsertQuote(${jsStr(html)})")
        else pendingQuote = html
    }
    /** Apply a quote buffered while the editor was loading — called after loadDraft so the
     *  quote appends to (rather than being clobbered by) a restored draft. */
    fun drainPendingQuote() { pendingQuote?.let { val h = it; pendingQuote = null; eval("dcampInsertQuote(${jsStr(h)})") } }
    fun resetPage() { pulledKey = null; draftRestored = false }
    fun loadHTML(html: String) { eval("dcampLoadHTML(${jsStr(html)})"); lastHtml = html }
    fun clear() { eval("dcampClear()"); lastHtml = "" }

    /** Read the editor's current HTML directly (Trix keeps it in sync), so posting never
     *  depends on the change-event plumbing. */
    suspend fun currentHTML(): String {
        val web = webView ?: return lastHtml
        val deferred = CompletableDeferred<String>()
        web.post {
            web.evaluateJavascript("dcampGetHTML()") { result ->
                // evaluateJavascript returns the value JSON-encoded (a quoted string).
                val decoded = runCatching { Json.decodeFromString<String>(result ?: "\"\"") }.getOrDefault(lastHtml)
                deferred.complete(decoded)
            }
        }
        return deferred.await()
    }

    // Called from the JS bridge (background thread) → marshal to main.
    fun onBridge(json: String) {
        val obj = runCatching { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return
        val type = obj["type"]?.jsonPrimitive?.contentOrNull
        webView?.post {
            when (type) {
                "ready" -> { if (pulledKey == null) draftRestored = false; ready = true }
                "change" -> {
                    lastHtml = obj["html"]?.jsonPrimitive?.contentOrNull ?: ""
                    obj["height"]?.jsonPrimitive?.intOrNull?.let { if (it > 0) contentHeight = it }
                    obj["toolbar"]?.jsonPrimitive?.intOrNull?.let { if (it > 0) toolbarHeight = it }
                    if (draftRestored) draftKey?.let { k ->
                        if (PlainText.strip(lastHtml).isEmpty()) com.decentespresso.dcamp.api.AppConfig.clearDraft(k)
                        else com.decentespresso.dcamp.api.AppConfig.setDraft(k, lastHtml)
                    }
                }
                "mention" -> mentionQuery = obj["q"]?.jsonPrimitive?.contentOrNull
                "grow" -> growForModal = (obj["on"]?.jsonPrimitive?.intOrNull ?: 0) != 0
                "attach" -> attachRequested++
                "submit" -> submitRequested++
                "urltitle" -> {
                    val url = obj["url"]?.jsonPrimitive?.contentOrNull
                    if (url != null) webView?.post {
                        // fire-and-forget title swap
                    }
                }
            }
        }
    }
}

private class ComposerBridge(val model: ComposerModel) {
    @JavascriptInterface fun post(json: String) { model.onBridge(json) }
    @JavascriptInterface fun draft(): String = model.pullDraft()
}

/** The picked file's display name (for the upload filename + attachment label). */
internal fun uriDisplayName(context: android.content.Context, uri: android.net.Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

/**
 * Inline rich-text composer (embedded Trix + Send), matching the web/iOS chat/DM/thread
 * composers. [onSend] receives the HTML and returns whether it succeeded (text is kept on
 * failure). Read-only sessions never see this (callers gate on canPost).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InlineComposer(placeholder: String, model: ComposerModel, draftKey: String? = null,
                   title: String? = null, onSend: suspend (String) -> Boolean) {
    val c = DcampTheme.colors
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var sending by remember { mutableStateOf(false) }

    // "Attach" (toolbar) → native file picker (any type) → upload → insert: images inline,
    // other files as a download link.
    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            for (uri in uris) {
                val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: continue
                val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val name = uriDisplayName(context, uri) ?: "file"
                val result = runCatching { com.decentespresso.dcamp.api.Services.api.uploadImage(bytes, name, mime) }
                val url = result.getOrNull()
                if (url == null) {
                    val msg = (result.exceptionOrNull() as? com.decentespresso.dcamp.api.ApiException)?.messageText ?: "Upload failed"
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                    continue
                }
                val full = com.decentespresso.dcamp.api.AppConfig.assetBase + url
                if (mime.startsWith("image/")) model.insertImage(full) else model.insertFile(full, name, mime)
            }
        }
    }
    // While a toolbar modal (e.g. Insert table) is open, grow the composer so the modal —
    // a fixed overlay inside the WebView — isn't clipped by a short inline composer.
    // Cap the editor by the room actually left above the keyboard: on the DM screen (extra header row)
    // a full 280dp editor + Send didn't fit and the Send button was clipped under the keyboard. The
    // editor scrolls internally, so a shorter frame loses nothing. ~200dp = top bar + screen header +
    // Send row + paddings.
    val imeBottomDp = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp().value }
    val roomDp = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - imeBottomDp - 200
    val maxEditor = roomDp.toInt().coerceIn(120, 360)
    val composerHeight = if (model.growForModal) 360.coerceAtMost(maxOf(maxEditor, 200)) else (maxOf(model.contentHeight, MIN_TEXT_DP) + model.toolbarHeight)
        // the room cap wins (keyboard open), but never below 3 lines of text above the toolbar
        .coerceIn(model.toolbarHeight + MIN_TEXT_DP, maxOf(minOf(280, maxEditor), model.toolbarHeight + MIN_TEXT_DP))

    fun send() {
        if (sending) return
        sending = true
        scope.launch {
            val html = model.currentHTML()
            if (PlainText.strip(html).isNotEmpty()) {
                if (onSend(html)) { model.clear(); model.clearDraft() }
            }
            sending = false
        }
    }

    // Apply placeholder + restore draft each time the editor reports ready — including after the
    // lazy item is disposed and its WebView recreated (ready flips false→true again).
    LaunchedEffect(model) {
        model.draftKey = draftKey
        snapshotFlow { model.ready }.collect { r -> if (r) { model.setPlaceholder(placeholder); model.loadDraft(); model.drainPendingQuote() } }
    }
    LaunchedEffect(model) { snapshotFlow { model.submitRequested }.collect { if (it > 0) send() } }
    LaunchedEffect(model) { snapshotFlow { model.attachRequested }.collect { if (it > 0) filePicker.launch("*/*") } }
    // Keep the composer above the keyboard: when it's focused, and each time the IME animates
    // or the editor grows, scroll the enclosing list (thread comments) so its bottom is visible.
    val bringIntoView = remember { BringIntoViewRequester() }
    // Opening the keyboard shrinks the list; if this lazy item scrolls out of the viewport the
    // list disposes it, its WebView loses focus and the keyboard closes again. The WebView's
    // View focus isn't Compose focus, so pin the item ourselves while it's focused.
    val pinnable = LocalPinnableContainer.current
    DisposableEffect(model.focused, pinnable) {
        val handle = if (model.focused) pinnable?.pin() else null
        onDispose { handle?.release() }
    }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(model) {
        snapshotFlow { Triple(model.focused, ime.getBottom(density), model.contentHeight + if (model.growForModal) 1 else 0) }
            .collectLatest { (focused, imeBottom, _) ->
                // also when a toolbar dialog/modal grows the composer (it may be the last list item)
                if ((focused && imeBottom > 0) || model.growForModal) { delay(60); bringIntoView.bringIntoView() }
            }
    }

    Column(Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView).background(c.bg).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val sendButton = @Composable {
            Text(T("Send"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(if (sending) c.accent.copy(alpha = 0.5f) else c.accent, RoundedCornerShape(10.dp))
                    .clickable(enabled = !sending) { send() }.padding(horizontal = 16.dp, vertical = 10.dp))
        }
        // With a title ("Add a comment") Send sits on the title's line, freeing a row below the
        // composer for text (the wrapped 44px toolbar is tall on a phone).
        if (title != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink, modifier = Modifier.weight(1f))
                sendButton()
            }
        }
        Box {
            ComposerWebView(model, Modifier.fillMaxWidth().height(composerHeight.dp)
                .background(c.panel, RoundedCornerShape(12.dp))
                .border(1.dp, c.lineStrong, RoundedCornerShape(12.dp)))
            MentionOverlay(model, Modifier.align(Alignment.TopStart).padding(top = 52.dp, start = 8.dp, end = 8.dp))
        }
        if (title == null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { sendButton() }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ComposerWebView(model: ComposerModel, modifier: Modifier = Modifier) {
    // The page can't detect our theme itself (the window theme is light, so the WebView reports
    // light even while Compose draws dark): hand it the palette on load and on every change.
    val c = DcampTheme.colors
    LaunchedEffect(model, c) { snapshotFlow { model.ready }.collect { if (it) model.setTheme(c) } }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                // AndroidView defaults to WRAP_CONTENT, which makes Chromium size its layout
                // viewport to the content: html/body height:100% resolve to 0, the editor's flex
                // form collapses and typed text spills under the toolbar. Fill the given frame.
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                addJavascriptInterface(ComposerBridge(model), "AndroidComposer")
                setOnFocusChangeListener { _, hasFocus -> model.focused = hasFocus }
                model.ready = false            // fresh page: JS bridge not loaded yet
                model.resetPage()
                model.webView = this
                loadUrl("file:///android_asset/editor/editor.html?theme=${if (c.dark) "dark" else "light"}" +
                    "&ink=${c.ink.hex6()}&soft=${c.inkSoft.hex6()}&panel=${c.panel.hex6()}" +
                    "&line=${c.lineStrong.hex6()}&link=${c.link.hex6()}&accent=${c.accent.hex6()}")
            }
        },
        // When this lazy item scrolls off-screen it's disposed; drop the stale WebView so a
        // later insertQuote/loadHTML buffers instead of evaluating into a dead page.
        onRelease = { web -> if (model.webView === web) { model.webView = null; model.ready = false; model.focused = false } },
    )
}

/** Native @mention suggestion list shown over the composer while typing "@query". */
@Composable
internal fun MentionOverlay(model: ComposerModel, modifier: Modifier = Modifier) {
    val c = DcampTheme.colors
    var results by remember { mutableStateOf<List<Person>>(emptyList()) }

    LaunchedEffect(model.mentionQuery) {
        val q = model.mentionQuery
        if (q.isNullOrEmpty()) { results = emptyList(); return@LaunchedEffect }
        delay(200)
        results = runCatching { Services.api.mentionSearch(q) }.getOrDefault(emptyList())
    }

    if (model.mentionQuery != null && results.isNotEmpty()) {
        Column(modifier.fillMaxWidth().background(c.panel, RoundedCornerShape(10.dp))
            .border(1.dp, c.lineStrong, RoundedCornerShape(10.dp))) {
            results.take(6).forEach { p ->
                Row(Modifier.fillMaxWidth().clickable { model.insertMention(p.id, p.name) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Avatar(person = p, size = 24.dp)
                    Text(p.name, color = c.ink, fontSize = 14.sp, modifier = Modifier.heightIn())
                    Spacer(Modifier.weight(1f))
                    MachineBadge(p)
                }
            }
        }
    }
}
