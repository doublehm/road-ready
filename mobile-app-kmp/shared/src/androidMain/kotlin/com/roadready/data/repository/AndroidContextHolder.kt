package com.roadready.data.repository

import android.content.Context

object AndroidContextHolder {
    lateinit var applicationContext: Context
        private set

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }
}
