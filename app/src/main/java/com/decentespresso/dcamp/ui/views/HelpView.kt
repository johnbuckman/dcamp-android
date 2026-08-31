package com.decentespresso.dcamp.ui.views

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.ui.T
import com.decentespresso.dcamp.ui.theme.DcampTheme

private data class HelpSection(val icon: ImageVector, val title: String, val points: List<String>)

@Composable
fun HelpView() {
    val c = DcampTheme.colors
    val sections = listOf(
        HelpSection(Icons.Filled.Forum, T("Forums"), listOf(
            T("Each forum holds threads. Tap a forum to see its threads, newest activity first."),
            T("Filter by Popular, I posted, I commented, or I’m mentioned."),
            T("The Problems board tracks issues with an Unsolved / Solved / Closed status."),
            T("Posts get auto-categorised, and problem-like posts may be moved to Problems."))),
        HelpSection(Icons.Filled.Edit, T("Writing posts"), listOf(
            T("Rich text with colour and highlight, plus @mentions of people (and bots)."),
            T("Attach images by picking from your library; tap an image to view it full-size."),
            T("Type Markdown as you go: # headings, - lists, > quotes, **bold**, *italic*, ``` code."),
            T("Quote & reply keeps the original author’s attribution."),
            T("Drafts are kept per thread, and ⌘↵ sends."))),
        HelpSection(Icons.Filled.ThumbUp, T("Reactions"), listOf(
            T("React to any message or comment with an emoji. Tap a reaction to add or remove yours."),
            T("Chat lines and direct messages don’t have reactions."))),
        HelpSection(Icons.AutoMirrored.Filled.Chat, T("Chat"), listOf(
            T("The Chat Room is a live, flat conversation for Decent owners."),
            T("Post rich text and @mention people or the AI assistants."),
            T("Use “Link to this” to copy a link to any chat line."))),
        HelpSection(Icons.Filled.Email, T("Direct messages"), listOf(
            T("Private one-to-one or group conversations, shown as chat bubbles."),
            T("Add or remove people; archived conversations return when there’s new activity."),
            T("Unread counts show which conversations have new messages."))),
        HelpSection(Icons.Filled.AutoAwesome, T("AI assistants"), listOf(
            T("@mention Derek or DeepSeek in any thread, comment, chat line, or a 1:1 DM."),
            T("They draw on the whole forum and reply in your language; answers stream in."),
            T("When you start a question-like post, Derek may already have an answer to show you."))),
        HelpSection(Icons.Filled.Search, T("Search"), listOf(
            T("Search threads, comments, chat, your DMs, and people."),
            T("Filter by type and forum, sort by relevance or newest, and keep only the best matches."),
            T("“Experts only” narrows people to recognised experts."),
            T("Ask Derek for an AI answer drawn from the whole forum."))),
        HelpSection(Icons.Filled.Shield, T("People & privacy"), listOf(
            T("Open anyone’s card to see their activity, machine, and a Message button."),
            T("Mute someone to hide their messages, comments, chat and DMs, and stop their notifications."),
            T("Report someone to the moderators."))),
        HelpSection(Icons.Filled.Summarize, T("Summaries"), listOf(
            T("Tap ✨ Summarize on the home, a forum, a thread, or chat for an AI digest."),
            T("Choose how far back — since your last visit or a number of days."),
            T("The home summary highlights where you were mentioned."),
            T("Get summaries by email daily, weekly, or monthly, and email yourself a sample."))),
        HelpSection(Icons.Filled.Language, T("Languages"), listOf(
            T("Content is shown in your language automatically."),
            T("Tap “Show original text” on any post to see what was originally written."))),
        HelpSection(Icons.Filled.Person, T("Your account"), listOf(
            T("Set your name, about, picture, and location (and whether it shows on your card)."),
            T("Choose which notifications you get as pop-ups or email."),
            T("Manage muted people, your Basecamp link, appearance (light/dark), and closing your account."))),
    )

    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text(T("Help"), fontSize = 26.sp, fontWeight = FontWeight.Black, color = c.ink)
        sections.forEach { s ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(s.icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                    Text(s.title, fontSize = 19.sp, fontWeight = FontWeight.Black, color = c.ink)
                }
                s.points.forEach { p ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("•", color = c.muted, fontSize = 15.sp)
                        Text(p, fontSize = 15.sp, color = c.inkSoft)
                    }
                }
            }
            Divider(color = c.line)
        }
    }
}
