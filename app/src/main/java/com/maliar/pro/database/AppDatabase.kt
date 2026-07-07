package com.maliar.pro.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

@Database(
    entities = [Contact::class, Income::class, Expense::class, Check::class, Installment::class, Reminder::class, 
               Asset::class, Debt::class, FinancialGoal::class, FixedIncome::class, FinancialPreferences::class,
               ReminderEntity::class, BankAccount::class, ProcessedSms::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun contactDao(): ContactDao
    abstract fun accountingDao(): AccountingDao
    abstract fun reminderDao(): ReminderDao
    abstract fun financialStatusDao(): FinancialStatusDao
    abstract fun reminderEntityDao(): ReminderEntityDao
    abstract fun bankAccountDao(): BankAccountDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Adds the two new tables for the bank-SMS feature without touching any existing
        // table - plain fallbackToDestructiveMigration() below would otherwise wipe every
        // user's accounting/reminders/contacts data the moment this version shipped.
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
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "maliar_pro_database"
                ).addMigrations(MIGRATION_3_4)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
