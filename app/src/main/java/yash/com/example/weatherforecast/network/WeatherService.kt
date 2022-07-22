package yash.com.example.weatherforecast.network

import retrofit.Call
import retrofit.http.GET
import retrofit.http.Query
import yash.com.example.weatherforecast.model.WeatherResponse

interface WeatherService {

    @GET("2.5/weather")
    fun getWeather(
        @Query("lat") lat :Double,
        @Query("lon") lon :Double,
        @Query("units") units : String?,
        @Query("appid") appid :String?
    ) : Call<WeatherResponse>
}