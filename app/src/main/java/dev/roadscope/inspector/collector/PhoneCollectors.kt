package dev.roadscope.inspector.collector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.GnssStatus
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import androidx.core.content.ContextCompat
import dev.roadscope.inspector.data.Reading
import java.util.Locale

class PhoneCollectors(
    context: Context,
    private val onReadings: (List<Reading>) -> Unit,
    private val onLocationStatus: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val sensors = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sensorTimestamp = { System.currentTimeMillis() }
    private val available = sensors.getSensorList(Sensor.TYPE_ALL).map { it.type }.toSet()
    val hasAccelerometer get() = Sensor.TYPE_ACCELEROMETER in available
    val hasGyroscope get() = Sensor.TYPE_GYROSCOPE in available
    val hasRotationVector get() = Sensor.TYPE_ROTATION_VECTOR in available ||
        (Sensor.TYPE_GRAVITY in available && Sensor.TYPE_MAGNETIC_FIELD in available)
    fun hasSensor(type: Int) = type in available

    private var running = false
    private var permissionStatus = "Not requested"
    private val lastGravity = FloatArray(3)
    private val lastMagnetic = FloatArray(3)
    private var hasGravity = false
    private var hasMagnetic = false
    private var gnssRegistered = false

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (index in 0 until status.satelliteCount) if (status.usedInFix(index)) used++
            val now = System.currentTimeMillis()
            onReadings(listOf(
                reading("satellites_visible", "Satellites visible", "Location", status.satelliteCount, "satellites", now, null, "Phone GPS"),
                reading("satellites_used", "Satellites used in fix", "Location", used, "satellites", now, null, "Phone GPS"),
            ))
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

        override fun onSensorChanged(event: SensorEvent) {
            val values = event.values
            val now = sensorTimestamp()
            val readings = when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> listOf(
                    reading("accel_x", "Acceleration X", "Motion", values[0], "m/s²", now, event.accuracy),
                    reading("accel_y", "Acceleration Y", "Motion", values[1], "m/s²", now, event.accuracy),
                    reading("accel_z", "Acceleration Z", "Motion", values[2], "m/s²", now, event.accuracy),
                )
                Sensor.TYPE_GYROSCOPE -> listOf(
                    reading("gyro_x", "Rotation X", "Motion", values[0], "rad/s", now, event.accuracy),
                    reading("gyro_y", "Rotation Y", "Motion", values[1], "rad/s", now, event.accuracy),
                    reading("gyro_z", "Rotation Z", "Motion", values[2], "rad/s", now, event.accuracy),
                )
                Sensor.TYPE_ROTATION_VECTOR -> {
                    val matrix = FloatArray(9)
                    val orientation = FloatArray(3)
                    SensorManager.getRotationMatrixFromVector(matrix, values)
                    SensorManager.getOrientation(matrix, orientation)
                    val degrees = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
                    listOf(
                        reading("compass_heading", "Compass heading", "Motion", degrees, "°", now, event.accuracy),
                        reading("phone_pitch", "Phone pitch", "Motion", Math.toDegrees(orientation[1].toDouble()), "°", now, event.accuracy),
                        reading("phone_roll", "Phone roll", "Motion", Math.toDegrees(orientation[2].toDouble()), "°", now, event.accuracy),
                    )
                }
                Sensor.TYPE_GRAVITY -> {
                    values.copyInto(lastGravity, endIndex = minOf(values.size, lastGravity.size)); hasGravity = true
                    listOf(
                        reading("gravity_x", "Gravity X", "Motion", values[0], "m/s²", now, event.accuracy),
                        reading("gravity_y", "Gravity Y", "Motion", values[1], "m/s²", now, event.accuracy),
                        reading("gravity_z", "Gravity Z", "Motion", values[2], "m/s²", now, event.accuracy),
                    ) + compassFromGravity(now, event.accuracy)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    values.copyInto(lastMagnetic, endIndex = minOf(values.size, lastMagnetic.size)); hasMagnetic = true
                    listOf(
                        reading("magnetic_x", "Magnetic field X", "Motion", values[0], "µT", now, event.accuracy),
                        reading("magnetic_y", "Magnetic field Y", "Motion", values[1], "µT", now, event.accuracy),
                        reading("magnetic_z", "Magnetic field Z", "Motion", values[2], "µT", now, event.accuracy),
                    ) + compassFromGravity(now, event.accuracy)
                }
                Sensor.TYPE_LINEAR_ACCELERATION -> listOf(
                    reading("linear_accel_x", "Linear acceleration X", "Motion", values[0], "m/s²", now, event.accuracy),
                    reading("linear_accel_y", "Linear acceleration Y", "Motion", values[1], "m/s²", now, event.accuracy),
                    reading("linear_accel_z", "Linear acceleration Z", "Motion", values[2], "m/s²", now, event.accuracy),
                )
                Sensor.TYPE_LIGHT -> listOf(reading("ambient_light", "Ambient light", "Environment", values[0], "lux", now, event.accuracy))
                Sensor.TYPE_PRESSURE -> listOf(reading("ambient_pressure", "Barometric pressure", "Environment", values[0], "hPa", now, event.accuracy))
                Sensor.TYPE_AMBIENT_TEMPERATURE -> listOf(reading("ambient_temperature", "Ambient temperature", "Environment", values[0], "°C", now, event.accuracy))
                Sensor.TYPE_RELATIVE_HUMIDITY -> listOf(reading("ambient_humidity", "Relative humidity", "Environment", values[0], "%", now, event.accuracy))
                Sensor.TYPE_PROXIMITY -> listOf(reading("proximity", "Proximity", "Environment", values[0], "cm", now, event.accuracy))
                else -> emptyList()
            }
            if (readings.isNotEmpty()) onReadings(readings)
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onLocationStatus("GPS fix · ${location.provider ?: "location"}")
            val timestamp = System.currentTimeMillis()
            val values = mutableListOf(
                reading("location_latitude", "Latitude", "Location", location.latitude.toFloat(), "°", timestamp, accuracy = null, source = "Phone GPS"),
                reading("location_longitude", "Longitude", "Location", location.longitude.toFloat(), "°", timestamp, accuracy = null, source = "Phone GPS"),
            )
            if (location.hasAltitude()) values += reading("location_altitude", "Altitude", "Location", location.altitude.toFloat(), "m", timestamp, null, "Phone GPS")
            if (location.hasAccuracy()) values += reading("location_accuracy", "GPS accuracy", "Location", location.accuracy, "m", timestamp, null, "Phone GPS")
            if (location.hasSpeed()) values += reading("location_speed", "GPS speed", "Location", location.speed, "m/s", timestamp, null, "Phone GPS")
            if (location.hasBearing()) values += reading("location_bearing", "GPS bearing", "Location", location.bearing, "°", timestamp, null, "Phone GPS")
            if (location.hasSpeedAccuracy()) values += reading("location_speed_accuracy", "GPS speed accuracy", "Location", location.speedAccuracyMetersPerSecond, "m/s", timestamp, null, "Phone GPS")
            if (location.hasVerticalAccuracy()) values += reading("location_altitude_accuracy", "Altitude accuracy", "Location", location.verticalAccuracyMeters, "m", timestamp, null, "Phone GPS")
            if (location.hasBearingAccuracy()) values += reading("location_bearing_accuracy", "Bearing accuracy", "Location", location.bearingAccuracyDegrees, "°", timestamp, null, "Phone GPS")
            if (location.time > 0) values += Reading(
                "location_fix_time", "GPS fix time", "Location", location.time.toString(), null, "ms since epoch", "Phone GPS", timestamp,
            )
            onReadings(values)
        }

        @Deprecated("Deprecated in Android but still required for LocationListener compatibility")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = onLocationStatus("Waiting for GPS fix")
        override fun onProviderDisabled(provider: String) = onLocationStatus("GPS provider disabled")
    }

    fun start() {
        if (running) return
        running = true
        registerIfPresent(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME)
        registerIfPresent(Sensor.TYPE_GYROSCOPE, SensorManager.SENSOR_DELAY_GAME)
        registerIfPresent(Sensor.TYPE_LINEAR_ACCELERATION, SensorManager.SENSOR_DELAY_GAME)
        registerIfPresent(Sensor.TYPE_ROTATION_VECTOR, SensorManager.SENSOR_DELAY_GAME)
        registerIfPresent(Sensor.TYPE_GRAVITY, SensorManager.SENSOR_DELAY_GAME)
        registerIfPresent(Sensor.TYPE_MAGNETIC_FIELD, SensorManager.SENSOR_DELAY_GAME)
        registerIfPresent(Sensor.TYPE_LIGHT, SensorManager.SENSOR_DELAY_NORMAL)
        registerIfPresent(Sensor.TYPE_PRESSURE, SensorManager.SENSOR_DELAY_NORMAL)
        registerIfPresent(Sensor.TYPE_AMBIENT_TEMPERATURE, SensorManager.SENSOR_DELAY_NORMAL)
        registerIfPresent(Sensor.TYPE_RELATIVE_HUMIDITY, SensorManager.SENSOR_DELAY_NORMAL)
        registerIfPresent(Sensor.TYPE_PROXIMITY, SensorManager.SENSOR_DELAY_NORMAL)
        requestPhoneLocation()
    }

    fun stop() {
        if (!running) return
        running = false
        sensors.unregisterListener(sensorListener)
        try { locationManager.removeUpdates(locationListener) } catch (_: SecurityException) { }
        if (gnssRegistered) {
            try { locationManager.unregisterGnssStatusCallback(gnssCallback) } catch (_: SecurityException) { }
            gnssRegistered = false
        }
        onLocationStatus("Paused while app is in background")
    }

    fun refreshLocation() = requestPhoneLocation()
    fun locationPermissionStatus(): String = permissionStatus
    fun markLocationPermissionDenied() { permissionStatus = "Denied" }

    private fun registerIfPresent(type: Int, rate: Int) {
        sensors.getDefaultSensor(type)?.let { sensors.registerListener(sensorListener, it, rate) }
    }

    private fun requestPhoneLocation() {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            onLocationStatus("Location permission needed")
            return
        }
        permissionStatus = if (fine) "Precise permission granted" else "Approximate permission granted"
        try {
            if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                onLocationStatus("GPS provider disabled")
                return
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, locationListener)
            if (fine) {
                locationManager.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
                gnssRegistered = true
            }
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let(locationListener::onLocationChanged)
            onLocationStatus("Waiting for GPS fix")
        } catch (_: SecurityException) {
            permissionStatus = "Permission denied"
            onLocationStatus("Location permission denied")
        } catch (_: IllegalArgumentException) {
            onLocationStatus("GPS provider unavailable")
        }
    }

    private fun compassFromGravity(timestamp: Long, accuracy: Int): List<Reading> {
        if (!hasGravity || !hasMagnetic) return emptyList()
        val rotation = FloatArray(9)
        val inclination = FloatArray(9)
        if (!SensorManager.getRotationMatrix(rotation, inclination, lastGravity, lastMagnetic)) return emptyList()
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotation, orientation)
        val degrees = ((Math.toDegrees(orientation[0].toDouble()) + 360.0) % 360.0).toFloat()
        return listOf(reading("compass_heading", "Compass heading", "Motion", degrees, "°", timestamp, accuracy))
    }

    private fun reading(
        key: String,
        label: String,
        category: String,
        value: Number,
        unit: String,
        timestamp: Long,
        accuracy: Int?,
        source: String = "Phone sensors",
    ) = Reading(
        key, label, category,
        String.format(Locale.US, "%.2f", value.toDouble()), value.toDouble(), unit, source, timestamp,
        accuracy?.let { it.toFloat() },
    )
}
