package com.mue.core.lyrics.di

import com.mue.core.lyrics.DefaultLyricsRepository
import com.mue.core.lyrics.LyricsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LyricsModule {
    @Binds
    @Singleton
    abstract fun bindLyricsRepository(impl: DefaultLyricsRepository): LyricsRepository
}

