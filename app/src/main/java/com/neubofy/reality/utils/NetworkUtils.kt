package com.neubofy.reality.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object NetworkUtils {
    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun checkInternetAndShowDialog(context: Context, onConnected: () -> Unit) {
        if (isInternetAvailable(context)) {
            onConnected()
        } else {
            MaterialAlertDialogBuilder(context)
                .setTitle("No Internet Connection")
                .setMessage("An active internet connection is required for this action. Please check your network status and try again.")
                .setPositiveButton("Retry") { dialog, _ ->
                    dialog.dismiss()
                    checkInternetAndShowDialog(context, onConnected)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
