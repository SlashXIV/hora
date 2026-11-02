package com.gabrielifrim.hora.core.data.di

import android.content.Context
import androidx.room.Room
import com.gabrielifrim.hora.core.data.database.AgendaDao
import com.gabrielifrim.hora.core.data.database.EventDao
import com.gabrielifrim.hora.core.data.database.HoraDatabase
import com.gabrielifrim.hora.core.data.database.SettingsDao
import com.gabrielifrim.hora.core.data.database.applyHoraConfiguration
import com.gabrielifrim.hora.core.data.repository.AgendaRepositoryImpl
import com.gabrielifrim.hora.core.data.repository.EventRepositoryImpl
import com.gabrielifrim.hora.core.data.repository.SettingsRepositoryImpl
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.domain.repository.EventRepository
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

    @Provides
    fun agendaDao(db: HoraDatabase): AgendaDao = db.agendaDao()

    @Provides
    fun eventDao(db: HoraDatabase): EventDao = db.eventDao()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun agendas(impl: AgendaRepositoryImpl): AgendaRepository

    @Binds
    @Singleton
    abstract fun events(impl: EventRepositoryImpl): EventRepository
}
