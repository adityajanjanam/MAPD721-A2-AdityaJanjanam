package com.example.healthconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {
    private lateinit var healthConnectClient: HealthConnectClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        healthConnectClient = HealthConnectClient.getOrCreate(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    HeartRateScreen(healthConnectClient)
                }
            }
        }
    }
}

@Composable
fun HeartRateScreen(healthConnectClient: HealthConnectClient) {
    var heartRate by remember { mutableStateOf("") }
    var dateTime by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<HeartRateRecord.Sample>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = heartRate,
            onValueChange = { heartRate = it },
            label = { Text("Heart Rate (1-300 bpm)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = dateTime,
            onValueChange = { dateTime = it },
            label = { Text("Date/Time (yyyy-MM-dd HH:mm:ss)") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            history = loadHeartRateHistory(healthConnectClient)
                        } catch (e: Exception) {
                            errorMessage = e.message
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                Text("Load")
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        try {
                            val hr = heartRate.toDoubleOrNull()
                                ?: throw IllegalArgumentException("Invalid heart rate")

                            val timestamp = if (dateTime.isBlank()) {
                                Instant.now()
                            } else {
                                LocalDateTime.parse(
                                    dateTime,
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                ).atZone(ZoneId.systemDefault()).toInstant()
                            }

                            saveHeartRate(healthConnectClient, hr, timestamp)
                            history = loadHeartRateHistory(healthConnectClient)
                            heartRate = ""
                            dateTime = ""
                        } catch (e: Exception) {
                            errorMessage = e.message
                        }
                        isLoading = false
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !isLoading
            ) {
                Text("Save")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Heart Rate History")
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(history) { reading ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${reading.beatsPerMinute} bpm")
                    Text(
                        reading.time
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    )
                }
            }
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Your Name")
                Text("Your Student ID")
            }
        }
    }
}

/**
 * Saves a heart rate record to Health Connect.
 */
suspend fun saveHeartRate(
    healthConnectClient: HealthConnectClient,
    heartRate: Double,
    instant: Instant
) {
    val heartRateSample = HeartRateRecord.Sample(
        time = instant, // FIX: Directly use Instant instead of converting to Long
        beatsPerMinute = heartRate.toLong()
    )

    val heartRateRecord = HeartRateRecord(
        startTime = instant,
        endTime = instant.plusMillis(1), // Required end time
        startZoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant),
        endZoneOffset = ZoneOffset.systemDefault().rules.getOffset(instant), // Required end zone offset
        samples = listOf(heartRateSample)
    )

    healthConnectClient.insertRecords(listOf(heartRateRecord))
}

/**
 * Loads heart rate records from the past 30 days.
 */
suspend fun loadHeartRateHistory(
    healthConnectClient: HealthConnectClient
): List<HeartRateRecord.Sample> {
    val endTime = Instant.now()
    val startTime = endTime.minus(30, ChronoUnit.DAYS)

    val response = healthConnectClient.readRecords(
        ReadRecordsRequest(
            recordType = HeartRateRecord::class,
            timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
        )
    )

    return response.records.flatMap { it.samples }.sortedByDescending { it.time }
}
