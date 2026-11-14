package ai.soran.app.voice

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** 음성 인식 이벤트 (Pleos SpeechToText ResultListener 를 앱 쪽 형태로 옮긴 것). */
sealed interface SpeechEvent {
    data class Partial(val text: String) : SpeechEvent
    data class Final(val text: String) : SpeechEvent
    data class Level(val rms: Double) : SpeechEvent
    data object Ended : SpeechEvent
    data class Error(val message: String) : SpeechEvent
}

/**
 * 음성 인식 엔진.
 * - [listen]: SDK 가 차량 마이크를 직접 사용 (HYBRID — 통신 음영 지역에서 자동으로 온디바이스 전환).
 *   '기록해줘' 대기와, 원본 음성을 저장하지 않는 기록에 사용해요.
 * - [beginStream]/[feed]/[endStream]: 앱이 녹음한 PCM(16kHz·16bit·mono)을 인식 (ON_DEVICE).
 *   원본 음성 파일을 함께 저장해 정차 후 다시 들을 수 있게 할 때 사용해요.
 */
interface SpeechEngine {
    val events: SharedFlow<SpeechEvent>
    val ready: StateFlow<Boolean>
    val supportsStreaming: Boolean
    fun listen()
    fun beginStream()
    fun feed(pcm: ByteArray)
    fun endStream()
    fun stop()
    fun release()
}

/** 음성 안내 (Pleos TextToSpeech). */
interface Speaker {
    fun speak(text: String)
    fun stop()
    fun release()
}

/** NavDestination.Unavailable 의 reason 이 이 값이면 ‘불러오는 중’이에요 */
const val NAV_LOADING = "연결 중"

sealed interface NavDestination {
    data class Found(val name: String) : NavDestination
    data object None : NavDestination
    data class Unavailable(val reason: String) : NavDestination
}

/** 내비게이션 목적지 조회 (Pleos NaviHelper). 목적지는 내비에서만 바꿔요. */
interface NavInfo {
    val destination: StateFlow<NavDestination>
    fun refresh()
    fun release()
}

/** demo 플레이버에서만 제공되는 시연 조작. pleos 플레이버에서는 null. */
interface DemoHooks {
    /** 다음 대기 인식 결과로 '지금 대화 기록해줘'를 들려줘요. */
    fun simulateTrigger()
    fun setDestination(d: NavDestination)
}

class VoiceServices(
    val speech: SpeechEngine,
    val speaker: Speaker,
    val nav: NavInfo,
    val demo: DemoHooks? = null,
) {
    fun release() { speech.release(); speaker.release(); nav.release() }
}
