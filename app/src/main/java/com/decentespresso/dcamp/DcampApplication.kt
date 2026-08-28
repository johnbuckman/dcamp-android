package com.decentespresso.dcamp

import android.app.Application
import com.decentespresso.dcamp.api.Services

class DcampApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Services.init(this)
    }
}
