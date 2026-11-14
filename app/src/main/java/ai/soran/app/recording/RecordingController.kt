package ai.soran.app.recording

import ai.soran.app.audio.PcmRecorder
import ai.soran.app.core.model.DriveContext
import ai.soran.app.core.model.Moment
import ai.soran.app.core.model.SeatPosition
import ai.soran.app.core.model.Utterance
import ai.soran.app.core.text.KeywordTagger
import ai.soran.app.core.text.MomentTitler
import ai.soran.app.core.text.PiiMasker
import ai.soran.app.core.text.TriggerPhrase
import ai.soran.app.data.SoranRepository
import ai.soran.app.location.LocationSource
import ai.soran.app.vehicle.VehicleMonitor
import ai.soran.app.voice.SpeechEvent
import ai.soran.app.voice.VoiceServices
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/** 기록 설정 (마이페이지 > 음성 메모·위치 설정). */
data class RecordingSettings(
    val sttEnabled: Boolean = true,
    val saveLocation: Boolean = true,
    val voiceGuide: Boolean = true,
    /** '기록해줘' 음성 명령으로 시작 */
    val voiceCommand: Boolean = true,
    /** 원본 음성 저장 → 정차 후 다시 듣기 (앱 마이크 + 온디바이스 인식) */
    val keepAudio: Boolean = true,
)

sealed interface RecState {
    /** 여행 중이 아님 */
    data object Off : RecState
    /** '기록해줘'를 기다리는 중 */
    data object Standby : RecState
    data class Recording(val startedAt: Long, val partial: String, val lines: Int, val level: Double) : RecState
    data object Processing : RecState
    data class Saved(val momentId: Long, val time: Long, val withLocation: Boolean, val title: String) : RecState
    data class Failed(val reason: String) : RecState
}

/** 기록 중인 여행 정보 (홈 화면 '여행 시작'에서 만들어져요). */
data class LiveTrip(
    val tripId: Long,
    val name: String,
    val type: String,
    val startedAt: Long,
    val startOdometerKm: Double,
    val participants: List<String>,
    val seats: List<SeatPosition>,
)

/**
 * 순간 기록 상태 기계.
 *
 *  Standby ──('기록해줘' / 버튼)──▶ Recording ──(말이 멈춤 / '기록 그만' / 버튼)──▶ Processing ──▶ Saved ──▶ Standby
 *
 *  - 시작·저장 시 TextToSpeech 안내
 *  - 문장마다 PII 마스킹 후 저장
 *  - 저장 시점의 위치·주행 맥락(경과 시간, 주행 거리, 속도, 외기온, 좌석, 내비 목적지)을 카드에 결합
 */
class RecordingController(
    private val scope: CoroutineScope,
    private val voice: VoiceServices,
    private val vehicle: VehicleMonitor,
    private val location: LocationSource,
    private val repo: SoranRepository,
) {
    private val _state = MutableStateFlow<RecState>(RecState.Off)
    val state: StateFlow<RecState> = _state.asStateFlow()
    /** 대기 중에 마지막으로 들린 말 — 인식이 되는지 화면에서 바로 확인하려고 보여줘요 */
    private val _heard = MutableStateFlow("")
    val heard: StateFlow<String> = _heard.asStateFlow()

    var settings = RecordingSettings()
    private var live: LiveTrip? = null
    private val recorder = PcmRecorder()

    private var startedAt = 0L
    private val lines = mutableListOf<Utterance>()
    @Volatile private var lastSpeechAt = 0L
    private var audioToken: String? = null
    private var streaming = false
    /** 실제 캡처(SDK 마이크 또는 앱 마이크)를 시작했는지 — 안내음 대기 중에는 false */
    private var capturing = false
    private var watchdog: Job? = null
    /** 스트림 종료 후 마지막 인식 결과가 도착했는지 */
    private var streamEnded: CompletableDeferred<Unit>? = null

    init {
        scope.launch { voice.speech.events.collect { onSpeech(it) } }
    }

    fun attach(trip: LiveTrip?) {
        live = trip
        if (trip == null) { cancel(); _state.value = RecState.Off } else enterStandby()
    }

    /** 화면 버튼 / 데모 컨트롤: 기록 시작 또는 종료 */
    fun toggleByButton() {
        when (_state.value) {
            is RecState.Standby, is RecState.Saved, is RecState.Failed -> begin("")
            is RecState.Recording -> finish()
            else -> Unit
        }
    }

    fun cancel() {
        watchdog?.cancel()
        stopCapture()
        audioToken?.let { repo.audioFileFor(it).delete() }
        audioToken = null
        lines.clear()
        if (live != null) enterStandby()
    }

    // ---- 내부 ----

    private fun enterStandby() {
        _state.value = RecState.Standby
        if (settings.sttEnabled && settings.voiceCommand) voice.speech.listen()
    }

    private fun onSpeech(e: SpeechEvent) {
        when (val s = _state.value) {
            is RecState.Standby -> when (e) {
                // 완료(Final)뿐 아니라 중간 결과(Partial)에서도 ‘기록해줘’를 찾아요.
                // SDK 가 완료 표시 없이 인식을 끝내는 경우에도 놓치지 않게요.
                is SpeechEvent.Partial, is SpeechEvent.Final -> {
                    val text = if (e is SpeechEvent.Final) e.text else (e as SpeechEvent.Partial).text
                    if (text.isNotBlank()) _heard.value = text
                    (TriggerPhrase.detect(text) as? TriggerPhrase.Command.Start)?.let { _heard.value = ""; begin(it.carryOver) }
                }
                is SpeechEvent.Ended, is SpeechEvent.Error -> scope.launch {
                    delay(if (e is SpeechEvent.Error) 3_000 else 300)
                    if (_state.value is RecState.Standby && settings.voiceCommand && settings.sttEnabled) voice.speech.listen()
                }
                else -> Unit
            }
            is RecState.Recording -> when (e) {
                is SpeechEvent.Partial -> { lastSpeechAt = now(); _state.value = s.copy(partial = PiiMasker.mask(e.text).text) }
                is SpeechEvent.Level -> _state.value = s.copy(level = e.rms)
                is SpeechEvent.Final -> {
                    lastSpeechAt = now()
                    if (TriggerPhrase.detect(e.text) == TriggerPhrase.Command.Stop) { finish(); return }
                    addLine(e.text)
                    _state.value = s.copy(partial = "", lines = lines.size)
                }
                // SDK 마이크 모드는 한 발화마다 인식이 끝나므로, 기록 중이면 이어서 다시 들어요.
                is SpeechEvent.Ended -> if (capturing && !streaming) voice.speech.listen()
                is SpeechEvent.Error -> Unit
            }
            is RecState.Processing -> when (e) {
                is SpeechEvent.Final -> addLine(e.text)
                is SpeechEvent.Ended, is SpeechEvent.Error -> streamEnded?.complete(Unit)
                else -> Unit
            }
            else -> Unit
        }
    }

    private fun begin(carryOver: String) {
        if (!settings.sttEnabled || live == null) return
        if (_state.value is RecState.Recording) return
        voice.speech.stop()
        lines.clear()
        startedAt = now(); lastSpeechAt = startedAt
        if (carryOver.isNotBlank()) addLine(carryOver)
        _state.value = RecState.Recording(startedAt, "", lines.size, 0.0)
        if (settings.voiceGuide) voice.speaker.speak("기록을 시작할게요")
        scope.launch {
            delay(if (settings.voiceGuide) 1_200 else 200) // 안내음이 기록에 섞이지 않도록
            if (_state.value !is RecState.Recording) return@launch
            startCapture()
        }
        watchdog?.cancel()
        watchdog = scope.launch {
            while (_state.value is RecState.Recording) {
                delay(500)
                val quiet = now() - lastSpeechAt
                val total = now() - startedAt
                if ((quiet > SILENCE_MS && total > MIN_MS) || total > MAX_MS) { finish(); break }
            }
        }
    }

    private fun startCapture() {
        capturing = true
        streaming = settings.keepAudio && voice.speech.supportsStreaming
        if (streaming) {
            val token = UUID.randomUUID().toString()
            audioToken = token
            voice.speech.beginStream()
            val ok = recorder.start(repo.audioFileFor(token)) { chunk ->
                voice.speech.feed(chunk)
                // 인식 결과와 별개로 음량으로 말소리 감지 → '말이 멈추면 저장'
                if (pcmRms(chunk) > VOICE_RMS) lastSpeechAt = now()
            }
            if (!ok) { streaming = false; audioToken = null; voice.speech.listen() }
        } else {
            voice.speech.listen()
        }
    }

    private fun stopCapture() {
        val wasCapturing = capturing
        capturing = false
        if (!wasCapturing) return
        if (streaming) {
            streamEnded = CompletableDeferred()
            recorder.stop(); voice.speech.endStream()
        } else voice.speech.stop()
        streaming = false
    }

    /** 16bit little-endian PCM 의 RMS (0..32767). */
    private fun pcmRms(b: ByteArray): Double {
        var sum = 0.0; var n = 0; var i = 0
        while (i + 1 < b.size) {
            val v = (b[i].toInt() and 0xff) or (b[i + 1].toInt() shl 8)
            sum += v.toDouble() * v; n++; i += 2
        }
        return if (n == 0) 0.0 else Math.sqrt(sum / n)
    }

    private fun addLine(raw: String) {
        val t = PiiMasker.mask(raw.trim()).text
        if (t.isNotEmpty()) lines += Utterance(((now() - startedAt) / 1000).toInt(), t)
    }

    private fun finish() {
        if (_state.value !is RecState.Recording) return
        watchdog?.cancel()
        val pending = (_state.value as RecState.Recording).partial
        stopCapture()
        _state.value = RecState.Processing
        scope.launch {
            // 마지막 인식 결과를 받을 시간: 앱 마이크 모드는 인식 종료(onEndedRecognition)까지 기다려요.
            streamEnded?.let { withTimeoutOrNull(6_000) { it.await() } } ?: delay(700)
            streamEnded = null
            if (lines.isEmpty() && pending.isNotBlank()) addLine(pending)
            val trip = live
            if (lines.isEmpty() || trip == null) {
                audioToken?.let { repo.audioFileFor(it).delete() }; audioToken = null
                _state.value = RecState.Failed("인식된 말이 없어요")
                delay(2_800); if (_state.value is RecState.Failed) enterStandby()
                return@launch
            }
            val m = buildMoment(trip)
            val id = repo.addMoment(m)
            if (settings.voiceGuide) voice.speaker.speak("순간을 저장했어요")
            _state.value = RecState.Saved(id, m.createdAt, m.location != null, m.title)
            lines.clear(); audioToken = null
            delay(2_800)
            if (_state.value is RecState.Saved) enterStandby()
        }
    }

    private fun buildMoment(trip: LiveTrip): Moment {
        val v = vehicle.state.value
        val loc = if (settings.saveLocation) location.current() else null
        val transcript = lines.joinToString(" ") { it.text }
        val tags = KeywordTagger.tags(transcript)
        val place = KeywordTagger.placeMentions(transcript).firstOrNull()
        val nav = voice.nav.destination.value
        return Moment(
            id = 0, tripId = trip.tripId, createdAt = now(), location = loc, placeLabel = place,
            autoTitle = MomentTitler.title(lines), customTitle = null,
            summary = MomentTitler.summary(lines, place, tags),
            utterances = lines.toList(), tags = tags,
            audioPath = audioToken?.let { repo.audioFileFor(it).absolutePath },
            durationSec = ((now() - startedAt) / 1000).toInt().coerceAtLeast(1),
            context = DriveContext(
                tripElapsedSec = (now() - trip.startedAt) / 1000,
                tripDistanceKm = (v.odometerKm - trip.startOdometerKm).takeIf { it > 0.05 },
                speedKmh = v.speedKmh, drivingState = v.drivingState,
                outsideTempC = v.outsideTempC, weather = null,
                occupiedSeats = trip.seats,
                navDestination = (nav as? ai.soran.app.voice.NavDestination.Found)?.name,
            ),
        )
    }

    private fun now() = System.currentTimeMillis()

    private companion object {
        const val SILENCE_MS = 6_000L
        const val MIN_MS = 3_000L
        const val MAX_MS = 180_000L
        const val VOICE_RMS = 900.0
    }
}
