package com.example.readfileandcleanup.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.readfileandcleanup.R
import com.example.readfileandcleanup.config.FileParams
import com.example.readfileandcleanup.config.NotificationConstants
import com.example.readfileandcleanup.task.AppTask

class TaskWorker(private val ctx: Context, params: WorkerParameters): CoroutineWorker(ctx, params)  {

    override suspend fun doWork(): Result {
        val fileUrl = inputData.getString(FileParams.KEY_FILE_URL) ?: ""
        val fileName = inputData.getString(FileParams.KEY_FILE_NAME) ?: ""
        val fileType = inputData.getString(FileParams.KEY_FILE_TYPE) ?: ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = NotificationConstants.CHANNEL_NAME
            val description = NotificationConstants.CHANNEL_DESCRIPTION
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(NotificationConstants.CHANNEL_ID, name, importance)
            channel.description = description

            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?
            notificationManager?.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(ctx, NotificationConstants.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Download your file...")
            .setOngoing(true)
            .setProgress(0,0, true)

        NotificationManagerCompat.from(ctx).notify(NotificationConstants.NOTIFICATION_ID, builder.build())
        val uri = AppTask.getSaveFileUri(
            fileName = fileName,
            fileType = fileType,
            fileUrl = fileUrl,
            context = ctx
        )

        return if (uri != null) {
            Result.success(workDataOf(FileParams.KEY_FILE_URI to uri.toString()))
        } else {
            return Result.failure()
        }
    }
}