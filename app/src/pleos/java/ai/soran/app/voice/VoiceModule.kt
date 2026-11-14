package ai.soran.app.voice

import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.OnServerConnectionListener
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.soran.app.BuildConfig
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import ai.pleos.playground.tts.constant.Mode as TtsMode

/*
 * ─────────────────────────────────────────────────────────────────────────────
 *  Pleos Connect SDK 연결부 (pleos 플레이버 전용)
 *  앱의 나머지 코드는 SpeechEngine / Speaker / NavInfo 인터페이스만 써요.
 *
 *  ⚠ SDK 버전에 따라 확인할 곳 (공식 문서 기준으로 작성, 에뮬레이터에서 컴파일 확인 필요)
 *   1) import 경로: stt.SpeechToText / stt.constant.Mode / tts.constant.Mode
 *   2) ResultListener 콜백 이름·인자 (onUpdated(stt, completed) 등)
 *   3) OnServerConnectionListener 의 실패 콜백 이름
 *   4) DestinationInfo 의 목적지 이름 필드 (name — 실제 SDK 에서 확인됨)
 * ─────────────────────────────────────────────────────────────────────────────
 */
object VoiceModule {
    /** 실행 중에 요청할 Pleos 권한 (매니페스트 선언만으로는 허용되지 않을 수 있어요) */
    val permissions = listOf("pleos.car.permission.STT_SERVICE", "pleos.car.permission.TTS_SERVICE", "pleos.car.permission.NAVI_ROUTE")

    fun create(context: Context): VoiceServices =
        VoiceServices(PleosSpeechEngine(context), PleosSpeaker(context), PleosNavInfo(context))
}

private const val TAG = "SoranPleos"

/**
 * SpeechToText 인스턴스 2개:
 *  - hybrid  : SDK 가 마이크를 쓰는 실시간 인식. 네트워크가 끊기면(터널 등) SDK 가 온디바이스로 전환.
 *  - onDevice: 앱이 녹음한 PCM 을 sendAudio() 로 넘기는 인식. 문서상 sendAudio 는 ON_DEVICE 모드 전용.
 */
class PleosSpeechEngine(context: Context) : SpeechEngine {
    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()
    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()
    override val supportsStreaming = true

    private val listener = object : ResultListener {
        override fun onReady() { Log.d(TAG, "STT onReady") }
        override fun onStartedRecognition() { Log.d(TAG, "STT onStartedRecognition") }
        override fun onUpdated(stt: String, completed: Boolean) {
            Log.d(TAG, "STT onUpdated completed=$completed text=$stt")
            _events.tryEmit(if (completed) SpeechEvent.Final(stt) else SpeechEvent.Partial(stt))
        }
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) { _events.tryEmit(SpeechEvent.Level(rms)) }
        override fun onEndedRecognition() { Log.d(TAG, "STT onEndedRecognition"); _events.tryEmit(SpeechEvent.Ended) }
        override fun onError(errorType: ErrorType) { Log.w(TAG, "STT onError $errorType"); _events.tryEmit(SpeechEvent.Error(errorType.name)) }
    }

    /** 초기화가 끝나기 전에 listen() 이 오면 기억해 뒀다가 준비되면 시작해요. */
    @Volatile private var pendingListen = false
    @Volatile private var onDeviceReady = false
    @Volatile private var feedFailed = false

    private val hybrid = SpeechToText(context.applicationContext, Mode.HYBRID)
    private val onDevice = SpeechToText(context.applicationContext, Mode.ON_DEVICE)

    init {
        hybrid.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                hybrid.addListener(listener)
                Log.d(TAG, "STT(HYBRID) initialized, clientId set=${BuildConfig.PLEOS_CLIENT_ID.isNotBlank()}")
                if (BuildConfig.PLEOS_CLIENT_ID.isBlank()) {
                    // 등록 정보가 없으면 HYBRID 여도 온디바이스 인식만 동작해요.
                    markReady()
                    return
                }
                hybrid.registerApp(BuildConfig.PLEOS_CLIENT_ID, BuildConfig.PLEOS_CLIENT_SECRET,
                    object : OnServerConnectionListener {
                        override fun onConnected() { Log.d(TAG, "STT server connected"); markReady() }
                        override fun onFailed(errorType: ErrorType) {
                            Log.w(TAG, "STT server registration failed: $errorType — on-device only")
                            markReady()
                        }
                    })
            }
            override fun onFailure(errorType: ErrorType) {
                Log.e(TAG, "STT(HYBRID) init failed: $errorType")
                _events.tryEmit(SpeechEvent.Error("STT 초기화 실패: $errorType"))
            }
        })
        onDevice.initialize(object : OnInitializeListener {
            override fun onSuccess() { Log.d(TAG, "STT(ON_DEVICE) initialized"); onDeviceReady = true; onDevice.addListener(listener) }
            override fun onFailure(errorType: ErrorType) { Log.w(TAG, "on-device STT init failed: $errorType") }
        })
    }

    private fun markReady() {
        _ready.value = true
        if (pendingListen) { pendingListen = false; listen() }
    }

    override fun listen() {
        if (!_ready.value) { pendingListen = true; Log.d(TAG, "listen() queued until STT ready"); return }
        Log.d(TAG, "hybrid.request()")
        runCatching { hybrid.request() }.onFailure {
            Log.e(TAG, "hybrid.request failed", it)
            _events.tryEmit(SpeechEvent.Error("인식 시작 실패: ${it.message}"))
        }
    }
    override fun beginStream() { feedFailed = false; Log.d(TAG, "beginStream onDeviceReady=$onDeviceReady") } // sendAudio 를 처음 호출하면 인식이 시작돼요.
    override fun feed(pcm: ByteArray) {
        if (feedFailed) return
        runCatching { onDevice.sendAudio(pcm) }.onFailure {
            feedFailed = true
            Log.e(TAG, "onDevice.sendAudio failed", it)
            _events.tryEmit(SpeechEvent.Error("온디바이스 인식 실패: ${it.message}"))
        }
    }
    override fun endStream() { Log.d(TAG, "completeAudioSend"); runCatching { onDevice.completeAudioSend() }.onFailure { Log.e(TAG, "completeAudioSend", it) } }
    override fun stop() { runCatching { hybrid.stop() } }

    override fun release() {
        runCatching { hybrid.removeListener(listener); hybrid.release() }
        runCatching { onDevice.removeListener(listener); onDevice.release() }
    }
}

/** 기록 시작·저장 안내. ON_DEVICE 합성은 앱 등록 없이 동작해요. */
class PleosSpeaker(context: Context) : Speaker {
    private val tts = TextToSpeech(context.applicationContext, TtsMode.ON_DEVICE).also { it.initialize() }
    override fun speak(text: String) { runCatching { tts.speak(text) }.onFailure { Log.w(TAG, "tts", it) } }
    override fun stop() { runCatching { tts.stop() } }
    override fun release() { runCatching { tts.release() } }
}

/**
 * 출발 준비 화면의 목적지. 앱은 조회만 하고, 바꾸려면 내비게이션에서 바꿔요.
 * - 연결되기 전에 refresh() 가 오면 실패로 처리하지 않고 다시 연결을 시도해요.
 * - 목적지를 요청했는데 3초 안에 답이 없으면 ‘설정된 목적지 없음’으로 봐요
 *   (목적지가 없을 때 SDK 가 콜백을 주지 않는 경우 대비 — 실제 동작은 로그로 확인).
 */
class PleosNavInfo(context: Context) : NavInfo {
    private val _dest = MutableStateFlow<NavDestination>(NavDestination.Unavailable(NAV_LOADING))
    override val destination: StateFlow<NavDestination> = _dest.asStateFlow()
    @Volatile private var connected = false
    @Volatile private var connecting = false
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val noAnswer = Runnable {
        if ((_dest.value as? NavDestination.Unavailable)?.reason == NAV_LOADING) {
            Log.d(TAG, "NaviHelper: no destination callback in 3s → treat as no destination")
            _dest.value = NavDestination.None
        }
    }

    private val listener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            Log.d(TAG, "NaviHelper onDestinationInfo: $destinationInfo")
            main.removeCallbacks(noAnswer)
            val name = destinationInfo.name
            _dest.value = if (name.isNullOrBlank()) NavDestination.None else NavDestination.Found(name)
        }
    }

    private val navi = NaviHelper(context.applicationContext)

    init { connect() }

    private fun connect() {
        if (connecting || connected) return
        connecting = true
        _dest.value = NavDestination.Unavailable(NAV_LOADING)
        Log.d(TAG, "NaviHelper.initialize()")
        runCatching {
            navi.initialize(
                onConnected = {
                    Log.d(TAG, "NaviHelper connected")
                    connecting = false; connected = true
                    runCatching { navi.addListener(listener) }.onFailure { Log.e(TAG, "NaviHelper.addListener", it) }
                    request()
                },
                onFailed = { t ->
                    Log.w(TAG, "NaviHelper connection failed", t)
                    connecting = false; connected = false
                    _dest.value = NavDestination.Unavailable(t.message ?: "내비게이션 연결 실패")
                },
            )
        }.onFailure {
            Log.e(TAG, "NaviHelper.initialize threw", it)
            connecting = false
            _dest.value = NavDestination.Unavailable(it.message ?: "내비게이션 연결 실패")
        }
    }

    private fun request() {
        _dest.value = NavDestination.Unavailable(NAV_LOADING)
        Log.d(TAG, "NaviHelper.getDestinationInfo()")
        runCatching { navi.getDestinationInfo() }.onFailure {
            Log.e(TAG, "getDestinationInfo", it)
            _dest.value = NavDestination.Unavailable(it.message ?: "목적지 조회 실패")
            return
        }
        main.removeCallbacks(noAnswer)
        main.postDelayed(noAnswer, 3_000)
    }

    override fun refresh() { if (connected) request() else connect() }

    override fun release() {
        main.removeCallbacks(noAnswer)
        runCatching { navi.removeListener(listener); navi.release() }
    }
}
