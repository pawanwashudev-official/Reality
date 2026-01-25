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
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        
        response.records.forEach { session ->
            val duration = java.time.Duration.between(session.startTime, session.endTime).toMinutes()
            val hrs = duration / 60
            val mins = duration % 60
            val startStr = formatter.format(session.startTime)
            val endStr = formatter.format(session.endTime)
            
            sb.append("$startStr - $endStr (${hrs}h ${mins}m)\n")
        }
        return sb.toString()
    }

    suspend fun hasPermissions(): Boolean {
        // Basic Read Permissions
        val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )
        return healthConnectClient.permissionController.getGrantedPermissions().containsAll(permissions)
    }

    suspend fun hasRequiredPermissions(): Boolean {
        // Full Permissions (including write)
        val permissions = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getWritePermission(SleepSessionRecord::class)
        )
        return healthConnectClient.permissionController.getGrantedPermissions().containsAll(permissions)
    }

    suspend fun writeSleepSession(startTime: Instant, endTime: Instant, title: String? = null) {
        if (!hasRequiredPermissions()) return
        
        try {
            val record = SleepSessionRecord(
                startTime = startTime,
                endTime = endTime,
                startZoneOffset = java.time.ZoneOffset.systemDefault().rules.getOffset(startTime),
                endZoneOffset = java.time.ZoneOffset.systemDefault().rules.getOffset(endTime),
                title = title ?: "Reality Smart Sleep"
            )
            healthConnectClient.insertRecords(listOf(record))
            com.neubofy.reality.utils.TerminalLogger.log("HEALTH: Sleep session written: $startTime to $endTime")
            
            // Cache the sync for this session to avoid immediate re-prompt
            lastSyncedDate = LocalDate.now()
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("HEALTH ERROR: Failed to write sleep - ${e.message}")
        }
    }

    suspend fun deleteSleepSessions(startTime: Instant, endTime: Instant) {
        if (!hasRequiredPermissions()) return
        try {
            // Health Connect doesn't allow direct filtering in deleteRecords yet, 
            // so we fetch reality records first then delete by ID.
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
                )
            )
            val toDelete = response.records.filter { it.title?.contains("Reality") == true }.map { it.metadata.id }
            if (toDelete.isNotEmpty()) {
                healthConnectClient.deleteRecords(
                    recordType = SleepSessionRecord::class,
                    recordIdsList = toDelete,
                    clientRecordIdsList = emptyList()
                )
                com.neubofy.reality.utils.TerminalLogger.log("HEALTH: Deleted ${toDelete.size} Reality sleep sessions")
                
                // Clear cache if we deleted today's data (simplified check)
                lastSyncedDate = null
            }
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("HEALTH ERROR: Failed to delete sleep - ${e.message}")
        }
    }

    suspend fun isSleepSyncedToday(date: LocalDate): Boolean {
        // Fast path: if we just wrote it in this session
        if (lastSyncedDate == date) return true

        if (!hasPermissions()) return false
        try {
            val start = date.minusDays(1).atTime(18, 0).atZone(ZoneId.systemDefault()).toInstant()
            val end = date.atTime(23, 59).atZone(ZoneId.systemDefault()).toInstant()
            
            val response = healthConnectClient.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            return response.records.any { it.title?.contains("Reality") == true }
        } catch (e: Exception) {
            return false
        }
    }
    
    companion object {
        private var lastSyncedDate: LocalDate? = null

        fun isHealthConnectAvailable(context: Context): Boolean {
            return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        }
    }
}
