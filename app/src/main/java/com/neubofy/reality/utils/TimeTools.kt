package com.neubofy.reality.utils

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TimeTools {
    companion object {
        fun convertToMinutesFromMidnight(hour: Int, minute: Int): Int {
            return (hour * 60) + minute
        }

        fun getCurrentTimeInMinutes(): Int {
             val now = Calendar.getInstance()
             return (now.get(Calendar.HOUR_OF_DAY) * 60) + now.get(Calendar.MINUTE)
        }

        fun convertMinutesTo24Hour(minutes: Int): Pair<Int, Int> {
            return Pair(minutes / 60, minutes % 60)
        }

        fun getCurrentDate(): String {
            val currentDate = LocalDate.now()
            val formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy")
            return currentDate.format(formatter)
        }
        fun getPreviousDate(daysAgo:Long = 1): String {
            val previousDate = LocalDate.now().minusDays(daysAgo)
            val formatter = DateTimeFormatter.ofPattern("dd MMMM yyyy")
            return previousDate.format(formatter)
        }
        
        fun getCurrentTime(): String {
            val currentTime = LocalTime.now()
            val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
            return currentTime.format(formatter)
        }

        fun shortenDate(dateString: String): String {
            val parts = dateString.split(" ")
            if (parts.size >= 2) {
                val day = parts[0]
                val month = parts[1].take(3)
                return "$day $month"
            }
            return dateString
        }

        fun formatDate(timestamp: Long): String {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            return dateFormat.format(Date(timestamp))
        }

        fun formatTime(timeInMillis: Long, showSeconds: Boolean = true): String {
            val hours = timeInMillis / (1000 * 60 * 60)
            val minutes = (timeInMillis % (1000 * 60 * 60)) / (1000 * 60)
            val seconds = (timeInMillis % (1000 * 60)) / 1000

            return buildString {
                if (hours > 0) append("$hours hr")
                if (minutes > 0) append(" $minutes mins")
                if (showSeconds && seconds > 0) append(" $seconds secs")
            }.trim()
        }

        fun formatTimeForWidget(timeInMillis: Long): String {
            val hours = timeInMillis / (1000 * 60 * 60)
            val minutes = (timeInMillis % (1000 * 60 * 60)) / (1000 * 60)

            return buildString {
                if (hours > 0) append("${hours}h")
                if (minutes > 0L) append("${minutes}m")
                if (hours == 0L && minutes == 0L) append("<1m") 
            }.trim()
        }

        fun isNewDay(lastDate: Long): Boolean {
            val last = Calendar.getInstance().apply { timeInMillis = lastDate }
            val now = Calendar.getInstance()
            return last.get(Calendar.DAY_OF_YEAR) != now.get(Calendar.DAY_OF_YEAR) ||
                   last.get(Calendar.YEAR) != now.get(Calendar.YEAR)
        }
        
        fun parseTime(timeStr: String): Int {
            return try {
                val parts = timeStr.split(":")
                parts[0].toInt() * 60 + parts[1].toInt()
            } catch (e: Exception) { 0 }
        }

        fun isActivePeriod(json: String): Boolean {
            if (json.length < 5) return true 
            try {
                val arr = org.json.JSONArray(json)
                if (arr.length() == 0) return true
                
                val now = Calendar.getInstance()
                val currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
                
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val startStr = obj.getString("start")
                    val endStr = obj.getString("end")
                    val startMins = parseTime(startStr)
                    val endMins = parseTime(endStr)
                    
                    val active = if (startMins <= endMins) {
                        currentMins in startMins..endMins
                    } else {
                        currentMins >= startMins || currentMins <= endMins
                    }
                    if (active) return true
                }
                return false
            } catch (e: Exception) {
                return true
            }
        }
    }
}