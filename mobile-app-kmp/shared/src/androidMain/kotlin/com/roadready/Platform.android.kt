package com.roadready

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

actual fun createPlatformHttpClient(): HttpClient {
    return HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = false
            })
        }
        install(Logging) {
            level = LogLevel.HEADERS
        }
    }
}

actual fun logDebug(tag: String, message: String) {
    android.util.Log.d(tag, message)
}

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    android.util.Log.e(tag, message, throwable)
}
