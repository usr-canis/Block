package dev.roadscope.inspector.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

data class Reading(
    val key: String,
    val label: String,
    val category: String,
    val value: String,
    val numericValue: Double?,
    val unit: String,
    val source: String,
    val timestampMs: Long,
    val accuracy: Float? = null,
)

@Entity(
    tableName = "telemetry_events",
    indices = [Index("timestampMs"), Index("sessionId"), Index("key")],
)
data class TelemetryEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val key: String,
    val label: String,
    val category: String,
    val value: String,
    val numericValue: Double?,
    val unit: String,
    val source: String,
    val timestampMs: Long,
    val accuracy: Float?,
    val rawJson: String,
    val rawPayloadHex: String? = null,
)

data class InvestigationItem(
    val category: String,
    val name: String,
    val status: String,
    val note: String,
)

val investigatedItems = listOf(
    InvestigationItem("Vehicle", "Vehicle speed", "Requires advanced access", "No public API exposes AA session vehicle speed to a regular phone app."),
    InvestigationItem("Vehicle", "Dashboard speed", "Requires advanced access", "Head-unit or vehicle hardware access required."),
    InvestigationItem("Vehicle", "Engine RPM", "Requires advanced access", "May be offered by the AA sensor channel on some head units."),
    InvestigationItem("Vehicle", "Odometer", "Requires advanced access", "Vehicle hardware API or head-unit implementation required."),
    InvestigationItem("Vehicle", "Fuel level and range", "Requires advanced access", "Head-unit support varies; not readable from Google's active session."),
    InvestigationItem("Vehicle", "Fuel consumption", "Requires advanced access", "Only if the vehicle/head unit provides it in an owned AAP session."),
    InvestigationItem("Vehicle", "Gear, brake, ignition", "Requires advanced access", "Vehicle or privileged head-unit data required."),
    InvestigationItem("Vehicle", "Driving and engine state", "Requires advanced access", "No ordinary-phone API reports authoritative vehicle state."),
    InvestigationItem("Vehicle", "VIN and diagnostics", "Unsupported", "No standard public Android Auto phone API exposes these fields."),
    InvestigationItem("Vehicle systems", "Door and light status", "Requires advanced access", "Vehicle-specific data; head-unit support and permissions vary."),
    InvestigationItem("Vehicle systems", "Tire pressure", "Requires advanced access", "Vehicle-specific data; head-unit support and permissions vary."),
    InvestigationItem("Vehicle systems", "HVAC and cabin environment", "Requires advanced access", "Vehicle-specific data; head-unit support and permissions vary."),
    InvestigationItem("Vehicle systems", "Passenger and trailer status", "Requires advanced access", "No general phone API; provider-specific head-unit data required."),
    InvestigationItem("EV", "Battery charge and capacity", "Requires advanced access", "Some protocol definitions exist; actual availability is vehicle-specific."),
    InvestigationItem("EV", "Remaining range and charging", "Requires advanced access", "Some protocol definitions exist; actual availability is vehicle-specific."),
    InvestigationItem("EV", "Connector, energy use, trip model", "Requires advanced access", "Some protocol definitions exist; actual availability is vehicle-specific."),
    InvestigationItem("Phone location", "GPS coordinates and accuracy", "Available but inactive", "Phone GPS; requires location permission and a usable provider."),
    InvestigationItem("Phone location", "GPS speed and bearing", "Available but inactive", "Derived from phone location fixes; never vehicle speed."),
    InvestigationItem("Phone location", "Satellite count", "Available but inactive", "Android GNSS status; visible/used satellite counts require precise location permission."),
    InvestigationItem("Phone location", "Dead-reckoning data", "Unsupported", "No portable public Android API provides a consistent dead-reckoning feed."),
    InvestigationItem("Phone location", "Compass metadata", "Available but inactive", "Provided through the phone's rotation vector when present."),
    InvestigationItem("Phone motion", "Accelerometer X / Y / Z", "Available but inactive", "Phone sensor; device orientation and mounting affect readings."),
    InvestigationItem("Phone motion", "Linear acceleration", "Available but inactive", "Phone sensor when present; removes gravity estimate in the device frame."),
    InvestigationItem("Phone motion", "Gravity and magnetic field", "Available but inactive", "Phone sensor axes; magnetic field is not a vehicle compass."),
    InvestigationItem("Phone motion", "Gyroscope X / Y / Z", "Available but inactive", "Phone sensor; device capability varies."),
    InvestigationItem("Phone motion", "Compass heading", "Available but inactive", "Phone rotation-vector sensor; may be affected by calibration and magnetic interference."),
    InvestigationItem("Phone motion", "Orientation and sensor metadata", "Available but inactive", "Device hardware and calibration determine the available sensor set."),
    InvestigationItem("Phone environment", "Light, pressure, temperature, humidity", "Available but inactive", "Optional ambient phone sensors; many phones do not include them."),
    InvestigationItem("Phone environment", "Proximity", "Available but inactive", "Phone proximity sensor when present; not vehicle occupancy."),
    InvestigationItem("Android Auto", "Connection state and transport", "Unknown", "Android does not expose the Google AA session state to ordinary apps."),
    InvestigationItem("Android Auto", "Channels, protocol, raw events", "Requires advanced access", "Requires owning a compatible AAP session or authorized head-unit capture."),
    InvestigationItem("Android Auto", "Sensor advertisements and subscriptions", "Requires advanced access", "Negotiated inside the encrypted AAP session; not visible to a passive app."),
    InvestigationItem("Android Auto", "Unknown protobuf fields / payloads", "Requires advanced access", "Only preserved when an authorized raw protocol capture is imported."),
)

fun eventJson(reading: Reading): String = buildString {
    val eventType = if (reading.source == "Phone GPS") "ANDROID_LOCATION_FIX" else "ANDROID_SENSOR_EVENT"
    append('{')
    append("\"timestamp\":").append(reading.timestampMs).append(',')
    append("\"source\":\"").append(reading.source).append("\",")
    append("\"channel\":null,")
    append("\"message_type\":\"").append(eventType).append("\",")
    append("\"field\":\"").append(reading.key).append("\",")
    append("\"label\":\"").append(reading.label).append("\",")
    append("\"value\":\"").append(reading.value.replace("\\", "\\\\").replace("\"", "\\\""))
        .append("\",")
    reading.numericValue?.let { append("\"value_number\":").append(it).append(',') }
    append("\"unit\":\"").append(reading.unit).append("\",")
    append("\"category\":\"").append(reading.category).append("\"")
    reading.accuracy?.let { append(",\"accuracy\":").append(it) }
    append(",\"raw_payload_hex\":null")
    append('}')
}
