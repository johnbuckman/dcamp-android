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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.decentespresso.dcamp.api.AppConfig
import com.decentespresso.dcamp.api.Person
import com.decentespresso.dcamp.api.Services
import com.decentespresso.dcamp.api.SessionStore
import com.decentespresso.dcamp.api.locationGet
import com.decentespresso.dcamp.api.locationSave
import com.decentespresso.dcamp.api.personMutes
import com.decentespresso.dcamp.api.personUnmute
import com.decentespresso.dcamp.api.settingsSave
import com.decentespresso.dcamp.api.uiPrefsSave
import com.decentespresso.dcamp.ui.LocalRouter
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.UIStrings
import com.decentespresso.dcamp.ui.theme.Avatar
import com.decentespresso.dcamp.ui.theme.DcampTheme
import com.decentespresso.dcamp.ui.theme.ThemeMode
import com.decentespresso.dcamp.ui.theme.ThemeState
import kotlinx.coroutines.launch

private val notifKinds = listOf(
    "mention" to "Mentions", "dm" to "Direct messages",
    "comment_own" to "Replies to my threads", "comment_followed" to "Threads I follow",
)
private val languages = listOf(
    "" to "Default", "en" to "English", "fr" to "Français", "de" to "Deutsch", "es" to "Español",
    "kr" to "한국어", "zh-hans" to "简体中文", "zh-hant" to "繁體中文", "ar" to "العربية",
)

@Composable
fun SettingsView(session: SessionStore, state: SessionStore.State) {
    val c = DcampTheme.colors
    val router = LocalRouter.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(state.me?.name ?: "") }
    var about by remember { mutableStateOf(state.me?.about ?: "") }
    val popup = remember { mutableStateListOf<String>() }
    val email = remember { mutableStateListOf<String>() }
    val muted = remember { mutableStateListOf<Person>() }
    var lang by remember { mutableStateOf(AppConfig.lang) }
    var city by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var showLocation by remember { mutableStateOf(true) }
    var avatarImg by remember { mutableStateOf(state.me?.avatarImg ?: "") }
    var uploadingAvatar by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadingAvatar = true
            scope.launch {
                val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                if (bytes != null) {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val url = runCatching { Services.api.uploadImage(bytes, "avatar.jpg", mime) }.getOrNull()
                    if (url != null) { avatarImg = url; Services.api.settingsSave(name, about, url, showLocation); session.refresh() }
                }
                uploadingAvatar = false
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { Services.api.notifPrefsGet() }.getOrNull()?.let {
            popup.clear(); popup.addAll(it.popup); email.clear(); email.addAll(it.email)
        }
        runCatching { Services.api.locationGet() }.getOrNull()?.let {
            city = it.city ?: ""; country = it.countryCode ?: ""; showLocation = (it.showLocation ?: 1) != 0
        }
        muted.clear(); muted.addAll(Services.api.personMutes())
    }

    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {

        Text(T("My settings"), fontSize = 26.sp, fontWeight = FontWeight.Black, color = c.ink)

        if (state.isAdmin) {
            SettingsSection(T("Server")) {
                SegmentedControl(listOf(false to T("Live"), true to T("Test")), AppConfig.useLocalDev) { wantLocal ->
                    scope.launch { session.switchServer(wantLocal) }
                }
                Text("${T("Now using")} ${AppConfig.serverLabel}. ${T("Switching signs you out; you'll sign in again on the selected server.")}",
                    fontSize = 12.sp, color = c.muted)
            }
        }

        SettingsSection(T("Profile")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(person = state.me?.copy(avatarImg = avatarImg.ifEmpty { state.me?.avatarImg }), size = 56.dp)
                Text(if (uploadingAvatar) T("Uploading…") else T("Change photo"), color = c.link, fontSize = 14.sp,
                    modifier = Modifier.clickable(enabled = !uploadingAvatar) { photoPicker.launch("image/*") })
            }
            LabeledField(T("Display name"), name) { name = it }
            LabeledField(T("About you"), about) { about = it }
            PrimaryTextButton(T("Save profile")) {
                scope.launch { Services.api.settingsSave(name, about, avatarImg.ifEmpty { state.me?.avatarImg ?: "" }, showLocation); session.refresh() }
            }
        }

        SettingsSection(T("Appearance")) {
            SegmentedControl(ThemeMode.entries.map { it to T(it.label) }, ThemeState.mode) { m ->
                ThemeState.mode = m; AppConfig.themeRaw = m.serverValue
                scope.launch { Services.api.uiPrefsSave("dcamp_theme", m.serverValue) }
            }
        }

        SettingsSection(T("Language")) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                languages.forEach { (code, label) ->
                    Row(Modifier.fillMaxWidth().clickable {
                        lang = code; AppConfig.lang = code
                        scope.launch { session.refresh(); UIStrings.lang = state.lang; UIStrings.load(Services.api) }
                    }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (lang == code) Text("✓", color = c.accent, fontSize = 16.sp)
                    }
                }
            }
        }

        SettingsSection(T("Location")) {
            LabeledField(T("City"), city) { city = it }
            LabeledField(T("Country"), country) { country = it }
            Row(Modifier.fillMaxWidth().clickable { showLocation = !showLocation }, verticalAlignment = Alignment.CenterVertically) {
                Text(T("Show my city & country on my profile"), color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = showLocation, onCheckedChange = { showLocation = it },
                    colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = c.accent))
            }
            PrimaryTextButton(T("Save location")) {
                scope.launch {
                    Services.api.locationSave(country, city)
                    Services.api.settingsSave(name, about, avatarImg.ifEmpty { state.me?.avatarImg ?: "" }, showLocation)
                }
            }
        }

        SettingsSection(T("Notifications")) {
            notifKinds.forEach { (key, label) ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(T(label), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.ink)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TogglePill(T("Popup"), popup.contains(key)) {
                            if (popup.contains(key)) popup.remove(key) else popup.add(key)
                            scope.launch { Services.api.notifPrefsSave(email.toList(), popup.toList()) }
                        }
                        TogglePill(T("Email"), email.contains(key)) {
                            if (email.contains(key)) email.remove(key) else email.add(key)
                            scope.launch { Services.api.notifPrefsSave(email.toList(), popup.toList()) }
                        }
                    }
                }
            }
        }

        if (muted.isNotEmpty()) {
            SettingsSection(T("Muted people")) {
                muted.toList().forEach { p ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Avatar(person = p, size = 28.dp)
                        Text(p.name, color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text(T("Unmute"), color = c.link, fontSize = 14.sp,
                            modifier = Modifier.clickable { scope.launch { Services.api.personUnmute(p.id); muted.remove(p) } })
                    }
                }
            }
        }

        SettingsSection(T("Basecamp")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(T("Account"), color = c.ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Text(if (state.bcLinked) T("Linked") else T("Not linked"),
                    color = if (state.bcLinked) c.accentInk else c.muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        SettingsSection(T("More")) {
            Text(T("Summaries by email"), color = c.link, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clickable { router.push(com.decentespresso.dcamp.ui.Dest.EmailSummaries) })
            Text(T("Help"), color = c.link, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clickable { router.push(com.decentespresso.dcamp.ui.Dest.Help) })
        }

        SettingsSection(T("Account")) {
            state.me?.let {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(T("dcamp id"), color = c.inkSoft, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("${it.id}", color = c.ink, fontSize = 15.sp)
                }
            }
            Text(T("Server"), color = c.muted, fontSize = 12.sp)
            Text(AppConfig.serverLabel, color = c.inkSoft, fontSize = 13.sp)
            Spacer(Modifier.size(4.dp))
            Text(T("Sign out"), color = Color(0xFFDC2626), fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { scope.launch { session.logout() } })
        }
        Spacer(Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val c = DcampTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.muted, letterSpacing = 0.5.sp)
        Column(Modifier.fillMaxWidth().background(c.panel, RoundedCornerShape(14.dp))
            .border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

private typealias ColumnScope = androidx.compose.foundation.layout.ColumnScope

@Composable
private fun LabeledField(label: String, value: String, onChange: (String) -> Unit) {
    val c = DcampTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = c.muted)
        Box(Modifier.fillMaxWidth().background(c.bg, RoundedCornerShape(8.dp))
            .border(1.dp, c.lineStrong, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 9.dp)) {
            BasicTextField(value = value, onValueChange = onChange, singleLine = false,
                textStyle = TextStyle(color = c.ink, fontSize = 15.sp), cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PrimaryTextButton(label: String, onClick: () -> Unit) {
    val c = DcampTheme.colors
    Text(label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(c.accent, RoundedCornerShape(10.dp)).clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp))
}

@Composable
private fun <T> SegmentedControl(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = DcampTheme.colors
    Row(Modifier.fillMaxWidth().background(c.bg, RoundedCornerShape(10.dp)).border(1.dp, c.lineStrong, RoundedCornerShape(10.dp)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Text(label, fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) Color.White else c.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f).background(if (active) c.accent else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { onSelect(value) }.padding(vertical = 8.dp))
        }
    }
}

@Composable
private fun TogglePill(label: String, active: Boolean, onClick: () -> Unit) {
    val c = DcampTheme.colors
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (active) Color.White else c.inkSoft,
        modifier = Modifier.background(if (active) c.accent else c.bg, RoundedCornerShape(8.dp))
            .border(1.dp, if (active) c.accent else c.lineStrong, RoundedCornerShape(8.dp))
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 6.dp))
}
