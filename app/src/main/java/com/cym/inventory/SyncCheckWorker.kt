package com.cym.inventory

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

private const val CHANNEL_ID = "cym_updates"
private const val PREFS_NAME = "cym_household"

/**
 * Free-tier friendly stand-in for push notifications: no server needed, just a periodic check
 * (WorkManager's minimum interval is 15 minutes) that looks for tasks/notes the other member
 * added since the last check and raises a local notification.
 */
internal class SyncCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val householdId = prefs.getString("householdId", null) ?: return Result.success()
        val memberId = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val lastCheck = prefs.getLong("lastSyncCheck", System.currentTimeMillis())
        val now = System.currentTimeMillis()
        val db = FirebaseFirestore.getInstance()

        val newTasks = runCatching {
            db.collection("households").document(householdId).collection("tasks")
                .whereGreaterThan("createdAt", lastCheck).get().await()
                .documents.filter { it.getString("createdById") != memberId }
        }.getOrDefault(emptyList())
        val newNotes = runCatching {
            db.collection("households").document(householdId).collection("notes")
                .whereGreaterThan("createdAt", lastCheck).get().await()
                .documents.filter { it.getString("authorId") != memberId }
        }.getOrDefault(emptyList())

        prefs.edit().putLong("lastSyncCheck", now).apply()
        updateLocation(householdId, memberId, db)

        if (newTasks.isNotEmpty() || newNotes.isNotEmpty()) {
            val body = buildString {
                if (newTasks.isNotEmpty()) append("${newTasks.size} tarea(s) nueva(s)")
                if (newTasks.isNotEmpty() && newNotes.isNotEmpty()) append(" · ")
                if (newNotes.isNotEmpty()) append("${newNotes.size} nota(s) nueva(s)")
            }
            showNotification("Novedades en casa", body)
        }
        return Result.success()
    }

    private suspend fun updateLocation(householdId: String, memberId: String, db: FirebaseFirestore) {
        val location = LocationUtil.getCurrentLocation(applicationContext) ?: return
        runCatching {
            db.collection("households").document(householdId).collection("members").document(memberId)
                .set(mapOf("lat" to location.latitude, "lng" to location.longitude, "locationUpdatedAt" to System.currentTimeMillis()),
                    com.google.firebase.firestore.SetOptions.merge()).await()
        }
    }

    private fun showNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Actualizaciones del hogar", NotificationManager.IMPORTANCE_HIGH))
        }
        val openApp = PendingIntent.getActivity(applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shared)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(1001, notification)
    }
}
