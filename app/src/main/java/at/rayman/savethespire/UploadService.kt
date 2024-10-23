package at.rayman.savethespire

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.PUT
import retrofit2.http.Part

interface UploadService {

    @Multipart
    @PUT("upload")
    fun uploadZip(@Part zip: MultipartBody.Part): Call<ResponseBody>

}