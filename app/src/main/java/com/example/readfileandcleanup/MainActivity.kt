package com.example.readfileandcleanup

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.readfileandcleanup.config.FileParams
import com.example.readfileandcleanup.config.Network
import com.example.readfileandcleanup.databinding.ActivityMainBinding
import com.example.readfileandcleanup.model.File
import com.example.readfileandcleanup.worker.TaskWorker
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Author: Felix Serang
 * Reference:
 * - https://proandroiddev.com/step-by-step-guide-to-download-files-with-workmanager-b0231b03efd1
 * - https://github.com/velmurugan-murugesan/Android-Example/tree/master/DownloadFilesWithWorkManager
 */
class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var workManager: WorkManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // view preparation
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // work manager instance set
        workManager = WorkManager.getInstance(this)

        // button listener set
        binding.btnExec.setOnClickListener(this)


        // check permission
        if (!checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE) && !checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                201
            )
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            binding.btnExec.id -> {
                var file = File(
                    id = "20",
                    name = "test.csv",
                    type = "csv",
                    url = "${Network.ENDPOINT}/test.csv",
                    downloadedUri = null
                )

                startDownloadingFile(
                    file,
                    success = {
                        file = file.copy().apply {
                            isDownloading = false
                            downloadedUri = it
                        }
                    },
                    failed = {
                        file = file.copy().apply {
                            isDownloading = false
                            downloadedUri = null
                        }
                    },
                    running = {
                        file = file.copy().apply {
                            isDownloading = true
                            downloadedUri = null
                        }
                    }
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 201) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startDownloadingFile(
        file: File,
        success: (String) -> Unit,
        failed: (String) -> Unit,
        running: () -> Unit
    ) {
        val data = Data.Builder()

        data.apply {
            putString(FileParams.KEY_FILE_NAME, file.name)
            putString(FileParams.KEY_FILE_URL, file.url)
            putString(FileParams.KEY_FILE_TYPE, file.type)
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val fileDownloadWorker = OneTimeWorkRequestBuilder<TaskWorker>()
            .setConstraints(constraints)
            .setInputData(data.build())
            .build()

        workManager.enqueueUniqueWork(
            "downloadFile_${System.currentTimeMillis()}",
            ExistingWorkPolicy.KEEP,
            fileDownloadWorker
        )

        workManager.getWorkInfoByIdLiveData(fileDownloadWorker.id)
            .observe(this@MainActivity) { info ->
                info?.let {
                    when(it.state) {
                        WorkInfo.State.SUCCEEDED -> {
                            val uri = it.outputData.getString(FileParams.KEY_FILE_URI) ?: ""

                            if (uri != "") {
                                val uriPath = Uri.parse(uri)
                                uriPath.path?.let { filePath ->
                                    val fileRead = java.io.File(filePath)
                                    if (fileRead.exists()) {
                                        val bufferFile = FileInputStream(fileRead)
                                        val reader = BufferedReader(InputStreamReader(bufferFile))
                                        var line = reader.readLine()

                                        var i = 1;
                                        while (line != null && line != "") {
                                            Log.d("FILE_DATA_ROW", line)
                                            line = reader.readLine()
                                            i++
                                        }

                                        fileRead.delete()
                                    }
                                }
                            }

                            success("File $uri has success to download and read")
                        }
                        WorkInfo.State.FAILED -> {
                            failed("Download failed!")
                        }
                        WorkInfo.State.RUNNING -> {
                            running()
                        }
                        else -> {
                            failed("Something went wrong")
                        }
                    }
                }
            }
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}