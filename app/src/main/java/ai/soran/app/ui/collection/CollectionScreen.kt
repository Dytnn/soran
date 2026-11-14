package ai.soran.app.ui.collection

import ai.soran.app.core.geo.GeoProjection
import ai.soran.app.core.geo.ViewBox
import ai.soran.app.core.model.Moment
import ai.soran.app.core.model.Trip
import ai.soran.app.ui.AppViewModel
import ai.soran.app.ui.ColMode
import ai.soran.app.ui.DatePick
import ai.soran.app.ui.TAGS
import ai.soran.app.ui.Tab
import ai.soran.app.ui.components.Avatar
import ai.soran.app.ui.components.AvatarStack
import ai.soran.app.ui.components.FilterChip
import ai.soran.app.ui.components.Gap
import ai.soran.app.ui.components.GlassLevel
import ai.soran.app.ui.components.Grow
import ai.soran.app.ui.components.Ic
import ai.soran.app.ui.components.IconBtn
import ai.soran.app.ui.components.Lnk
import ai.soran.app.ui.components.Pill
import ai.soran.app.ui.components.R12
import ai.soran.app.ui.components.R16
import ai.soran.app.ui.components.Seg
import ai.soran.app.ui.components.TagChip
import ai.soran.app.ui.components.Txt
import ai.soran.app.ui.components.glass
import ai.soran.app.ui.components.shadowLg
import ai.soran.app.ui.components.tap
import ai.soran.app.ui.map.KoreaMap
import ai.soran.app.ui.map.MapPin
import ai.soran.app.ui.ms
import ai.soran.app.ui.theme.CoverTones
import ai.soran.app.ui.theme.Ico
import ai.soran.app.ui.theme.LocalSoran
import ai.soran.app.ui.theme.ts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun fmt(p: String, t: Long) = SimpleDateFormat(p, Locale.KOREA).format(Date(t))
private fun em(v: Float, size: Int) = TextUnit(v * size, TextUnitType.Sp)

@Composable
fun CollectionScreen(vm: AppViewModel) {
    if (vm.tripView?.from == Tab.COL) { TripDetail(vm); return }
    val c = LocalSoran.current
    val trips by vm.trips.collectAsStateWithLifecycle()
    val saved = trips.filter { it.saved }.sortedByDescending { it.startedAt }
    Column(Modifier.fillMaxSize()) {
        // .colhead
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt(if (vm.colMode == ColMode.ALBUMS) "함께한 여행 ${saved.size}권" else "모든 순간", ts(30, 600, 38f, em(-.015f, 30)))
            Grow()
            Seg(listOf("여행별", "모든 순간"), vm.colMode.ordinal, { vm.colMode = ColMode.entries[it]; vm.colPop = null })
        }
        if (vm.colMode == ColMode.ALBUMS) Albums(vm, saved) else AllMoments(vm)
    }
}

// ───────────────────────── 여행별 (.covers) ─────────────────────────

@Composable
private fun Albums(vm: AppViewModel, trips: List<Trip>) {
    val c = LocalSoran.current
    val people by vm.people.collectAsStateWithLifecycle()
    val byId = people.associateBy { it.id }
    if (trips.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Txt("아직 저장된 여행이 없어요.\n홈에서 새 여행을 시작해 보세요.", ts(18, 400, 28f).copy(textAlign = TextAlign.Center), c.sub)
        }
        return
    }
    Row(Modifier.fillMaxSize().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        trips.forEachIndexed { i, t ->
            Column(
                Modifier.width(252.dp).height(352.dp).clip(R16).background(CoverTones[i % CoverTones.size]).tap { vm.openTrip(t.id) }.padding(20.dp),
            ) {
                Txt(t.name, ts(28, 600, 34f, em(-.025f, 28)), Color.White)
                Gap(8.dp)
                Txt(fmt("yyyy.MM.dd", t.startedAt), ts(16, 400, 24f), Color.White.copy(alpha = .78f))
                Grow()
                Box(Modifier.glass(c, GlassLevel.SECONDARY, 999.dp, shadow = false).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    AvatarStack(t.participantIds.mapNotNull { byId[it] }, 32.dp, Color.White.copy(alpha = .9f))
                }
            }
        }
    }
}

// ───────────────────────── 모든 순간 ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllMoments(vm: AppViewModel) {
    val c = LocalSoran.current
    val trips by vm.trips.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val moments by vm.moments.collectAsStateWithLifecycle()
    val byId = people.associateBy { it.id }
    val tripById = trips.associateBy { it.id }
    val list = remember(vm.filter, moments, trips) { vm.filtered() }
    val f = vm.filter

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // .mfbar — 검색 + 날짜 + 함께한 사람 + 초기화
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.width(300.dp).height(52.dp).glass(c, GlassLevel.STRONG, 12.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Ic(Ico.search, 22.dp, c.sub)
                    Box(Modifier.weight(1f)) {
                        if (f.query.isEmpty()) Txt("제목이나 기록 내용 검색", ts(17, 400, 24f), c.sub, maxLines = 1)
                        BasicTextField(f.query, { vm.filter = f.copy(query = it) }, singleLine = true,
                            textStyle = ts(17, 400, 24f).copy(color = c.ink), cursorBrush = SolidColor(c.blue), modifier = Modifier.fillMaxWidth())
                    }
                }
                FilterChip(dateLabel(f.from, f.to) ?: "날짜", f.from != null, { vm.colPop = if (vm.colPop == "date") null else "date" }) {
                    Ic(Ico.clock, 18.dp, if (f.from != null) c.blue else c.ink2)
                }
                FilterChip(f.who?.let { byId[it]?.name } ?: "함께한 사람", f.who != null, { vm.colPop = if (vm.colPop == "who") null else "who" }) {
                    if (f.who != null) Avatar(byId[f.who], 24.dp) else Ic(Ico.user, 18.dp, c.ink2)
                }
                if (f.active) Lnk("초기화", { vm.filter = f.copy(who = null, from = null, to = null); vm.datePick = vm.datePick.copy(start = null, end = null) })
            }
            Gap(12.dp)
            // .tagrow
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TagChip("전체", f.tag == null, { vm.filter = f.copy(tag = null) })
                TAGS.forEach { t -> TagChip("#$t", f.tag == t, { vm.filter = f.copy(tag = if (f.tag == t) null else t) }) }
            }
            Gap(12.dp)
            Txt("${list.size}개의 순간", ts(14, 400, 20f), c.sub)
            Gap(10.dp)
            if (list.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Txt("조건에 맞는 순간이 없어요", ts(18, 400, 28f), c.sub)
            } else LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list, key = { it.id }) { m ->
                    val t = tripById[m.tripId]
                    Column(Modifier.glass(c, GlassLevel.STRONG, 16.dp).tap { vm.openMoment(m.id) }.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt(m.title, ts(20, 600, 28f, em(-.01f, 20)), maxLines = 2)
                        Txt(fmt("yyyy.MM.dd", m.createdAt), ts(14, 400, 20f), c.sub)
                        AvatarStack(t?.participantIds?.mapNotNull { byId[it] } ?: emptyList(), 30.dp)
                    }
                }
            }
        }
        // 팝오버
        if (vm.colPop != null) Box(Modifier.fillMaxSize().tap { vm.colPop = null })
        when (vm.colPop) {
            "date" -> Box(Modifier.offset(x = 306.dp, y = 58.dp).zIndex(2f)) { DatePop(vm, moments.map { it.createdAt }) }
            "who" -> Column(Modifier.offset(x = 420.dp, y = 58.dp).zIndex(2f).widthIn(min = 200.dp)
                .shadowLg(16.dp, c).glass(c, GlassLevel.STRONG, 16.dp, shadow = false).padding(6.dp)) {
                WhoRow("전체", f.who == null, null) { vm.filter = f.copy(who = null); vm.colPop = null }
                people.forEach { p -> WhoRow(p.name, f.who == p.id, p) { vm.filter = f.copy(who = p.id); vm.colPop = null } }
            }
        }
    }
}

private fun dateLabel(from: Long?, to: Long?): String? {
    if (from == null) return null
    val a = fmt("M.d", from)
    return if (to == null || fmt("yyMMdd", from) == fmt("yyMMdd", to)) a else "$a – ${fmt("M.d", to)}"
}

@Composable
private fun WhoRow(name: String, on: Boolean, p: ai.soran.app.core.model.Person?, onClick: () -> Unit) {
    val c = LocalSoran.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) c.blueSoft else Color.Transparent).tap(onClick = onClick)
        .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (p != null) Avatar(p, 28.dp)
        Txt(name, ts(16, 400, 24f), if (on) c.blue else c.ink)
    }
}

private fun dayStart(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m, d) }.timeInMillis

/** .pop.cal — 특정 날짜 / 기간 달력 */
@Composable
private fun DatePop(vm: AppViewModel, stamps: List<Long>) {
    val c = LocalSoran.current
    val dp = vm.datePick
    val cal = Calendar.getInstance().apply { clear(); set(dp.year, dp.month, 1) }
    val first = cal.get(Calendar.DAY_OF_WEEK) - 1
    val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val marked = remember(stamps, dp.year, dp.month) {
        stamps.map { Calendar.getInstance().apply { timeInMillis = it } }
            .filter { it.get(Calendar.YEAR) == dp.year && it.get(Calendar.MONTH) == dp.month }.map { it.get(Calendar.DAY_OF_MONTH) }.toSet()
    }
    fun pick(n: DatePick) {
        vm.datePick = n
        val s = n.start; val e = n.end ?: n.start
        vm.filter = vm.filter.copy(from = s, to = e?.plus(86_400_000L - 1))
    }
    Column(Modifier.width(300.dp).shadowLg(16.dp, c).glass(c, GlassLevel.STRONG, 16.dp, shadow = false).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Seg(listOf("특정 날짜", "기간"), if (dp.range) 1 else 0, { pick(dp.copy(range = it == 1, start = null, end = null)) },
            Modifier.fillMaxWidth(), itemHeight = 36.dp, fontSize = 14, fill = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).tap {
                vm.datePick = if (dp.month == 0) dp.copy(year = dp.year - 1, month = 11) else dp.copy(month = dp.month - 1)
            }, contentAlignment = Alignment.Center) { Ic(Ico.chevL, 18.dp, c.ink2) }
            Txt("${dp.year}년 ${dp.month + 1}월", ts(16, 600, 24f).copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
            Box(Modifier.size(36.dp).clip(CircleShape).tap {
                vm.datePick = if (dp.month == 11) dp.copy(year = dp.year + 1, month = 0) else dp.copy(month = dp.month + 1)
            }, contentAlignment = Alignment.Center) { Ic(Ico.chevR, 18.dp, c.ink2) }
        }
        Row { "일월화수목금토".forEach { d -> Txt("$d", ts(13, 400, 18f).copy(textAlign = TextAlign.Center), c.sub, Modifier.weight(1f)) } }
        val cells = List(first) { 0 } + (1..days).toList()
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { d ->
                    if (d == 0) { Box(Modifier.weight(1f).height(38.dp)); return@forEach }
                    val t = dayStart(dp.year, dp.month, d)
                    val isEdge = t == dp.start || t == dp.end
                    val inRange = dp.range && dp.start != null && dp.end != null && t > dp.start && t < dp.end
                    Box(Modifier.weight(1f).height(38.dp).padding(1.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (isEdge) c.blue else if (inRange) c.blueSoft else Color.Transparent)
                        .tap {
                            if (!dp.range) pick(dp.copy(start = if (dp.start == t) null else t, end = null))
                            else if (dp.start == null || dp.end != null) pick(dp.copy(start = t, end = null))
                            else if (t < dp.start) pick(dp.copy(start = t, end = dp.start))
                            else pick(dp.copy(end = t))
                        }, contentAlignment = Alignment.Center) {
                        Txt("$d", ts(15, 400, 20f), if (isEdge) c.onBlue else c.ink)
                        if (d in marked) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(4.dp).clip(CircleShape)
                            .background(if (isEdge) c.onBlue else c.accent))
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f).height(38.dp)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Txt(dateLabel(dp.start, dp.end) ?: if (dp.range) "시작일과 종료일을 선택해요" else "날짜를 선택해요", ts(14, 400, 20f), c.sub)
            Grow()
            Lnk("초기화", { pick(dp.copy(start = null, end = null)) }, style = ts(14, 600, 20f))
        }
    }
}

// ───────────────────────── 여행 상세 (.tv) — 지도 + 타임라인 ─────────────────────────

@Composable
fun TripDetail(vm: AppViewModel) {
    val c = LocalSoran.current
    val tv = vm.tripView ?: return
    val trips by vm.trips.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val moments by vm.moments.collectAsStateWithLifecycle()
    val t = trips.firstOrNull { it.id == tv.tripId } ?: run { vm.closeTrip(); return }
    val byId = people.associateBy { it.id }
    val mine = moments.filter { it.tripId == t.id }.sortedBy { it.createdAt }
    val pins = mine.mapIndexedNotNull { i, m -> m.location?.let { MapPin(m.id, GeoProjection.x(it), GeoProjection.y(it), m.placeText, i + 1) } }
    val focus = remember(t.id, pins.size) { ViewBox.around(pins.map { it.x to it.y }, pad = 60.0, minSize = 120.0) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 760.dp
        val map: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                KoreaMap(vm.provinces, pins, Modifier.fillMaxSize(), selectedPin = tv.selected, route = pins, cluster = false,
                    focus = focus, onPin = { vm.selectInTrip(it) }, showScope = false,
                    popup = { p ->
                        val mm = mine.first { it.id == p.id }
                        Column(Modifier.tap { vm.openMoment(mm.id) }) {
                            Txt("${fmt("HH:mm", mm.createdAt)} · ${mm.placeText}", ts(14, 400, 20f), c.sub, maxLines = 1)
                            Txt(mm.title, ts(17, 600, 24f), maxLines = 2)
                        }
                    })
                Box(Modifier.padding(12.dp)) { IconBtn(Ico.chevL, "뒤로", { vm.closeTrip() }) }
                Box(Modifier.align(Alignment.BottomStart).padding(14.dp).glass(c, GlassLevel.SECONDARY, 12.dp).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Txt(if (pins.isEmpty()) "위치가 있는 기록이 없어요" else "기록 지점을 순서대로 이은 선이에요 · 실제 주행 경로가 아니에요", ts(13, 400, 18f), c.sub)
                }
            }
        }
        val panel: @Composable (Modifier) -> Unit = { m ->
            Column(m.glass(c).padding(20.dp)) {
                Txt(fmt("M월 d일 E", t.startedAt), ts(14, 500, 20f), c.sub)
                Txt(t.name, ts(26, 600, 34f), maxLines = 1)
                Txt("${mine.size}개의 순간", ts(15, 400, 22f), c.sub)
                Gap(12.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarStack(t.participantIds.mapNotNull { byId[it] }, 28.dp)
                    Gap(4.dp)
                    Box(Modifier.size(32.dp).clip(CircleShape).border(1.5.dp, c.line, CircleShape).tap { vm.addCrewByQr(t.id) }, contentAlignment = Alignment.Center) {
                        Ic(Ico.plus, 16.dp, c.sub, "함께 탄 사람 추가")
                    }
                }
                Gap(16.dp)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    mine.forEachIndexed { i, m -> TimelineItem(vm, m, i + 1, m.id == tv.selected, i == mine.lastIndex) }
                }
            }
        }
        if (narrow) Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            map(Modifier.fillMaxWidth().aspectRatio(1.6f)); panel(Modifier.fillMaxWidth().weight(1f))
        } else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            map(Modifier.weight(1f).fillMaxHeight()); panel(Modifier.width(340.dp).fillMaxHeight())
        }
    }
}

/** .tli2 — 번호 원 + 세로선 + 시각·장소·요약, 선택 시 재생·카드 열기 */
@Composable
private fun TimelineItem(vm: AppViewModel, m: Moment, n: Int, on: Boolean, last: Boolean) {
    val c = LocalSoran.current
    val play by vm.playback.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().drawBehind {
        if (!last) drawRect(c.line, androidx.compose.ui.geometry.Offset(19.dp.toPx(), 40.dp.toPx()),
            androidx.compose.ui.geometry.Size(2.dp.toPx(), size.height - 40.dp.toPx()))
    }.tap { vm.selectInTrip(m.id) }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(40.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (on) c.blue else Color.White)
                .then(if (on) Modifier else Modifier.border(2.dp, c.blue, CircleShape)), contentAlignment = Alignment.Center) {
                Txt("$n", ts(17, 700, 22f), if (on) c.onBlue else c.blue)
            }
        }
        Column(Modifier.weight(1f).padding(bottom = 16.dp)) {
            Txt(fmt("HH:mm", m.createdAt), ts(14, 400, 20f), c.sub)
            Txt(m.placeText, ts(19, 600, 26f), maxLines = 1)
            Txt(m.summary.ifBlank { m.title }, ts(15, 400, 22f), c.ink2, maxLines = 1)
            if (on) {
                Gap(10.dp)
                val playing = play?.momentId == m.id && play?.playing == true
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.height(48.dp).glass(c, GlassLevel.STRONG, 12.dp).tap { vm.togglePlay(m) }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Ic(if (playing) Ico.pause else Ico.play, 18.dp, c.blue)
                        Txt(ms(m.durationSec), ts(16, 600, 24f))
                    }
                    Box(Modifier.height(48.dp).weight(1f).glass(c, GlassLevel.STRONG, 12.dp).tap { vm.openMoment(m.id) }, contentAlignment = Alignment.Center) {
                        Txt("순간 카드 열기", ts(16, 600, 24f))
                    }
                }
            }
        }
    }
}

