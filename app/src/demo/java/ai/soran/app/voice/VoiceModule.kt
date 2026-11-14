package ai.soran.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/*
 * demo 플레이버: Pleos SDK 없이 일반 AAOS 에뮬레이터에서 흐름을 확인하기 위한 구현.
 *  - 음성 인식: 실제 인식 대신 HTML 시안의 예시 대화를 단어 단위로 흘려보내요 (시연용).
 *  - 음성 안내: Android 표준 TextToSpeech 로 실제로 읽어줘요.
 *  - 목적지: 데모 컨트롤에서 있음/없음/연결 실패를 바꿀 수 있어요.
 */
object VoiceModule {
    val permissions = emptyList<String>()

    fun create(context: Context): VoiceServices {
        val speech = DemoSpeechEngine()
        val nav = DemoNavInfo()
        val hooks = object : DemoHooks {
            override fun simulateTrigger() { speech.nextIsTrigger = true; speech.listen() }
            override fun setDestination(d: NavDestination) = nav.set(d)
        }
        return VoiceServices(speech, AndroidSpeaker(context), nav, hooks)
    }
}

class DemoSpeechEngine : SpeechEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _events = MutableSharedFlow<SpeechEvent>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()
    override val ready: StateFlow<Boolean> = MutableStateFlow(true)
    override val supportsStreaming = true

    private var job: Job? = null
    private var turn = 0

    /** '기록해줘' 시연 버튼이 누르면 다음 listen() 결과가 트리거 문장이 돼요. */
    @Volatile var nextIsTrigger = false

    private val pool = listOf(
        listOf("저 단풍 봐. 우리 처음 여행 갔을 때도 이 길 지나갔잖아.", "맞아, 그때 길 잘못 들어서 두 시간 헤맸지."),
        listOf("나는 닭갈비. 춘천 왔으면 먹어야지.", "난 막국수가 땡기는데.", "그럼 둘 다 시키자."),
        listOf("바다다! 창문 열어도 돼?", "추우니까 딱 10초만.", "와, 냄새 진짜 바다야."),
        listOf("올해 안에 겨울 바다 라면 해보자.", "좋아. 버너는 내가 챙길게."),
    )

    override fun listen() {
        job?.cancel()
        job = scope.launch {
            if (nextIsTrigger) {
                nextIsTrigger = false
                delay(600); _events.emit(SpeechEvent.Final("지금 대화 기록해줘"))
            } else delay(4000) // 대기 중에는 아무 말도 없다고 가정
            _events.emit(SpeechEvent.Ended)
        }
    }

    override fun beginStream() {
        job?.cancel()
        val lines = pool[turn++ % pool.size]
        job = scope.launch {
            delay(500)
            for (line in lines) {
                val words = line.split(" ")
                for (i in words.indices) {
                    _events.emit(SpeechEvent.Level(0.3 + (i % 3) * 0.2))
                    _events.emit(SpeechEvent.Partial(words.take(i + 1).joinToString(" ")))
                    delay(320)
                }
                _events.emit(SpeechEvent.Final(line))
                delay(500)
            }
        }
    }

    override fun feed(pcm: ByteArray) = Unit
    override fun endStream() { scope.launch { delay(300); _events.emit(SpeechEvent.Ended) } }
    override fun stop() { job?.cancel(); _events.tryEmit(SpeechEvent.Ended) }
    override fun release() { job?.cancel() }
}

class AndroidSpeaker(context: Context) : Speaker {
    private var ok = false
    private val tts = TextToSpeech(context.applicationContext) { status ->
        ok = status == TextToSpeech.SUCCESS
    }.also { it.language = Locale.KOREAN }
    override fun speak(text: String) { if (ok) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "soran") }
    override fun stop() { tts.stop() }
    override fun release() { tts.shutdown() }
}

class DemoNavInfo : NavInfo {
    private val _dest = MutableStateFlow<NavDestination>(NavDestination.Found("강릉 안목해변"))
    override val destination: StateFlow<NavDestination> = _dest.asStateFlow()
    override fun refresh() = Unit
    override fun release() = Unit
    fun set(d: NavDestination) { _dest.value = d }
}
