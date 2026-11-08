package app.widapp.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import java.io.File

/**
 * Tiny persistent store: one JSON file, mirrored in a [StateFlow].
 * Loaded synchronously on first access (files are small), written atomically on every change.
 */
class JsonFileStore<T>(
    private val file: File,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val state: MutableStateFlow<T> by lazy { MutableStateFlow(read()) }

    val flow: StateFlow<T> get() = state.asStateFlow()
    val value: T get() = state.value

    private fun read(): T = try {
        if (file.exists()) AppJson.decodeFromString(serializer, file.readText()) else default()
    } catch (e: Exception) {
        Log.w("JsonFileStore", "Failed to read ${file.name}, using defaults", e)
        default()
    }

    fun update(transform: (T) -> T) {
        var next: T
        synchronized(this) {
            next = transform(state.value)
            state.value = next
        }
        scope.launch { persist(next) }
    }

    private suspend fun persist(value: T) = writeLock.withLock {
        try {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(AppJson.encodeToString(serializer, value))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText()); tmp.delete()
            }
        } catch (e: Exception) {
            Log.e("JsonFileStore", "Failed to write ${file.name}", e)
        }
    }
}
