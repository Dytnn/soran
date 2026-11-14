package ai.soran.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * 시안의 아이콘 세트(ICON)를 그대로 옮겼어요.
 * 24×24 viewBox, stroke 2, round cap/join, currentColor (Icon 의 tint 로 색을 바꿔요).
 */
object Ico {
    private class P(val d: String, val fill: Boolean = false)

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0z"

    private fun rect(x: Float, y: Float, w: Float, h: Float, rx: Float) =
        "M${x + rx},${y}h${w - 2 * rx}a$rx,$rx 0 0 1 $rx,$rx" +
            "v${h - 2 * rx}a$rx,$rx 0 0 1 -$rx,$rx" +
            "h${-(w - 2 * rx)}a$rx,$rx 0 0 1 -$rx,-$rx" +
            "v${-(h - 2 * rx)}a$rx,$rx 0 0 1 $rx,-${rx}z"

    private fun icon(name: String, vararg parts: P): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            parts.forEach { p ->
                if (p.fill) addPath(addPathNodes(p.d), fill = SolidColor(Color.Black))
                else addPath(
                    addPathNodes(p.d), stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val home = icon("home", P("M3 11l9-8 9 8v9a1 1 0 0 1-1 1h-5v-6H9v6H4a1 1 0 0 1-1-1z"))
    val map = icon("map", P("M9 4L3 6v14l6-2 6 2 6-2V4l-6 2z"), P("M9 4v14M15 6v14"))
    val col = icon("col", P("M4 5a2 2 0 0 1 2-2h6v16H6a2 2 0 0 0-2 2z"), P("M20 5a2 2 0 0 0-2-2h-6v16h6a2 2 0 0 1 2 2z"))
    val user = icon("user", P(circle(12f, 8f, 4f)), P("M4 21c0-4 4-6 8-6s8 2 8 6"))
    val mic = icon("mic", P(rect(9f, 3f, 6f, 12f, 3f)), P("M5 11a7 7 0 0 0 14 0M12 18v3"))
    val stop = icon("stop", P(rect(6f, 6f, 12f, 12f, 2f), fill = true))
    val play = icon("play", P("M7 4l13 8-13 8z", fill = true))
    val pause = icon("pause", P("M7 4h4v16H7zM13 4h4v16h-4z", fill = true))
    val plus = icon("plus", P("M12 5v14M5 12h14"))
    val check = icon("check", P("M5 12l5 5 9-10"))
    val lock = icon("lock", P(rect(5f, 11f, 14f, 10f, 2f)), P("M8 11V8a4 4 0 0 1 8 0v3"))
    val search = icon("search", P(circle(11f, 11f, 7f)), P("M20 20l-4-4"))
    val close = icon("close", P("M6 6l12 12M18 6L6 18"))
    val chevL = icon("chevL", P("M15 5l-7 7 7 7"))
    val chevR = icon("chevR", P("M9 5l7 7-7 7"))
    /** chevR 를 90° 돌린 모양 (필터 칩의 펼침 표시) */
    val chevD = icon("chevD", P("M5 9l7 7 7-7"))
    val pin = icon("pin", P("M12 21s7-6.5 7-12a7 7 0 0 0-14 0c0 5.5 7 12 7 12z"), P(circle(12f, 9f, 2.5f)))
    val pinOff = icon("pinoff", P("M12 21s7-6.5 7-12a7 7 0 0 0-14 0c0 5.5 7 12 7 12z"), P("M4 4l16 16"))
    val sun = icon("sun", P(circle(12f, 12f, 4f)), P("M12 2v2M12 20v2M2 12h2M20 12h2M5 5l1.5 1.5M17.5 17.5L19 19M5 19l1.5-1.5M17.5 6.5L19 5"))
    val car = icon("car", P("M5 16l1.5-5.5A2 2 0 0 1 8.4 9h7.2a2 2 0 0 1 1.9 1.5L19 16M4 16h16v3H4zM7 19v1.5M17 19v1.5"))
    val clock = icon("clock", P(circle(12f, 12f, 9f)), P("M12 7v5l3 2"))
    val edit = icon("edit", P("M4 20h4L19 9l-4-4L4 16z"))
    val trash = icon("trash", P("M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13"))
    val share = icon("share", P("M12 3v12M7 8l5-5 5 5M5 14v5a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-5"))
}
