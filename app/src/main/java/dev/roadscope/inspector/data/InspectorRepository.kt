package dev.roadscope.inspector.data

import android.content.Context
import android.net.Uri
import dev.roadscope.inspector.collector.PhoneCollectors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedWriter
import java.util.concurrent.atomic.AtomicReference

data class LiveEvent(val reading: Reading, val rawJson: String)
private data class PendingWrite(val event: TelemetryEvent)

class InspectorRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = InspectorDatabase.get(appContext).telemetryDao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeQueue = Channel<PendingWrite>(capacity = 2_048)
    private val activeSession = AtomicReference<String?>(null)

    private val _fields = MutableStateFlow<Map<String, Reading>>(emptyMap())
    val fields = _fields.asStateFlow()
    private val _events = MutableStateFlow<List<LiveEvent>>(emptyList())
    val events = _events.asStateFlow()
    private val _droppedWrites = MutableStateFlow(0L)
    val droppedWrites = _droppedWrites.asStateFlow()
    private val _storedEventCount = MutableStateFlow(0L)
    val storedEventCount = _storedEventCount.asStateFlow()
    private val _observedCount = MutableStateFlow(0L)
    val observedCount = _observedCount.asStateFlow()
    private val _locationStatus = MutableStateFlow("Location permission needed")
    val locationStatus = _locationStatus.asStateFlow()

    val collector = PhoneCollectors(appContext, ::accept, { _locationStatus.value = it })
    val hasAccelerometer get() = collector.hasAccelerometer
    val hasGyroscope get() = collector.hasGyroscope
    val hasRotationVector get() = collector.hasRotationVector
    fun hasSensor(type: Int) = collector.hasSensor(type)

    init {
        scope.launch {
            _storedEventCount.value = dao.count()
            val batch = ArrayList<TelemetryEvent>(128)
            while (true) {
                val first = withTimeoutOrNull(800) { writeQueue.receive() }
                if (first != null) batch += first.event
                while (batch.size < 128) {
                    val next = writeQueue.tryReceive().getOrNull() ?: break
                    batch += next.event
                }
                if (batch.isNotEmpty()) {
                    val toWrite = batch.toList()
                    batch.clear()
                    try {
                        dao.insertBatch(toWrite)
                        _storedEventCount.value += toWrite.size
                    } catch (_: Exception) {
                        _droppedWrites.value += toWrite.size
                    }
                }
            }
        }
    }

    fun start() = collector.start()
    fun stop() = collector.stop()
    fun refreshLocation() = collector.refreshLocation()
    fun locationPermissionStatus() = collector.locationPermissionStatus()
    fun markLocationPermissionDenied() = collector.markLocationPermissionDenied()

    fun setRecording(enabled: Boolean): Long? {
        if (enabled) {
            val session = System.currentTimeMillis().toString()
            activeSession.set(session)
            return session.toLongOrNull()
        }
        activeSession.set(null)
        return null
    }

    fun isRecording() = activeSession.get() != null

    private fun accept(readings: List<Reading>) {
        if (readings.isEmpty()) return
        _observedCount.value += readings.size
        val updated = _fields.value.toMutableMap()
        val fresh = ArrayList<LiveEvent>(readings.size)
        val session = activeSession.get()
        for (reading in readings) {
            updated[reading.key] = reading
            val json = eventJson(reading)
            fresh += LiveEvent(reading, json)
            if (session != null) {
                val event = TelemetryEvent(
                    sessionId = session,
                    key = reading.key,
                    label = reading.label,
                    category = reading.category,
                    value = reading.value,
                    numericValue = reading.numericValue,
                    unit = reading.unit,
                    source = reading.source,
                    timestampMs = reading.timestampMs,
                    accuracy = reading.accuracy,
                    rawJson = json,
                )
                if (writeQueue.trySend(PendingWrite(event)).isFailure) _droppedWrites.value++
            }
        }
        _fields.value = updated
        _events.value = (fresh.asReversed() + _events.value).take(300)
    }

    suspend fun export(uri: Uri, format: ExportFormat): Result<Int> = runCatching {
        val output = appContext.contentResolver.openOutputStream(uri)
            ?: error("Could not open the selected destination")
        output.bufferedWriter().use { writer ->
            if (format == ExportFormat.CSV) writer.appendLine("timestamp_ms,source,category,field,value,unit,accuracy,raw_json")
            var afterId = 0L
            var total = 0
            while (true) {
                val page = dao.pageAfter(afterId, 1_000)
                if (page.isEmpty()) break
                page.forEach { event ->
                    when (format) {
                        ExportFormat.JSON -> {
                            if (total == 0) writer.appendLine("[") else writer.appendLine(",")
                            writer.append(event.rawJson)
                        }
                        ExportFormat.JSONL -> writer.appendLine(event.rawJson)
                        ExportFormat.CSV -> {
                        val columns = listOf(
                            event.timestampMs.toString(), event.source, event.category, event.key,
                            event.value, event.unit, event.accuracy?.toString().orEmpty(), event.rawJson,
                        )
                        writer.appendLine(columns.joinToString(",") { csv(it) })
                        }
                    }
                    afterId = event.id
                    total++
                }
                writer.flush()
            }
            if (format == ExportFormat.JSON) {
                if (total == 0) writer.append("[")
                writer.appendLine("]")
            }
            total
        }
    }

    suspend fun readRecent(limit: Int = 500) = dao.recent(limit)

    private fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
}

enum class ExportFormat(val mimeType: String, val extension: String) {
    JSON("application/json", "json"), JSONL("application/x-ndjson", "jsonl"), CSV("text/csv", "csv")
}
