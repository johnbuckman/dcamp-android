package com.decentespresso.dcamp.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Models pinned to dcamp's api.adp JSON, ported 1:1 from the iOS Models.swift.
 * The JSON uses snake_case; the shared [DcampJson] applies JsonNamingStrategy.SnakeCase,
 * so `avatarColor` maps to `avatar_color` automatically (matches iOS convertFromSnakeCase).
 *
 * Booleans arrive as 0/1 ints → decoded as Int? with Bool helpers.
 * Timestamps are Unix epoch seconds (Int). Unknown keys are ignored.
 */

// MARK: - People

@Serializable
data class Person(
    val id: Int = 0,
    val name: String = "",
    val avatarColor: String? = null,
    val avatarImg: String? = null,
    val initials: String? = null,
    val about: String? = null,
    val isMember: Int? = null,
    val machine: String? = null,
    val machineSku: String? = null,
    val since: Int? = null,
    val isDecent: Int? = null,
    val isBot: Int? = null,
) {
    val member: Boolean get() = (isMember ?: 0) != 0
    val bot: Boolean get() = (isBot ?: 0) != 0
    val decent: Boolean get() = (isDecent ?: 0) != 0
    val avatarUrl: String? get() = AppConfig.absUrl(avatarImg)
}

// MARK: - Boards (a.k.a. projects)

@Serializable
data class Board(
    val id: Int = 0,
    val name: String = "",
    val slug: String? = null,
    val kind: String? = null,
    val description: String? = null,
    val color: String? = null,
    val archived: Int? = null,
    val joined: Int? = null,
    val messageCount: Int? = null,
    val createdAt: Int? = null,
    val lastVisit: Int? = null,
    val newSince: Int? = null,
) {
    val isArchived: Boolean get() = (archived ?: 0) != 0
    val isJoined: Boolean get() = (joined ?: 0) != 0
}

@Serializable
data class Category(
    val id: Int = 0,
    val projectId: Int? = null,
    val name: String = "",
    val emoji: String? = null,
    val sortOrder: Int? = null,
)

// MARK: - Bootstrap

@Serializable
data class Bootstrap(
    val ok: Boolean? = null,
    val me: Person = Person(),
    val myUserid: String? = null,
    val isAdmin: Int? = null,
    val canPost: Int? = null,
    val bcLinked: Int? = null,
    val showRegions: Int? = null,
    val showRoasters: Int? = null,
    val memberCount: Int? = null,
    val pingUnread: Int? = null,
    val chatUnread: Int? = null,
    val lang: String? = null,
    val dcampTheme: String? = null,
    val myEmail: String? = null,
    val offtopicOn: Int? = null,
    val reopened: Int? = null,
    val status: String? = null,
    val projects: List<Board> = emptyList(),
    val categories: List<Category>? = null,
) {
    val admin: Boolean get() = (isAdmin ?: 0) != 0
    val canPostBool: Boolean get() = (canPost ?: 0) != 0
    val bcLinkedBool: Boolean get() = (bcLinked ?: 0) != 0
    val offtopicEnabled: Boolean get() = (offtopicOn ?: 0) != 0
}

// MARK: - Threads / messages

@Serializable
data class MessageThumb(val type: String? = null, val src: String? = null)

@Serializable
data class RecentComment(
    val id: Int? = null,
    val author: String? = null,
    val text: String? = null,
    val lang: String? = null,
    val bodyTr: String? = null,
)

@Serializable
data class MessageRow(
    val id: Int = 0,
    val projectId: Int? = null,
    val categoryName: String? = null,
    val categoryEmoji: String? = null,
    val slug: String? = null,
    val subject: String = "",
    val subjectTr: String? = null,
    val pinned: Int? = null,
    val status: String? = null,
    val createdAt: Int? = null,
    val updatedAt: Int? = null,
    val commentCount: Int? = null,
    val lang: String? = null,
    val author: Person? = null,
    val preview: String? = null,
    val bodyTr: String? = null,
    val thumb: MessageThumb? = null,
    val recentComment: RecentComment? = null,
) {
    val isPinned: Boolean get() = (pinned ?: 0) != 0
    val displaySubject: String get() = if (!subjectTr.isNullOrEmpty()) subjectTr else subject
    val displayPreview: String get() = PlainText.strip(if (!bodyTr.isNullOrEmpty()) bodyTr else (preview ?: ""))
    val thumbUrl: String? get() = AppConfig.absUrl(thumb?.src)
    val recentCommentText: String?
        get() {
            val rc = recentComment ?: return null
            return if (!rc.bodyTr.isNullOrEmpty()) rc.bodyTr else rc.text
        }
}

@Serializable
data class MessagesPage(
    val ok: Boolean? = null,
    val messages: List<MessageRow> = emptyList(),
    val total: Int? = null,
    val offset: Int? = null,
    val lastVisit: Int? = null,
)

@Serializable
data class MessageDetail(
    val id: Int = 0,
    val projectId: Int? = null,
    val categoryId: Int? = null,
    val slug: String? = null,
    val subject: String = "",
    val subjectTr: String? = null,
    val status: String? = null,
    val pinned: Int? = null,
    val createdAt: Int? = null,
    val updatedAt: Int? = null,
    val commentCount: Int? = null,
    val body: String? = null,
    val bodyHtml: String? = null,
    val bodyTr: String? = null,
    val lang: String? = null,
    val author: Person? = null,
    val authorCountry: String? = null,
    val boosts: Boosts? = null,
) {
    val html: String get() = if (!bodyHtml.isNullOrEmpty()) bodyHtml else (body ?: "")
    val displaySubject: String get() = if (!subjectTr.isNullOrEmpty()) subjectTr else subject
}

@Serializable
data class Comment(
    val id: Int = 0,
    val messageId: Int? = null,
    val body: String? = null,
    val bodyHtml: String? = null,
    val bodyTr: String? = null,
    val lang: String? = null,
    val createdAt: Int? = null,
    val streaming: Int? = null,
    val author: Person? = null,
    val authorCountry: String? = null,
    val boosts: Boosts? = null,
) {
    val html: String get() = if (!bodyHtml.isNullOrEmpty()) bodyHtml else (body ?: "")
}

@Serializable
data class MessageResponse(
    val ok: Boolean? = null,
    val projectName: String? = null,
    val lastVisit: Int? = null,
    val message: MessageDetail = MessageDetail(),
    val comments: List<Comment>? = null,
)

// MARK: - Auth / envelope

@Serializable
data class TokenResponse(val ok: Boolean? = null, val token: String? = null, val error: String? = null)

@Serializable
data class OAuthTokenResponse(
    val ok: Boolean? = null,
    val accessToken: String? = null,
    val isAdmin: Int? = null,
    val error: String? = null,
)

@Serializable
data class Envelope(val ok: Boolean? = null, val error: String? = null, val code: String? = null)

// MARK: - Reactions (boosts)

@Serializable
data class Boost(
    val content: String = "",
    val count: Int = 0,
    val people: List<Person>? = null,
) {
    /** Emoji/text to display (entities decoded); "fa:" icons fall back to a star. */
    val display: String get() = if (content.startsWith("fa:")) "★" else PlainText.decodeEntities(content)
    val whoNames: String get() = (people ?: emptyList()).joinToString(", ") { it.name }
}

@Serializable
data class Boosts(val list: List<Boost> = emptyList(), val mine: String = "")

// MARK: - Summaries

@Serializable
data class SummaryResult(
    val ok: Boolean? = null,
    val summary: String? = null,
    val mine: String? = null,
    val cached: Boolean? = null,
    val hours: Int? = null,
    val days: Int? = null,
)

@Serializable
data class SummaryInfo(val ok: Boolean? = null, val hours: Int? = null, val first: Boolean? = null)

// MARK: - AI pipeline

@Serializable
data class SubjectCheck(val ok: Boolean? = null, val verdict: String? = null, val suggestion: String? = null) {
    val isGood: Boolean get() = (verdict ?: "good") == "good"
}

@Serializable
data class PreAnswer(val ok: Boolean? = null, val answer: String? = null, val hasAnswer: Int? = null) {
    val has: Boolean get() = (hasAnswer ?: 0) != 0 || !answer.isNullOrEmpty()
}

@Serializable
data class OfftopicSuggest(
    val ok: Boolean? = null,
    val offtopic: Int? = null,
    val title: String? = null,
    val reason: String? = null,
) {
    val suggested: Boolean get() = (offtopic ?: 0) != 0
}

@Serializable
data class DerekStart(val ok: Boolean? = null, val token: String? = null)

@Serializable
data class DerekPoll(val ok: Boolean? = null, val done: Int? = null, val answer: String? = null) {
    val isDone: Boolean get() = (done ?: 0) != 0
}

// MARK: - Settings

@Serializable
data class SummaryPrefs(
    val ok: Boolean? = null,
    val sources: String? = null,
    val period: String? = null,
    val emailEnabled: Int? = null,
)

@Serializable
data class LocationInfo(
    val ok: Boolean? = null,
    val located: Boolean? = null,
    val countryCode: String? = null,
    val city: String? = null,
    val showLocation: Int? = null,
)

@Serializable
data class CreateResult(
    val ok: Boolean? = null,
    val id: Int? = null,
    val slug: String? = null,
    val projectId: Int? = null,
    val error: String? = null,
    val code: String? = null,
)

@Serializable
data class UploadResult(
    val ok: Boolean? = null,
    val url: String? = null,
    val isImage: Int? = null,
    val filename: String? = null,
    val error: String? = null,
)

// MARK: - Notifications

@Serializable
data class Notif(
    val id: Int = 0,
    val kind: String? = null,
    val route: String? = null,
    val createdAt: Int? = null,
    val preview: String? = null,
    val actor: Person? = null,
) {
    val kindLabel: String
        get() = when (kind) {
            "mention" -> "mentioned you"
            "dm" -> "sent you a message"
            "comment_own" -> "replied to your thread"
            "comment_followed" -> "commented on a thread you follow"
            "moved" -> "moved your post to its own thread"
            else -> "new activity"
        }
}

@Serializable
data class NotifPollResult(val ok: Boolean? = null, val items: List<Notif> = emptyList())

@Serializable
data class NotifPrefs(
    val ok: Boolean? = null,
    val types: List<String> = emptyList(),
    val email: List<String> = emptyList(),
    val popup: List<String> = emptyList(),
)

// MARK: - Chat

@Serializable
data class ChatLine(
    val id: Int = 0,
    val body: String? = null,
    val lang: String? = null,
    val bodyTr: String? = null,
    val createdAt: Int? = null,
    val streaming: Int? = null,
    val author: Person? = null,
    val boosts: Boosts? = null,
) {
    val html: String get() = body ?: ""
}

@Serializable
data class ChatResponse(
    val ok: Boolean? = null,
    val room: String? = null,
    val lastId: Int? = null,
    val lines: List<ChatLine> = emptyList(),
)

// MARK: - Direct messages

@Serializable
data class DMConversation(
    val id: Int = 0,
    val lastAt: Int? = null,
    val last: String? = null,
    val lastId: Int? = null,
    val unread: Int? = null,
    val members: List<Person> = emptyList(),
) {
    val title: String get() = if (members.isEmpty()) "Conversation" else members.joinToString(", ") { it.name }
    val lead: Person? get() = members.firstOrNull()
    val unreadCount: Int get() = unread ?: 0
}

@Serializable
data class DMMessage(
    val id: Int = 0,
    val body: String? = null,
    val lang: String? = null,
    val bodyTr: String? = null,
    val createdAt: Int? = null,
    val mine: Int? = null,
    val streaming: Int? = null,
    val sender: Person? = null,
) {
    val isMine: Boolean get() = (mine ?: 0) != 0
    val html: String get() = body ?: ""
}

@Serializable
data class DMThread(
    val ok: Boolean? = null,
    val id: Int? = null,
    val members: List<Person> = emptyList(),
    val messages: List<DMMessage> = emptyList(),
    val hasMore: Int? = null,
    val maxId: Int? = null,
)

@Serializable
data class Recipient(
    val id: Int = 0,
    val name: String = "",
    val email: String? = null,
    val avatarColor: String? = null,
    val avatarImg: String? = null,
    val initials: String? = null,
) {
    val person: Person get() = Person(id = id, name = name, avatarColor = avatarColor, avatarImg = avatarImg, initials = initials)
}

@Serializable
data class RecipientsResult(val ok: Boolean? = null, val recipients: List<Recipient> = emptyList())

// MARK: - Search

@Serializable
data class SearchHit(
    val type: String? = null,
    val id: Int? = null,
    val title: String? = null,
    val snippet: String? = null,
    val link: String? = null,
    val createdAt: Int? = null,
    val author: Person? = null,
    val forum: String? = null,
    val forumId: Int? = null,
    val score: Double? = null,
    val untranslated: Int? = null,
) {
    val uid: String get() = "${type ?: ""}-${id ?: 0}"
    val isUntranslated: Boolean get() = (untranslated ?: 0) != 0
    val typeLabel: String
        get() = when (type) {
            "message" -> "Thread"
            "comment" -> "Comment"
            "chat" -> "Chat"
            "pings", "dm" -> "DM"
            else -> "Result"
        }
}

@Serializable
data class SearchForum(val id: Int = 0, val name: String = "")

@Serializable
data class SearchResponse(
    val ok: Boolean? = null,
    val results: List<SearchHit> = emptyList(),
    val people: List<Person> = emptyList(),
    val forums: List<SearchForum>? = emptyList(),
    val experts: List<Int>? = emptyList(),
)

// MARK: - Person profile

@Serializable
data class CardMessage(
    val id: Int = 0,
    val subject: String? = null,
    val messageSubject: String? = null,
    val messageId: Int? = null,
    val body: String? = null,
    val createdAt: Int? = null,
)

// MARK: - Small raw wrappers for endpoints whose shape isn't a first-class model

@Serializable
data class DMThreadListRaw(val conversations: List<DMConversation> = emptyList(), val hasMore: Int? = null)

@Serializable
data class DMUnreadRaw(val unread: Int? = null)

@Serializable
data class MentionSearchRaw(val people: List<Person>? = null, val results: List<Person>? = null)

@Serializable
data class UiMapRaw(val map: Map<String, String>? = null)

@Serializable
data class PersonCard(
    val ok: Boolean? = null,
    val person: Person = Person(),
    val city: String? = null,
    val countryCode: String? = null,
    val muted: Int? = null,
    val moderated: Int? = null,
    val messages: List<CardMessage> = emptyList(),
    val comments: List<CardMessage> = emptyList(),
    val chat: List<CardMessage> = emptyList(),
)
