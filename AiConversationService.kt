package com.aicaller.app.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.aicaller.app.R
import com.aicaller.app.ai.LlmClient
import com.aicaller.app.ai.SpeechListener
import com.aicaller.app.ai.VoiceSpeaker
import com.aicaller.app.data.AppDatabase
import com.aicaller.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Foreground service that runs for the lifetime of an AI-handled call.
 * Loop: listen for caller speech -> send transcript to LLM -> speak the reply -> repeat,
 * until the call ends or the caller asks for a human / the LLM signals the call is done.
 */
class AiConversationService : Service() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private lateinit var speaker: VoiceSpeaker
    private lateinit var listener: SpeechListener
    private val transcript = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        speaker = VoiceSpeaker(applicationContext)
        listener = SpeechListener(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.getStringExtra(EXTRA_PHONE_NUMBER) ?: "Unknown"
        val recordId = intent?.getLongExtra(EXTRA_CALL_RECORD_ID, -1L) ?: -1L

        startForeground(NOTIFICATION_ID, buildNotification(number))
        runConversation(number, recordId)
        return START_NOT_STICKY
    }

    private fun runConversation(number: String, recordId: Long) {
        scope.launch {
            val settings = SettingsRepository(applicationContext).settingsFlow.first()
            val greeting = settings.greeting.replace("{OWNER_NAME}", settings.ownerName.ifBlank { "the owner" })
            val llm = LlmClient(settings.llmApiKey, settings.llmEndpoint, settings.llmModel)

            val systemPrompt = """
                You are a polite phone-answering assistant speaking on behalf of ${settings.ownerName.ifBlank { "the device owner" }}.
                Keep replies short (1-2 sentences), spoken-language style, no markdown.
                Find out who is calling and why, and offer to take a message.
                If the caller has an emergency, tell them to call emergency services directly.
                If the caller insists on reaching the owner immediately, say you'll pass along the message right away.
            """.trimIndent()

            speaker.speak(greeting)
            appendTranscript("assistant", greeting)

            val history = mutableListOf(LlmClient.Turn("assistant", greeting))

            // Conversation loop — bounded so a stuck call can't run forever.
            repeat(MAX_TURNS) {
                val heard = listener.listenOnce().firstOrNull() ?: return@repeat
                appendTranscript("caller", heard)
                history.add(LlmClient.Turn("caller", heard))

                val reply = llm.nextReply(systemPrompt, history).getOrElse {
                    "Sorry, I'm having trouble right now — I'll make sure your message gets through."
                }
                appendTranscript("assistant", reply)
                history.add(LlmClient.Turn("assistant", reply))
                speaker.speak(reply)
            }

            if (recordId >= 0) {
                val dao = AppDatabase.get(applicationContext).callRecordDao()
                dao.getById(recordId)?.let { record ->
                    dao.update(record.copy(transcript = transcript.toString()))
                }
            }
        }
    }

    private fun appendTranscript(speakerTag: String, text: String) {
        transcript.append("[$speakerTag] $text\n")
    }

    override fun onDestroy() {
        job.cancel()
        speaker.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(number: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI answering call")
            .setContentText("Talking with $number on your behalf")
            .setSmallIcon(R.drawable.ic_call_notification)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Active AI calls", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CALL_RECORD_ID = "extra_call_record_id"
        private const val CHANNEL_ID = "ai_call_channel"
        private const val NOTIFICATION_ID = 42
        private const val MAX_TURNS = 20

        /**
         * Holds a reference to the live Telecom Call so the service (or UI) can
         * hang up / transfer if needed. Set by AiInCallService when a call goes active.
         */
        @Volatile var activeCall: android.telecom.Call? = null
            private set

        fun attachCall(call: android.telecom.Call) {
            activeCall = call
        }
    }
}
