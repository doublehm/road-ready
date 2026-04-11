package com.roadready

import io.ktor.client.HttpClient

expect fun createPlatformHttpClient(): HttpClient
