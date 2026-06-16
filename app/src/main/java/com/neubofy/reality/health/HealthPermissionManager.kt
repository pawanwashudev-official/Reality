package com.neubofy.reality.health

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.SleepSessionRecord

object HealthPermissionManager {

    val REQUIRED_PERMISSIONS = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getWritePermission(SleepSessionRecord::class)
    )

    suspend fun hasAllPermissions(context: Context): Boolean {
        return try {
            val healthConnectClient = HealthConnectClient.getOrCreate(context)
            healthConnectClient.permissionController.getGrantedPermissions().containsAll(REQUIRED_PERMISSIONS)
        } catch (e: Exception) {
            false
        }
    }

    fun requestPermissionsLauncher(
        activity: androidx.activity.ComponentActivity,
        onResult: (Set<String>) -> Unit
    ): ActivityResultLauncher<Set<String>> {
        return activity.registerForActivityResult(
            PermissionController.createRequestPermissionResultContract(),
            onResult
        )
    }

    fun launchHealthConnectSettings(context: Context) {
        try {
            val intent = Intent("android.settings.HEALTH_CONNECT_SETTINGS")
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
                context.startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Unable to open Health Settings", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
