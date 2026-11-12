package dev.roadscope.inspector

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import dev.roadscope.inspector.data.ExportFormat
import dev.roadscope.inspector.ui.InspectorViewModel
import dev.roadscope.inspector.ui.RoadscopeApp

class MainActivity : ComponentActivity() {
    private val viewModel: InspectorViewModel by viewModels()
    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            viewModel.locationPermissionGranted()
        } else {
            viewModel.locationPermissionDenied()
        }
    }
    private val jsonExport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-ndjson")) { uri ->
        uri?.let { viewModel.export(it, ExportFormat.JSONL) }
    }
    private val jsonArrayExport = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.export(it, ExportFormat.JSON) }
    }
    private val csvExport = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.export(it, ExportFormat.CSV) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoadscopeApp(
                viewModel = viewModel,
                onRequestLocation = {
                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                onExportJson = { jsonArrayExport.launch("roadscope-session.json") },
                onExportJsonl = { jsonExport.launch("roadscope-session.jsonl") },
                onExportCsv = { csvExport.launch("roadscope-session.csv") },
            )
        }
    }

    override fun onStart() { super.onStart(); viewModel.onForeground() }
    override fun onStop() { viewModel.onBackground(); super.onStop() }
}
