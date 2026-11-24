package com.mue.core.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(factory: HttpClientFactory): HttpClient = factory.create()

    @Provides
    @Singleton
    fun provideListenApiClient(httpClient: HttpClient): ListenApiClient = ListenApiClient(httpClient)
}

