package com.neubofy.reality.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HealthManager(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun getSteps(date: LocalDate): Long {
        if (!hasPermissions()) return 0
        
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.sumOf { it.count }
    }
    
    suspend fun getCalories(date: LocalDate): Double {
        if (!hasPermissions()) return 0.0
        
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = TotalCaloriesBurnedRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        return response.records.sumOf { it.energy.inKilocalories }
    }
    
    suspend fun getSleep(date: LocalDate): String {
        if (!hasPermissions()) return "Permission Denied"
        
        // Sleep usually spans across days. Fetching sessions ending on this date.
        val start = date.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() // Previous night
        val end = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant() // Noon today
        
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        
        if (response.records.isEmpty()) return "No sleep data."
        
        val sb = StringBuilder()
        response.records.forEach { session ->
            val duration = java.time.Duration.between(session.startTime, session.endTime).toMinutes()
            val hrs = duration / 60
            val mins = duration % 60
            sb.append("${hrs}h ${mins}m (Ends: ${session.endTime})\n")
        }
        return sb.toString()
    }

    suspend fun hasPermissions(): Boolean {
        // Permissions set
        val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
        return healthConnectClient.permissionController.getGrantedPermissions().containsAll(permissions)
    }
    
    companion object {
        fun isHealthConnectAvailable(context: Context): Boolean {
            return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        }
    }
}
