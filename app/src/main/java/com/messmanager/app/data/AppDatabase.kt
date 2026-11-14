package com.messmanager.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    entities = [
        Mess::class, Member::class, Expense::class, ExpenseSplit::class, Payment::class, Deposit::class,
        BazarEntry::class, BazarItem::class, BazarDuty::class, Note::class, MonthlyCycle::class,
        AuditLog::class, AppSettings::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): MessDao

    companion object {
        /** Add explicit Migration objects here when bumping the version. Destructive fallback is never used. */
        val MIGRATIONS: Array<Migration> = arrayOf()

        @Volatile private var instance: AppDatabase? = null

        fun get(ctx: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "messmanager.db")
                .addMigrations(*MIGRATIONS)
                .build().also { instance = it }
        }
    }
}
