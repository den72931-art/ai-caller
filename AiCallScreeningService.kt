package com.aicaller.app.call

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.CallScreeningService.CallResponse
import android.util.Log
import com.aicaller.app.data.ScreeningMode
import com.aicaller.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Runs BEFORE the phone rings. This is where we can silently decline known spam,
 * or just let a call through so AiInCallService / the dialer UI can handle it.
 *
 * To receive callbacks, the user must set AiCaller as their
 * "Caller ID & spam app" (Settings > Apps > Default apps > Phone app),
 * which does NOT require taking over as the full default dialer.
 */
class AiCallScreeningService : CallScreeningService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart ?: ""
        scope.launch {
            val settings = SettingsRepository(applicationContext).settingsFlow.first()

            val looksLikeSpam = callDetails.callerNumberVerificationStatus ==
                Call.Details.VERIFICATION_STATUS_FAILED

            val response = CallResponse.Builder()

            when {
                settings.autoBlockSpamLikely && looksLikeSpam -> {
                    // Silently reject calls that failed STIR/SHAKEN verification (likely spoofed/spam)
                    response
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipCallLog(false)
                        .setSkipNotification(true)
                    Log.i(TAG, "Blocked likely-spam call from $number")
                }
                settings.screeningMode == ScreeningMode.OFF -> {
                    response.setDisallowCall(false)
                }
                else -> {
                    // Let it through — AiInCallService decides whether to auto-answer
                    // based on contacts / screening mode, once it actually rings.
                    response.setDisallowCall(false)
                }
            }

            respondToCall(callDetails, response.build())
        }
    }

    companion object {
        private const val TAG = "AiCallScreeningService"
    }
}
