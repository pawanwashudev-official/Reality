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
        
        val sessions = getSleepSessions(date)
        if (sessions.isEmpty()) return "No record"
        
        val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        val sb = StringBuilder()
        
        sessions.forEach { session ->
            val duration = java.time.Duration.between(session.first, session.second).toMinutes()
            val hrs = duration / 60
            val mins = duration % 60
            val startStr = formatter.format(session.first)
            val endStr = formatter.format(session.second)
            sb.append("$startStr - $endStr (${hrs}h ${mins}m)\n")
        }
            
        return sb.toString().trim()
    }

    suspend fun getSleepSessions(date: LocalDate): List<Pair<Instant, Instant>> {
        if (!hasPermissions()) return emptyList()
        
        val windowStart = date.minusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        val windowEnd = date.plusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(windowStart, windowEnd)
            )
        )
        
        val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        return response.records.filter { record ->
             val startMs = record.startTime.toEpochMilli()
             val endMs = record.endTime.toEpochMilli()
             val duration = endMs - startMs
             
             val overlapStart = maxOf(startMs, dayStart)
             val overlapEnd = minOf(endMs, dayEnd)
             val overlap = (overlapEnd - overlapStart).coerceAtLeast(0)
             
             overlap > (duration / 2) // Majority Rule for attribution
        }.sortedBy { it.startTime }.map { it.startTime to it.endTime }
    }

    suspend fun findOverlappingSessions(startTime: Instant, endTime: Instant, excludeStartTime: Instant? = null): List<Pair<Instant, Instant>> {
        if (!hasPermissions()) return emptyList()
        
        // Search wide window around the target
        val searchStart = startTime.minus(java.time.Duration.ofHours(24))
        val searchEnd = endTime.plus(java.time.Duration.ofHours(24))
        
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(searchStart, searchEnd)
            )
        )
        
        return response.records.filter { record ->
            // Skip the one we are editing
            if (excludeStartTime != null && record.startTime == excludeStartTime) return@filter false
            
            val rStart = record.startTime
            val rEnd = record.endTime
            
            // Overlap check: (StartA < EndB) and (EndA > StartB)
            startTime.isBefore(rEnd) && endTime.isAfter(rStart)
        }.map { it.startTime to it.endTime }
    }

    suspend fun getSleepSession(date: LocalDate): Pair<Instant, Instant>? {
        if (!hasPermissions()) return null
        
        // Reuse Majority Rule Logic
        val windowStart = date.minusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        val windowEnd = date.plusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant()
        
        val response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(windowStart, windowEnd)
            )
        )
        
        val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        val session = response.records.firstOrNull { record ->
             val startMs = record.startTime.toEpochMilli()
             val endMs = record.endTime.toEpochMilli()
             val duration = endMs - startMs
             val overlapStart = maxOf(startMs, dayStart)
             val overlapEnd = minOf(endMs, dayEnd)
             val overlap = (overlapEnd - overlapStart).coerceAtLeast(0)
             overlap > (duration / 2)
        }
        
        return session?.let { it.startTime to it.endTime }
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
