package com.gabrielifrim.hora.core.data.di

import android.content.Context
import androidx.room.Room
import com.gabrielifrim.hora.core.data.database.HoraDatabase
import com.gabrielifrim.hora.core.data.database.SettingsDao
import com.gabrielifrim.hora.core.data.database.applyHoraConfiguration
import com.gabrielifrim.hora.core.data.repository.SettingsRepositoryImpl
import com.gabrielifrim.hora.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HoraDatabase =
        Room.databaseBuilder(context, HoraDatabase::class.java, HoraDatabase.NAME).applyHoraConfiguration().build()

    @Provides
    fun settingsDao(db: HoraDatabase): SettingsDao = db.settingsDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
}
