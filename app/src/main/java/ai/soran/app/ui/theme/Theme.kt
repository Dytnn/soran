package ai.soran.app.ui.theme

import ai.soran.app.R
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * 시안(soran-app-only.html)의 디자인 토큰을 그대로 옮겼어요. (Unified Soft Glass)
 * 단위: 시안의 CSS px = 여기서의 dp/sp (앱 창 984×608 기준)
 */

@Immutable
data class SoranColors(
    val bg: Color, val panel: Color, val panel2: Color, val line: Color,
    val blue: Color, val onBlue: Color, val blueSoft: Color,
    val accent: Color, val accentSoft: Color, val onAccent: Color,
    val ink: Color, val ink2: Color, val sub: Color,
    val danger: Color, val ok: Color, val okBg: Color, val warn: Color, val warnBg: Color,
    val sea: Color, val land: Color, val hwy: Color,
    /** Glass/Primary · Secondary · Strong 배경 */
    val g1: Color, val g2: Color, val g3: Color,
    /** Glass 테두리 */
    val g1Border: Color, val g2Border: Color, val g3Border: Color,
    /** --glass-shadow: inset 하이라이트 · 1px 링 · 0 8px 24px */
    val glassHighlight: Color, val glassRing: Color, val glassDrop: Color,
    /** --shadow-lg: 0 12px 32px */
    val dropLg: Color,
    val scrim: Color,
    val dark: Boolean,
)

val LightSoran = SoranColors(
    bg = Color(0xFFF7F8FA), panel = Color(0xFFFFFFFF), panel2 = Color(0xFFEEF1F5), line = Color(0xFFE9EDF2),
    blue = Color(0xFF285FE8), onBlue = Color(0xFFFFFFFF), blueSoft = Color(0x1A285FE8),
    accent = Color(0xFFFFBA85), accentSoft = Color(0xFFFFF3EA), onAccent = Color(0xFF1E293B),
    ink = Color(0xFF1E293B), ink2 = Color(0xFF334155), sub = Color(0xFF64748B),
    danger = Color(0xFFD92D3A), ok = Color(0xFF15803D), okBg = Color(0xFFE8F5EC), warn = Color(0xFFB45309), warnBg = Color(0xFFFEF3E2),
    sea = Color(0xFFEEF1F5), land = Color(0xFFFFFFFF), hwy = Color(0xFFCBD3DE),
    g1 = Color(0xD1FFFFFF), g2 = Color(0xA6FFFFFF), g3 = Color(0xF0FFFFFF),
    g1Border = Color(0xBFFFFFFF), g2Border = Color(0x99FFFFFF), g3Border = Color(0xCCFFFFFF),
    glassHighlight = Color(0xE6FFFFFF), glassRing = Color(0x0D1E293B), glassDrop = Color(0x121E293B),
    dropLg = Color(0x1F1E293B), scrim = Color(0x6B172033),
    dark = false,
)

val DarkSoran = SoranColors(
    bg = Color(0xFF101722), panel = Color(0xFF1D2939), panel2 = Color(0xFF243247), line = Color(0xFF2F3F55),
    blue = Color(0xFF8AB1FF), onBlue = Color(0xFF0B1B3F), blueSoft = Color(0x298AB1FF),
    accent = Color(0xFFFFBA85), accentSoft = Color(0xFF4A3526), onAccent = Color(0xFF1E293B),
    ink = Color(0xFFF5F8FE), ink2 = Color(0xFFCBD5E1), sub = Color(0xFF94A3B8),
    danger = Color(0xFFFF8A8A), ok = Color(0xFF6EE7A0), okBg = Color(0xFF16382A), warn = Color(0xFFFFC36B), warnBg = Color(0xFF3A2D10),
    sea = Color(0xFF16202E), land = Color(0xFF223248), hwy = Color(0xFF3A4C66),
    g1 = Color(0xD11D2939), g2 = Color(0x9E1D2939), g3 = Color(0xF01D2939),
    g1Border = Color(0x1AFFFFFF), g2Border = Color(0x14FFFFFF), g3Border = Color(0x1FFFFFFF),
    glassHighlight = Color(0x0FFFFFFF), glassRing = Color(0x0AFFFFFF), glassDrop = Color(0x52000000),
    dropLg = Color(0x73000000), scrim = Color(0x6B172033),
    dark = true,
)

/** 지도 시·도 색: color-mix(tint N%, land) — .rg0~rg5 */
val RegionTints = listOf(
    Color(0xFF285FE8) to 0.13f, Color(0xFF14B8A6) to 0.15f, Color(0xFF8B5CF6) to 0.12f,
    Color(0xFF0EA5E9) to 0.15f, Color(0xFF64748B) to 0.14f, Color(0xFF22C55E) to 0.13f,
)

/** 여행 표지 색 (COVER_TONES) */
val CoverTones = listOf(Color(0xFF1E293B), Color(0xFF273449), Color(0xFF2F3A4D), Color(0xFF374151))

/** 프로필 색상 견본 */
val Swatches = listOf(0xFF8DB4FF, 0xFFFFD27A, 0xFF7FE0BC, 0xFFFFA9C6, 0xFFC4B0FF, 0xFFFFA58A)

val LocalSoran = staticCompositionLocalOf { LightSoran }

/** --font: Pretendard (앱에 포함, SIL OFL 1.1) */
val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

/** CSS 와 같은 줄 높이 배치 (글자를 줄 가운데에, 위·아래 잘림 없음) */
private val CssLine = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** 시안의 font-size / font-weight / line-height / letter-spacing 그대로 */
fun ts(size: Int, weight: Int = 400, lh: Float = size * 1.5f, ls: TextUnit = TextUnit.Unspecified): TextStyle =
    TextStyle(
        fontFamily = Pretendard, fontSize = size.sp, fontWeight = FontWeight(weight),
        lineHeight = lh.sp, letterSpacing = ls,
        lineHeightStyle = CssLine, platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

/** 타이포 스케일 (.t-display … .t-cap) */
object T {
    val display = ts(36, 600, 44f, (-0.025).em)
    val h1 = ts(30, 600, 38f, (-0.02).em)
    val h2 = ts(26, 600, 34f, (-0.02).em)
    val title = ts(22, 600, 30f)
    val bodyL = ts(20, 500, 30f)
    val body = ts(18, 400, 28f)
    val label = ts(16, 500, 24f)
    val cap = ts(14, 400, 20f)
}

@Composable
fun SoranTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val c = if (dark) DarkSoran else LightSoran
    val scheme = if (dark) darkColorScheme(primary = c.blue, background = c.bg, surface = c.panel, onSurface = c.ink)
    else lightColorScheme(primary = c.blue, background = c.bg, surface = c.panel, onSurface = c.ink)
    CompositionLocalProvider(LocalSoran provides c) {
        MaterialTheme(colorScheme = scheme) {
            androidx.compose.material3.ProvideTextStyle(ts(18, 400, 27f).copy(color = c.ink), content)
        }
    }
}
