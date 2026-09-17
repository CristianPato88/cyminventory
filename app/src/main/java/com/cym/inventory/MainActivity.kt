package com.cym.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.File
import java.util.concurrent.TimeUnit
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(cacheDir, "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
        }
        val store = InventoryStore(applicationContext)
        val repo = HouseholdRepository(applicationContext, store)
        setContent { AppRoot(store, repo) }

        val syncCheck = PeriodicWorkRequestBuilder<SyncCheckWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork("cym_sync_check", ExistingPeriodicWorkPolicy.KEEP, syncCheck)
    }
}
