package com.example.argon.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class TruckVoiceNavigator(context: Context) : TextToSpeech.OnInitListener {
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext, this)
    private var isReady = false
    private var lastSpokenKey: String? = null

    private val _lastAnnouncement = MutableStateFlow("Guía de voz lista (es-MX)")
    val lastAnnouncement: StateFlow<String> = _lastAnnouncement.asStateFlow()

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val esMx = Locale("es", "MX")
            val res = tts.setLanguage(esMx)
            isReady = res != TextToSpeech.LANG_MISSING_DATA && res != TextToSpeech.LANG_NOT_SUPPORTED
            if (!isReady) {
                tts.language = Locale("es", "ES")
                isReady = true
            }
            tts.setSpeechRate(0.95f)
        }
    }

    fun announce(message: String, dedupeKey: String? = null, enabled: Boolean = true, interrupt: Boolean = false) {
        if (dedupeKey != null && dedupeKey == lastSpokenKey) return
        lastSpokenKey = dedupeKey
        _lastAnnouncement.value = message
        if (!enabled || !isReady) return
        val queueMode = if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        runCatching {
            tts.speak(message, queueMode, null, dedupeKey ?: System.currentTimeMillis().toString())
        }
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }
    }
}
