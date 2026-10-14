package com.aicaller.app.call

import android.content.Intent
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.aicaller.app.data.AppDatabase
import com.aicaller.app.data.CallOutcome
import com.aicaller.app.data.CallRecord
import com.aicaller.app.data.ScreeningMode
import com.aicaller.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receives Call objects only while AiCaller is set as the device's
 * DEFAULT PHONE APP (Settings > Apps > Default apps > Phone app).
 * That's what makes Call.answer() / Call.reject() work.
 */
class AiInCallService : InCallService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var activeRecordId: Long? = null
    private var callStartTime: Long = 0L

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            when (state) {
                Call.STATE_ACTIVE -> onCallBecameActive(call)
                Call.STATE_DISCONNECTED -> onCallEnded(call)
                else -> Unit
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        call.registerCallback(callCallback)

        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        Log.i(TAG, "Incoming call from $number")

        scope.launch {
            val settings = SettingsRepository(applicationContext).settingsFlow.first()
            val isKnownContact = isNumberInContacts(number)

            val shouldAutoAnswer = when (settings.screeningMode) {
                ScreeningMode.OFF -> false
                ScreeningMode.ALL_CALLS -> true
                ScreeningMode.UNKNOWN_NUMBERS_ONLY -> !isKnownContact
            }

            if (shouldAutoAnswer && call.state == Call.STATE_RINGING) {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                Log.i(TAG, "Auto-answered call from $number")
            }
        }
    }

    private fun onCallBecameActive(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"
        callStartTime = System.currentTimeMillis()

        scope.launch {
            val dao = AppDatabase.get(applicationContext).callRecordDao()
            activeRecordId = dao.insert(
                CallRecord(
                    phoneNumber = number,
                    displayName = null,
                    startTimeMillis = callStartTime,
                    endTimeMillis = null,
                    outcome = CallOutcome.ANSWERED_BY_AI,
                    transcript = null,
                    aiSummary = null
                )
            )
        }

        // Hand audio control to the conversation service, which runs the
        // STT -> LLM -> TTS loop for the duration of the call.
        val intent = Intent(this, AiConversationService::class.java).apply {
            putExtra(AiConversationService.EXTRA_PHONE_NUMBER, number)
            putExtra(AiConversationService.EXTRA_CALL_RECORD_ID, activeRecordId ?: -1L)
        }
        startForegroundService(intent)
        AiConversationService.attachCall(call)
    }

    private fun onCallEnded(call: Call) {
        call.unregisterCallback(callCallback)
        stopService(Intent(this, AiConversationService::class.java))

        val recordId = activeRecordId ?: return
        val endTime = System.currentTimeMillis()
        scope.launch {
            val dao = AppDatabase.get(applicationContext).callRecordDao()
            dao.getById(recordId)?.let { record ->
                dao.update(record.copy(endTimeMillis = endTime))
            }
        }
        activeRecordId = null
    }

    private fun isNumberInContacts(number: String): Boolean {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(number)
            )
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)
                ?.use { it.moveToFirst() } ?: false
        } catch (e: SecurityException) {
            false // READ_CONTACTS not granted — treat as unknown
        }
    }

    companion object {
        private const val TAG = "AiInCallService"
    }
}
