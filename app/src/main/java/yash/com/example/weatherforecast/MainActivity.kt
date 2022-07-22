package yash.com.example.weatherforecast

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit.*
import yash.com.example.weatherforecast.model.WeatherResponse
import yash.com.example.weatherforecast.network.WeatherService
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {

    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    private var mProgressDialog: Dialog? = null

    private lateinit var mSharedPreferences: SharedPreferences   // to make sure that app does not feel empty


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val appSettingPrefs: SharedPreferences = getSharedPreferences("AppSettingPrefs", 0)
        val sharedAppEdit: SharedPreferences.Editor = appSettingPrefs.edit()

        date_display.text = SimpleDateFormat("EEE, MMM d, yy").format(Date())

        //Night Mode

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        val isNightModeON: Boolean = appSettingPrefs.getBoolean("NightMode", false)
        if (isNightModeON) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            night_mode.text = "Disable Dark Mode"
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            night_mode.text = "Enable Dark Mode"
        }
        night_mode.setOnClickListener {
            if (isNightModeON) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                sharedAppEdit.putBoolean("NightMode", false)
                night_mode.text = "Enable Dark Mode"
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                sharedAppEdit.putBoolean("NightMode", true)
                night_mode.text = "Disable Dark Mode"
            }
            sharedAppEdit.apply()
        }
        //Night Mode End

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mSharedPreferences = getSharedPreferences(Constants.PREFERENCE_NAME, Context.MODE_PRIVATE)

        setupUI()

        if (!isLocationEnabled()) {
            Toast.makeText(
                this, "Please Turn On Location",
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
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
                                "You have denied Location Permissions. \n" +
                                        "Please enable them as it's mandatory for the app to work. ",
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

    }

    @SuppressLint("MissingPermission")      //try removing it you will understand why we need it
    private fun requestLocationData() {

        val mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest, mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val mLastLocation: Location = locationResult.lastLocation
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")

            val longitude = mLastLocation.longitude
            Log.i("Current Latitude", "$longitude")

            getLocationDetails(latitude, longitude)
        }
    }

    private fun getLocationDetails(latitude: Double, longitude: Double) {
        if (Constants.isNetworkAvailable(this)) {

            Toast.makeText(
                this,
                "Connected to Internet",
                Toast.LENGTH_SHORT
            ).show()

            val retrofit: Retrofit = Retrofit.Builder()
                .baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service: WeatherService =
                retrofit.create<WeatherService>(WeatherService::class.java)

            val listCall: Call<WeatherResponse> = service.getWeather(
                latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID
            )

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse> {

                override fun onFailure(t: Throwable) {
                    Log.e("Errorrrrrrrr", t!!.message.toString())
                    hideCustomProgressDialog()
                }

                override fun onResponse(
                    response: Response<WeatherResponse>,
                    retrofit: Retrofit
                ) {
                    if (response.isSuccess) {
                        hideCustomProgressDialog()
                        val weatherList: WeatherResponse = response.body()

                        val weatherResponseJsonString = Gson().toJson(weatherList)
                        val editor = mSharedPreferences.edit()
                        editor.putString(Constants.WEATHER_RESPONSE_DATA, weatherResponseJsonString)
                        editor.apply()

                        setupUI()

                        Log.i("Response Result", "$weatherList")
                    } else {
                        val rc = response.code()
                        when (rc) {
                            400 -> {
                                Log.e("Error 400", "Bas Connection")
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

            })

        } else {
            Toast.makeText(
                this,
                "No Internet Connection",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage(
                "It Looks like you have turned off permissions required for this feature. \n" +
                        "It can be enabled under Application Settings"
            )
            .setPositiveButton(
                "Go To Settings"
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
            .setNegativeButton("Cancel") { dialog,
                                           _ ->
                dialog.dismiss()        //_,_-> not used
            }.show()

    }

    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
            LocationManager.NETWORK_PROVIDER
        )
    }

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.progress_dialog)

        mProgressDialog!!.show()
    }

    private fun hideCustomProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }

    private fun setupUI() {

        val weatherResponseJsonString =
            mSharedPreferences.getString(Constants.WEATHER_RESPONSE_DATA, "")

        if (!weatherResponseJsonString.isNullOrEmpty()) {
            val weatherList =
                Gson().fromJson(weatherResponseJsonString, WeatherResponse::class.java)

            for (i in weatherList.weather.indices) {
                Log.i("Weather Name", weatherList.weather.toString())

                firstCard_head.text = weatherList.weather[i].main
                firstCard_description.text = weatherList.weather[i].description
                secondCard_head.text = "Humidity: " + weatherList.main.humidity.toString() + " %"

                if(weatherList.visibility>=6000){
                    secondCard_description.text = "Visibility: Clear"
                    secondCard_Image.setImageResource(R.drawable.humidity)
                }else if(weatherList.visibility<=2000){
                    secondCard_description.text = "Visibility: Haze"
                    secondCard_Image.setImageResource(R.drawable.haze)
                }else{
                    secondCard_description.text = "Visibility: Fog"
                    secondCard_Image.setImageResource(R.drawable.fog)
                }

                max_temp.text =
                    "MAX: " + weatherList.main.temp_max.toString() + getUnit(application.resources.configuration.locales.toString())
                min_Temp.text =
                    "MIN: " + weatherList.main.temp_min.toString() + getUnit(application.resources.configuration.locales.toString())
                forthCard_head.text = weatherList.wind.speed.toString() + " miles/hour"

                feel_like.text = weatherList.main.feels_like.toString() + getUnit(application.resources.configuration.locales.toString())

                sunrise_description.text = unixTime(weatherList.sys.sunrise) + " AM"
                sunset_description.text = unixTime(weatherList.sys.sunset) + " PM"

                location_head.text = weatherList.name
                location_description.text = weatherList.sys.country

                gnd_lvl.text = weatherList.main.gnd_level.toString()
                sea_lvl.text = weatherList.main.sea_level.toString()

                val time =getCurrentDateTime()

                if(getCurrentDateTime() <= "11:59:59" && getCurrentDateTime() >="05:00:00"){
                    day_display.setImageResource(R.drawable.morning)
                }else if (getCurrentDateTime() > "11:59:59" && getCurrentDateTime() <="17:00:00"){
                    day_display.setImageResource(R.drawable.afternoon)
                }else if (getCurrentDateTime() > "17:00:00" && getCurrentDateTime() <="19:00:00"){
                    day_display.setImageResource(R.drawable.evening)
                }else{
                    day_display.setImageResource(R.drawable.night)
                }

                when (weatherList.weather[i].icon) {
                    "01d" -> firstCard_Image.setImageResource(R.drawable.sunny)
                    "02d" -> firstCard_Image.setImageResource(R.drawable.pcloud)
                    "03d" -> firstCard_Image.setImageResource(R.drawable.cloud)
                    "04d" -> firstCard_Image.setImageResource(R.drawable.hcloud)
                    "09d" -> firstCard_Image.setImageResource(R.drawable.lrain)
                    "10d" -> firstCard_Image.setImageResource(R.drawable.rain)
                    "11d" -> firstCard_Image.setImageResource(R.drawable.thunder)
                    "13d" -> firstCard_Image.setImageResource(R.drawable.snow)
                    "13d" -> firstCard_Image.setImageResource(R.drawable.mist)
                    "01n" -> firstCard_Image.setImageResource(R.drawable.moon)
                    "02n" -> firstCard_Image.setImageResource(R.drawable.ncloud)
                    "03n" -> firstCard_Image.setImageResource(R.drawable.cloud)
                    "04n" -> firstCard_Image.setImageResource(R.drawable.hcloud)
                    "09n" -> firstCard_Image.setImageResource(R.drawable.lrain)
                    "10n" -> firstCard_Image.setImageResource(R.drawable.rain)
                    "11n" -> firstCard_Image.setImageResource(R.drawable.thunder)
                    "13n" -> firstCard_Image.setImageResource(R.drawable.snow)
                    "13n" -> firstCard_Image.setImageResource(R.drawable.mist)
                }
            }

        }
    }

    fun getCurrentDateTime(): String {
        val currentDate = Date()

        // Creating simple date formatter to 24 hours
        val formatter = SimpleDateFormat("kk:mm:ss")

        // getting the time in 24 hours format
        val timeIn24Hours = formatter.format(currentDate)

        // printing the time
        return timeIn24Hours.toString()
    }

    fun Date.toString(format: String, locale: Locale = Locale.getDefault()): String {
        val formatter = SimpleDateFormat(format, locale)
        return formatter.format(this)
    }

    private fun getUnit(value: String): String? {
        Log.i("Unit: ", value)
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)
        val sdf = SimpleDateFormat("hh:mm", Locale.UK)
        sdf.timeZone = TimeZone.getDefault()

        return sdf.format(date)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.refresh -> {
                requestLocationData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}

