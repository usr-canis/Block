package dev.roadscope.inspector.ui

import android.hardware.Sensor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.roadscope.inspector.data.InvestigationItem
import dev.roadscope.inspector.data.Reading
import dev.roadscope.inspector.data.investigatedItems
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Background = Color(0xFF0B1110)
private val SurfaceDark = Color(0xFF121B19)
private val SurfaceRaised = Color(0xFF182320)
private val Line = Color(0xFF263631)
private val Mint = Color(0xFF9AE6C1)
private val TextMain = Color(0xFFE8F0EB)
private val TextSoft = Color(0xFF9BAAA2)
private val Amber = Color(0xFFE6B96E)
private val Coral = Color(0xFFED8F7D)

@Composable
fun RoadscopeApp(
    viewModel: InspectorViewModel,
    onRequestLocation: () -> Unit,
    onExportJson: () -> Unit,
    onExportJsonl: () -> Unit,
    onExportCsv: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Mint, onPrimary = Background, secondary = Mint, background = Background,
        surface = SurfaceDark, onSurface = TextMain, onBackground = TextMain,
    )) {
        Scaffold(
            containerColor = Background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = { BottomNavigation(state.tab, viewModel::selectTab) },
        ) { insets ->
            Column(Modifier.fillMaxSize().padding(insets)) {
                Header(state.recording)
                when (state.tab) {
                    AppTab.DASHBOARD -> Dashboard(state, viewModel, onRequestLocation, onExportJson, onExportJsonl, onExportCsv)
                    AppTab.DATA -> DataExplorer(state, viewModel)
                    AppTab.EVENTS -> EventViewer(state, viewModel)
                    AppTab.AVAILABILITY -> AvailabilityScreen(state, viewModel)
                }
            }
        }
        state.selectedField?.let { reading ->
            SensorDetail(
                reading = reading,
                history = state.histories[reading.key].orEmpty(),
                timestamps = state.historyTimes[reading.key].orEmpty(),
                onDismiss = { viewModel.selectField(null) },
            )
        }
    }
}

@Composable
private fun Header(recording: Boolean) {
    Row(
        Modifier.fillMaxWidth().background(Background).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Mint), contentAlignment = Alignment.Center) {
            Text("R", color = Background, fontSize = 15.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Text("ROADSCOPE", fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
        Spacer(Modifier.weight(1f))
        StatusPill(if (recording) "RECORDING" else "LOCAL ONLY", if (recording) Coral else TextSoft)
    }
}

@Composable
private fun BottomNavigation(selected: AppTab, onSelect: (AppTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(SurfaceDark).border(1.dp, Line).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        AppTab.entries.forEach { tab ->
            val active = selected == tab
            Column(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable { onSelect(tab) }.padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(tabIcon(tab), color = if (active) Mint else TextSoft, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(tab.title, color = if (active) Mint else TextSoft, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

private fun tabIcon(tab: AppTab) = when (tab) {
    AppTab.DASHBOARD -> "◫"
    AppTab.DATA -> "⌁"
    AppTab.EVENTS -> "≋"
    AppTab.AVAILABILITY -> "◎"
}

@Composable
private fun Dashboard(
    state: InspectorState,
    viewModel: InspectorViewModel,
    onRequestLocation: () -> Unit,
    onExportJson: () -> Unit,
    onExportJsonl: () -> Unit,
    onExportCsv: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val speed = state.fields["location_speed"]
    val latitude = state.fields["location_latitude"]
    val longitude = state.fields["location_longitude"]
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionEyebrow("PHONE TELEMETRY  /  LIVE")
            Text("A clear view of what\nyour phone can see.", fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1.15f)) {
                    CardSurface {
                        Text("GPS SPEED", color = TextSoft, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(speed?.numericValue?.times(3.6)?.let { String.format(Locale.US, "%.0f", it) } ?: "—", fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.Light)
                            Spacer(Modifier.width(6.dp)); Text("km/h", color = TextSoft, fontSize = 12.sp, modifier = Modifier.padding(bottom = 7.dp))
                        }
                        Text("PHONE GPS · NOT VEHICLE SPEED", color = TextSoft, fontSize = 8.sp, letterSpacing = .7.sp)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard("LIVE FIELDS", state.fields.values.count { now - it.timestampMs < 10_000 }.toString(), "active · last 10 sec")
                    MetricCard("EVENT RATE", "${state.eventsPerSecond}", "updates / sec")
                }
            }
        }
        item {
            CardSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ANDROID AUTO", color = TextSoft, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text("Session not observable", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Android does not expose connection state to this app.", color = TextSoft, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    StatusPill("NO SESSION API", Amber)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardSurface(Modifier.weight(1f)) {
                    Text("PHONE GPS", color = TextSoft, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(11.dp))
                    Text(if (latitude != null && longitude != null) "${latitude.value}°\n${longitude.value}°" else "No fix yet", fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(7.dp)); Text(state.locationStatus, color = TextSoft, fontSize = 10.sp)
                    Text("Permission · ${state.locationPermissionStatus}", color = TextSoft, fontSize = 9.sp)
                    if (state.locationStatus.contains("permission", true)) {
                        TextButton(onClick = onRequestLocation, contentPadding = PaddingValues(0.dp)) { Text("Allow location", color = Mint, fontSize = 11.sp) }
                    }
                }
                CardSurface(Modifier.weight(1f)) {
                    Text("MOTION", color = TextSoft, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(11.dp))
                    val accel = listOf("accel_x", "accel_y", "accel_z").mapNotNull { state.fields[it] }
                    Text(if (accel.size == 3) accel.joinToString("\n") { "${it.label.last()}   ${it.value} ${it.unit}" } else "Waiting for sensor", fontSize = 13.sp, lineHeight = 19.sp)
                    Spacer(Modifier.height(7.dp)); Text("Phone-mounted sensors", color = TextSoft, fontSize = 10.sp)
                }
            }
        }
        item {
            CardSurface {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (state.recording) "RECORDING SESSION" else "LOCAL RECORDING", color = TextSoft, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(if (state.recording) formatDuration(state.recordingDurationMs) else "Stopped", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("${state.storedEvents} saved  ·  ${state.droppedEvents} dropped", color = TextSoft, fontSize = 10.sp)
                    }
                    Button(
                        onClick = viewModel::toggleRecording,
                        colors = ButtonDefaults.buttonColors(containerColor = if (state.recording) SurfaceRaised else Mint, contentColor = if (state.recording) TextMain else Background),
                        shape = RoundedCornerShape(9.dp),
                    ) { Text(if (state.recording) "Stop" else "Record", fontWeight = FontWeight.Bold) }
                }
                Spacer(Modifier.height(11.dp))
                Text("Data stays on this device. Sensors pause when you leave the app.", color = TextSoft, fontSize = 10.sp)
            }
        }
        item {
            Column {
                SectionEyebrow("LATEST PHONE EVENTS")
                state.events.take(3).forEach { EventRow(it, compact = true) }
                if (state.events.isEmpty()) Text("No sensor updates received yet.", color = TextSoft, fontSize = 12.sp)
            }
        }
        item {
            Column {
                SectionEyebrow("EXPORT RECORDINGS")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onExportJson, shape = RoundedCornerShape(9.dp)) { Text("JSON", color = Mint, fontSize = 11.sp) }
                    OutlinedButton(onClick = onExportJsonl, shape = RoundedCornerShape(9.dp)) { Text("JSONL", color = Mint, fontSize = 11.sp) }
                    OutlinedButton(onClick = onExportCsv, shape = RoundedCornerShape(9.dp)) { Text("Export CSV", color = Mint, fontSize = 11.sp) }
                }
            }
        }
        item {
            Text("USE ONLY WHILE PARKED", color = Coral, fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DataExplorer(state: InspectorState, viewModel: InspectorViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(8.dp)); SectionEyebrow("OBSERVED VALUES · PHONE ONLY")
        Text("Live data", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        SearchBox(value = state.search, onValueChange = viewModel::setSearch, placeholder = "Search fields")
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            val visible = state.fields.values.filter { it.label.contains(state.search, true) || it.category.contains(state.search, true) }
                .sortedWith(compareBy<Reading>({ it.category }, { it.label }))
            if (visible.isEmpty()) item { EmptyNotice("Waiting for real sensor readings", "Grant location access and keep this screen open to observe your phone sensors.") }
            val grouped = visible.groupBy { it.category }
            grouped.forEach { (category, fields) ->
                item(key = "header-$category") { SectionEyebrow(category.uppercase(Locale.US)) }
                items(fields, key = { it.key }) { reading -> FieldRow(reading) { viewModel.selectField(reading) } }
            }
        }
    }
}

@Composable
private fun FieldRow(reading: Reading, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceDark).clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(reading.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("${reading.source}  ·  ${formatTime(reading.timestampMs)}", fontSize = 10.sp, color = TextSoft)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isLive = System.currentTimeMillis() - reading.timestampMs < 10_000
                StatusPill(if (isLive) "LIVE" else "WAITING", if (isLive) Mint else Amber)
            }
            Spacer(Modifier.height(5.dp))
            Text(reading.value, fontSize = 16.sp, color = if (System.currentTimeMillis() - reading.timestampMs < 10_000) Mint else TextSoft, fontWeight = FontWeight.Medium)
            Text(reading.unit, fontSize = 10.sp, color = TextSoft)
        }
    }
}

@Composable
private fun EventViewer(state: InspectorState, viewModel: InspectorViewModel) {
    val events = if (state.pausedEvents) state.pausedEventSnapshot else state.events
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(8.dp)); SectionEyebrow("LOCAL SENSOR EVENT STREAM")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Events", fontSize = 25.sp, fontWeight = FontWeight.SemiBold); Text("${state.observedEvents} observed · ${state.storedEvents} recorded", color = TextSoft, fontSize = 11.sp) }
            OutlinedButton(onClick = viewModel::toggleEventPause, shape = RoundedCornerShape(9.dp)) { Text(if (state.pausedEvents) "Resume" else "Pause", color = Mint, fontSize = 11.sp) }
        }
        Spacer(Modifier.height(12.dp)); SearchBox(state.eventFilter, viewModel::setEventFilter, "Filter by field or source")
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            val filtered = events.filter { it.reading.label.contains(state.eventFilter, true) || it.reading.source.contains(state.eventFilter, true) }
            if (filtered.isEmpty()) item { EmptyNotice("No matching events", "Events are recorded only from sensors actually observed on this phone.") }
            items(filtered, key = { "${it.reading.key}-${it.reading.timestampMs}" }) { EventRow(it, compact = false) }
        }
    }
}

@Composable
private fun EventRow(event: dev.roadscope.inspector.data.LiveEvent, compact: Boolean) {
    var expanded by remember(event.reading.key, event.reading.timestampMs) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(SurfaceDark).clickable { expanded = !expanded }.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(event.reading.timestampMs), color = TextSoft, fontSize = 9.sp, modifier = Modifier.width(58.dp))
            Column(Modifier.weight(1f)) {
                Text(event.reading.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("${event.reading.source}  ·  ${event.reading.category}", color = TextSoft, fontSize = 9.sp)
            }
            Text("${event.reading.value} ${event.reading.unit}", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        if (expanded && !compact) {
            Spacer(Modifier.height(10.dp)); Text("JSON", color = TextSoft, fontSize = 9.sp, letterSpacing = 1.sp)
            Text(event.rawJson, color = Mint, fontSize = 9.sp, lineHeight = 14.sp)
            Spacer(Modifier.height(6.dp)); Text("Raw protocol bytes: unavailable for phone sensor events", color = TextSoft, fontSize = 9.sp)
        }
    }
}

@Composable
private fun AvailabilityScreen(state: InspectorState, viewModel: InspectorViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(8.dp)); SectionEyebrow("OBSERVED VS. PROTOCOL-DEFINED")
        Text("Availability", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp)); Text("An API or schema definition does not mean a real car exposes that field.", color = TextSoft, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            val rows = investigatedItems.map { item ->
                when (item.name) {
                    "Accelerometer X / Y / Z" -> item.copy(status = phoneMotionStatus(state, viewModel, "accel_x", viewModel.hasAccelerometer()), note = "Phone accelerometer; values are phone-derived.")
                    "Linear acceleration" -> item.copy(status = phoneSensorStatus(state, viewModel, listOf("linear_accel_x"), Sensor.TYPE_LINEAR_ACCELERATION), note = "Phone linear acceleration; device frame and mounting apply.")
                    "Gravity and magnetic field" -> item.copy(status = phoneSensorStatus(state, viewModel, listOf("gravity_x", "magnetic_x"), Sensor.TYPE_GRAVITY, Sensor.TYPE_MAGNETIC_FIELD), note = "Phone sensor axes; not vehicle orientation or compass data.")
                    "Gyroscope X / Y / Z" -> item.copy(status = phoneMotionStatus(state, viewModel, "gyro_x", viewModel.hasGyroscope()), note = "Phone gyroscope; values are phone-derived.")
                    "Compass heading" -> item.copy(status = phoneMotionStatus(state, viewModel, "compass_heading", viewModel.hasRotationVector()), note = "Phone rotation vector; not a head-unit compass.")
                    "Light, pressure, temperature, humidity" -> item.copy(status = phoneSensorStatus(state, viewModel, listOf("ambient_light", "ambient_pressure", "ambient_temperature", "ambient_humidity"), Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE, Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_RELATIVE_HUMIDITY), note = "Optional ambient phone sensors; never vehicle cabin readings.")
                    "Proximity" -> item.copy(status = phoneSensorStatus(state, viewModel, listOf("proximity"), Sensor.TYPE_PROXIMITY), note = "Phone proximity only; not passenger or seat status.")
                    "GPS coordinates and accuracy" -> item.copy(status = locationStatus(state), note = "Phone location only; location permission is required.")
                    "GPS speed and bearing" -> item.copy(status = locationStatus(state), note = "Phone GPS estimates only; never presented as dashboard speed.")
                    "Satellite count" -> item.copy(
                        status = when {
                            System.currentTimeMillis() - (state.fields["satellites_visible"]?.timestampMs ?: 0L) < 10_000 -> "Live"
                            state.locationPermissionStatus == "Denied" -> "Permission denied"
                            state.locationPermissionStatus == "Precise permission granted" -> "Supported but awaiting data"
                            state.locationPermissionStatus == "Not requested" -> "Available but inactive"
                            else -> "Permission denied"
                        },
                        note = "Phone GNSS satellite counts; Android requires precise location permission.",
                    )
                    else -> item
                }
            }
            rows.groupBy { it.category }.forEach { (category, items) ->
                item(key = "matrix-$category") { SectionEyebrow(category.uppercase(Locale.US)) }
                items(items, key = { it.name }) { item -> AvailabilityRow(item) }
            }
            item {
                CardSurface {
                    Text("Verified boundary", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("A normal app cannot observe Google Android Auto's authenticated session. Protocol diagnostics require an authorized head-unit capture or control of a compatible head-unit session.", color = TextSoft, fontSize = 10.sp, lineHeight = 15.sp)
                }
            }
        }
    }
}

private fun phoneMotionStatus(state: InspectorState, viewModel: InspectorViewModel, key: String, hasSensor: Boolean): String = when {
    state.fields[key]?.let { System.currentTimeMillis() - it.timestampMs < 5_000 } == true -> "Live"
    hasSensor -> "Supported but awaiting data"
    else -> "Unsupported"
}

private fun phoneSensorStatus(state: InspectorState, viewModel: InspectorViewModel, keys: List<String>, vararg sensorTypes: Int): String = when {
    keys.any { state.fields[it]?.let { reading -> System.currentTimeMillis() - reading.timestampMs < 5_000 } == true } -> "Live"
    sensorTypes.any(viewModel::hasSensor) -> "Supported but awaiting data"
    else -> "Unsupported"
}

private fun locationStatus(state: InspectorState): String = when {
    state.fields["location_latitude"]?.let { System.currentTimeMillis() - it.timestampMs < 10_000 } == true -> "Live"
    state.locationPermissionStatus == "Denied" -> "Permission denied"
    state.locationPermissionStatus == "Not requested" -> "Available but inactive"
    state.locationStatus.contains("fix", true) || state.locationStatus.contains("waiting", true) -> "Supported but awaiting data"
    else -> "Available but inactive"
}

@Composable
private fun AvailabilityRow(item: InvestigationItem) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(SurfaceDark).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusPill(item.status.uppercase(Locale.US), statusColor(item.status))
        }
        Spacer(Modifier.height(5.dp)); Text(item.note, color = TextSoft, fontSize = 9.sp, lineHeight = 14.sp)
    }
}

@Composable
private fun SensorDetail(reading: Reading, history: List<Float>, timestamps: List<Long>, onDismiss: () -> Unit) {
    val avg = history.takeIf { it.isNotEmpty() }?.average()
    val elapsed = if (timestamps.size > 1) (timestamps.last() - timestamps.first()).coerceAtLeast(1) / 1000.0 else null
    val rate = if (elapsed != null) (timestamps.size - 1) / elapsed else null
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = { Text(reading.label, fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(reading.value, color = Mint, fontSize = 34.sp, fontWeight = FontWeight.Light)
                    Spacer(Modifier.width(8.dp)); Text(reading.unit, color = TextSoft, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
                }
                Text("${reading.source} · ${reading.category}", color = TextSoft, fontSize = 10.sp)
                Spacer(Modifier.height(14.dp))
                if (history.size > 1) HistoryChart(history) else EmptyNotice("Collecting history", "Keep the app open to build a local sample history.")
                Spacer(Modifier.height(12.dp))
                DetailStat("MIN / MAX", if (history.isEmpty()) "—" else "${formatNumber(history.minOrNull()!!.toDouble())} / ${formatNumber(history.maxOrNull()!!.toDouble())} ${reading.unit}")
                DetailStat("AVERAGE", avg?.let { "${formatNumber(it)} ${reading.unit}" } ?: "—")
                DetailStat("UPDATE RATE", rate?.let { "${formatNumber(it)} / sec · recent samples" } ?: "Estimating")
                DetailStat("LAST UPDATE", formatTimestamp(reading.timestampMs))
                DetailStat("ACCURACY", reading.accuracy?.let { "sensor accuracy code $it" } ?: "Not provided")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Mint) } },
    )
}

@Composable
private fun HistoryChart(values: List<Float>) {
    Canvas(Modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(8.dp)).background(Background).padding(8.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = index.toFloat() / (values.size - 1) * size.width
            val y = size.height - ((value - min) / span * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, Mint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        drawLine(Line, Offset(0f, size.height - 1), Offset(size.width, size.height - 1), strokeWidth = 1.dp.toPx())
    }
}

@Composable
private fun DetailStat(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSoft, fontSize = 9.sp, letterSpacing = .7.sp, modifier = Modifier.weight(1f))
        Text(value, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MetricCard(label: String, value: String, detail: String) {
    CardSurface {
        Text(label, color = TextSoft, fontSize = 9.sp, letterSpacing = 1.1.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp)); Text(value, fontSize = 24.sp, lineHeight = 25.sp, color = Mint, fontWeight = FontWeight.Light)
        Text(detail, fontSize = 9.sp, color = TextSoft)
    }
}

@Composable
private fun CardSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SurfaceDark).border(1.dp, Line, RoundedCornerShape(12.dp)).padding(15.dp), content = content)
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Row(Modifier.clip(CircleShape).background(color.copy(alpha = .1f)).padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color)); Spacer(Modifier.width(5.dp))
        Text(text, color = color, fontSize = 8.sp, letterSpacing = .5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SectionEyebrow(text: String) {
    Text(text, color = TextSoft, fontSize = 9.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(SurfaceDark).padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⌕", color = TextSoft, fontSize = 17.sp); Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = TextSoft, fontSize = 12.sp)
            BasicTextField(value, onValueChange, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = TextMain, fontSize = 12.sp), modifier = Modifier.fillMaxWidth())
        }
        if (value.isNotEmpty()) Text("×", color = TextSoft, fontSize = 16.sp, modifier = Modifier.clickable { onValueChange("") })
    }
}

@Composable
private fun EmptyNotice(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceDark).padding(16.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp)); Text(detail, color = TextSoft, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

private fun statusColor(status: String) = when (status) {
    "Live" -> Mint
    "Available but inactive", "Supported but awaiting data" -> Amber
    "Permission denied" -> Coral
    "Unknown" -> TextSoft
    else -> Coral
}

private fun formatTime(timestamp: Long) = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(timestamp))
private fun formatTimestamp(timestamp: Long) = SimpleDateFormat("MMM d · HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
private fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1_000
    return "%02d:%02d:%02d".format(Locale.US, seconds / 3600, (seconds % 3600) / 60, seconds % 60)
}
private fun formatNumber(value: Double) = String.format(Locale.US, "%.2f", value)
