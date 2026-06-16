package com.neubofy.reality.utils

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Singleton cache for app icons.
 * 
 * Loading app icons is expensive because:
 * 1. It requires disk I/O to read the APK
 * 2. It decodes image resources
 * 3. It may need to scale the image
 * 
 * This cache stores icons in memory after first load,
 * dramatically improving RecyclerView scroll performance.
 * 
 * Cache capacity: 150 icons (covers most use cases)
 * Eviction policy: LRU (Least Recently Used)
 */
object AppIconCache {
    
    // LruCache automatically evicts least-recently-used entries when full
    // 150 entries is generous - most users won't have more than 100 apps
    private val cache = LruCache<String, Drawable>(150)
    
    // Track failed packages to avoid retrying
    private val failedPackages = mutableSetOf<String>()
    
    /**
     * Get app icon synchronously from cache or load it.
     * Best used from a background thread.
     * 
     * @param context Context for PackageManager
     * @param packageName Package name to get icon for
     * @return Drawable icon or null if not found/failed
     */
    fun get(context: Context, packageName: String): Drawable? {
        // Check cache first (O(1))
        cache.get(packageName)?.let { return it }
        
        // Skip if previously failed
        if (failedPackages.contains(packageName)) return null
        
        // Load from PackageManager
        return try {
            val pm = context.packageManager
            val icon = pm.getApplicationIcon(packageName)
            cache.put(packageName, icon)
            icon
        } catch (e: PackageManager.NameNotFoundException) {
            failedPackages.add(packageName)
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get app icon with app name - useful for list displays.
     * 
     * @param context Context for PackageManager
     * @param packageName Package name to get icon for
     * @return Pair of (icon, appName) or null if not found
     */
    fun getWithName(context: Context, packageName: String): Pair<Drawable, String>? {
        val icon = get(context, packageName) ?: return null
        
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val name = pm.getApplicationLabel(appInfo).toString()
            Pair(icon, name)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Coroutine-friendly version for UI usage.
     * Loads icon on IO dispatcher, returns on calling dispatcher.
     */
    suspend fun getAsync(context: Context, packageName: String): Drawable? = 
        withContext(Dispatchers.IO) {
            get(context, packageName)
        }
    
    /**
     * Preload icons for a list of packages.
     * Call this when entering a screen that will display many icons.
     * 
     * @param context Context for PackageManager
     * @param packages List of package names to preload
     */
    suspend fun preload(context: Context, packages: List<String>) = 
        withContext(Dispatchers.IO) {
            packages.forEach { pkg ->
                get(context, pkg)
            }
        }
    
    /**
     * Preload icons with limit - useful for large lists.
     * 
     * @param context Context for PackageManager
     * @param packages List of package names to preload
     * @param limit Maximum number of icons to preload
     */
    suspend fun preloadLimited(context: Context, packages: List<String>, limit: Int = 50) = 
        withContext(Dispatchers.IO) {
            packages.take(limit).forEach { pkg ->
                get(context, pkg)
            }
        }
    
    /**
     * Clear the cache.
     * Call on low memory or when cache might be stale (e.g., after app updates).
     */
    fun clear() {
        cache.evictAll()
        failedPackages.clear()
    }
    
    /**
     * Remove a specific package from cache.
     * Useful after an app update when icon might have changed.
     */
    fun invalidate(packageName: String) {
        cache.remove(packageName)
        failedPackages.remove(packageName)
    }
    
    /**
     * Get cache statistics for debugging.
     */
    fun getStats(): CacheStats {
        return CacheStats(
            size = cache.size(),
            maxSize = cache.maxSize(),
            hitCount = cache.hitCount(),
            missCount = cache.missCount(),
            evictionCount = cache.evictionCount()
        )
    }
    
    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val hitCount: Int,
        val missCount: Int,
        val evictionCount: Int
    ) {
        val hitRate: Float get() = if (hitCount + missCount > 0) {
            hitCount.toFloat() / (hitCount + missCount)
        } else 0f
    }
}
