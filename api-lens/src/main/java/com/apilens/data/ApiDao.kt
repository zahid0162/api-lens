package com.apilens.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiDao {

    @Query("SELECT * FROM api_transactions ORDER BY requestTimestampMs DESC")
    fun observeAll(): Flow<List<ApiEntity>>

    @Query("SELECT * FROM api_transactions WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ApiEntity?>

    @Query("SELECT * FROM api_transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ApiEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ApiEntity)

    @Query(
        "DELETE FROM api_transactions WHERE id NOT IN " +
            "(SELECT id FROM api_transactions ORDER BY requestTimestampMs DESC LIMIT :maxRequests)"
    )
    suspend fun trimToLimit(maxRequests: Int)

    @Query("DELETE FROM api_transactions")
    suspend fun clearAll()
}
