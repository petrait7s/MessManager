package com.messmanager.app

import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.messmanager.app.ui.AppRoot
import com.messmanager.app.ui.AppVm
import com.messmanager.app.ui.MessTheme

class MainActivity : ComponentActivity() {
    private val vm: AppVm by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val systemReduce = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        setContent {
            val p by vm.uiPrefs.collectAsState()
            MessTheme(p.theme, p.reduceMotion || systemReduce) { AppRoot(vm) }
        }
    }

    override fun onStart() { super.onStart(); vm.onStart() }
    override fun onStop() { super.onStop(); vm.onStop() }
}
