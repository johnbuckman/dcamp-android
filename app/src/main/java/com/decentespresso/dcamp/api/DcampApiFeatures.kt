package com.decentespresso.dcamp.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

/**
 * Long-tail API actions (summaries, settings, reactions, edit/delete, status, DM
 * participants, people, discovery, i18n). Ported from iOS DcampAPI+Features.swift.
 * Extension functions on the public call/rawData surface.
 */

// MARK: - Threads / messages

suspend fun DcampApi.messagesPage(params: Map<String, String>): MessagesPage = call("messages", params)

suspend fun DcampApi.messageDetail(id: Int): MessageResponse = call("message", mapOf("id" to id.toString()))

// MARK: - Summaries

suspend fun DcampApi.homeSummaryInfo(): SummaryInfo =
    runCatching { call<SummaryInfo>("home_summary_info") }.getOrDefault(SummaryInfo())

suspend fun DcampApi.homeSummary(days: Int, peek: Boolean = false): SummaryResult =
    call("home_summary", mapOf("days" to days.toString(), "peek" to if (peek) "1" else "0"))

suspend fun DcampApi.forumSummary(projectID: Int, days: Int, peek: Boolean = false): SummaryResult =
    call("forum_summary", mapOf("project_id" to projectID.toString(), "days" to days.toString(), "peek" to if (peek) "1" else "0"))

suspend fun DcampApi.threadSummary(messageID: Int, days: Int, peek: Boolean = false): SummaryResult =
    call("thread_summary", mapOf("id" to messageID.toString(), "days" to days.toString(), "peek" to if (peek) "1" else "0"))

suspend fun DcampApi.chatSummary(days: Int, room: String = "main", peek: Boolean = false): SummaryResult =
    call("chat_summary", mapOf("days" to days.toString(), "room" to room, "peek" to if (peek) "1" else "0"))

suspend fun DcampApi.activitySummary(sources: String, period: String): SummaryResult =
    runCatching { call<SummaryResult>("activity_summary", mapOf("sources" to sources, "period" to period)) }.getOrDefault(SummaryResult())

suspend fun DcampApi.summaryEmailSample(sources: String, period: String) {
    runCatching { rawData("summary_email_sample", mapOf("sources" to sources, "period" to period)) }
}

// MARK: - Saved forum filters

@Serializable private data class FiltersListRaw(val filters: List<SavedFilter>? = null)

suspend fun DcampApi.filtersList(): List<SavedFilter> =
    runCatching { call<FiltersListRaw>("filters_list").filters ?: emptyList() }.getOrDefault(emptyList())

suspend fun DcampApi.filterSave(name: String, spec: String) {
    runCatching { rawData("filter_save", mapOf("name" to name, "spec" to spec)) }
}

suspend fun DcampApi.filterDelete(id: Int) {
    runCatching { rawData("filter_delete", mapOf("id" to id.toString())) }
}

// MARK: - AI pipeline (pre-post checks + Ask-Derek)

suspend fun DcampApi.subjectCheck(subject: String, body: String): SubjectCheck =
    runCatching { call<SubjectCheck>("subject_check", mapOf("subject" to subject, "body" to body)) }.getOrDefault(SubjectCheck(verdict = "good"))

suspend fun DcampApi.preAnswer(subject: String, body: String): PreAnswer =
    runCatching { call<PreAnswer>("pre_answer", mapOf("subject" to subject, "body" to body)) }.getOrDefault(PreAnswer())

suspend fun DcampApi.offtopicSuggest(commentID: Int): OfftopicSuggest =
    runCatching { call<OfftopicSuggest>("offtopic_suggest", mapOf("id" to commentID.toString())) }.getOrDefault(OfftopicSuggest())

suspend fun DcampApi.offtopicMove(commentID: Int): CreateResult =
    runCatching { call<CreateResult>("offtopic_move", mapOf("id" to commentID.toString())) }.getOrDefault(CreateResult())

suspend fun DcampApi.derekSearchStart(query: String): DerekStart =
    runCatching { call<DerekStart>("derek_search_start", mapOf("q" to query)) }.getOrDefault(DerekStart())

suspend fun DcampApi.derekSearchPoll(token: String): DerekPoll =
    runCatching { call<DerekPoll>("derek_search_poll", mapOf("token" to token)) }.getOrDefault(DerekPoll())

// MARK: - Streaming helpers

@Serializable private data class CommentOneRaw(val comment: Comment? = null)

suspend fun DcampApi.commentOne(id: Int): Comment? =
    runCatching { call<CommentOneRaw>("comment_one", mapOf("id" to id.toString())).comment }.getOrNull()

// MARK: - Account

suspend fun DcampApi.emailChange(email: String): Envelope =
    runCatching { call<Envelope>("email_change", mapOf("email" to email)) }.getOrDefault(Envelope())

suspend fun DcampApi.bcUnlink() { runCatching { rawData("bc_unlink") } }

@Serializable private data class ChatInfoRaw(val total: Int? = null, val last24: Int? = null)

suspend fun DcampApi.chatInfo(room: String = "main"): Pair<Int, Int> {
    val r = runCatching { call<ChatInfoRaw>("chat_info", mapOf("room" to room)) }.getOrNull()
    return (r?.total ?: 0) to (r?.last24 ?: 0)
}

/** Register + translate a batch of UI-chrome strings into [lang]. English/empty → no-op. */
suspend fun DcampApi.uiStrings(strings: List<String>, lang: String): Map<String, String> {
    if (lang == "en" || lang.isEmpty()) return emptyMap()
    val json = DcampJson.json.encodeToString(strings)
    return runCatching { call<UiMapRaw>("ui_strings", mapOf("lang" to lang, "strings" to json)).map ?: emptyMap() }.getOrDefault(emptyMap())
}

@Serializable private data class ResolveMsgRaw(val id: Int? = null)

suspend fun DcampApi.resolveMsg(board: String, slug: String): Int? =
    runCatching { call<ResolveMsgRaw>("resolve_msg", mapOf("project_id" to board, "slug" to slug)).id }.getOrNull()

@Serializable private data class UrlTitleRaw(val title: String? = null)

suspend fun DcampApi.urlTitle(url: String): String =
    runCatching { call<UrlTitleRaw>("url_title", mapOf("url" to url)).title ?: "" }.getOrDefault("")

// MARK: - Settings / account

suspend fun DcampApi.summaryPrefsGet(): SummaryPrefs =
    runCatching { call<SummaryPrefs>("summary_prefs_get") }.getOrDefault(SummaryPrefs())

suspend fun DcampApi.summaryPrefsSave(sources: String, period: String, emailEnabled: Boolean) {
    runCatching { rawData("summary_prefs_save", mapOf("sources" to sources, "period" to period, "email_enabled" to if (emailEnabled) "1" else "0")) }
}

suspend fun DcampApi.locationGet(): LocationInfo =
    runCatching { call<LocationInfo>("location_get") }.getOrDefault(LocationInfo())

suspend fun DcampApi.locationSave(country: String, city: String) {
    runCatching { rawData("location_save", mapOf("country" to country, "city" to city)) }
}

/** avatarImg MUST be the member's CURRENT avatar URL — the server overwrites avatar_img,
 *  so passing "" blanks the avatar on every save (verified iOS bug). */
suspend fun DcampApi.settingsSave(name: String, about: String, avatarImg: String, showLocation: Boolean) {
    runCatching { rawData("settings_save", mapOf("name" to name, "avatar_img" to avatarImg, "about" to about, "show_location" to if (showLocation) "1" else "0")) }
}

suspend fun DcampApi.accountClose() { runCatching { rawData("account_close") } }

suspend fun DcampApi.uiPrefsSave(name: String, value: String) {
    runCatching { rawData("ui_prefs_save", mapOf("name" to name, "value" to value)) }
}

// MARK: - Muted people

@Serializable private data class PersonMutesRaw(val people: List<Person>? = null, val mutes: List<Person>? = null)

suspend fun DcampApi.personMutes(): List<Person> {
    val r = runCatching { call<PersonMutesRaw>("person_mutes") }.getOrNull()
    return r?.people ?: r?.mutes ?: emptyList()
}

suspend fun DcampApi.personMute(id: Int) { runCatching { rawData("person_mute", mapOf("id" to id.toString())) } }
suspend fun DcampApi.personUnmute(id: Int) { runCatching { rawData("person_unmute", mapOf("id" to id.toString())) } }
suspend fun DcampApi.reportPerson(id: Int, reason: String, url: String) {
    runCatching { rawData("report_person", mapOf("id" to id.toString(), "reason" to reason, "url" to url)) }
}

// MARK: - Edit / delete / status

suspend fun DcampApi.messageUpdate(id: Int, categoryID: Int, subject: String, bodyHTML: String) {
    rawData("message_update", mapOf("id" to id.toString(), "category_id" to categoryID.toString(), "subject" to subject, "body" to bodyHTML))
}

suspend fun DcampApi.messageSetStatus(id: Int, status: String) {
    runCatching { rawData("message_set_status", mapOf("id" to id.toString(), "status" to status)) }
}

suspend fun DcampApi.messageStatus(id: Int, status: String) {
    runCatching { rawData("message_status", mapOf("id" to id.toString(), "status" to status)) }
}

suspend fun DcampApi.messagePin(id: Int, pinned: Boolean) {
    runCatching { rawData("message_pin", mapOf("id" to id.toString(), "pinned" to if (pinned) "1" else "0")) }
}

suspend fun DcampApi.commentUpdate(id: Int, bodyHTML: String) {
    rawData("comment_update", mapOf("id" to id.toString(), "body" to bodyHTML))
}

suspend fun DcampApi.commentDelete(id: Int) { runCatching { rawData("comment_delete", mapOf("id" to id.toString())) } }

suspend fun DcampApi.chatUpdate(id: Int, bodyHTML: String) {
    rawData("chat_update", mapOf("id" to id.toString(), "body" to bodyHTML))
}

suspend fun DcampApi.chatDelete(id: Int) { runCatching { rawData("chat_delete", mapOf("id" to id.toString())) } }

// MARK: - DM participants

suspend fun DcampApi.dmArchive(convoID: Int) { runCatching { rawData("dm_archive", mapOf("convo" to convoID.toString())) } }

suspend fun DcampApi.dmRemove(convoID: Int, personID: Int) {
    runCatching { rawData("dm_remove", mapOf("convo" to convoID.toString(), "person" to personID.toString())) }
}

suspend fun DcampApi.dmInvite(convoID: Int, participantIDs: List<Int>, days: Int) {
    runCatching { rawData("dm_invite", mapOf("convo" to convoID.toString(), "participants" to participantIDs.joinToString(","), "days" to days.toString())) }
}

@Serializable private data class DmDirectRaw(val id: Int? = null, val convo: Int? = null, val error: String? = null)

suspend fun DcampApi.dmDirect(personID: Int): Int {
    val r = call<DmDirectRaw>("dm_direct", mapOf("person" to personID.toString()))
    return r.id ?: r.convo ?: throw ApiException(r.error ?: "Couldn’t open conversation")
}

// MARK: - Forum discovery

@Serializable private data class ProjectsDiscoverRaw(val projects: List<Board> = emptyList())

suspend fun DcampApi.projectsDiscover(kind: String): List<Board> =
    runCatching { call<ProjectsDiscoverRaw>("projects_discover", mapOf("kind" to kind)).projects }.getOrDefault(emptyList())

suspend fun DcampApi.projectJoin(id: Int) { runCatching { rawData("project_join", mapOf("id" to id.toString())) } }
suspend fun DcampApi.projectLeave(id: Int) { runCatching { rawData("project_leave", mapOf("id" to id.toString())) } }

@Serializable private data class PersonActivityRaw(val items: List<CardMessage>? = null, val activity: List<CardMessage>? = null)

suspend fun DcampApi.personActivity(id: Int): List<CardMessage> {
    val r = runCatching { call<PersonActivityRaw>("person_activity", mapOf("id" to id.toString())) }.getOrNull()
    return r?.items ?: r?.activity ?: emptyList()
}
