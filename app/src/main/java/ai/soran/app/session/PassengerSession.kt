package ai.soran.app.session

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.security.SecureRandom

/**
 * 동승자 QR 참여.
 *
 * 차량 화면에 참여 QR 을 띄우고, 동승자가 휴대폰으로 스캔해 세션에 들어와 녹음 안내를 확인·동의해요.
 * 휴대폰 쪽 동반 앱과 세션 중계 서버는 이 저장소 범위 밖이에요 → [JoinGateway] 를 구현해 연결하세요.
 * 연결 전에는 '직접 추가' + 차량 화면에서 안내 확인·동의 완료를 누르는 방식으로 동작해요.
 */
object JoinCode {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // 헷갈리는 0/O, 1/I 제외
    private val rnd = SecureRandom()

    fun newCode(len: Int = 6) = (1..len).map { ALPHABET[rnd.nextInt(ALPHABET.length)] }.joinToString("")

    /** 동반 앱이 여는 링크. 실제 도메인·스킴은 서비스 설계에 맞게 바꾸세요. */
    fun joinUri(code: String) = "soran://join?code=$code"

    fun qrBitmap(content: String, sizePx: Int = 512): ImageBitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2)
        val m = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val px = IntArray(sizePx * sizePx) { i -> if (m.get(i % sizePx, i / sizePx)) 0xFF1E293B.toInt() else 0xFFFFFFFF.toInt() }
        return Bitmap.createBitmap(px, sizePx, sizePx, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

/** 동반 앱/서버와의 연결점. 세션에 누가 들어왔고 동의했는지를 알려줘요. */
interface JoinGateway {
    fun open(code: String, onJoined: (personId: String, consented: Boolean) -> Unit)
    fun close()
}

/** 서버 없는 기본 구현 — 아무 이벤트도 보내지 않아요 (데모 컨트롤의 'QR 스캔 시연'으로 흉내 낼 수 있어요). */
class LocalJoinGateway : JoinGateway {
    override fun open(code: String, onJoined: (String, Boolean) -> Unit) = Unit
    override fun close() = Unit
}
