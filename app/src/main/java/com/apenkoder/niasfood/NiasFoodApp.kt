package com.apenkoder.niasfood

import android.app.Application
import com.apenkoder.niasfood.di.AppContainer

class NiasFoodApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
