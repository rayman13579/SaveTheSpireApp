package at.rayman.savethespire

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import at.rayman.savethespire.ui.theme.SaveTheSpireTheme
import io.github.lumkit.io.LintFile
import io.github.lumkit.io.LintFileConfiguration
import io.github.lumkit.io.data.IoModel
import io.github.lumkit.io.data.LintFileConfig
import io.github.lumkit.io.data.PermissionType
import io.github.lumkit.io.file
import io.github.lumkit.io.requestAccessPermission
import io.github.lumkit.io.shell.AdbShellPublic
import io.github.lumkit.io.shell.ShizukuUtil
import io.github.lumkit.io.takePersistableUriPermission
import io.github.lumkit.io.use
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.io.File

const val DIRECTORY = "/storage/emulated/0"

const val STS_DIRECTORY = "/storage/emulated/0/Android/Data/com.humble.SlayTheSpire/files"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaveTheSpireTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.0.119:2929/")
            .build()
        val uploadService = retrofit.create(UploadService::class.java)
        val activity = this
        LintFileConfiguration.instance.init(this, LintFileConfig(IoModel.SHIZUKU))
        val file = file(STS_DIRECTORY)
        file.use(
            onRequestPermission = { type: PermissionType ->
                when (type) {
                    PermissionType.EXTERNAL_STORAGE -> {
                        ActivityCompat.requestPermissions(
                            activity,
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ),
                            0x000001
                        )
                    }

                    PermissionType.MANAGE_STORAGE -> {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                        intent.data = Uri.parse("package:$packageName")
                        this.startActivity(intent)
                    }

                    PermissionType.STORAGE_ACCESS_FRAMEWORK -> {
                        activity.requestAccessPermission(0x000002, file.path)
                    }

                    PermissionType.SU -> {}
                    PermissionType.SHIZUKU -> try {
                        ShizukuUtil.requestPermission()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        Toast.makeText(
                            activity,
                            "shizuku not active",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            granted = {
                zipAndUpload(uploadService, file)
            }
        )
    }

    override fun onDestroy() {
        LintFileConfiguration.instance.destroy()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        takePersistableUriPermission(0x000002, requestCode, resultCode, data)
    }

    fun copy() {
        AdbShellPublic.doCmdSync("cp -r $DIRECTORY/Download/WATCHER.autosave $STS_DIRECTORY/saves/WATCHER.autosave")
    }

    fun zipAndUpload(uploadService: UploadService, file: LintFile) {
        ZipperKt.zip(file, "$DIRECTORY/TestKt.zip")
        val zipFile = File("$DIRECTORY/TestKt.zip")
        val filePart = MultipartBody.Part.createFormData(
            "zip",
            zipFile.name,
            RequestBody.create(null, File("$DIRECTORY/TestKt.zip"))
        )
        val call = uploadService.uploadZip(filePart)
        call.enqueue(object : Callback<ResponseBody> {
            override fun onResponse(
                call: Call<ResponseBody>,
                response: Response<ResponseBody>
            ) {
                println(response)
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                println("rip")
            }
        })
    }

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SaveTheSpireTheme {
        Greeting("Android")
    }
}