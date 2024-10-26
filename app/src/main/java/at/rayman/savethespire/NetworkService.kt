package at.rayman.savethespire

import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.Multipart
import retrofit2.http.PUT
import retrofit2.http.Part

interface NetworkService {

    companion object {
        private const val BASE_URL = "http://10.0.0.119:2929/"

        private val retrofit: Retrofit by lazy {
            val userAgentInterceptor = Interceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", "SaveTheSpireApp")
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(userAgentInterceptor)
                .build()
            Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(okHttpClient)
                .build()
        }

        val instance: NetworkService by lazy {
            retrofit.create(NetworkService::class.java)
        }

        fun Companion.uploadZip(zip: MultipartBody.Part): Call<ResponseBody> {
            return instance.uploadZip(zip)
        }

    }

    @Multipart
    @PUT("upload")
    fun uploadZip(@Part zip: MultipartBody.Part): Call<ResponseBody>

}