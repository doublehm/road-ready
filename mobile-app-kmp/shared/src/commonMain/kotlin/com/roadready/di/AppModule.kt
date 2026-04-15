package com.roadready.di

import com.roadready.data.remote.ApiClient
import com.roadready.data.repository.AuthRepository
import com.roadready.data.repository.TokenStorage
import com.roadready.ui.screens.auth.LoginViewModel
import com.roadready.ui.screens.auth.RegisterViewModel
import com.roadready.ui.screens.student.StudentHomeViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

const val DEFAULT_BASE_URL = "http://10.66.66.2:8001/api/v1/"

val sharedModule = module {
    single {
        ApiClient(
            baseUrl = DEFAULT_BASE_URL,
            tokenProvider = { get<AuthRepository>().authState.value.token },
        )
    }

    single { AuthRepository(get(), get()) }

    factoryOf(::LoginViewModel)
    factoryOf(::RegisterViewModel)
    factoryOf(::StudentHomeViewModel)
}

// Platform modules provide TokenStorage implementation
expect fun platformModule(): org.koin.core.module.Module
