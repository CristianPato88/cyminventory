package com.cym.inventory

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = InventoryStore(applicationContext)
        val repo = HouseholdRepository(applicationContext, store)
        setContent { AppRoot(store, repo) }
    }
}
