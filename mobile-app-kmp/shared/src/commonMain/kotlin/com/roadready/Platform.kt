package com.roadready

import io.ktor.client.HttpClient

expect fun createPlatformHttpClient(): HttpClient

expect fun logDebug(tag: String, message: String)
expect fun logError(tag: String, message: String, throwable: Throwable? = null)
