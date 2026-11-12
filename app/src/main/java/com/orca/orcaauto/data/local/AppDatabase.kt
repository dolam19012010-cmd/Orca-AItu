package com.orca.orcaauto.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NotificationEntity::class,
        SummaryHistoryEntity::class,
        DailyMemoryEntity::class,
        SmartAlarmEntity::class,
        SharedNoteEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun notificationDao(): NotificationDao
    abstract fun summaryDao(): SummaryDao
    abstract fun dailyMemoryDao(): DailyMemoryDao
    abstract fun smartAlarmDao(): SmartAlarmDao
    abstract fun sharedNoteDao(): SharedNoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Safe migration preserving all notifications and summaries
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Safe migration preserving all notifications and summaries
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_isProcessed_timestamp ON notifications(isProcessed, timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notifications_appPackage_timestamp ON notifications(appPackage, timestamp)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE summary_history ADD COLUMN modelInfo TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS smart_alarms (
                        alarmId TEXT PRIMARY KEY NOT NULL,
                        taskTitle TEXT NOT NULL,
                        targetTimeMillis INTEGER NOT NULL,
                        targetDateLabel TEXT NOT NULL,
                        cascadeMode TEXT NOT NULL DEFAULT 'x2',
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        isClockAppStaged INTEGER NOT NULL DEFAULT 0,
                        clockAppStageTimeMillis INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_smart_alarms_alarmId ON smart_alarms(alarmId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_smart_alarms_status_targetTimeMillis ON smart_alarms(status, targetTimeMillis)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_smart_alarms_isClockAppStaged_targetTimeMillis ON smart_alarms(isClockAppStaged, targetTimeMillis)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE summary_history ADD COLUMN sessionId TEXT NOT NULL DEFAULT 'session_main_default'")
                db.execSQL("ALTER TABLE summary_history ADD COLUMN sessionName TEXT NOT NULL DEFAULT 'Phi\u00EAn ch\u00EDnh'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_summary_history_sessionId ON summary_history(sessionId)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS shared_notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        isPrivate INTEGER NOT NULL DEFAULT 0,
                        isPinned INTEGER NOT NULL DEFAULT 0,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shared_notes_isDeleted_updatedAt ON shared_notes(isDeleted, updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shared_notes_isPrivate ON shared_notes(isPrivate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shared_notes_updatedAt ON shared_notes(updatedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_shared_notes_isPinned ON shared_notes(isPinned)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "orca_auto_v3.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8
                    )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
