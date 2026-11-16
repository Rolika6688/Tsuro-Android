package com.tsuro.game.storage

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import game.storage.GameCodec
import game.storage.SaveFormatException
import game.storage.SavedGame
import game.storage.SettingsCodec
import game.storage.SettingsData
import game.storage.Statistics
import game.storage.StatisticsManager
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.tsuroStore by preferencesDataStore(name = "tsuro")

/** Thin DataStore wrapper. All parsing/validation lives in the tested :engine codecs; damaged data never crashes the app. */
class AppStorage(context: Context) {
    private val store = context.applicationContext.tsuroStore
    private val settingsKey = stringPreferencesKey("settings")
    private val statsKey = stringPreferencesKey("statistics")
    private val gameKey = stringPreferencesKey("saved_game")

    private suspend fun prefs(): Preferences = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()

    suspend fun settings(): SettingsData = SettingsCodec.decode(prefs()[settingsKey])
    suspend fun saveSettings(s: SettingsData) { store.edit { it[settingsKey] = SettingsCodec.encode(s) } }

    suspend fun stats(): Statistics = StatisticsManager.decode(prefs()[statsKey])
    suspend fun saveStats(s: Statistics) { store.edit { it[statsKey] = StatisticsManager.encode(s) } }
    suspend fun resetStats() { store.edit { it.remove(statsKey) } }

    /** Returns null (and deletes the bad entry) when the stored game is missing or fails validation. */
    suspend fun savedGame(): SavedGame? {
        val text = prefs()[gameKey] ?: return null
        return try { GameCodec.decode(text) } catch (e: SaveFormatException) { clearGame(); null }
    }
    suspend fun saveGame(g: SavedGame) { store.edit { it[gameKey] = GameCodec.encode(g) } }
    suspend fun clearGame() { store.edit { it.remove(gameKey) } }
}
