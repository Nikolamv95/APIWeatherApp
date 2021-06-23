package com.weatherapp.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

object Constants {

    const val APP_ID: String = "4a2f0815be487b13ab5e8dc2761f6fed"
    const val BASE_URL: String = "http://api.openweathermap.org/data/"
    const val METRIC_UNIT: String = "metric"


    fun isNetworkAvailable(context: Context): Boolean {
        val connectActivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // we check for the version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // if its new phone get the network
            val network = connectActivityManager.activeNetwork ?: return false
            // then we use this network to check the capabilities
            val activeNetwork = connectActivityManager.getNetworkCapabilities(network) ?: return false

            return when {
                // then we check the WIFI, CELLULAR, ETHERNET if any of
                // them has capabilities true else false
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            // if the version is old we return activeNetworkInfo
            val networkInfo = connectActivityManager.activeNetworkInfo
            return networkInfo != null && networkInfo.isConnectedOrConnecting
        }


    }
}