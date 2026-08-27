package com.maliar.pro.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Contact::class, Income::class, Expense::class, Check::class, Installment::class, Reminder::class, 
               Asset::class, Debt::class, FinancialGoal::class, FixedIncome::class, FinancialPreferences::class,
               ReminderEntity::class, Debtor::class, DebtorPayment::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun contactDao(): ContactDao
    abstract fun accountingDao(): AccountingDao
    abstract fun reminderDao(): ReminderDao
    abstract fun financialStatusDao(): FinancialStatusDao
    abstract fun reminderEntityDao(): ReminderEntityDao
    abstract fun debtorDao(): DebtorDao
    
    companion object {
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE advanced_reminders ADD COLUMN soundUri TEXT NOT NULL DEFAULT 'DEFAULT_ALARM'")
            }
        }
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Play-Safe test build (test-07-clean-sms): the bank_accounts / processed_sms
        // tables and their migration were removed along with the rest of SMS Banking.
        // fallbackToDestructiveMigration() below is enough to take any existing installed
        // copy of the app (schema versions 1-4, with or without those two tables) to this
        // build's version-4 schema.
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "maliar_pro_database"
                ).addMigrations(MIGRATION_5_6)
                 .fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
