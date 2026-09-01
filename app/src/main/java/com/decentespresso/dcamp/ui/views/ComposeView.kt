package com.decentespresso.dcamp.ui.views

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.decentespresso.dcamp.api.AppConfig
import com.decentespresso.dcamp.api.Board
import com.decentespresso.dcamp.api.ChatLine
import com.decentespresso.dcamp.api.Comment
import com.decentespresso.dcamp.api.MessageDetail
import com.decentespresso.dcamp.api.PlainText
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.api.SessionStore
import com.decentespresso.dcamp.api.chatUpdate
import com.decentespresso.dcamp.api.commentUpdate
import com.decentespresso.dcamp.api.messageUpdate
import com.decentespresso.dcamp.api.preAnswer
import com.decentespresso.dcamp.api.subjectCheck
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.theme.DCPrimaryButton
import com.decentespresso.dcamp.ui.theme.DcampTheme
import com.decentespresso.dcamp.ui.trix.TrixContentView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class ComposeMode {
    data class NewThread(val board: Board) : ComposeMode()
    data class CommentOn(val messageID: Int) : ComposeMode()
    data class EditThread(val message: MessageDetail) : ComposeMode()
    data class EditComment(val comment: Comment) : ComposeMode()
    data class EditChat(val line: ChatLine) : ComposeMode()
}

/** Full-screen composer: new thread (subject + category), reply comment, or edit. Hosts the
 *  bundled Trix editor; new-thread posts run AI pre-checks (clearer subject, Derek pre-answer). */
@Composable
fun ComposeView(mode: ComposeMode, session: SessionStore.State, onPosted: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = DcampTheme.colors
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val model = remember { ComposerModel() }
    var subject by remember { mutableStateOf(if (mode is ComposeMode.EditThread) mode.message.displaySubject else "") }
    var categoryID by remember { mutableIntStateOf(0) }
    var posting by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var catMenu by remember { mutableStateOf(false) }
    var precheckPassed by remember { mutableStateOf(false) }
    var subjectSuggestion by remember { mutableStateOf<String?>(null) }
    var derekHtml by remember { mutableStateOf<String?>(null) }

    val isNewThread = mode is ComposeMode.NewThread || mode is ComposeMode.EditThread
    val boardPid = when (mode) {
        is ComposeMode.NewThread -> mode.board.id
        is ComposeMode.EditThread -> mode.message.projectId ?: 0
        else -> 0
    }
    val categories = session.categories.filter { (it.projectId ?: 0) == 0 || it.projectId == boardPid }
    val title = when (mode) {
        is ComposeMode.NewThread -> mode.board.name
        is ComposeMode.CommentOn -> T("Reply")
        is ComposeMode.EditThread -> T("Edit")
        is ComposeMode.EditComment -> T("Edit comment")
        is ComposeMode.EditChat -> T("Edit message")
    }
    val editHtml = when (mode) {
        is ComposeMode.EditThread -> mode.message.html
        is ComposeMode.EditComment -> mode.comment.html
        is ComposeMode.EditChat -> mode.line.html
        else -> null
    }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            uploading = true
            scope.launch {
                for (uri in uris) {
                    val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: continue
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val name = uriDisplayName(context, uri) ?: "file"
                    val result = runCatching { Services.api.uploadImage(bytes, name, mime) }
                    val url = result.getOrNull()
                    if (url == null) {
                        val msg = (result.exceptionOrNull() as? com.decentespresso.dcamp.api.ApiException)?.messageText ?: "Upload failed"
                        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        continue
                    }
                    val full = AppConfig.assetBase + url
                    if (mime.startsWith("image/")) model.insertImage(full) else model.insertFile(full, name, mime)
                }
                uploading = false
            }
        }
    }

    LaunchedEffect(model) {
        repeat(30) { if (model.ready) return@repeat; delay(120) }
        model.draftKey = when (mode) {
            is ComposeMode.NewThread -> "new:${mode.board.id}"
            is ComposeMode.CommentOn -> "comment:${mode.messageID}"
            else -> null
        }
        if (editHtml != null) model.loadHTML(editHtml) else model.loadDraft()
        model.focus()
    }
    // Toolbar "Attach image" button (in the editor) → same native photo picker.
    LaunchedEffect(model) { androidx.compose.runtime.snapshotFlow { model.attachRequested }.collect { if (it > 0) photoPicker.launch("*/*") } }

    fun doPost() {
        posting = true
        scope.launch {
            try {
                val html = model.currentHTML().replace(AppConfig.assetBase, "")
                if (PlainText.strip(html).isEmpty()) { error = T("Write something before posting."); posting = false; return@launch }
                when (mode) {
                    is ComposeMode.NewThread -> {
                        val r = Services.api.createMessage(mode.board.id, categoryID, subject.trim(), html)
                        val id = r.id ?: throw Exception(r.error ?: "Failed"); model.clearDraft(); onPosted(id); onDismiss()
                    }
                    is ComposeMode.CommentOn -> {
                        val r = Services.api.createComment(mode.messageID, html)
                        val id = r.id ?: throw Exception(r.error ?: "Failed"); model.clearDraft(); onPosted(id); onDismiss()
                    }
                    is ComposeMode.EditThread -> { Services.api.messageUpdate(mode.message.id, mode.message.categoryId ?: 0, subject.trim(), html); onPosted(mode.message.id); onDismiss() }
                    is ComposeMode.EditComment -> { Services.api.commentUpdate(mode.comment.id, html); onPosted(mode.comment.id); onDismiss() }
                    is ComposeMode.EditChat -> { Services.api.chatUpdate(mode.line.id, html); onPosted(mode.line.id); onDismiss() }
                }
            } catch (e: Exception) { error = e.message } finally { posting = false }
        }
    }

    fun post() {
        if (!isNewThread || precheckPassed) { doPost(); return }
        posting = true
        scope.launch {
            val html = model.currentHTML().replace(AppConfig.assetBase, "")
            val subj = subject.trim()
            if (PlainText.strip(html).isEmpty()) { error = T("Write something before posting."); posting = false; return@launch }
            val sc = Services.api.subjectCheck(subj, html)
            if (!sc.isGood && !sc.suggestion.isNullOrEmpty()) { subjectSuggestion = sc.suggestion; posting = false; return@launch }
            val question = (mode is ComposeMode.NewThread && mode.board.name == "Problems") || subj.contains("?") || PlainText.strip(html).contains("?")
            if (question) {
                val pa = Services.api.preAnswer(subj, html)
                if (pa.has && !pa.answer.isNullOrEmpty()) { derekHtml = pa.answer; posting = false; return@launch }
            }
            precheckPassed = true; doPost()
        }
    }

    val canPost = !posting && (!isNewThread || subject.trim().isNotEmpty())

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.bg)) {
            // Top bar
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(T("Cancel"), color = c.inkSoft, fontSize = 15.sp, modifier = Modifier.clickable { onDismiss() })
                Spacer(Modifier.weight(1f))
                Text(title, color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (posting) CircularProgressIndicator(strokeWidth = 2.dp, color = c.accent, modifier = Modifier.size(18.dp))
                else Text(T("Post"), color = if (canPost) c.accent else c.muted, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(enabled = canPost) { post() })
            }
            Divider(color = c.line)

            if (isNewThread) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                    if (subject.isEmpty()) Text(T("Subject"), color = c.muted, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    BasicTextField(value = subject, onValueChange = { subject = it }, singleLine = true,
                        textStyle = TextStyle(color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
                }
                if (categories.isNotEmpty()) {
                    Divider(color = c.line)
                    Box(Modifier.fillMaxWidth().clickable { catMenu = true }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        val catName = categories.firstOrNull { it.id == categoryID }?.name ?: T("No category")
                        Text("${T("Category")}: $catName ▾", color = c.inkSoft, fontSize = 14.sp)
                        DropdownMenu(expanded = catMenu, onDismissRequest = { catMenu = false }) {
                            DropdownMenuItem(text = { Text(T("No category")) }, onClick = { categoryID = 0; catMenu = false })
                            categories.forEach { cat -> DropdownMenuItem(text = { Text(cat.name) }, onClick = { categoryID = cat.id; catMenu = false }) }
                        }
                    }
                }
                Divider(color = c.line)
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                ComposerWebView(model, Modifier.fillMaxSize().background(c.panel))
                MentionOverlay(model, Modifier.align(Alignment.TopStart).padding(top = 8.dp, start = 8.dp, end = 8.dp))
                if (uploading) {
                    Row(Modifier.align(Alignment.BottomCenter).padding(12.dp).background(c.panel, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(strokeWidth = 2.dp, color = c.accent, modifier = Modifier.size(14.dp))
                        Text(T("Uploading image…"), fontSize = 13.sp, color = c.inkSoft)
                    }
                }
            }
        }
    }

    // AI pre-post dialogs
    subjectSuggestion?.let { sug ->
        Dialog(onDismissRequest = { subjectSuggestion = null }) {
            Column(Modifier.fillMaxWidth(0.92f).background(c.bg, RoundedCornerShape(16.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(T("Clearer subject?"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Text(T("A clearer subject helps people find and answer your post."), fontSize = 14.sp, color = c.inkSoft)
                Text(T("YOUR SUBJECT"), fontSize = 11.sp, color = c.muted); Text(subject, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                Text(T("SUGGESTED"), fontSize = 11.sp, color = c.accentInk); Text(sug, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.ink)
                DCPrimaryButton(onClick = { subject = sug; subjectSuggestion = null; precheckPassed = true; doPost() }, modifier = Modifier.fillMaxWidth()) {
                    Text(T("Use suggested subject"), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text(T("Keep mine"), color = c.inkSoft, fontSize = 14.sp, modifier = Modifier.clickable { subjectSuggestion = null; precheckPassed = true; doPost() }.padding(8.dp))
            }
        }
    }
    derekHtml?.let { html ->
        Dialog(onDismissRequest = { derekHtml = null }) {
            Column(Modifier.fillMaxWidth(0.92f).background(c.bg, RoundedCornerShape(16.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("✨ ${T("Derek may already have an answer")}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
                TrixContentView(html)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(T("Let me reconsider"), color = c.inkSoft, fontSize = 14.sp, modifier = Modifier.weight(1f).clickable { derekHtml = null }.padding(8.dp))
                    DCPrimaryButton(onClick = { derekHtml = null; precheckPassed = true; doPost() }, modifier = Modifier.weight(1f)) {
                        Text(T("Post anyway"), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    error?.let { msg ->
        Dialog(onDismissRequest = { error = null }) {
            Column(Modifier.fillMaxWidth(0.9f).background(c.bg, RoundedCornerShape(16.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(T("Couldn’t post"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.ink)
                Text(msg, fontSize = 14.sp, color = c.inkSoft)
                Text(T("OK"), color = c.accent, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End).clickable { error = null }.padding(8.dp))
            }
        }
    }
}
