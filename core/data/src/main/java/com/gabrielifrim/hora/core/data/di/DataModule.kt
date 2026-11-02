package com.gabrielifrim.hora.core.data.di

import android.content.Context
import androidx.room.Room
import com.gabrielifrim.hora.core.data.database.AgendaDao
import com.gabrielifrim.hora.core.data.database.EventDao
import com.gabrielifrim.hora.core.data.database.HoraDatabase
import com.gabrielifrim.hora.core.data.database.ReminderDao
import com.gabrielifrim.hora.core.data.database.SettingsDao
import com.gabrielifrim.hora.core.data.database.applyHoraConfiguration
import com.gabrielifrim.hora.core.data.repository.AgendaRepositoryImpl
import com.gabrielifrim.hora.core.data.repository.EventRepositoryImpl
import com.gabrielifrim.hora.core.data.repository.ReminderSetRepositoryImpl
import com.gabrielifrim.hora.core.data.repository.ReminderStoreImpl
import com.gabrielifrim.hora.core.data.repository.SettingsRepositoryImpl
import com.gabrielifrim.hora.core.domain.repository.AgendaRepository
import com.gabrielifrim.hora.core.domain.repository.EventRepository
import com.gabrielifrim.hora.core.domain.repository.ReminderSetRepository
import com.gabrielifrim.hora.core.domain.repository.ReminderStore
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

    @Provides
    fun reminderDao(db: HoraDatabase): ReminderDao = db.reminderDao()
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

    @Binds
    @Singleton
    abstract fun reminderSets(impl: ReminderSetRepositoryImpl): ReminderSetRepository

    @Binds
    @Singleton
    abstract fun reminderStore(impl: ReminderStoreImpl): ReminderStore
}
