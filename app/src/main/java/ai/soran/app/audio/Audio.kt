package ai.soran.app.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

/** 16kHz · 16bit · mono — Pleos STT sendAudio() 문서 예시와 같은 포맷 (100ms = 3,200 bytes). */
object PcmFormat {
    const val SAMPLE_RATE = 16_000
    const val CHUNK_BYTES = 3_200
}

/**
 * 앱 마이크 녹음. 녹음한 PCM 을 WAV 파일로 저장하면서 같은 데이터를 [onChunk] 로 넘겨
 * STT(ON_DEVICE, sendAudio)에 실시간으로 흘려보내요.
 */
class PcmRecorder {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var record: AudioRecord? = null

    @SuppressLint("MissingPermission") // RECORD_AUDIO 는 MainActivity 에서 미리 받아요.
    fun start(out: File, onChunk: (ByteArray) -> Unit): Boolean {
        stop()
        val minBuf = AudioRecord.getMinBufferSize(
            PcmFormat.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val r = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION, PcmFormat.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, PcmFormat.CHUNK_BYTES * 4)
            )
        }.getOrNull() ?: return false
        if (r.state != AudioRecord.STATE_INITIALIZED) { r.release(); return false }
        record = r
        out.parentFile?.mkdirs()
        val wav = WavWriter(out)
        r.startRecording()
        job = scope.launch {
            val buf = ByteArray(PcmFormat.CHUNK_BYTES)
            try {
                while (isActive) {
                    val n = r.read(buf, 0, buf.size)
                    if (n > 0) {
                        val chunk = buf.copyOf(n)
                        wav.write(chunk)
                        onChunk(chunk)
                    }
                }
            } finally {
                wav.close()
            }
        }
        return true
    }

    fun stop() {
        job?.cancel(); job = null
        record?.let { runCatching { it.stop() }; it.release() }
        record = null
    }
}

/** 최소한의 WAV(RIFF) 기록기. 닫을 때 헤더의 길이를 채워요. */
class WavWriter(file: File) {
    private val raf = RandomAccessFile(file, "rw").apply { setLength(0); write(ByteArray(44)) }
    private var dataLen = 0L

    @Synchronized fun write(b: ByteArray) { raf.write(b); dataLen += b.size }

    @Synchronized fun close() {
        val sr = PcmFormat.SAMPLE_RATE; val ch = 1; val bits = 16
        val byteRate = sr * ch * bits / 8
        raf.seek(0)
        raf.writeBytes("RIFF"); raf.writeIntLE((36 + dataLen).toInt()); raf.writeBytes("WAVE")
        raf.writeBytes("fmt "); raf.writeIntLE(16); raf.writeShortLE(1); raf.writeShortLE(ch)
        raf.writeIntLE(sr); raf.writeIntLE(byteRate); raf.writeShortLE(ch * bits / 8); raf.writeShortLE(bits)
        raf.writeBytes("data"); raf.writeIntLE(dataLen.toInt())
        raf.close()
    }

    private fun RandomAccessFile.writeIntLE(v: Int) {
        write(v and 0xff); write(v shr 8 and 0xff); write(v shr 16 and 0xff); write(v shr 24 and 0xff)
    }
    private fun RandomAccessFile.writeShortLE(v: Int) { write(v and 0xff); write(v shr 8 and 0xff) }
}

/**
 * 정차 중 원본 음성 다시 듣기.
 * AAOS 오디오 포커스 정책을 따라요: 재생할 때 GAIN_TRANSIENT 를 요청하고,
 * 내비 안내·통화 등으로 포커스를 잃으면 즉시 멈춰 차량 미디어·안내음과 겹치지 않아요.
 */
class AudioPlayer(context: Context) {
    data class Progress(val momentId: Long? = null, val playing: Boolean = false, val positionMs: Int = 0, val durationMs: Int = 0)

    private val am = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { change -> if (change < 0) pause() }
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val _progress = MutableStateFlow(Progress())
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    fun toggle(momentId: Long, path: String) {
        val p = _progress.value
        if (p.momentId == momentId && player != null) { if (p.playing) pause() else resume(); return }
        stop()
        val f = File(path); if (!f.exists()) return
        player = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setDataSource(f.absolutePath)
            setOnCompletionListener { stop(keepId = true) }
            prepare()
        }
        _progress.value = Progress(momentId, false, 0, player!!.duration)
        resume()
    }

    private fun resume() {
        val mp = player ?: return
        if (am.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        mp.start()
        _progress.value = _progress.value.copy(playing = true)
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                _progress.value = _progress.value.copy(positionMs = runCatching { mp.currentPosition }.getOrDefault(0))
                delay(200)
            }
        }
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        ticker?.cancel()
        am.abandonAudioFocusRequest(focus)
        _progress.value = _progress.value.copy(playing = false)
    }

    fun stop(keepId: Boolean = false) {
        ticker?.cancel()
        player?.release(); player = null
        am.abandonAudioFocusRequest(focus)
        val id = _progress.value.momentId
        _progress.value = if (keepId) Progress(id, false, 0, _progress.value.durationMs) else Progress()
    }
}
