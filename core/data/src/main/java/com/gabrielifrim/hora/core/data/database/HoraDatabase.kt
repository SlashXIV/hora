package com.gabrielifrim.hora.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

@Database(entities = [SettingsEntity::class], version = 1, exportSchema = true)
abstract class HoraDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "hora.db"
    }
}

/** Migrations explicites, dans l'ordre. Chacune a son test dans `MigrationTest`. */
internal val HORA_MIGRATIONS: Array<Migration> = arrayOf()

/**
 * Configuration de la base, définie une seule fois : les tests instrumentés
 * appliquent exactement cette fonction, pas une copie destinée à diverger.
 */
fun RoomDatabase.Builder<HoraDatabase>.applyHoraConfiguration(): RoomDatabase.Builder<HoraDatabase> = this
    // Jamais de fallbackToDestructiveMigration : la base est la SEULE copie de
    // l'agenda. Un schéma non migrable doit échouer bruyamment, pas tout effacer.
    .addMigrations(*HORA_MIGRATIONS)
    .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
    // Un seul écrivain à la fois en WAL : une file d'attente plutôt qu'un SQLITE_BUSY.
    .setTransactionExecutor(Executors.newSingleThreadExecutor())
    .addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    })
