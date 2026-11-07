package com.example.calculators.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Phase 10: Local Room database for storing mechanical machine parts calibration bills and line items.
 * Persists data on device across application restarts without requiring cloud services.
 */
@Database(
    entities = [BillEntity::class, BillItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BillingDatabase : RoomDatabase() {

    abstract fun billDao(): BillDao
    abstract fun billItemDao(): BillItemDao

    companion object {
        const val DATABASE_NAME = "calculators_billing.db"

        @Volatile
        private var INSTANCE: BillingDatabase? = null

        @Volatile
        private var instanceDbPath: String? = null

        fun getInstance(context: Context): BillingDatabase {
            val appContext = context.applicationContext
            val currentPath = appContext.getDatabasePath(DATABASE_NAME).absolutePath
            val currentInstance = INSTANCE
            if (currentInstance != null && currentInstance.isOpen && instanceDbPath == currentPath) {
                return currentInstance
            }
            return synchronized(this) {
                val existing = INSTANCE
                if (existing != null && existing.isOpen && instanceDbPath == currentPath) {
                    existing
                } else {
                    Room.databaseBuilder(
                        appContext,
                        BillingDatabase::class.java,
                        DATABASE_NAME
                    )
                        .allowMainThreadQueries()
                        .fallbackToDestructiveMigration(dropAllTables = true)
                        .build()
                        .also {
                            INSTANCE = it
                            instanceDbPath = currentPath
                        }
                }
            }
        }

        /**
         * Creates a file-backed database instance for a specific database filename without replacing
         * the active UI singleton, allowing tests to simulate closing and restarting the application
         * while verifying persistent disk storage.
         */
        fun openPersistentInstance(
            context: Context,
            dbName: String = "calculators_restart_verify.db"
        ): BillingDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                BillingDatabase::class.java,
                dbName
            )
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
