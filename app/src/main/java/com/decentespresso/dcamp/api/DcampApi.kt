package com.decentespresso.dcamp.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Shared JSON config: snake_case keys, tolerant of unknown/missing/null fields. */
@OptIn(ExperimentalSerializationApi::class)
object DcampJson {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true      // null / wrong-enum → default value
        explicitNulls = false
        isLenient = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }
}

class ApiException(val messageText: String, val code: String? = null) : Exception(messageText)

/**
 * Single typed client for dcamp's api.adp?action=… endpoint. Native identity is a
 * bearer token (lib/dcamp_native.tcl), NOT the /support cookie, so we run cookie-less.
 * Every call carries client=native. Ported from iOS DcampAPI (actor → thread-safe
 * suspend client on Dispatchers.IO).
 */
class DcampApi(private val tokenStore: TokenStore) {

    @Volatile private var token: String? = tokenStore.loadToken()

    val isAuthenticated: Boolean get() = token != null

    fun setToken(t: String?) {
        token = t
        if (t != null) tokenStore.saveToken(t) else tokenStore.clearToken()
    }

    private val normalClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    // AI-backed actions generate on the server and can take 30–60s; give them a long window.
    private val slowClient = normalClient.newBuilder()
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    companion object {
        val slowActions = setOf(
            "forum_summary", "home_summary", "thread_summary", "chat_summary",
            "activity_summary", "summary_email_sample",
            "subject_check", "pre_answer", "offtopic_suggest", "offtopic_move",
            "derek_search_start", "derek_search_poll",
        )
    }

    // MARK: - Transport

    /** POST the form, validate HTTP + the {ok:false,error} envelope, return the body string. */
    suspend fun rawData(action: String, params: Map<String, String> = emptyMap()): String = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().apply {
            for ((k, v) in params) add(k, v)
            add("action", action)
            add("client", AppConfig.nativeClient)
        }.build()

        val builder = Request.Builder().url(AppConfig.apiUrl).post(form)
        token?.let { builder.header("Authorization", "Bearer $it") }
        AppConfig.lang.takeIf { it.isNotEmpty() }?.let { builder.header("X-Dcamp-Lang", it) }

        val client = if (action in slowActions) slowClient else normalClient
        val body: String
        try {
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) throw ApiException("Server error (${resp.code})", "http${resp.code}")
                body = resp.body?.string() ?: ""
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Can’t reach dcamp. ${e.message ?: ""}", "network")
        }

        // Envelope check: many actions return {"ok":false,"error":"…"}.
        try {
            val env = DcampJson.json.decodeFromString(Envelope.serializer(), body)
            if (env.ok == false) throw ApiException(env.error ?: "Request failed", env.code ?: "api")
        } catch (e: ApiException) {
            throw e
        } catch (_: Exception) {
            // Not an envelope-shaped payload (or not JSON) — fine, let the typed decode handle it.
        }
        body
    }

    suspend inline fun <reified T> call(action: String, params: Map<String, String> = emptyMap()): T {
        val body = rawData(action, params)
        return try {
            DcampJson.json.decodeFromString<T>(body)
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            throw ApiException("Couldn’t read $action response.", "decode")
        }
    }

    // MARK: - Auth

    /** Verify email+pw, mint & persist a bearer token. Runs logged-out. */
    suspend fun login(email: String, password: String, deviceID: String): String {
        val body = rawData("auth_token", mapOf(
            "email" to email, "pw" to password,
            "device_id" to deviceID, "platform" to "android",
        ))
        val resp = DcampJson.json.decodeFromString(TokenResponse.serializer(), body)
        val tok = resp.token
        if (tok.isNullOrEmpty()) throw ApiException(resp.error ?: "Login failed")
        setToken(tok)
        return tok
    }

    suspend fun logout() {
        try { rawData("token_revoke") } catch (_: Exception) {}
        setToken(null)
    }

    /** Exchange an OAuth code (+ PKCE verifier) for a bearer token. Returns is_admin. */
    suspend fun exchangeOAuth(code: String, verifier: String, deviceID: String): Boolean = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("code_verifier", verifier)
            .add("redirect_uri", AppConfig.oauthRedirectUri)
            .add("client_id", AppConfig.oauthClientId)
            .add("device_id", deviceID)
            .build()
        val req = Request.Builder().url(AppConfig.oauthTokenUrl).post(form).build()
        val body = normalClient.newCall(req).execute().use { it.body?.string() ?: "" }
        val r = DcampJson.json.decodeFromString(OAuthTokenResponse.serializer(), body)
        val tok = r.accessToken
        if (tok.isNullOrEmpty()) throw ApiException(r.error ?: "Sign-in failed")
        setToken(tok)
        (r.isAdmin ?: 0) != 0
    }

    // MARK: - Posting

    suspend fun createMessage(projectID: Int, categoryID: Int, subject: String, bodyHTML: String): CreateResult =
        call("message_create", mapOf(
            "project_id" to projectID.toString(),
            "category_id" to categoryID.toString(),
            "subject" to subject,
            "body" to bodyHTML,
        ))

    suspend fun createComment(messageID: Int, bodyHTML: String): CreateResult =
        call("comment_create", mapOf("message_id" to messageID.toString(), "body" to bodyHTML))

    /** Toggle a reaction. [type] is "message" | "comment" | "chat". */
    suspend fun boostToggle(type: String, id: Int, emoji: String) {
        rawData("boost_toggle", mapOf("parent_type" to type, "parent_id" to id.toString(), "content" to emoji))
    }

    // MARK: - Notifications

    suspend fun notifPoll(): List<Notif> = call<NotifPollResult>("notif_poll").items

    suspend fun notifSeen(ids: List<Int>) {
        if (ids.isEmpty()) return
        try { rawData("notif_seen", mapOf("ids" to ids.joinToString(","))) } catch (_: Exception) {}
    }

    suspend fun notifPrefsGet(): NotifPrefs = call("notif_prefs_get")

    suspend fun notifPrefsSave(email: List<String>, popup: List<String>) {
        try {
            rawData("notif_prefs_save", mapOf("email" to email.joinToString(","), "popup" to popup.joinToString(",")))
        } catch (_: Exception) {}
    }

    // MARK: - Chat

    suspend fun chatLines(room: String = "main", since: Int = 0): ChatResponse =
        call("chat", mapOf("room" to room, "since" to since.toString()))

    suspend fun chatPost(room: String = "main", bodyHTML: String) {
        rawData("chat_post", mapOf("room" to room, "body" to bodyHTML))
    }

    // MARK: - Direct messages

    data class DMConversationsPage(val conversations: List<DMConversation>, val hasMore: Boolean)

    suspend fun dmConversations(offset: Int = 0, limit: Int = 30, q: String = ""): DMConversationsPage {
        val params = buildMap {
            put("offset", offset.toString()); put("limit", limit.toString())
            if (q.isNotEmpty()) put("q", q)
        }
        val r = call<DMThreadListRaw>("dm_conversations", params)
        return DMConversationsPage(r.conversations, (r.hasMore ?: 0) != 0)
    }

    suspend fun dmThread(convoID: Int, limit: Int = 40): DMThread =
        call("dm_thread", mapOf("id" to convoID.toString(), "limit" to limit.toString()))

    suspend fun dmSend(convoID: Int, bodyHTML: String) {
        rawData("dm_send", mapOf("convo" to convoID.toString(), "body" to bodyHTML))
    }

    suspend fun dmCreate(participantIDs: List<Int>): Int {
        val r = call<CreateResult>("dm_create", mapOf("participants" to participantIDs.joinToString(",")))
        return r.id ?: throw ApiException(r.error ?: "Couldn’t start conversation")
    }

    suspend fun dmUnread(): Int = try { call<DMUnreadRaw>("dm_unread").unread ?: 0 } catch (_: Exception) { 0 }

    suspend fun pingRecipients(query: String): List<Recipient> =
        call<RecipientsResult>("ping_recipients", mapOf("q" to query)).recipients

    // MARK: - Search & people

    suspend fun search(query: String, types: String = "messages,comments,chat", years: Int = 0): SearchResponse =
        call("search", mapOf("q" to query, "types" to types, "years" to years.toString()))

    suspend fun personCard(id: Int): PersonCard = call("person_card", mapOf("id" to id.toString()))

    suspend fun mentionSearch(query: String): List<Person> {
        val r = call<MentionSearchRaw>("mention_search", mapOf("q" to query))
        return r.people ?: r.results ?: emptyList()
    }

    // MARK: - i18n

    suspend fun uiMap(): Map<String, String> = call<UiMapRaw>("ui_map").map ?: emptyMap()

    // MARK: - Push (registration kept for future FCM lane; not wired this pass)

    suspend fun pushRegister(token: String, platform: String = "fcm") {
        try { rawData("push_register", mapOf("token" to token, "platform" to platform)) } catch (_: Exception) {}
    }

    suspend fun pushUnregister(token: String) {
        try { rawData("push_unregister", mapOf("token" to token)) } catch (_: Exception) {}
    }

    // MARK: - Upload

    suspend fun uploadImage(bytes: ByteArray, filename: String, mime: String): String = withContext(Dispatchers.IO) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("action", "upload")
            .addFormDataPart("client", AppConfig.nativeClient)
            .addFormDataPart("file", filename, bytes.toRequestBody(mime.toMediaType()))
            .build()
        val builder = Request.Builder().url(AppConfig.apiUrl).post(body)
        token?.let { builder.header("Authorization", "Bearer $it") }
        val resp = slowClient.newCall(builder.build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("Upload failed", "http")
            r.body?.string() ?: ""
        }
        val result = DcampJson.json.decodeFromString(UploadResult.serializer(), resp)
        result.url?.takeIf { it.isNotEmpty() } ?: throw ApiException(result.error ?: "Upload failed", "upload")
    }
}
