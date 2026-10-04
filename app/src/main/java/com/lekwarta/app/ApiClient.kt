// app/ApiClient.kt
package com.lekwarta.app

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object ApiClient {
    // If Apache is on port 80 (default)
    const val BASE_URL = "http://10.0.2.2:8000/lekwarta/api"

    // If Apache is on port 8080, use this instead:
    // const val BASE_URL = "http://10.0.2.2:8080/lekwarta/api"

    // If you're testing on a real device, use your computer's IP:
    // const val BASE_URL = "http://192.168.1.xxx/lekwarta/api"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
}