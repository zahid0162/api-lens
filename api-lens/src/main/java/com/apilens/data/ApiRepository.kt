package com.apilens.data

import android.content.Context
import com.apilens.ApiLensConfig
import com.apilens.core.ApiTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates reactive StateFlow history and asynchronous Room persistence.
 * Network threads enqueue work without blocking on SQLite disk I/O.
 */
class ApiRepository(
    context: Context?,
    initialConfig: ApiLensConfig,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val daoOverride: ApiDao? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val writeMutex = Mutex()

    @Volatile
    private var config: ApiLensConfig = initialConfig

    private val dao: ApiDao? by lazy {
        daoOverride ?: if (config.enableDatabase && context != null) {
            ApiDatabase.getInstance(context).apiDao()
        } else {
            null
        }
    }

    private val _transactions = MutableStateFlow<List<ApiTransaction>>(emptyList())
    val transactions: StateFlow<List<ApiTransaction>> = _transactions.asStateFlow()

    init {
        if (initialConfig.enableDatabase && (daoOverride != null || context != null)) {
            scope.launch {
                val activeDao = dao ?: return@launch
                activeDao.observeAll().collect { entities ->
                    _transactions.value = entities.map { it.toDomain() }
                }
            }
        }
    }

    fun updateConfig(newConfig: ApiLensConfig) {
        val oldMax = config.maxRequests
        config = newConfig
        if (newConfig.maxRequests < oldMax) {
            trimInMemory(newConfig.maxRequests)
            if (newConfig.enableDatabase) {
                scope.launch {
                    writeMutex.withLock {
                        dao?.trimToLimit(newConfig.maxRequests)
                    }
                }
            }
        }
    }

    fun observeTransaction(id: String): Flow<ApiTransaction?> {
        return transactions.map { list -> list.firstOrNull { it.id == id } }
    }

    fun recordAsync(transaction: ApiTransaction) {
        // Update in-memory list immediately so UI and badge react without DB latency.
        upsertInMemory(transaction)

        if (config.enableDatabase) {
            val activeDao = dao ?: return
            val maxLimit = config.maxRequests
            scope.launch {
                writeMutex.withLock {
                    activeDao.upsert(ApiEntity.fromDomain(transaction))
                    activeDao.trimToLimit(maxLimit)
                }
            }
        }
    }

    fun clearAllAsync() {
        _transactions.value = emptyList()
        if (config.enableDatabase) {
            val activeDao = dao ?: return
            scope.launch {
                writeMutex.withLock {
                    activeDao.clearAll()
                }
            }
        }
    }

    private fun upsertInMemory(transaction: ApiTransaction) {
        synchronized(_transactions) {
            val current = _transactions.value.toMutableList()
            val idx = current.indexOfFirst { it.id == transaction.id }
            if (idx >= 0) {
                current[idx] = transaction
            } else {
                current.add(0, transaction)
            }
            val sorted = current
                .sortedByDescending { it.request.timestampMs }
                .take(config.maxRequests)
            _transactions.value = sorted
        }
    }

    private fun trimInMemory(maxRequests: Int) {
        synchronized(_transactions) {
            _transactions.value = _transactions.value.take(maxRequests)
        }
    }
}
