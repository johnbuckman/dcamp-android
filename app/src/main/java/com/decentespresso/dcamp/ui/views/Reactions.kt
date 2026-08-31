package com.decentespresso.dcamp.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.decentespresso.dcamp.api.Boosts
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.theme.DcampTheme
import kotlinx.coroutines.launch

/**
 * Reaction (boost) row: existing emoji reactions with counts + an add button.
 * Tapping an existing one toggles the viewer's reaction; "+" opens the emoji picker.
 * Ported from iOS BoostBar. [onChange] re-fetches the parent so counts refresh.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoostBar(type: String, id: Int, boosts: Boosts, onChange: suspend () -> Unit) {
    val c = DcampTheme.colors
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }

    fun toggle(content: String) {
        scope.launch { Services.api.boostToggle(type, id, content); onChange() }
    }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        boosts.list.forEach { b ->
            val mine = b.content == boosts.mine
            Row(
                Modifier
                    .background(if (mine) c.accent.copy(alpha = 0.15f) else c.panel, CircleShape)
                    .border(1.dp, if (mine) c.accent.copy(alpha = 0.5f) else c.lineStrong, CircleShape)
                    .clickable { toggle(b.content) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(b.display, fontSize = 14.sp)
                Text("${b.count}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.inkSoft)
            }
        }
        Box(
            Modifier.size(28.dp).background(c.panel, CircleShape).border(1.dp, c.lineStrong, CircleShape)
                .clickable { picking = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.EmojiEmotions, contentDescription = "Add reaction", tint = c.muted, modifier = Modifier.size(15.dp))
        }
    }

    if (picking) EmojiPicker(onDismiss = { picking = false }) { emoji -> picking = false; toggle(emoji) }
}

private val emojiCategories: List<Pair<String, List<Pair<String, String>>>> = listOf(
    "Boosts" to listOf("👍" to "thumbs up like yes good", "🎉" to "party tada celebrate", "❤️" to "heart love",
        "😄" to "smile happy", "🙏" to "thanks pray please", "🔥" to "fire hot lit", "👀" to "eyes look watching",
        "🚀" to "rocket launch ship", "💯" to "hundred perfect", "😮" to "wow surprised", "👏" to "clap applause",
        "✅" to "check done yes", "🙌" to "raise hands praise", "💪" to "strong muscle"),
    "Smileys" to listOf("😀" to "grin happy", "😊" to "blush smile", "🙂" to "slight smile", "😉" to "wink",
        "😍" to "heart eyes love", "🥰" to "adore love", "😎" to "cool sunglasses", "🤩" to "star struck",
        "🤔" to "thinking hmm", "😅" to "sweat laugh", "😂" to "laugh cry", "🤣" to "rofl", "🙃" to "upside down",
        "😴" to "sleep tired", "😢" to "sad cry", "😭" to "sob cry", "🥳" to "party celebrate", "🤯" to "mind blown", "🤗" to "hug"),
    "Gestures" to listOf("👎" to "thumbs down no", "👌" to "ok perfect", "✌️" to "peace victory", "🤙" to "call shaka",
        "🤞" to "fingers crossed hope", "👋" to "wave hi bye", "🤝" to "handshake deal", "👉" to "point", "✍️" to "write note"),
    "Coffee & food" to listOf("☕" to "coffee espresso", "🍵" to "tea matcha", "🫖" to "teapot", "🧊" to "ice cold",
        "🍫" to "chocolate", "🍩" to "donut", "🥛" to "milk", "🍪" to "cookie", "🧁" to "cupcake", "🥐" to "croissant"),
    "Objects" to listOf("⭐️" to "star favorite", "⚡️" to "lightning fast power", "💡" to "idea bulb", "🧠" to "brain smart",
        "📈" to "chart up growth", "🛠️" to "tools fix", "🧪" to "test experiment", "🎯" to "target goal",
        "🥇" to "gold medal first", "🔧" to "wrench fix", "⚙️" to "gear settings", "🔋" to "battery power"),
    "Symbols" to listOf("❌" to "x no wrong", "❓" to "question", "❗" to "exclaim important", "➕" to "plus add",
        "♻️" to "recycle", "⚠️" to "warning", "🔔" to "bell notify", "💬" to "comment chat", "🏆" to "trophy win"),
)

@Composable
private fun EmojiPicker(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val c = DcampTheme.colors
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val q = query.text.trim().lowercase()
    val matches = remember(q) {
        if (q.isEmpty()) emptyList() else emojiCategories.flatMap { it.second }
            .filter { (_, kw) -> kw.split(" ").any { it.startsWith(q) } }.map { it.first }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(320.dp).background(c.bg, RoundedCornerShape(14.dp)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier.background(c.panel, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(16.dp))
                Box(Modifier.width(260.dp)) {
                    if (query.text.isEmpty()) Text(T("Search emoji"), color = c.muted, fontSize = 15.sp)
                    BasicTextField(value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = c.ink, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent))
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.height(320.dp),
            ) {
                if (q.isEmpty()) {
                    emojiCategories.forEach { (name, items) ->
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(name.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.muted)
                        }
                        items(items) { (e, _) ->
                            Text(e, fontSize = 24.sp, modifier = Modifier.size(34.dp).clickable { onPick(e) },
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                } else {
                    items(matches) { e ->
                        Text(e, fontSize = 24.sp, modifier = Modifier.size(34.dp).clickable { onPick(e) },
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}
