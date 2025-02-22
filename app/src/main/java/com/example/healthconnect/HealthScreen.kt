package com.example.healthconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.temporal.ChronoUnit

@Composable
fun HealthScreen(healthConnectClient: HealthConnectClient) {
    var heartRate by remember { mutableStateOf("") }
    var dateTime by remember { mutableStateOf("") }
    var history by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var allRecords by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
            allRecords = loadHeartRateHistory(healthConnectClient)
            history = allRecords
        }
    }

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFE3E3E3), Color(0xFFFFFFFF))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = backgroundBrush)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Heart Rate Tracker",
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = heartRate,
                onValueChange = { heartRate = it },
                label = { Text("Heart Rate (1-300 bpm)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Black)
                    .background(Color(0xFFD9EFFF)),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = dateTime,
                onValueChange = { dateTime = it },
                label = { Text("Date/Time (yyyy-MM-dd HH:mm) [Optional]") },
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Black)
                    .background(Color(0xFFD9EFFF)),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            history = if (heartRate.isNotBlank()) {
                                allRecords.filter { it.second.contains("$heartRate bpm") }
                            } else {
                                allRecords // Load all records if no heart rate is entered
                            }
                            isLoading = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4A90E2)) // Blue Button
                ) {
                    Text("Load", color = Color.White)
                }

                Button(
                    onClick = {
                        scope.launch {
                            isLoading = true
                            val newEntry = Pair(
                                dateTime.ifBlank { Instant.now().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) },
                                "$heartRate bpm"
                            )
                            allRecords = allRecords + newEntry
                            history = allRecords // Update history
                            isLoading = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF50E3C2)) // Green Button
                ) {
                    Text("Save", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text("Heart Rate History", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, Color.Black)
            ) {
                items(history) { (date, rate) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, Color.Black)
                            .padding(12.dp)
                    ) {
                        Column {
                            Text("Heart Rate: $rate", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Recorded on: $date", fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.Black)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Aditya Janjanam", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Text("301357523", fontSize = 16.sp, color = Color.Black)
                }
            }
        }
    }
}

suspend fun loadHeartRateHistory(healthConnectClient: HealthConnectClient): List<Pair<String, String>> {
    return try {
        val endTime = Instant.now()
        val startTime = endTime.minus(30, ChronoUnit.DAYS)

        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )

        response.records.flatMap { it.samples }
            .sortedByDescending { it.time }
            .map { sample ->
                val formattedDate = sample.time.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                Pair(formattedDate, "${sample.beatsPerMinute} bpm")
            }
    } catch (e: Exception) {
        emptyList()
    }
}
