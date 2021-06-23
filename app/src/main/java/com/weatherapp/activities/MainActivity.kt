package com.weatherapp.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.weatherapp.R
import com.weatherapp.models.Weather
import com.weatherapp.models.WeatherResponse
import com.weatherapp.network.WeatherService
import com.weatherapp.utils.Constants
import kotlinx.android.synthetic.main.activity_main.*
import retrofit.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private var progressDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Your location provider is turned off. Please turn it on.",
                Toast.LENGTH_SHORT
            ).show()

            // This will redirect you to settings from where you need to turn on the location provider.
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            dexterDoPermissionActivity()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.toolbar_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_refresh -> {
                requestLocationData()
                Toast.makeText(this, "Clicked", Toast.LENGTH_SHORT).show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {

            // We say that we want to convert the data to the right format (Gson)
            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            // Prepare the service to make a call to the API with it
            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(response: Response<WeatherResponse>?, retrofit: Retrofit?) {
                    if (response?.isSuccess == true) {
                        hideCustomProgressDialog()
                        val weatherList: WeatherResponse = response.body()
                        setUpMainActivityUI(weatherList)
                    } else {
                        when (response?.code()) {
                            400 -> {
                                Log.e("Error 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("Error 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")

                            }
                        }
                    }

                }

                override fun onFailure(t: Throwable?) {
                    hideCustomProgressDialog()
                    Log.e("On failure Error:", t?.message.toString())
                }

            })

        } else {
            Log.e("Connection", "NO Internet connection")
        }
    }

    // Request permissions functions - START
    // A 3-rd party library Dexter for access the needed permissions
    private fun dexterDoPermissionActivity() {
        Dexter.withActivity(this)
            .withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            .withListener(object : MultiplePermissionsListener {
                override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                    if (report!!.areAllPermissionsGranted()) {
                        requestLocationData()
                    }

                    if (report.isAnyPermissionPermanentlyDenied) {
                        Toast.makeText(
                            this@MainActivity,
                            "You have denied location permission. Please allow it is mandatory.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onPermissionRationaleShouldBeShown(
                    permissions: MutableList<PermissionRequest>?,
                    token: PermissionToken?
                ) {
                    showRationalDialogForPermissions()
                }
            }).onSameThread()
            .check()
    }

    // A function which is used to verify that the location or GPS is enable or not of the user's device.
    private fun isLocationEnabled(): Boolean {
        // This provides access to the system location services.
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    // Function to get the location of the device using the fusedLocationProviderClient
    @SuppressLint("MissingPermission")
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationProviderClient.requestLocationUpdates(
            mLocationRequest, locationCallback,
            Looper.myLooper()
        )
    }

    // Register a request location callback to get the location.
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult ?: return
            Log.e("Problem", locationResult.toString())

            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            val longitude = mLastLocation.longitude
            getLocationWeatherDetails(latitude, longitude)
        }
    }

    // A alert dialog for denied permissions and if needed to allow it from the settings app info
    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage("It Looks like you have turned off permissions required for this feature. It can be enabled under Application Settings")
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    // Request permissions functions - END

    private fun showCustomProgressDialog() {
        progressDialog = Dialog(this)
        progressDialog?.setContentView(R.layout.dialog_custom_progress)
        progressDialog?.show()
    }

    private fun hideCustomProgressDialog() {
        if (progressDialog != null) {
            progressDialog?.dismiss()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setUpMainActivityUI(weatherList: WeatherResponse) {

        weatherList.weather.forEach { item ->
            setWeatherUI(item)
        }

        tv_temp.text =
            weatherList.main.temp.toString() + getUnit(application.resources.configuration.toString())
        tv_humidity.text = weatherList.main.humidity.toString() + " per cent"
        tv_min.text = weatherList.main.temp_min.toString() + " min"
        tv_max.text = weatherList.main.temp_max.toString() + " max"
        tv_speed.text = weatherList.wind.speed.toString()
        tv_name.text = weatherList.name
        tv_country.text = weatherList.sys.country
        tv_sunrise_time.text = unixTime(weatherList.sys.sunrise.toLong())
        tv_sunset_time.text = unixTime(weatherList.sys.sunset.toLong())
    }

    private fun setWeatherUI(item: Weather) {
        tv_main.text = item.main
        tv_main_description.text = item.description
        when (item.icon) {
            "01d" -> iv_main.setImageResource(R.drawable.sunny)
            "02d" -> iv_main.setImageResource(R.drawable.cloud)
            "03d" -> iv_main.setImageResource(R.drawable.cloud)
            "04d" -> iv_main.setImageResource(R.drawable.cloud)
            "04n" -> iv_main.setImageResource(R.drawable.cloud)
            "10d" -> iv_main.setImageResource(R.drawable.rain)
            "11d" -> iv_main.setImageResource(R.drawable.storm)
            "13d" -> iv_main.setImageResource(R.drawable.snowflake)
            "01n" -> iv_main.setImageResource(R.drawable.cloud)
            "02n" -> iv_main.setImageResource(R.drawable.cloud)
            "03n" -> iv_main.setImageResource(R.drawable.cloud)
            "10n" -> iv_main.setImageResource(R.drawable.cloud)
            "11n" -> iv_main.setImageResource(R.drawable.rain)
            "13n" -> iv_main.setImageResource(R.drawable.snowflake)
        }
    }

    private fun getUnit(value: String): String {
        var returnValue = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            returnValue = "°F"
        }

        return returnValue

    }

    private fun unixTime(timex: Long): String {
        val date = Date(timex * 1000L)
        @SuppressLint("SimpleDateFormat") val dateFormatter = SimpleDateFormat("HH:mm", Locale.UK)
        dateFormatter.timeZone = TimeZone.getDefault()
        return dateFormatter.format(date)
    }
}
