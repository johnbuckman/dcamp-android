package com.decentespresso.dcamp.ui.views

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
class ComposerModel {
    var webView: WebView? = null
    var ready by mutableStateOf(false)
    var contentHeight by mutableStateOf(0)
    var mentionQuery by mutableStateOf<String?>(null)
    var submitRequested by mutableStateOf(0)
    var draftKey: String? = null   // per-context draft persistence
    private var lastHtml = ""

    /** Restore this context's saved draft into the editor (call once ready). */
    fun loadDraft() {
        val k = draftKey ?: return
        val d = com.decentespresso.dcamp.api.AppConfig.getDraft(k)
        if (d.isNotEmpty() && PlainText.strip(d).isNotEmpty()) loadHTML(d)
    }
    /** Drop the saved draft (call after a successful post/send). */
    fun clearDraft() { draftKey?.let { com.decentespresso.dcamp.api.AppConfig.clearDraft(it) } }

    private fun eval(js: String) { webView?.post { webView?.evaluateJavascript(js, null) } }
    private fun jsStr(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    fun setPlaceholder(text: String) = eval("dcampSetPlaceholder(${jsStr(text)})")
    fun focus() = eval("dcampFocus()")
    fun insertMention(id: Int, name: String) { eval("dcampInsertMention($id, ${jsStr(name)})"); mentionQuery = null }
    fun insertImage(url: String) = eval("dcampInsertImage(${jsStr(url)})")
    fun insertQuote(html: String) = eval("dcampInsertQuote(${jsStr(html)})")
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
                "ready" -> ready = true
                "change" -> {
                    lastHtml = obj["html"]?.jsonPrimitive?.contentOrNull ?: ""
                    obj["height"]?.jsonPrimitive?.intOrNull?.let { if (it > 0) contentHeight = it }
                    draftKey?.let { k ->
                        if (PlainText.strip(lastHtml).isEmpty()) com.decentespresso.dcamp.api.AppConfig.clearDraft(k)
                        else com.decentespresso.dcamp.api.AppConfig.setDraft(k, lastHtml)
                    }
                }
                "mention" -> mentionQuery = obj["q"]?.jsonPrimitive?.contentOrNull
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
}

/**
 * Inline rich-text composer (embedded Trix + Send), matching the web/iOS chat/DM/thread
 * composers. [onSend] receives the HTML and returns whether it succeeded (text is kept on
 * failure). Read-only sessions never see this (callers gate on canPost).
 */
@Composable
fun InlineComposer(placeholder: String, model: ComposerModel, draftKey: String? = null, onSend: suspend (String) -> Boolean) {
    val c = DcampTheme.colors
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    val composerHeight = (model.contentHeight + 44).coerceIn(120, 280)

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

    // Apply placeholder once the editor is ready; restore any draft; observe ⌘↵ submit.
    LaunchedEffect(model) {
        repeat(40) {
            if (model.ready) { model.setPlaceholder(placeholder); model.draftKey = draftKey; model.loadDraft(); return@LaunchedEffect }
            delay(150)
        }
    }
    LaunchedEffect(model) { snapshotFlow { model.submitRequested }.collect { if (it > 0) send() } }

    Column(Modifier.fillMaxWidth().background(c.bg).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box {
            ComposerWebView(model, Modifier.fillMaxWidth().height(composerHeight.dp)
                .background(c.panel, RoundedCornerShape(12.dp))
                .border(1.dp, c.lineStrong, RoundedCornerShape(12.dp)))
            MentionOverlay(model, Modifier.align(Alignment.TopStart).padding(top = 52.dp, start = 8.dp, end = 8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(T("Send"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.background(if (sending) c.accent.copy(alpha = 0.5f) else c.accent, RoundedCornerShape(10.dp))
                    .clickable(enabled = !sending) { send() }.padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ComposerWebView(model: ComposerModel, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addJavascriptInterface(ComposerBridge(model), "AndroidComposer")
                model.webView = this
                loadUrl("file:///android_asset/editor/editor.html")
            }
        },
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
