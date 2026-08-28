package com.decentespresso.dcamp.api

import android.content.Context

/** Process-wide singletons. Initialized once from DcampApplication.onCreate. */
object Services {
    lateinit var api: DcampApi
        private set
    lateinit var session: SessionStore
        private set

    fun init(context: Context) {
        AppConfig.init(context)
        val tokenStore = TokenStore(context)
        api = DcampApi(tokenStore)
        session = SessionStore(api, SessionStore.deviceID(context))
    }
}
