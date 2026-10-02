package com.alphadot.app

import android.app.Application

/**
 * AlphaDot application entry point.
 *
 * AlphaDot is an independent, open-source chat client. It does not ship with
 * any API keys and does not depend on a third-party account. It launches
 * straight into local/demo mode and lets the user configure their own AI
 * provider (if they want one) from Settings.
 */
class AlphaDotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AlphaDotApp
            private set
    }
}
