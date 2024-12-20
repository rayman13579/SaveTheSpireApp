package at.rayman.savethespire

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import at.rayman.savethespire.NetworkService.Companion.downloadSave
import at.rayman.savethespire.NetworkService.Companion.getLatestCommit
import at.rayman.savethespire.NetworkService.Companion.uploadSave
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.RequestBody
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Supplier

const val ZIP_PATH = "/storage/emulated/0/SaveTheSpire.zip"

const val STS_PATH = "/storage/emulated/0/Android/Data/com.humble.SlayTheSpire/files"

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SaveTheSpireTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Page(
                        zipSave = ::zipSave,
                        uploadSave = ::uploadSave,
                        downloadSave = ::downloadSave,
                        clearSaveDirectory = ::clearSaveDirectory,
                        unzipSave = ::unzipSave,
                        getLatestCommit = ::getLatestCommit
                    )
                }
            }
        }

        LintFileConfiguration.instance.init(this, LintFileConfig(IoModel.SHIZUKU))
    }

    fun copy() {
        AdbShellPublic.doCmdSync("cp -r $ZIP_PATH/Download/WATCHER.autosave $STS_PATH/saves/WATCHER.autosave")
    }

    fun zipSave(): Result {
        val file = file(STS_PATH)
        return doFileStuff(file) {
            Zipper.zip(file)
        }
    }

    fun unzipSave(): Result {
        val file = file(ZIP_PATH)
        return doFileStuff(file) {
            Zipper.unzip(file)
        }
    }

    fun clearSaveDirectory(): Result {
        return doFileStuff(file(STS_PATH)) {
            file("$STS_PATH/preferences").delete()
            file("$STS_PATH/runs").delete()
            file("$STS_PATH/saves").delete()
            Result.success("Cleared directories")
        }
    }

    fun doFileStuff(file: LintFile, action: Supplier<Result>): Result {
        var result: Result = Result.error("Unknown error")
        file.use(onRequestPermission = {
            try {
                handlePermissions(this, it, file)
            } catch (e: Exception) {
                result = Result.error(e.message ?: "Error while requesting permission")
            }
        }, granted = {
            result = action.get()
        })
        return result
    }

    fun uploadSave(): Result {
        val zipFile = File(ZIP_PATH)
        val filePart = MultipartBody.Part.createFormData(
            "saveTheSpire", zipFile.name,
            RequestBody.create(null, zipFile)
        )
        try {
            var response = NetworkService.uploadSave(filePart).execute()
            return if (response.isSuccessful) {
                Result.success("Upload ended with status: ${response.code()}")
            } else {
                Result.error("Upload failed with status: ${response.code()}")
            }
        } catch (e: Exception) {
            return Result.error(e.message ?: "Unknown error while uploading")
        }
    }

    fun downloadSave(): Result {
        try {
            var response = NetworkService.downloadSave().execute()
            return if (response.isSuccessful) {
                val file = File(ZIP_PATH)
                file.writeBytes(response.body()?.bytes() ?: byteArrayOf())
                Result.success("Download ended with status: ${response.code()}")
            } else {
                Result.error("Download failed")
            }
        } catch (e: Exception) {
            return Result.error(e.message ?: "Unknown error while downloading")
        }
    }

    fun getLatestCommit(): String {
        var response = NetworkService.getLatestCommit().execute()
        return if (response.isSuccessful) {
            response.body()?.string() ?: "Error while getting latest commit"
        } else {
            "Error while getting latest commit"
        }
    }

    fun handlePermissions(activity: Activity, type: PermissionType, file: LintFile) {
        when (type) {
            PermissionType.EXTERNAL_STORAGE -> {
                ActivityCompat.requestPermissions(
                    activity, arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), 0x000001
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
                    activity, "shizuku not active", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onDestroy() {
        LintFileConfiguration.instance.destroy()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        takePersistableUriPermission(0x000002, requestCode, resultCode, data)
    }

}

@Composable
fun Page(
    zipSave: () -> Result, uploadSave: () -> Result,
    downloadSave: () -> Result, clearSaveDirectory: () -> Result, unzipSave: () -> Result,
    getLatestCommit: () -> String
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("No Errors :D") }
    var latestCommit by remember { mutableStateOf("Loading...") }

    scope.launch(Dispatchers.IO) {
        latestCommit = getLatestCommit()
    }

    fun addLog(value: String) {
        log += "\n" + DateTimeFormatter.ofPattern("HH:mm:ss")
            .format(LocalDateTime.now()) + " | " + value
    }

    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxSize()
    ) {
        Spacer(modifier = Modifier.fillMaxHeight(0.15f))
        Text(
            "Slay the Spire Cross Save",
            fontSize = 30.sp
        )
        Spacer(modifier = Modifier.fillMaxHeight(0.2f))
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        log += System.lineSeparator()
                        var result = withContext(Dispatchers.IO) {
                            zipSave()
                        }
                        addLog(result.value)
                        result = withContext(Dispatchers.IO) {
                            uploadSave()
                        }
                        addLog(result.value)
                        withContext(Dispatchers.IO) {
                            File(ZIP_PATH).delete()
                            addLog("Zip deleted")
                        }
                        latestCommit = withContext(Dispatchers.IO) {
                            getLatestCommit()
                        }
                        loading = false
                    }
                },
                shape = RoundedCornerShape(15),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .aspectRatio(1f)
            ) {
                Text(
                    "Upload Save",
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        log += System.lineSeparator()
                        var result = withContext(Dispatchers.IO) {
                            downloadSave()
                        }
                        addLog(result.value)
                        if (result.success) {
                            result = withContext(Dispatchers.IO) {
                                clearSaveDirectory()
                            }
                            addLog(result.value)
                            if (result.success) {
                                result = withContext(Dispatchers.IO) {
                                    unzipSave()
                                }
                                addLog(result.value)
                            }
                            withContext(Dispatchers.IO) {
                                File(ZIP_PATH).delete()
                                addLog("Zip deleted")
                            }
                        }
                        loading = false
                    }
                },
                shape = RoundedCornerShape(15),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .aspectRatio(1f)
            ) {
                Text(
                    "Download Save",
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .padding(top = 20.dp)
                    .height(10.dp)
                    .fillMaxWidth(0.8f)
            )
        } else {
            LinearProgressIndicator(
                progress = 0f,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .height(8.dp)
                    .fillMaxWidth(0.8f)
            )
        }
        Spacer(modifier = Modifier.fillMaxHeight(0.03f))
        Text(
            "latest commit",
            color = Color.Gray,
            fontSize = 12.sp
        )
        Text(
            latestCommit,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.fillMaxHeight(0.03f))
        TextField(
            value = log,
            enabled = false,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight()
                .padding(bottom = 20.dp)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    SaveTheSpireTheme {
        Page(
            { Result.success("zipSave") },
            { Result.success("uploadSave") },
            { Result.success("downloadSave") },
            { Result.success("clearDirectories") },
            { Result.success("unzipSave") },
            { "latestCommit" }
        )
    }
}