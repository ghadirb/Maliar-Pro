package com.maliar.pro.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [Income::class, Expense::class, Check::class, Installment::class, Reminder::class,
               Asset::class, Debt::class, FinancialGoal::class, FixedIncome::class, FinancialPreferences::class,
               ReminderEntity::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountingDao(): AccountingDao
    abstract fun reminderDao(): ReminderDao
    abstract fun financialStatusDao(): FinancialStatusDao
    abstract fun reminderEntityDao(): ReminderEntityDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Keep the historical 3 -> 4 migration so an older installed build can still
        // upgrade without an unnecessary destructive migration before the SMS tables are
        // removed again by 4 -> 5.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `bank_accounts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `bankName` TEXT NOT NULL,
                        `lastDigits` TEXT NOT NULL DEFAULT '',
                        `balance` REAL NOT NULL DEFAULT 0.0,
                        `updatedAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `processed_sms` (
                        `id` TEXT PRIMARY KEY NOT NULL,
                        `processedAt` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        // Removes the legacy SMS-only tables while preserving all normal app data.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `processed_sms`")
                db.execSQL("DROP TABLE IF EXISTS `bank_accounts`")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `contacts`")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `advanced_reminders_new` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL,
                    `reminderType` TEXT NOT NULL, `priority` TEXT NOT NULL, `alertType` TEXT NOT NULL, `triggerTime` INTEGER NOT NULL,
                    `repeatPattern` TEXT NOT NULL, `customRepeatDays` TEXT NOT NULL, `locationLat` REAL, `locationLng` REAL,
                    `locationRadius` INTEGER NOT NULL, `locationName` TEXT NOT NULL, `isCompleted` INTEGER NOT NULL, `completedAt` INTEGER,
                    `createdAt` INTEGER NOT NULL, `tags` TEXT NOT NULL, `relatedPerson` TEXT NOT NULL, `snoozeCount` INTEGER NOT NULL,
                    `lastSnoozed` INTEGER, `notes` TEXT NOT NULL, `category` TEXT NOT NULL, `linkedCheckId` INTEGER, `linkedInstallmentId` INTEGER
                )""".trimIndent())
                db.execSQL("""INSERT INTO `advanced_reminders_new`
                    (`id`,`title`,`description`,`reminderType`,`priority`,`alertType`,`triggerTime`,`repeatPattern`,`customRepeatDays`,
                     `locationLat`,`locationLng`,`locationRadius`,`locationName`,`isCompleted`,`completedAt`,`createdAt`,`tags`,
                     `relatedPerson`,`snoozeCount`,`lastSnoozed`,`notes`,`category`,`linkedCheckId`,`linkedInstallmentId`)
                    SELECT `id`,`title`,`description`,`reminderType`,`priority`,`alertType`,`triggerTime`,`repeatPattern`,`customRepeatDays`,
                     `locationLat`,`locationLng`,`locationRadius`,`locationName`,`isCompleted`,`completedAt`,`createdAt`,`tags`,
                     `relatedPerson`,`snoozeCount`,`lastSnoozed`,`notes`,`category`,`linkedCheckId`,`linkedInstallmentId`
                    FROM `advanced_reminders`""".trimIndent())
                db.execSQL("DROP TABLE `advanced_reminders`")
                db.execSQL("ALTER TABLE `advanced_reminders_new` RENAME TO `advanced_reminders`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "maliar_pro_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
