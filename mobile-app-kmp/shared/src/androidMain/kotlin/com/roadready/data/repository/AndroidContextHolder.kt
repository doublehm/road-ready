package com.roadready.data.repository

import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

object AndroidContextHolder {
    lateinit var applicationContext: Context
        private set

    private var activityRef: WeakReference<Activity>? = null
    val activity: Activity? get() = activityRef?.get()

    fun init(context: Context) {
        applicationContext = context.applicationContext
        (context as? Activity)?.let { activityRef = WeakReference(it) }
    }

    fun setActivity(activity: Activity?) {
        activityRef = activity?.let { WeakReference(it) }
    }
}
