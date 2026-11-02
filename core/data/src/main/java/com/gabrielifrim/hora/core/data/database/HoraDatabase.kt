package com.gabrielifrim.hora.core.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.Executors

@Database(
    entities = [SettingsEntity::class, AgendaEntity::class, EventEntity::class, ReminderSetEntity::class, FiredReminderEntity::class, SnoozeEntity::class],
    version = HoraDatabase.VERSION,
    exportSchema = true,
)
abstract class HoraDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun agendaDao(): AgendaDao
    abstract fun eventDao(): EventDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val NAME = "hora.db"

        /** Version du schéma ; une sauvegarde plus récente ne se restaure pas. */
        const val VERSION = 3
    }
}

/**
 * v1 → v2 (J2) : agendas et événements. Le SQL reprend exactement celui du schéma
 * exporté (schemas/…/2.json), que `MigrationTest` compare au résultat.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `agenda` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`color_key` TEXT NOT NULL, `hidden` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `event` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uid` TEXT NOT NULL, " +
                "`agenda_id` INTEGER NOT NULL, `title` TEXT NOT NULL, `location` TEXT NOT NULL, `notes` TEXT NOT NULL, " +
                "`color_key` TEXT, `all_day` INTEGER NOT NULL, `start` INTEGER NOT NULL, `end` INTEGER NOT NULL, `time_zone` TEXT, " +
                "FOREIGN KEY(`agenda_id`) REFERENCES `agenda`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_agenda_id` ON `event` (`agenda_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_event_uid` ON `event` (`uid`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_all_day_start_end` ON `event` (`all_day`, `start`, `end`)")
    }
}

/**
 * v2 → v3 (J3) : récurrence (RRULE, exceptions, occurrences modifiées à part),
 * rappels par événement et par agenda, jeux de rappels, journal des rappels
 * présentés et reports. Colonnes ajoutées vides : les événements existants restent
 * des événements simples, sans rappel.
 */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `agenda` ADD COLUMN `default_reminders` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `reminders` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `rrule` TEXT")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `exdates` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `parent_id` INTEGER")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `recurrence_id` INTEGER")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_parent_id` ON `event` (`parent_id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `reminder_set` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `spec` TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `fired_reminder` (`event_id` INTEGER NOT NULL, `occurrence` INTEGER NOT NULL, " +
                "`reminder` TEXT NOT NULL, `fired_at` INTEGER NOT NULL, PRIMARY KEY(`event_id`, `occurrence`, `reminder`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `snooze` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `event_id` INTEGER NOT NULL, " +
                "`occurrence` INTEGER NOT NULL, `trigger_at` INTEGER NOT NULL, `alarm` INTEGER NOT NULL)",
        )
    }
}

/** Migrations explicites, dans l'ordre. Chacune a son test dans `MigrationTest`. */
internal val HORA_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

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
