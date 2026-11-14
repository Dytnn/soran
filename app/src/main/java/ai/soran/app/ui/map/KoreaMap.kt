package ai.soran.app.ui.map

import ai.soran.app.core.geo.Clusterer
import ai.soran.app.core.geo.Province
import ai.soran.app.core.geo.ViewBox
import ai.soran.app.core.geo.GeoProjection
import ai.soran.app.core.model.GeoPoint
import ai.soran.app.ui.components.GlassLevel
import ai.soran.app.ui.components.Pill
import ai.soran.app.ui.components.Txt
import ai.soran.app.ui.components.glass
import ai.soran.app.ui.components.shadowLg
import ai.soran.app.ui.components.tap
import ai.soran.app.ui.theme.LocalSoran
import ai.soran.app.ui.theme.RegionTints
import ai.soran.app.ui.theme.ts
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** 지도 위 핀. x,y 는 GeoProjection 평면 좌표. */
data class MapPin(val id: Long, val x: Double, val y: Double, val label: String, val number: Int? = null)

/**
 * 대한민국 간략 지도 (통계청 시·도 경계 단순화본). 외부 지도 SDK 없이 Canvas 로 그려요.
 * 확대·축소(핀치·버튼), 이동(드래그), 시·도 선택, 화면 크기 기준 핀 클러스터링을 지원해요.
 * 국내 지도 SDK 를 붙이려면 같은 입력(핀·경로·선택)을 받는 다른 컴포저블로 바꾸면 돼요.
 */
@Composable
fun KoreaMap(
    provinces: List<Province>,
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    selectedPin: Long? = null,
    regionMode: Boolean = false,
    selectedProvince: String? = null,
    provinceCounts: Map<String, Int> = emptyMap(),
    route: List<MapPin> = emptyList(),
    cluster: Boolean = true,
    focus: ViewBox? = null,
    onProvince: (String?) -> Unit = {},
    onPin: (Long) -> Unit = {},
    showScope: Boolean = true,
    /** 선택한 핀 위에 띄우는 말풍선 (.mpop) */
    popup: (@Composable (MapPin) -> Unit)? = null,
) {
    val c = LocalSoran.current
    val tm = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val full = remember(provinces) {
        val b = provinces.map { it.bounds }
        val x0 = b.minOf { it.x }; val y0 = b.minOf { it.y }
        val x1 = b.maxOf { it.x + it.w }; val y1 = b.maxOf { it.y + it.h }
        ViewBox(x0 - 40, y0 - 60, x1 - x0 + 80, y1 - y0 + 120)
    }

    BoxWithConstraints(modifier.clip(RoundedCornerShape(16.dp)).background(c.sea).border(1.dp, c.line, RoundedCornerShape(16.dp))) {
        val wPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val hPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val aspect = (wPx / hPx).toDouble()
        val density = LocalDensity.current.density
        var vb by remember { mutableStateOf((focus ?: full).fitAspect(aspect)) }
        val anim = remember { Animatable(1f) }

        fun flyTo(target: ViewBox) {
            val from = vb; val to = target.fitAspect(aspect)
            scope.launch {
                anim.snapTo(0f)
                anim.animateTo(1f, tween(380)) {
                    val t = value.toDouble()
                    vb = ViewBox(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t, from.w + (to.w - from.w) * t, from.h + (to.h - from.h) * t)
                }
            }
        }

        LaunchedEffect(focus, aspect) { flyTo(focus ?: full) }
        LaunchedEffect(selectedProvince) {
            val p = provinces.firstOrNull { it.code == selectedProvince }
            if (regionMode) flyTo(p?.bounds?.let { ViewBox(it.x - 30, it.y - 30, it.w + 60, it.h + 60) } ?: full)
        }

        val minW = 24.0; val maxW = full.w
        fun clampZoom(v: ViewBox): ViewBox {
            val w = v.w.coerceIn(minW, maxW); val h = w / aspect
            return ViewBox(v.cx - w / 2, v.cy - h / 2, w, h)
        }
        fun toScreen(x: Double, y: Double) = Offset(((x - vb.x) / vb.w * wPx).toFloat(), ((y - vb.y) / vb.h * hPx).toFloat())
        fun toPlane(o: Offset) = (vb.x + o.x / wPx * vb.w) to (vb.y + o.y / hPx * vb.h)

        val unitsPerPx = vb.w / (wPx / density)
        val clusters = if (cluster) Clusterer.cluster(pins.map { Clusterer.Item(it, it.x, it.y) }, unitsPerPx, 40.0)
        else pins.map { Clusterer.Cluster(listOf(Clusterer.Item(it, it.x, it.y)), it.x, it.y) }
        val clustersNow by rememberUpdatedState(clusters)
        val selProvinceNow by rememberUpdatedState(selectedProvince)
        val onPinNow by rememberUpdatedState(onPin)
        val onProvinceNow by rememberUpdatedState(onProvince)

        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(aspect) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val k = vb.w / wPx
                        val (ax, ay) = toPlane(centroid)
                        vb = clampZoom(vb.zoom(1.0 / zoom, ax, ay).pan(-pan.x * k, -pan.y * k))
                    }
                }
                .pointerInput(regionMode) {
                    detectTapGestures { tap ->
                        val hitR = 28 * density
                        val hit = clustersNow.minByOrNull { cl -> toScreen(cl.x, cl.y).let { hypot(it.x - tap.x, it.y - tap.y) } }
                        val d = hit?.let { toScreen(it.x, it.y).let { s -> hypot(s.x - tap.x, s.y - tap.y) } } ?: Float.MAX_VALUE
                        if (hit != null && d < hitR) {
                            if (hit.size > 1) {
                                val box = ViewBox.around(hit.items.map { it.x to it.y }, pad = 20.0, minSize = minW * 1.4)
                                box?.let { flyTo(it) }
                            } else onPinNow(hit.items.first().key.id)
                            return@detectTapGestures
                        }
                        if (regionMode) {
                            val (px, py) = toPlane(tap)
                            val p = provinces.firstOrNull { it.contains(px, py) }
                            if (p != null && p.code != selProvinceNow) onProvinceNow(p.code)
                        }
                    }
                }
        ) {
            // 바다
            drawRect(c.sea)
            // 시·도
            provinces.forEach { p ->
                val path = Path().apply {
                    fillType = PathFillType.EvenOdd
                    p.rings.forEach { ring ->
                        ring.forEachIndexed { i, (x, y) ->
                            val o = toScreen(x, y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y)
                        }
                        close()
                    }
                }
                val (tint, mix) = RegionTints[p.colorIndex % RegionTints.size]
                val fill = when {
                    !regionMode -> c.land
                    selectedProvince == null -> lerp(c.land, tint, mix)
                    p.code == selectedProvince -> lerp(c.land, c.blue, .08f)
                    else -> lerp(c.sea, c.land, .6f)
                }
                drawPath(path, fill)
                val sel = regionMode && p.code == selectedProvince
                // 여행 지도(regionMode=false)는 시·도 경계를 그리지 않아요 (fill=stroke=land)
                drawPath(path, if (sel) c.blue else if (regionMode) c.hwy else c.land,
                    style = Stroke(width = if (sel) 2.5f * density else 1f * density, join = StrokeJoin.Round))
            }
            // 여행 지도: 주요 도시 (CITIES)
            if (!regionMode) CITIES.forEach { (n, lat, lng) ->
                val o = toScreen(GeoProjection.x(GeoPoint(lat, lng)), GeoProjection.y(GeoPoint(lat, lng)))
                drawCircle(c.hwy, 3 * density, o)
                val st = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.sub, fontFamily = ai.soran.app.ui.theme.Pretendard)
                val l = tm.measure(n, st)
                drawText(l, topLeft = Offset(o.x + 8 * density, o.y - l.size.height / 2f))
            }
            // 경로선 (기록 지점을 순서대로 이은 선 — 실제 주행 경로가 아님)
            if (route.size > 1) {
                val path = Path().apply { route.forEachIndexed { i, r -> val o = toScreen(r.x, r.y); if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                drawPath(path, Color.White, style = Stroke(9 * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, c.blue, style = Stroke(4 * density, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            // 핀
            val detail = vb.w < maxW * .5
            clusters.forEach { cl ->
                val o = toScreen(cl.x, cl.y)
                if (cl.size > 1) {
                    val has = cl.items.any { it.key.id == selectedPin }
                    drawCircle(c.accent.copy(alpha = .3f), (if (has) 22 else 19) * density, o)
                    drawCircle(Color.White, 15f * density, o)
                    drawCircle(c.accent, 12 * density, o)
                    drawCircle(Color(0xFF1E293B), 4.5f * density, o)
                    return@forEach
                }
                val pin = cl.items.first().key
                val on = pin.id == selectedPin
                if (pin.number != null) {
                    if (on) drawCircle(c.blue.copy(alpha = .18f), 26 * density, o)
                    drawCircle(if (on) c.blue else Color.White, (if (on) 19 else 16) * density, o)
                    drawCircle(if (on) Color.White else c.blue, (if (on) 19 else 16) * density, o, style = Stroke((if (on) 3f else 2.5f) * density))
                    label(tm, "${pin.number}", o, if (on) Color.White else c.blue, null, 16.sp, FontWeight.Bold)
                } else {
                    drawCircle(Color.White, ((if (on) 14 else 10) + 3) * density, o)
                    drawCircle(if (on) c.blue else c.accent, (if (on) 14 else 10) * density, o)
                    drawCircle(if (on) Color.White else Color(0xFF1E293B), (if (on) 5f else 3.5f) * density, o)
                    if (on || detail) label(tm, pin.label, o + Offset(0f, -20 * density - 7 * density), c.ink, c.land, 14.sp, FontWeight.SemiBold)
                }
            }
        }

        // 선택한 핀 말풍선 (.mpop) — 핀 위, 화면 위쪽이면 아래
        val sel = pins.firstOrNull { it.id == selectedPin }
        if (popup != null && sel != null) {
            val o = toScreen(sel.x, sel.y)
            var ph by remember { mutableStateOf(0) }
            var pw by remember { mutableStateOf(0) }
            val below = o.y < 150 * density
            val px = (o.x - pw / 2f).coerceIn(8 * density, wPx - pw - 8 * density)
            val py = if (below) o.y + 30 * density else o.y - ph - 30 * density
            Box(Modifier.offset { IntOffset(px.toInt(), py.toInt()) }.onSizeChanged { pw = it.width; ph = it.height }
                .widthIn(min = 180.dp, max = 260.dp).shadowLg(12.dp, c).glass(c, GlassLevel.STRONG, 12.dp, shadow = false)
                .padding(horizontal = 14.dp, vertical = 10.dp)) { popup(sel) }
        }

        // .mapctl — 확대·축소
        Column(Modifier.align(Alignment.BottomEnd).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MapCtl("+") { flyTo(clampZoom(vb.zoom(.6))) }
            MapCtl("−") { flyTo(clampZoom(vb.zoom(1.6))) }
        }
        // .scope — 지역 지도에서만: 전국 / 시·도 이름 + 전국 보기
        val zoomed = vb.w < full.fitAspect(aspect).w * .92
        if (showScope && regionMode) {
            val narrowed = zoomed || selectedProvince != null
            Row(Modifier.align(Alignment.TopStart).padding(14.dp).glass(c, GlassLevel.SECONDARY, 999.dp)
                .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp).height(32.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Txt(if (!narrowed) "전국" else provinces.firstOrNull { it.code == selectedProvince }?.name ?: "확대한 지역", ts(15, 600, 22f),
                    modifier = Modifier.padding(end = if (narrowed) 0.dp else 6.dp))
                if (narrowed) Box(Modifier.height(32.dp).clip(Pill).background(c.blue).tap { onProvince(null); flyTo(full) }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center) { Txt("전국 보기", ts(14, 600, 20f), c.onBlue) }
            }
        }
    }
}

/** 여행 지도의 주요 도시 */
private val CITIES = listOf(Triple("서울", 37.5665, 126.978), Triple("대전", 36.3504, 127.3845), Triple("대구", 35.8714, 128.6014),
    Triple("광주", 35.1595, 126.8526), Triple("부산", 35.1796, 129.0756))

@Composable
private fun MapCtl(t: String, onClick: () -> Unit) {
    val c = LocalSoran.current
    Box(Modifier.size(48.dp).glass(c, GlassLevel.STRONG, 12.dp).tap(onClick = onClick),
        contentAlignment = Alignment.Center) { Txt(t, ts(26, 400, 30f)) }
}

private fun DrawScope.label(tm: TextMeasurer, text: String, center: Offset, color: Color, halo: Color?, size: androidx.compose.ui.unit.TextUnit, w: FontWeight) {
    val style = TextStyle(fontSize = size, fontWeight = w, color = color, fontFamily = ai.soran.app.ui.theme.Pretendard)
    val layout = tm.measure(text, style)
    val tl = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
    if (halo != null) {
        val r = 2f * density
        listOf(Offset(-r, 0f), Offset(r, 0f), Offset(0f, -r), Offset(0f, r)).forEach {
            drawText(tm, text, tl + it, style.copy(color = halo))
        }
    }
    drawText(layout, topLeft = tl)
}
