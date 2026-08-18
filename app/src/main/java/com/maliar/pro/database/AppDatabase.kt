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
               ReminderEntity::class],
    version = 5,
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

        // Removes the legacy SMS-only tables while preserving all normal app data.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `processed_sms`")
                db.execSQL("DROP TABLE IF EXISTS `bank_accounts`")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "maliar_pro_database"
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
