package ai.soran.app.ui

import ai.soran.app.ui.theme.LocalSoran
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.tappableElement
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import android.view.WindowManager
import kotlin.math.min

/** 시안(soran-app-only.html)의 앱 창 크기 — 이 크기를 기준으로 화면 전체를 비율대로 키워요. */
const val DESIGN_W = 984f
const val DESIGN_H = 608f

/**
 * Pleos 차량 화면의 위쪽 상태 바·아래쪽 공조 바 높이 (화면 전체 높이 대비 비율).
 * Pleos Connect Emulator 1638×920 캡처 기준: 위 58px, 아래 90px. 실제 차량에서 다르면 이 두 값만 고치면 돼요.
 * 시스템이 바 높이(insets)를 알려주지 않을 때, 앱 창이 실제로 바와 겹치는 만큼만 비워요.
 * (전체 화면이면 겹친 만큼 비우고, 분할 화면처럼 창이 바 사이에 있으면 비우지 않아요)
 */
private const val FALLBACK_TOP = 58f / 920f
private const val FALLBACK_BOTTOM = 90f / 920f

/**
 * 1) 차량 상태 바·공조 바에 가리지 않게 그만큼 비워 두고
 * 2) 남은 영역에 시안의 984×608 화면이 들어가도록 밀도(density)를 바꿔요.
 *    → 높이가 시안의 608 과 같아지게 확대하고(가로가 더 길면 남는 폭은 레이아웃이 늘어나 채워요),
 *      글자·여백·아이콘이 시안과 같은 비율로 보여요.
 */
@Composable
fun DesignFrame(content: @Composable () -> Unit) {
    val c = LocalSoran.current
    val sys = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout).union(WindowInsets.tappableElement)
        .union(WindowInsets.mandatorySystemGestures)
    val topPx = insets.getTop(sys)
    val bottomPx = insets.getBottom(sys)
    // 앱 창이 화면 어디에 놓였는지 (전체 화면 / 분할 화면) — 바와 겹치는 만큼만 비우려고 써요
    val ctx = LocalContext.current
    val (win, screenH) = remember(ctx, LocalConfiguration.current) {
        val wm = ctx.getSystemService(WindowManager::class.java)
        wm.currentWindowMetrics.bounds to wm.maximumWindowMetrics.bounds.height()
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val totalH = constraints.maxHeight.toFloat()
        val noInsets = topPx == 0 && bottomPx == 0
        // 창이 위쪽 바 아래에서 시작하거나(분할 화면) 아래쪽 바 위에서 끝나면 겹치지 않으니 비우지 않아요
        val overlapTop = (screenH * FALLBACK_TOP - win.top).coerceAtLeast(0f)
        val overlapBottom = (win.bottom - screenH * (1 - FALLBACK_BOTTOM)).coerceAtLeast(0f)
        val top = if (noInsets) overlapTop else topPx.toFloat()
        val bottom = if (noInsets) overlapBottom else bottomPx.toFloat()
        val left = insets.getLeft(sys, dir).toFloat()
        val right = insets.getRight(sys, dir).toFloat()

        val wPx = constraints.maxWidth - left - right
        val hPx = totalH - top - bottom
        val scale = min(wPx / DESIGN_W, hPx / DESIGN_H).coerceAtLeast(0.5f)

        Box(
            Modifier.fillMaxSize().padding(
                start = with(sys) { left.toDp() }, top = with(sys) { top.toDp() },
                end = with(sys) { right.toDp() }, bottom = with(sys) { bottom.toDp() },
            )
        ) {
            CompositionLocalProvider(LocalDensity provides Density(scale, fontScale = 1f)) { content() }
        }
    }
}

