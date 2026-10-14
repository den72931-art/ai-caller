package com.aicaller.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aicaller_settings")

enum class ScreeningMode {
    OFF,                 // AiCaller does nothing, normal dialer behavior
    UNKNOWN_NUMBERS_ONLY,// AI answers only numbers not in contacts
    ALL_CALLS            // AI answers every incoming call
}

data class AiCallerSettings(
    val screeningMode: ScreeningMode = ScreeningMode.UNKNOWN_NUMBERS_ONLY,
    val greeting: String = "Hi, this is ${"{OWNER_NAME}"}'s AI assistant. ${"{OWNER_NAME}"} can't take " +
        "the call right now — I can take a message, or help with simple questions. Who's calling and what's this about?",
    val ownerName: String = "",
    val recordCalls: Boolean = false,
    val llmApiKey: String = "",
    val llmEndpoint: String = "https://api.anthropic.com/v1/messages",
    val llmModel: String = "claude-sonnet-5",
    val autoBlockSpamLikely: Boolean = true
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SCREENING_MODE = stringPreferencesKey("screening_mode")
        val GREETING = stringPreferencesKey("greeting")
        val OWNER_NAME = stringPreferencesKey("owner_name")
        val RECORD_CALLS = booleanPreferencesKey("record_calls")
        val LLM_API_KEY = stringPreferencesKey("llm_api_key")
        val LLM_ENDPOINT = stringPreferencesKey("llm_endpoint")
        val LLM_MODEL = stringPreferencesKey("llm_model")
        val AUTO_BLOCK_SPAM = booleanPreferencesKey("auto_block_spam")
    }

    val settingsFlow: Flow<AiCallerSettings> = context.dataStore.data.map { prefs ->
        val defaults = AiCallerSettings()
        AiCallerSettings(
            screeningMode = prefs[Keys.SCREENING_MODE]?.let {
                runCatching { ScreeningMode.valueOf(it) }.getOrDefault(defaults.screeningMode)
            } ?: defaults.screeningMode,
            greeting = prefs[Keys.GREETING] ?: defaults.greeting,
            ownerName = prefs[Keys.OWNER_NAME] ?: defaults.ownerName,
            recordCalls = prefs[Keys.RECORD_CALLS] ?: defaults.recordCalls,
            llmApiKey = prefs[Keys.LLM_API_KEY] ?: defaults.llmApiKey,
            llmEndpoint = prefs[Keys.LLM_ENDPOINT] ?: defaults.llmEndpoint,
            llmModel = prefs[Keys.LLM_MODEL] ?: defaults.llmModel,
            autoBlockSpamLikely = prefs[Keys.AUTO_BLOCK_SPAM] ?: defaults.autoBlockSpamLikely
        )
    }

    suspend fun update(transform: (AiCallerSettings) -> AiCallerSettings) {
        context.dataStore.edit { prefs ->
            // Read current, apply transform, write back
            val current = AiCallerSettings(
                screeningMode = prefs[Keys.SCREENING_MODE]?.let {
                    runCatching { ScreeningMode.valueOf(it) }.getOrDefault(ScreeningMode.UNKNOWN_NUMBERS_ONLY)
                } ?: ScreeningMode.UNKNOWN_NUMBERS_ONLY,
                greeting = prefs[Keys.GREETING] ?: AiCallerSettings().greeting,
                ownerName = prefs[Keys.OWNER_NAME] ?: "",
                recordCalls = prefs[Keys.RECORD_CALLS] ?: false,
                llmApiKey = prefs[Keys.LLM_API_KEY] ?: "",
                llmEndpoint = prefs[Keys.LLM_ENDPOINT] ?: AiCallerSettings().llmEndpoint,
                llmModel = prefs[Keys.LLM_MODEL] ?: AiCallerSettings().llmModel,
                autoBlockSpamLikely = prefs[Keys.AUTO_BLOCK_SPAM] ?: true
            )
            val updated = transform(current)
            prefs[Keys.SCREENING_MODE] = updated.screeningMode.name
            prefs[Keys.GREETING] = updated.greeting
            prefs[Keys.OWNER_NAME] = updated.ownerName
            prefs[Keys.RECORD_CALLS] = updated.recordCalls
            prefs[Keys.LLM_API_KEY] = updated.llmApiKey
            prefs[Keys.LLM_ENDPOINT] = updated.llmEndpoint
            prefs[Keys.LLM_MODEL] = updated.llmModel
            prefs[Keys.AUTO_BLOCK_SPAM] = updated.autoBlockSpamLikely
        }
    }
}
