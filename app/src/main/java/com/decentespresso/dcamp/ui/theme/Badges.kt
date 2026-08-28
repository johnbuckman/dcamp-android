package com.decentespresso.dcamp.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.decentespresso.dcamp.api.Person
import com.decentespresso.dcamp.ui.LocalRouter

/** Green outlined pill: machine + "since YEAR" (dcamp's .owner-badge). */
@Composable
fun MachineBadge(person: Person?) {
    val m = person?.machine
    if (person == null || m.isNullOrEmpty()) return
    val c = DcampTheme.colors
    Row(
        Modifier
            .background(c.panel, CircleShape)
            .border(1.dp, c.lineStrong, CircleShape)
            .padding(horizontal = 7.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(Icons.Filled.Coffee, contentDescription = null, tint = c.accentInk,
            modifier = Modifier.size(11.dp))
        Text(person.since?.let { "$m since $it" } ?: m, color = c.accentInk,
            fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Dark "DECENT" pill (dcamp's .decent-badge). */
@Composable
fun DecentBadge(person: Person?) {
    if (person?.decent != true) return
    val c = DcampTheme.colors
    Text(
        "DECENT",
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .background(c.decentBg, CircleShape)
            .padding(horizontal = 8.dp, vertical = 1.5.dp),
    )
}

/** Author line used on posts/comments/chat: name + DECENT + machine + date. Wraps on narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuthorLine(person: Person?, date: Int? = null, tappable: Boolean = true) {
    val c = DcampTheme.colors
    val router = LocalRouter.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            person?.name ?: "",
            color = c.inkSoft,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = if (tappable && (person?.id ?: 0) > 0)
                Modifier.clickable { router.personID = person?.id } else Modifier,
        )
        DecentBadge(person)
        MachineBadge(person)
        if (date != null) {
            Text("· ${RelativeTime.string(date)}", color = c.muted, fontSize = 13.sp)
        }
    }
}
