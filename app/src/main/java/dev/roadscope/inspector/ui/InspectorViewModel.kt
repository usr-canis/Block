package dev.roadscope.inspector.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.roadscope.inspector.data.ExportFormat
import dev.roadscope.inspector.data.InspectorRepository
import dev.roadscope.inspector.data.LiveEvent
import dev.roadscope.inspector.data.Reading
import dev.roadscope.inspector.data.TelemetryEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppTab(val title: String) { DASHBOARD("Overview"), DATA("Live data"), EVENTS("Events"), AVAILABILITY("Availability") }

data class InspectorState(
    val tab: AppTab = AppTab.DASHBOARD,
    val fields: Map<String, Reading> = emptyMap(),
    val events: List<LiveEvent> = emptyList(),
    val recording: Boolean = false,
    val recordingSinceMs: Long? = null,
    val recordingDurationMs: Long = 0,
    val observedEvents: Long = 0,
    val eventsPerSecond: Long = 0,
    val storedEvents: Long = 0,
    val droppedEvents: Long = 0,
    val locationStatus: String = "Location permission needed",
    val locationPermissionStatus: String = "Not requested",
    val pausedEvents: Boolean = false,
    val pausedEventSnapshot: List<LiveEvent> = emptyList(),
    val eventFilter: String = "",
    val search: String = "",
    val selectedField: Reading? = null,
    val histories: Map<String, List<Float>> = emptyMap(),
    val historyTimes: Map<String, List<Long>> = emptyMap(),
    val message: String? = null,
)

class InspectorViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = InspectorRepository(application)
    private val _state = MutableStateFlow(InspectorState())
    val state = _state.asStateFlow()
    private var foreground = false
    private var previousObserved = 0L
    private var previousFields: Map<String, Long> = emptyMap()

    init {
        viewModelScope.launch {
            repository.fields.collect { fields ->
                val histories = _state.value.histories.toMutableMap()
                val historyTimes = _state.value.historyTimes.toMutableMap()
                fields.forEach { (key, reading) ->
                    if (previousFields[key] != reading.timestampMs && reading.numericValue != null) {
                        val current = histories[key].orEmpty()
                        histories[key] = (current + reading.numericValue.toFloat()).takeLast(60)
                        historyTimes[key] = (historyTimes[key].orEmpty() + reading.timestampMs).takeLast(60)
                    }
                }
                previousFields = fields.mapValues { it.value.timestampMs }
                _state.value = _state.value.copy(fields = fields, histories = histories, historyTimes = historyTimes)
            }
        }
        viewModelScope.launch { repository.events.collect { _state.value = _state.value.copy(events = it) } }
        viewModelScope.launch { repository.locationStatus.collect { _state.value = _state.value.copy(locationStatus = it) } }
        viewModelScope.launch { repository.droppedWrites.collect { _state.value = _state.value.copy(droppedEvents = it) } }
        viewModelScope.launch { repository.storedEventCount.collect { _state.value = _state.value.copy(storedEvents = it) } }
        viewModelScope.launch {
            while (true) {
                val observed = repository.observedCount.value
                val recordingSince = _state.value.recordingSinceMs
                _state.value = _state.value.copy(
                    observedEvents = observed,
                    eventsPerSecond = observed - previousObserved,
                    recordingDurationMs = recordingSince?.let { System.currentTimeMillis() - it } ?: 0,
                    locationPermissionStatus = repository.locationPermissionStatus(),
                )
                previousObserved = observed
                delay(1_000)
            }
        }
    }

    fun onForeground() {
        if (!foreground) { foreground = true; repository.start() }
    }

    fun onBackground() {
        foreground = false
        repository.stop()
    }

    fun selectTab(tab: AppTab) { _state.value = _state.value.copy(tab = tab, selectedField = null) }
    fun toggleRecording() {
        val enabled = !_state.value.recording
        val timestamp = repository.setRecording(enabled)
        _state.value = _state.value.copy(
            recording = enabled,
            recordingSinceMs = if (enabled) timestamp else null,
            recordingDurationMs = 0,
            message = if (enabled) "Recording started on this device" else "Recording stopped",
        )
    }
    fun setSearch(value: String) { _state.value = _state.value.copy(search = value) }
    fun setEventFilter(value: String) { _state.value = _state.value.copy(eventFilter = value) }
    fun toggleEventPause() {
        val pause = !_state.value.pausedEvents
        _state.value = _state.value.copy(
            pausedEvents = pause,
            pausedEventSnapshot = if (pause) _state.value.events else emptyList(),
        )
    }
    fun selectField(reading: Reading?) { _state.value = _state.value.copy(selectedField = reading) }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    fun export(uri: Uri, format: ExportFormat) {
        viewModelScope.launch {
            repository.export(uri, format).fold(
                onSuccess = { _state.value = _state.value.copy(message = "Exported $it events") },
                onFailure = { _state.value = _state.value.copy(message = "Export failed: ${it.message ?: "unknown error"}") },
            )
        }
    }

    suspend fun recentEvents(): List<TelemetryEvent> = repository.readRecent()

    fun locationPermissionDenied() {
        repository.markLocationPermissionDenied()
        _state.value = _state.value.copy(locationStatus = "Permission denied", locationPermissionStatus = "Denied")
    }

    fun locationPermissionGranted() {
        repository.refreshLocation()
    }

    fun hasAccelerometer() = repository.hasAccelerometer
    fun hasGyroscope() = repository.hasGyroscope
    fun hasRotationVector() = repository.hasRotationVector
    fun hasSensor(type: Int) = repository.hasSensor(type)
}
