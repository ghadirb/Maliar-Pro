package com.maliar.pro.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Contact::class, Income::class, Expense::class, Check::class, Installment::class, Reminder::class, 
               Asset::class, Debt::class, FinancialGoal::class, FixedIncome::class, FinancialPreferences::class,
               ReminderEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun contactDao(): ContactDao
    abstract fun accountingDao(): AccountingDao
    abstract fun reminderDao(): ReminderDao
    abstract fun financialStatusDao(): FinancialStatusDao
    abstract fun reminderEntityDao(): ReminderEntityDao
    
    companion object {
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
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
