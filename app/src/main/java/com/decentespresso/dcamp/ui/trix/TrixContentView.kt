package com.decentespresso.dcamp.ui.trix

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.decentespresso.dcamp.api.AppConfig
import com.decentespresso.dcamp.api.DcampRoute
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.api.resolveMsg
import com.decentespresso.dcamp.ui.Dest
import com.decentespresso.dcamp.ui.LocalRouter
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.theme.DcampTheme
import kotlinx.coroutines.launch

/**
 * Renders parsed Trix HTML as native Compose. Internal dcamp routes are handled via the
 * Router (or [onRoute] override for in-place thread nav); external links open in a browser.
 * Ported from iOS TrixContentView.
 */
@Composable
fun TrixContentView(html: String, onRoute: ((String) -> Unit)? = null) {
    val blocks = remember(html) { TrixParser.parse(html) }
    val router = LocalRouter.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()

    val onLink: (String) -> Unit = handler@{ href ->
        if (!TrixInline.isInternal(href)) {
            runCatching { uriHandler.openUri(href) }
            return@handler
        }
        val token = TrixInline.routeToken(href)
        val pid = DcampRoute.personID(token)
        val cleanParts = DcampRoute.cleanMsgParts(token)
        when {
            pid != null -> router.personID = pid
            cleanParts != null -> scope.launch {
                val id = Services.api.resolveMsg(cleanParts.first, cleanParts.second)
                if (id != null) { if (onRoute != null) onRoute("/message/$id") else router.push(Dest.Thread(id)) }
            }
            onRoute != null -> onRoute(token)
            else -> router.open(token)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { TrixBlockView(it, onLink) }
    }
}

/**
 * Shows machine-translated content by default (like the web) with a "Show original" toggle.
 */
@Composable
fun TranslatableText(original: String, translated: String?, onRoute: ((String) -> Unit)? = null) {
    val hasTr = !translated.isNullOrEmpty() && translated != original
    var showOriginal by remember(original, translated) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TrixContentView(if (hasTr && !showOriginal) translated!! else original, onRoute)
        if (hasTr) {
            Text(
                if (showOriginal) T("Show translation") else T("Translated · Show original"),
                color = DcampTheme.colors.link,
                fontSize = 12.sp,
                modifier = Modifier.clickable { showOriginal = !showOriginal },
            )
        }
    }
}

@Composable
private fun TrixBlockView(block: TrixBlock, onLink: (String) -> Unit) {
    val c = DcampTheme.colors
    when (block) {
        is TrixBlock.Paragraph ->
            Text(TrixInline.parse(block.html, c, onLink), color = c.ink, fontSize = 15.sp, lineHeight = 22.sp)

        is TrixBlock.Heading -> {
            val size = when (block.level) { 1 -> 26.sp; 2 -> 22.sp; else -> 19.sp }
            Text(TrixInline.parse(block.html, c, onLink), color = c.ink, fontSize = size, fontWeight = FontWeight.Bold)
        }

        is TrixBlock.Quote ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(c.muted.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    block.children.forEach { TrixBlockView(it, onLink) }
                }
            }

        is TrixBlock.ListBlock ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.itemsHtml.forEachIndexed { idx, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (block.ordered) "${idx + 1}." else "•", color = c.muted, fontSize = 15.sp,
                            modifier = Modifier.width(20.dp))
                        Text(TrixInline.parse(item, c, onLink), color = c.ink, fontSize = 15.sp, lineHeight = 22.sp)
                    }
                }
            }

        is TrixBlock.Image -> ZoomableTrixImage(block.url)
        is TrixBlock.YouTube -> YouTubeCard(block.id)
        is TrixBlock.Table -> TrixTableView(block.rowsHtml, onLink)
        TrixBlock.Rule -> Divider(color = c.line)
    }
}

@Composable
private fun TrixTableView(rows: List<List<String>>, onLink: (String) -> Unit) {
    val c = DcampTheme.colors
    val cols = rows.maxOfOrNull { it.size } ?: 0
    Box(Modifier.horizontalScroll(rememberScrollState())) {
        Column(Modifier.border(1.dp, c.lineStrong, RoundedCornerShape(6.dp))) {
            rows.forEachIndexed { r, row ->
                Row {
                    for (col in 0 until cols) {
                        val cell = row.getOrNull(col) ?: ""
                        Text(
                            TrixInline.parse(cell, c, onLink),
                            fontSize = 14.sp,
                            fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal,
                            color = c.ink,
                            modifier = Modifier
                                .width(150.dp)
                                .background(if (r == 0) c.panel else Color.Transparent)
                                .border(0.5.dp, c.line)
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTubeCard(id: String) {
    val uriHandler = LocalUriHandler.current
    Box(
        Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black)
            .clickable { runCatching { uriHandler.openUri("https://www.youtube.com/watch?v=$id") } },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = "https://img.youtube.com/vi/$id/hqdefault.jpg",
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Icon(Icons.Filled.PlayCircle, contentDescription = "Play", tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.sizeIn(minWidth = 52.dp, minHeight = 52.dp).width(52.dp).height(52.dp))
    }
}

/** Inline content image (width-capped .avif) that opens a full-screen pinch-zoom viewer on tap. */
@Composable
private fun ZoomableTrixImage(url: String) {
    val c = DcampTheme.colors
    var showFull by remember { mutableStateOf(false) }
    var useOriginal by remember { mutableStateOf(false) }
    val display = if (useOriginal) url else AppConfig.scaledImageUrl(url)

    AsyncImage(
        model = display,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        onError = { if (!useOriginal) useOriginal = true },
        modifier = Modifier
            .sizeIn(maxWidth = 320.dp, maxHeight = 420.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, c.line, RoundedCornerShape(10.dp))
            .clickable { showFull = true },
    )

    if (showFull) ImageZoomViewer(url) { showFull = false }
}

@Composable
private fun ImageZoomViewer(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableStateOf(1f) }
        var offsetX by remember { mutableStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }
        Box(
            Modifier.fillMaxSize().background(Color.Black)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        if (scale > 1f) { offsetX += pan.x; offsetY += pan.y } else { offsetX = 0f; offsetY = 0f }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY),
            )
            Icon(
                Icons.Filled.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).width(30.dp).height(30.dp).clickable { onDismiss() },
            )
        }
    }
}
