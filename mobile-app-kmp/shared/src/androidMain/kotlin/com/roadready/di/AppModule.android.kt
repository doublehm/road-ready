package com.roadready.di

import com.roadready.data.repository.TokenStorage
import com.russhwolf.settings.Settings
import org.koin.dsl.module

actual fun platformModule() = module {
    single<TokenStorage> { AndroidTokenStorage() }
}

class AndroidTokenStorage : TokenStorage {
    private val settings = Settings()

    override suspend fun getToken(): String? = settings.getStringOrNull(KEY_TOKEN)

    override suspend fun saveToken(token: String) {
        settings.putString(KEY_TOKEN, token)
    }

    override suspend fun clearToken() {
        settings.remove(KEY_TOKEN)
    }

    companion object {
        private const val KEY_TOKEN = "auth_token"
    }
}
