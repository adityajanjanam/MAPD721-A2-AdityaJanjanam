package com.example.healthconnect

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord

class MainActivity : ComponentActivity() {
    private lateinit var healthConnectClient: HealthConnectClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if Health Connect is available
        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) {
            healthConnectClient = HealthConnectClient.getOrCreate(this)
        } else {
            Log.e("HealthConnect", "Health Connect is not available on this device.")
            return
        }

        // Request Permissions
        val permissionsLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                Log.d("HealthConnect", "All required permissions granted.")
            } else {
                Log.e("HealthConnect", "Some permissions were not granted.")
            }
        }
        requestPermissions(permissionsLauncher)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HealthScreen(healthConnectClient)
                }
            }
        }
    }

    /**
     * Requests necessary Health Connect permissions.
     */
    private fun requestPermissions(permissionsLauncher: ActivityResultLauncher<Array<String>>) {
        val permissions = arrayOf(
            HealthPermission.getReadPermission(HeartRateRecord::class).toString(),
            HealthPermission.getWritePermission(HeartRateRecord::class).toString()
        )
        permissionsLauncher.launch(permissions)
    }
}
