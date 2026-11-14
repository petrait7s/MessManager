package com.messmanager.app.ui

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.ds by preferencesDataStore("mess_prefs")

data class UiPrefs(val theme: Int = 0, val reduceMotion: Boolean = false, val autoBackup: Boolean = false, val lastAutoBackup: Long = 0)

class Prefs(private val ctx: Context) {
    private val kTheme = intPreferencesKey("theme")
    private val kMotion = booleanPreferencesKey("reduce_motion")
    private val kAuto = booleanPreferencesKey("auto_backup")
    private val kLast = longPreferencesKey("last_auto_backup")

    val flow: Flow<UiPrefs> = ctx.ds.data.map {
        UiPrefs(it[kTheme] ?: 0, it[kMotion] ?: false, it[kAuto] ?: false, it[kLast] ?: 0L)
    }

    suspend fun setTheme(v: Int) { ctx.ds.edit { it[kTheme] = v } }
    suspend fun setReduceMotion(v: Boolean) { ctx.ds.edit { it[kMotion] = v } }
    suspend fun setAutoBackup(v: Boolean) { ctx.ds.edit { it[kAuto] = v } }
    suspend fun setLastAuto(v: Long) { ctx.ds.edit { it[kLast] = v } }
}
