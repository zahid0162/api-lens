package com.apilens.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ApiEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ApiDatabase : RoomDatabase() {
    abstract fun apiDao(): ApiDao

    companion object {
        @Volatile
        private var instance: ApiDatabase? = null

        fun getInstance(context: Context): ApiDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ApiDatabase::class.java,
                    "api_lens_traces.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
