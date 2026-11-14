package ai.soran.app.ui.components

import ai.soran.app.core.model.Person
import ai.soran.app.ui.theme.Ico
import ai.soran.app.ui.theme.LocalSoran
import ai.soran.app.ui.theme.SoranColors
import ai.soran.app.ui.theme.T
import ai.soran.app.ui.theme.ts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

val R10 = RoundedCornerShape(10.dp)
val R12 = RoundedCornerShape(12.dp)
val R16 = RoundedCornerShape(16.dp)
val Pill = RoundedCornerShape(999.dp)

// ───────────────────────── 그림자 (CSS box-shadow 재현) ─────────────────────────

/**
 * CSS `0 {dy} {blur} {color}` 그림자 + (선택) 1px 링. 네이티브 setShadowLayer 로 그려서
 * 시안의 부드러운 그림자와 같은 모양이 나와요. [radius] 는 모서리 둥글기.
 */
fun Modifier.cssShadow(radius: Dp, color: Color, blur: Dp, dy: Dp, ring: Color? = null): Modifier = drawBehind {
    val r = radius.toPx().coerceAtMost(size.minDimension / 2)
    drawIntoCanvas { canvas ->
        val p = Paint().asFrameworkPaint().apply {
            isAntiAlias = true
            this.color = android.graphics.Color.TRANSPARENT
            // CSS blur B → σ = B/2,  Skia: σ = 0.57735·R + 0.5  →  R = (σ − 0.5) / 0.57735
            val sigma = blur.toPx() / 2f
            setShadowLayer(((sigma - 0.5f) / 0.57735f).coerceAtLeast(1f), 0f, dy.toPx(), color.toArgb())
        }
        canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, r, r, p)
        if (ring != null) {
            val s = Paint().asFrameworkPaint().apply {
                isAntiAlias = true; style = android.graphics.Paint.Style.STROKE; strokeWidth = 1.dp.toPx(); this.color = ring.toArgb()
            }
            val h = 0.5.dp.toPx()
            canvas.nativeCanvas.drawRoundRect(-h, -h, size.width + h, size.height + h, r + h, r + h, s)
        }
    }
}

/** --glass-shadow */
fun Modifier.glassShadow(radius: Dp, c: SoranColors) = cssShadow(radius, c.glassDrop, 24.dp, 8.dp, c.glassRing)

/** --shadow-lg */
fun Modifier.shadowLg(radius: Dp, c: SoranColors) = cssShadow(radius, c.dropLg, 32.dp, 12.dp)

enum class GlassLevel { PRIMARY, SECONDARY, STRONG }

/** Glass/Primary · Secondary · Strong 표면 (배경 + 흰 테두리 + glass-shadow) */
fun Modifier.glass(c: SoranColors, level: GlassLevel = GlassLevel.PRIMARY, radius: Dp = 16.dp, shadow: Boolean = true): Modifier {
    val shape = RoundedCornerShape(radius)
    val (bg, bd) = when (level) {
        GlassLevel.PRIMARY -> c.g1 to c.g1Border
        GlassLevel.SECONDARY -> c.g2 to c.g2Border
        GlassLevel.STRONG -> c.g3 to c.g3Border
    }
    return (if (shadow) this.glassShadow(radius, c) else this).clip(shape).background(bg).border(1.dp, bd, shape)
}

@Composable
fun Glass(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.PRIMARY,
    radius: Dp = 16.dp,
    padding: PaddingValues = PaddingValues(20.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.glass(LocalSoran.current, level, radius).padding(padding), verticalArrangement = verticalArrangement, content = content)
}

// ───────────────────────── 텍스트·아이콘 ─────────────────────────

@Composable
fun Txt(text: String, style: TextStyle, color: Color = LocalSoran.current.ink, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) =
    Text(text, modifier, color = color, style = style, maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) androidx.compose.ui.text.style.TextOverflow.Clip else androidx.compose.ui.text.style.TextOverflow.Ellipsis)

@Composable
fun Ic(icon: ImageVector, size: Dp = 24.dp, tint: Color = LocalSoran.current.ink, desc: String? = null, modifier: Modifier = Modifier) =
    Icon(icon, desc, modifier.size(size), tint = tint)

@Composable fun Gap(w: Dp) = Spacer(Modifier.size(w))
@Composable fun RowScope.Grow() = Spacer(Modifier.weight(1f))
@Composable fun ColumnScope.Grow() = Spacer(Modifier.weight(1f))

/** 눌림 효과 없는 클릭 (시안의 button 과 같은 느낌) */
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(enabled = enabled, interactionSource = MutableInteractionSource(), indication = null, onClick = onClick)
)

// ───────────────────────── 버튼 ─────────────────────────

enum class BtnKind { PRIMARY, LINE, DANGER, ON_BLUE }

/** .btn.xl — 높이 60, 글자 20/600, 아이콘 24 */
@Composable
fun BtnXL(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: BtnKind = BtnKind.PRIMARY,
    icon: ImageVector? = null, enabled: Boolean = true, height: Dp = 60.dp,
) = Btn(text, onClick, modifier, kind, icon, enabled, height, if (kind == BtnKind.LINE) 28.dp else 32.dp, ts(20, 600, 28f), 24.dp, 10.dp)

/** .sm — 높이 44, 글자 16/600, 아이콘 16 */
@Composable
fun BtnSm(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: BtnKind = BtnKind.LINE,
    icon: ImageVector? = null, enabled: Boolean = true, height: Dp = 44.dp,
) = Btn(text, onClick, modifier, kind, icon, enabled, height, 18.dp, ts(16, 600, 24f), 16.dp, 8.dp)

@Composable
fun Btn(
    text: String, onClick: () -> Unit, modifier: Modifier, kind: BtnKind, icon: ImageVector?, enabled: Boolean,
    height: Dp, hPad: Dp, style: TextStyle, iconSize: Dp, gap: Dp,
) {
    val c = LocalSoran.current
    val (bg, fg) = when {
        !enabled -> c.panel2 to c.sub
        kind == BtnKind.PRIMARY -> c.blue to c.onBlue
        kind == BtnKind.DANGER -> c.danger to Color.White
        kind == BtnKind.ON_BLUE -> Color.White to c.blue
        else -> c.g3 to c.ink
    }
    val base = when {
        !enabled -> Modifier.clip(R12).background(bg)
        kind == BtnKind.LINE -> Modifier.glass(c, GlassLevel.STRONG, 12.dp)
        else -> Modifier.clip(R12).background(bg)
    }
    Row(
        modifier.height(height).then(base).tap(enabled, onClick).padding(horizontal = hPad),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Ic(icon, iconSize, fg); Gap(gap) }
        Txt(text, style, fg, maxLines = 1)
    }
}

/** .sm.dng — 빨간 테두리 버튼 */
@Composable
fun BtnDanger(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 44.dp) {
    val c = LocalSoran.current
    Row(
        modifier.height(height).clip(R12).background(c.g3).border(1.5.dp, c.danger, R12).tap(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Ic(icon, 16.dp, c.danger); Gap(8.dp) }
        Txt(text, ts(16, 600, 24f), c.danger, maxLines = 1)
    }
}

/** 48×48 Glass/Strong 아이콘 버튼 (.dt-btn, .tv-back, .mc) */
@Composable
fun IconBtn(icon: ImageVector, desc: String, onClick: () -> Unit, enabled: Boolean = true, size: Dp = 48.dp, iconSize: Dp = 22.dp) {
    val c = LocalSoran.current
    Box(Modifier.size(size).glass(c, GlassLevel.STRONG, 12.dp).tap(enabled, onClick), contentAlignment = Alignment.Center) {
        Ic(icon, iconSize, if (enabled) c.ink else c.sub.copy(alpha = .4f), desc)
    }
}

/** .lnk */
@Composable
fun Lnk(text: String, onClick: () -> Unit, color: Color = LocalSoran.current.blue, style: TextStyle = ts(16, 600, 24f)) =
    Box(Modifier.height(36.dp).tap(onClick = onClick), contentAlignment = Alignment.Center) { Txt(text, style, color) }

// ───────────────────────── 칩 ─────────────────────────

/** .chp — 36 높이 알약. 선택 시 blue-soft + 1.5px 파란 테두리 */
@Composable
fun Chp(text: String, selected: Boolean = false, onClick: () -> Unit, modifier: Modifier = Modifier, height: Dp = 36.dp) {
    val c = LocalSoran.current
    Box(
        modifier.height(height).clip(Pill)
            .background(if (selected) c.blueSoft else c.panel)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) c.blue else c.line, Pill)
            .tap(onClick = onClick).padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, ts(16, 600, 24f), if (selected) c.blue else c.ink2, maxLines = 1) }
}

/** .chp.tg — 해시태그 칩 (선택: 진한 배경 + 2px 파란 링) */
@Composable
fun TagChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalSoran.current
    val m = if (selected) Modifier.clip(Pill).background(c.ink).border(2.dp, c.blue, Pill)
    else Modifier.glass(c, GlassLevel.STRONG, 999.dp)
    Box(Modifier.height(40.dp).then(m).tap(onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Txt(text, ts(16, 600, 24f), if (selected) c.bg else c.ink2, maxLines = 1)
    }
}

/** .chp.fil — 필터 칩 (아이콘 + 글자 + 펼침 표시) */
@Composable
fun FilterChip(text: String, on: Boolean, onClick: () -> Unit, leading: @Composable () -> Unit) {
    val c = LocalSoran.current
    val m = if (on) Modifier.clip(Pill).background(c.blueSoft).border(1.dp, c.blue, Pill) else Modifier.glass(c, GlassLevel.STRONG, 999.dp)
    Row(Modifier.height(48.dp).then(m).tap(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        leading()
        Txt(text, ts(16, 600, 24f), if (on) c.blue else c.ink2, maxLines = 1)
        Ic(Ico.chevD, 14.dp, c.sub)
    }
}

/** .chip.live / .chip.warn — 상단 바 상태 칩 */
@Composable
fun TopChip(text: String, bg: Color, fg: Color, lead: @Composable () -> Unit) {
    Row(Modifier.height(34.dp).clip(Pill).background(bg).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        lead(); Txt(text, ts(16, 600, 24f), fg, maxLines = 1)
    }
}

/** .stt — 주행 중 오른쪽 위 상태 (44 높이, 18/600) */
@Composable
fun SttPill(text: String, dot: Color, bg: Color, fg: Color) {
    Row(Modifier.height(44.dp).clip(Pill).background(bg).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        Txt(text, ts(18, 600, 24f), fg, maxLines = 1)
    }
}

/** .seg — 두 칸 전환 */
@Composable
fun Seg(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, itemHeight: Dp = 40.dp, fontSize: Int = 15, fill: Boolean = false) {
    val c = LocalSoran.current
    Row(modifier.glass(c, GlassLevel.STRONG, 12.dp).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                (if (fill) Modifier.weight(1f) else Modifier).height(itemHeight).clip(R12).background(if (on) c.panel else Color.Transparent)
                    .tap { onSelect(i) }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) { Txt(o, ts(fontSize, 600, fontSize * 1.5f), if (on) c.ink else c.sub, maxLines = 1) }
        }
    }
}

/** .sw — 60×34 스위치 */
@Composable
fun Sw(on: Boolean, onToggle: () -> Unit, enabled: Boolean = true) {
    val c = LocalSoran.current
    val x by animateDpAsState(if (on) 30.dp else 4.dp, label = "sw")
    Box(
        Modifier.size(60.dp, 34.dp).clip(RoundedCornerShape(17.dp))
            .background(if (on) c.blue else c.panel2)
            .then(if (on) Modifier else Modifier.border(1.5.dp, c.line, RoundedCornerShape(17.dp)))
            .tap(enabled, onToggle),
    ) {
        Box(Modifier.offset(x = x, y = 4.dp).size(26.dp).shadow(2.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

// ───────────────────────── 아바타 ─────────────────────────

/** .av — 지름 s, 글자 0.42s/600, 흰 2px 테두리 */
@Composable
fun Avatar(p: Person?, size: Dp = 36.dp, ring: Color = Color.White, ringWidth: Dp = 2.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(Color(p?.colorArgb ?: 0xFFCBD3DE)).border(ringWidth, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Txt(p?.name?.take(1) ?: "?", ts(0, 600, 0f).copy(fontSize = (size.value * 0.42f).sp, lineHeight = (size.value * 0.63f).sp), Color(0xFF172033))
    }
}

/** .avs — 10px 씩 겹친 아바타 */
@Composable
fun AvatarStack(people: List<Person>, size: Dp = 32.dp, ring: Color = Color.White, ringWidth: Dp = 2.dp) {
    Row {
        people.forEachIndexed { i, p ->
            Box(Modifier.offset(x = (-10 * i).dp).zIndex((people.size - i).toFloat())) { Avatar(p, size, ring, ringWidth) }
        }
    }
}

// ───────────────────────── 기타 ─────────────────────────

/** .tile — 사진이 없을 때의 시각 타일 (72×52) */
@Composable
fun TimeTile(time: String, tod: String) {
    val c = LocalSoran.current
    Column(Modifier.size(72.dp, 52.dp).clip(R12).background(c.panel2).padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
        Txt(time, ts(19, 600, 26f, (-0.02).sp * 19), c.ink, maxLines = 1)
        Txt(tod, ts(14, 400, 20f), c.sub, maxLines = 1)
    }
}

/** 구분선 */
@Composable
fun HLine(modifier: Modifier = Modifier) = Box(modifier.height(1.dp).background(LocalSoran.current.line))

@Composable
fun VLine(modifier: Modifier = Modifier) = Box(modifier.width(1.dp).background(LocalSoran.current.line))

/** 시각 → 하루 중 때 (tod) */
fun todOf(hour: Int) = when { hour < 6 -> "새벽"; hour < 11 -> "아침"; hour < 17 -> "낮"; hour < 21 -> "저녁"; else -> "밤" }

@Composable
fun rememberTap() = remember { MutableInteractionSource() }

