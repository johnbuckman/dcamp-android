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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.decentespresso.dcamp.api.Countries
import com.decentespresso.dcamp.api.SavedFilter
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.api.filterDelete
import com.decentespresso.dcamp.api.filterSave
import com.decentespresso.dcamp.api.filtersList
import com.decentespresso.dcamp.ui.ForumFilterStore
import com.decentespresso.dcamp.ui.SavedFilterSpec
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.parsedSpec
import com.decentespresso.dcamp.ui.theme.DcampTheme
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(filter: ForumFilterStore, onApply: () -> Unit, onDismiss: () -> Unit) {
    val c = DcampTheme.colors
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val saved = remember { mutableStateListOf<SavedFilter>() }
    var saveName by remember { mutableStateOf("") }
    // recomposition tick so the sheet reflects filter mutations
    var tick by remember { mutableStateOf(0) }
    fun changed() { tick++; onApply() }

    LaunchedEffect(Unit) { saved.clear(); saved.addAll(Services.api.filtersList()) }

    val views = listOf(
        ForumFilterStore.View.popular to T("Popular"),
        ForumFilterStore.View.posted to T("I posted"),
        ForumFilterStore.View.commented to T("I commented"),
        ForumFilterStore.View.mentioned to T("I'm mentioned"),
    )
    val q = query.trim().lowercase()
    val visibleCodes = remember(q, tick) {
        if (q.isEmpty()) {
            val sel = Countries.sortedCodes.filter { filter.hasCountry(it) }
            val rest = Countries.sortedCodes.filter { !filter.hasCountry(it) }.take(60)
            sel + rest
        } else Countries.sortedCodes.filter { filter.hasCountry(it) || Countries.name(it).lowercase().contains(q) || it.lowercase() == q }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxWidth(0.95f).heightIn(max = 680.dp).background(c.bg, RoundedCornerShape(16.dp))
            .verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            key(tick) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(T("Filter"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c.ink, modifier = Modifier.weight(1f))
                    Switch(checked = filter.on, enabled = filter.selected, onCheckedChange = { filter.toggleOn(); changed() },
                        colors = SwitchDefaults.colors(checkedTrackColor = c.accent))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(T("Show").uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        views.forEach { (v, label) -> Pill(label, filter.view == v) { filter.toggleView(v); changed() } }
                    }
                }
                Text("✕ ${T("Clear filter")}", color = c.inkSoft, fontSize = 14.sp,
                    modifier = Modifier.clickable { filter.clear(); changed(); onDismiss() })

                // Saved filters
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(T("Saved filters").uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.muted)
                    saved.toList().forEach { f ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(f.name, color = c.accentInk, fontSize = 15.sp, modifier = Modifier.weight(1f)
                                .clickable { filter.applySpec(f.parsedSpec()); onApply(); onDismiss() })
                            Text("🗑", fontSize = 14.sp, modifier = Modifier.clickable {
                                scope.launch { Services.api.filterDelete(f.id); saved.clear(); saved.addAll(Services.api.filtersList()) }
                            })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f).background(c.panel, RoundedCornerShape(8.dp)).border(1.dp, c.lineStrong, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                            if (saveName.isEmpty()) Text(T("Name this filter…"), color = c.muted, fontSize = 14.sp)
                            BasicTextField(saveName, { saveName = it }, singleLine = true, textStyle = TextStyle(color = c.ink, fontSize = 14.sp), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
                        }
                        val canSave = saveName.trim().isNotEmpty() && filter.selected
                        Text(T("Save"), color = if (canSave) Color.White else c.muted, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            modifier = Modifier.background(if (canSave) c.accent else c.panel, RoundedCornerShape(8.dp)).clickable(enabled = canSave) {
                                val spec = Json.encodeToString(SavedFilterSpec.serializer(), filter.spec)
                                scope.launch { Services.api.filterSave(saveName.trim(), spec); saveName = ""; saved.clear(); saved.addAll(Services.api.filtersList()) }
                            }.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }

                // Countries
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(T("Countries").uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.muted, modifier = Modifier.weight(1f))
                        if (filter.countries.isNotEmpty()) Text(filter.countries.joinToString(", ") { Countries.name(it) }, fontSize = 12.sp, color = c.inkSoft, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().background(c.panel, RoundedCornerShape(8.dp)).border(1.dp, c.lineStrong, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        if (query.isEmpty()) Text(T("Search countries…"), color = c.muted, fontSize = 14.sp)
                        BasicTextField(query, { query = it }, singleLine = true, textStyle = TextStyle(color = c.ink, fontSize = 14.sp), cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth())
                    }
                    Column(Modifier.fillMaxWidth().background(c.panel, RoundedCornerShape(12.dp)).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp)) {
                        visibleCodes.forEach { code ->
                            Row(Modifier.fillMaxWidth().clickable { filter.toggleCountry(code); changed() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (filter.hasCountry(code)) "☑" else "☐", color = if (filter.hasCountry(code)) c.accent else c.muted, fontSize = 16.sp)
                                Text(Countries.name(code), color = c.ink, fontSize = 15.sp)
                            }
                            Divider(color = c.line)
                        }
                    }
                }
                Spacer(Modifier.padding(4.dp))
                Text(T("Done"), color = c.accent, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.End).clickable { onDismiss() }.padding(8.dp))
            }
        }
    }
}

@Composable
private fun Pill(title: String, active: Boolean, onClick: () -> Unit) {
    val c = DcampTheme.colors
    Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (active) c.accentInk else c.inkSoft,
        modifier = Modifier.border(1.dp, if (active) c.accent.copy(alpha = 0.5f) else c.lineStrong, CircleShape)
            .background(if (active) c.accent.copy(alpha = 0.12f) else c.panel, CircleShape)
            .clickable { onClick() }.padding(horizontal = 11.dp, vertical = 6.dp))
}
