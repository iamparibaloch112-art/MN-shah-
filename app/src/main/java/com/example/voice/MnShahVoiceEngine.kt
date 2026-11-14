package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.market.TradingAsset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

sealed interface ParsedVoiceCommand {
    data class RequestSignal(val asset: TradingAsset?, val rawCommand: String) : ParsedVoiceCommand
    data class SwitchAsset(val asset: TradingAsset, val rawCommand: String) : ParsedVoiceCommand
    data class GeneralQuestion(val query: String) : ParsedVoiceCommand
}

class MnShahVoiceEngine(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _ttsStatus = MutableStateFlow("Initializing Urdu Male Voice...")
    val ttsStatus: StateFlow<String> = _ttsStatus.asStateFlow()

    private val _sttError = MutableStateFlow<String?>(null)
    val sttError: StateFlow<String?> = _sttError.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var ttsInitializedRequested = false

    val isSpeechRecognitionAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun initializeAsync() {
        if (ttsInitializedRequested) return
        ttsInitializedRequested = true
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                scope.launch {
                    val selectedLocale = withContext(Dispatchers.IO) {
                        val urduPk = Locale.forLanguageTag("ur-PK")
                        val urduIn = Locale.forLanguageTag("ur-IN")

                        val pkRes = engine.setLanguage(urduPk)
                        val locale = when {
                            pkRes != TextToSpeech.LANG_MISSING_DATA && pkRes != TextToSpeech.LANG_NOT_SUPPORTED -> urduPk
                            engine.setLanguage(urduIn) !in listOf(
                                TextToSpeech.LANG_MISSING_DATA,
                                TextToSpeech.LANG_NOT_SUPPORTED
                            ) -> urduIn
                            else -> {
                                engine.setLanguage(Locale.US)
                                Locale.US
                            }
                        }

                        // Enumerating engine.voices involves Binder IPC across dozens of TTS profiles; keep off Main thread.
                        try {
                            val voices = engine.voices
                            val maleUrduVoice = voices?.firstOrNull { v ->
                                v.locale.language.equals("ur", ignoreCase = true) &&
                                    (v.name.contains("male", ignoreCase = true) ||
                                        v.name.contains("urd-x-urm", ignoreCase = true) ||
                                        v.name.contains("ur-pk-x", ignoreCase = true))
                            } ?: voices?.firstOrNull { v ->
                                v.locale.language.equals("ur", ignoreCase = true)
                            }
                            if (maleUrduVoice != null) {
                                engine.voice = maleUrduVoice
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not enumerate TTS voices: ${e.message}")
                        }
                        locale
                    }

                    engine.setPitch(0.84f)
                    engine.setSpeechRate(0.96f)
                    engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }
                    })

                    ttsReady = true
                    _ttsStatus.value = if (selectedLocale.language == "ur") {
                        "Urdu Male Voice Active (${selectedLocale.toLanguageTag()})"
                    } else {
                        "Urdu TTS pack missing on device — using ${selectedLocale.displayName} fallback"
                    }
                }
            } else {
                ttsReady = false
                _ttsStatus.value = "TTS engine unavailable on this device"
            }
        }
    }

    fun speakUrdu(text: String) {
        if (!ttsReady || text.isBlank()) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak failed", e)
            _isSpeaking.value = false
        }
    }

    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
        _isSpeaking.value = false
    }

    fun startListening(
        onFinalResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        _sttError.value = null
        if (!isSpeechRecognitionAvailable) {
            val msg = "On-device Speech Recognition service not installed. Use the text command bar below."
            _sttError.value = msg
            onError(msg)
            return
        }

        try {
            speechRecognizer?.destroy()
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer = recognizer

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _isListening.value = true
                    _partialTranscript.value = "Listening (Urdu/English)..."
                }

                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    _isListening.value = false
                }

                override fun onError(error: Int) {
                    _isListening.value = false
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Voice unclear. Try saying: MN SHAH mujhe signal do"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap mic and try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                        SpeechRecognizer.ERROR_NETWORK -> "Speech network error."
                        else -> "Speech recognition error ($error)."
                    }
                    _sttError.value = message
                    onError(message)
                }

                override fun onResults(results: Bundle?) {
                    _isListening.value = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull().orEmpty().trim()
                    _partialTranscript.value = best
                    if (best.isNotBlank()) {
                        onFinalResult(best)
                    } else {
                        onError("Could not capture speech.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!partial.isNullOrBlank()) {
                        _partialTranscript.value = partial
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ur-PK")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ur-PK")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer.startListening(intent)
        } catch (e: Exception) {
            _isListening.value = false
            val msg = e.localizedMessage ?: "Failed to start microphone recognition."
            _sttError.value = msg
            onError(msg)
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
        }
        _isListening.value = false
    }

    fun release() {
        stopSpeaking()
        scope.cancel()
        try {
            tts?.shutdown()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val TAG = "MnShahVoiceEngine"

        fun parseCommand(raw: String): ParsedVoiceCommand {
            val normalized = raw.trim().lowercase(Locale.US)
            val detectedAsset = when {
                normalized.contains("nzd") || normalized.contains("kiwi") -> TradingAsset.NZD_USD
                normalized.contains("ngn") || normalized.contains("naira") -> TradingAsset.USD_NGN
                normalized.contains("axie") || normalized.contains("axs") || normalized.contains("infinity") ->
                    TradingAsset.AXS_USD
                normalized.contains("eur") || normalized.contains("euro") -> TradingAsset.EUR_USD
                normalized.contains("gbp") || normalized.contains("pound") -> TradingAsset.GBP_USD
                normalized.contains("jpy") || normalized.contains("yen") -> TradingAsset.USD_JPY
                normalized.contains("xau") || normalized.contains("gold") || normalized.contains("sona") ->
                    TradingAsset.XAU_USD
                else -> null
            }

            val asksForSignal = normalized.contains("signal") ||
                normalized.contains("سگنل") ||
                normalized.contains("analyze") ||
                normalized.contains("tajziya") ||
                normalized.contains("tajzia") ||
                normalized.contains("trade") ||
                normalized.contains("mujhe") ||
                normalized.contains("batao") ||
                normalized.contains("shah")

            return when {
                asksForSignal -> ParsedVoiceCommand.RequestSignal(detectedAsset, raw)
                detectedAsset != null -> ParsedVoiceCommand.SwitchAsset(detectedAsset, raw)
                else -> ParsedVoiceCommand.GeneralQuestion(raw)
            }
        }
    }
}
