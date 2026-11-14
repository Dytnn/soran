package ai.soran.app.ui.moment

import ai.soran.app.core.model.Comment
import ai.soran.app.core.model.Person
import ai.soran.app.ui.AppViewModel
import ai.soran.app.ui.components.Avatar
import ai.soran.app.ui.components.AvatarStack
import ai.soran.app.ui.components.BtnKind
import ai.soran.app.ui.components.BtnSm
import ai.soran.app.ui.components.Gap
import ai.soran.app.ui.components.GlassLevel
import ai.soran.app.ui.components.Grow
import ai.soran.app.ui.components.HLine
import ai.soran.app.ui.components.Ic
import ai.soran.app.ui.components.IconBtn
import ai.soran.app.ui.components.Pill
import ai.soran.app.ui.components.R12
import ai.soran.app.ui.components.R16
import ai.soran.app.ui.components.Txt
import ai.soran.app.ui.components.VLine
import ai.soran.app.ui.components.glass
import ai.soran.app.ui.components.tap
import ai.soran.app.ui.hms
import ai.soran.app.ui.ms
import ai.soran.app.ui.theme.Ico
import ai.soran.app.ui.theme.LocalSoran
import ai.soran.app.ui.theme.ts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmt(p: String, t: Long) = SimpleDateFormat(p, Locale.KOREA).format(Date(t))

/**
 * 순간 카드 상세 (.detail) — 화면 영역 안에서 열려요 (내비게이션·상단 바는 그대로).
 * 좌 441: 진한 카드 · 장소/시각/날씨/함께한 사람 · 음성 재생
 * 우 401: 대화 기록 · 댓글
 * 차량 맥락(출발 후 경과·거리)은 시안의 날짜 줄에 덧붙였어요.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MomentDetail(vm: AppViewModel, id: Long, narrow: Boolean) {
    val c = LocalSoran.current
    val moments by vm.moments.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val pb by vm.playback.collectAsStateWithLifecycle()
    val m = moments.firstOrNull { it.id == id } ?: return
    val t = trips.firstOrNull { it.id == m.tripId } ?: return
    val byId = people.associateBy { it.id }
    val sib = moments.filter { it.tripId == m.tripId }.sortedBy { it.createdAt }
    val idx = sib.indexOfFirst { it.id == m.id }
    var askDelete by remember(id) { mutableStateOf(false) }
    var title by remember(id, vm.sheetEdit) { mutableStateOf(m.title) }
    var place by remember(id, vm.sheetEdit) { mutableStateOf(m.placeLabel ?: "") }
    val mine = pb.momentId == m.id
    val playing = mine && pb.playing
    val posSec = if (mine) pb.positionMs / 1000f else 0f
    val durSec = if (mine && pb.durationMs > 0) pb.durationMs / 1000f else m.durationSec.toFloat()
    val progress = if (durSec > 0) (posSec / durSec).coerceIn(0f, 1f) else 0f

    Column(Modifier.fillMaxSize().background(c.bg).tap {}) {
        // .dt-head
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconBtn(Ico.chevL, "뒤로", { vm.closeMoment() })
            Gap(4.dp)
            Column(Modifier.weight(1f)) {
                Txt("${t.name} · 순간 ${idx + 1} / ${sib.size}", ts(14, 400, 20f), c.sub, maxLines = 1)
                Txt("순간 카드", ts(24, 600, 30f))
            }
            if (!narrow) { IconBtn(Ico.chevL, "이전 순간", { vm.stepMoment(-1) }, enabled = idx > 0); IconBtn(Ico.chevR, "다음 순간", { vm.stepMoment(1) }, enabled = idx < sib.size - 1) }
            if (askDelete) {
                BtnSm("취소", { askDelete = false }, height = 48.dp)
                DtBtn("한 번 더 눌러 삭제", Ico.trash, c.danger) { vm.deleteMoment(m.id) }
            } else {
                if (vm.sheetEdit) DtBtn("저장", Ico.check, c.blue) { vm.saveMomentEdits(m.id, title, place) }
                else DtBtn("수정", Ico.edit, c.ink) { vm.sheetEdit = true }
                DtBtn("삭제", Ico.trash, c.danger) { askDelete = true }
            }
        }
        Gap(18.dp)

        val left: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // .dcard — 진한 카드
                Column(Modifier.fillMaxWidth().clip(R16).background(c.ink).padding(horizontal = 26.dp, vertical = 22.dp)) {
                    val drive = listOfNotNull(
                        "출발 후 ${hms(m.context.tripElapsedSec)}".takeIf { m.context.tripElapsedSec > 0 },
                        m.context.tripDistanceKm?.let { "%.1fkm".format(it) },
                    )
                    Txt((listOf(fmt("yyyy.MM.dd E · a h:mm", m.createdAt)) + drive).joinToString(" · "), ts(15, 400, 22f), c.bg.copy(alpha = .8f))
                    Gap(8.dp)
                    if (vm.sheetEdit) Box(Modifier.fillMaxWidth().clip(R12).border(2.dp, c.blue, R12).padding(horizontal = 10.dp, vertical = 4.dp)) {
                        BasicTextField(title, { title = it.take(40) }, singleLine = true,
                            textStyle = ts(30, 600, 38f, TextUnit(-.75f, TextUnitType.Sp)).copy(color = c.bg), cursorBrush = SolidColor(c.blue))
                    } else Txt(m.title, ts(30, 600, 38f, TextUnit(-.75f, TextUnitType.Sp)), c.bg)
                    val quote = m.utterances.maxByOrNull { it.text.length }?.text.orEmpty()
                    if (quote.isNotEmpty()) { Gap(10.dp); Txt("“$quote”", ts(16, 400, 24f), c.bg.copy(alpha = .92f), maxLines = 3) }
                    if (m.tags.isNotEmpty()) {
                        Gap(14.dp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            m.tags.forEach { tag ->
                                Row(Modifier.height(26.dp).clip(Pill).background(Color.White.copy(alpha = .14f)).padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Txt(tag, ts(13, 400, 18f), c.bg)
                                    if (vm.sheetEdit) Box(Modifier.tap { vm.setTags(m.id, m.tags - tag) }) { Ic(Ico.close, 12.dp, c.bg, "태그 삭제") }
                                }
                            }
                        }
                    }
                }
                // .dmeta — 장소 · 시각 · 날씨 · 함께한 사람
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).glass(c, GlassLevel.STRONG, 12.dp)) {
                    Meta(Ico.pin, "장소", Modifier.weight(1.3f)) {
                        if (vm.sheetEdit) Box(Modifier.height(24.dp).clip(Pill).border(1.5.dp, c.blue, Pill).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                            if (place.isEmpty()) Txt("장소 이름", ts(14, 600, 20f), c.sub)
                            BasicTextField(place, { place = it.take(20) }, singleLine = true, textStyle = ts(14, 600, 20f).copy(color = c.blue), cursorBrush = SolidColor(c.blue))
                        } else MetaValue(m.placeText)
                    }
                    VLine(Modifier.fillMaxHeight())
                    Meta(Ico.clock, "시각", Modifier.weight(1f)) { MetaValue(fmt("a h:mm", m.createdAt)) }
                    VLine(Modifier.fillMaxHeight())
                    Meta(Ico.sun, "날씨", Modifier.weight(1f)) {
                        MetaValue(listOfNotNull(m.context.weather, m.context.outsideTempC?.let { "%.0f°".format(it) }).joinToString(" ").ifBlank { "정보 없음" })
                    }
                    VLine(Modifier.fillMaxHeight())
                    Meta(Ico.user, "함께한 사람", Modifier.weight(1.1f)) { AvatarStack(t.participantIds.mapNotNull { byId[it] }, 24.dp, ringWidth = 1.dp) }
                }
                // .vp — 음성 재생
                Column(Modifier.fillMaxWidth().glass(c, GlassLevel.STRONG, 16.dp).padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Txt("음성 재생", ts(17, 600, 24f)); Grow()
                        Txt(if (m.audioPath == null) "원본 음성 없음" else if (playing) "재생 중" else "정지됨", ts(14, 400, 20f), c.sub)
                    }
                    Gap(10.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(if (m.audioPath != null) c.blue else c.panel2)
                            .tap(m.audioPath != null) { vm.togglePlay(m) }, contentAlignment = Alignment.Center) {
                            Ic(if (playing) Ico.pause else Ico.play, 20.dp, if (m.audioPath != null) c.onBlue else c.sub, if (playing) "일시정지" else "재생")
                        }
                        Waveform("m${m.id}", progress, Modifier.weight(1f).height(44.dp))
                    }
                    Gap(8.dp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Txt(ms(posSec.toInt()), ts(14, 400, 20f), c.sub)
                        Box(Modifier.weight(1f).height(4.dp).clip(Pill).background(c.line)) {
                            Box(Modifier.fillMaxWidth(progress).fillMaxHeight().clip(Pill).background(c.blue))
                        }
                        Txt(ms(durSec.toInt()), ts(14, 400, 20f), c.sub)
                    }
                }
            }
        }
        val right: @Composable (Modifier) -> Unit = { mod ->
            Column(mod.glass(c, GlassLevel.STRONG, 16.dp).padding(horizontal = 24.dp, vertical = 20.dp)) {
                val speakers = m.utterances.mapNotNull { it.speakerId }.distinct()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Txt("대화 기록", ts(20, 600, 30f)); Grow()
                    Txt(listOfNotNull(if (speakers.isNotEmpty()) "말한 사람 ${speakers.size}명" else null, ms(m.durationSec)).joinToString(" · "), ts(14, 400, 20f), c.sub)
                }
                Gap(8.dp)
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    m.utterances.forEachIndexed { i, u ->
                        val next = m.utterances.getOrNull(i + 1)?.offsetSec ?: Int.MAX_VALUE
                        val active = mine && (pb.playing || pb.positionMs > 0) && posSec >= u.offsetSec && posSec < next
                        Column {
                            Row(Modifier.fillMaxWidth().clip(R12).background(if (active) c.blueSoft else Color.Transparent).padding(horizontal = 10.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Avatar(u.speakerId?.let { byId[it] }, 40.dp)
                                Column(Modifier.weight(1f)) {
                                    Txt("${u.speakerId?.let { byId[it]?.name } ?: "대화"} · ${ms(u.offsetSec)}", ts(13, 400, 18f), c.sub)
                                    Txt(u.text, ts(16, 400, 24f))
                                }
                            }
                            if (i < m.utterances.lastIndex) HLine(Modifier.fillMaxWidth())
                        }
                    }
                }
                Comments(vm, m.id, comments[m.id].orEmpty(), byId)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            if (narrow || maxWidth < 760.dp) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                left(Modifier.fillMaxWidth().heightIn(max = 2000.dp)); right(Modifier.fillMaxWidth().height(520.dp))
            } else Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                left(Modifier.weight(441f).fillMaxHeight()); right(Modifier.weight(401f).fillMaxHeight())
            }
        }
    }
}

/** .dt-act — 48 높이 Glass/Strong 글자 버튼 (아이콘 + 글자) */
@Composable
private fun DtBtn(text: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    val c = LocalSoran.current
    Row(Modifier.height(48.dp).glass(c, GlassLevel.STRONG, 12.dp).tap(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Ic(icon, 18.dp, color); Txt(text, ts(16, 600, 24f), color, maxLines = 1)
    }
}

@Composable
private fun Meta(icon: ImageVector, label: String, modifier: Modifier, value: @Composable () -> Unit) {
    val c = LocalSoran.current
    Row(modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Ic(icon, 18.dp, c.sub)
        Column {
            Txt(label, ts(12, 400, 16f), c.sub, maxLines = 1)
            value()
        }
    }
}

@Composable
private fun MetaValue(text: String) = Txt(text, ts(14, 600, 20f), maxLines = 1)

/** 시안의 hash / wfBars — 순간마다 고정된 파형 모양 */
private fun wfBars(id: String, n: Int = 56): List<Int> {
    var h = 0L
    id.forEach { ch -> h = (h * 31 + ch.code) and 0xFFFFFFFFL }
    return List(n) { i ->
        h = (h * 1103515245L + 12345L) and 0xFFFFFFFFL
        val v = ((h ushr 8) % 100).toInt()
        8 + Math.round(kotlin.math.abs(kotlin.math.sin(i / 3.2)) * 14 + v * 0.22).toInt()
    }
}

@Composable
private fun Waveform(id: String, progress: Float, modifier: Modifier) {
    val c = LocalSoran.current
    val bars = remember(id) { wfBars(id) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        bars.forEachIndexed { i, h ->
            Box(Modifier.weight(1f).height(h.dp).clip(RoundedCornerShape(2.dp))
                .background(if (progress > 0 && (i + .5f) / bars.size <= progress) c.blue else c.line))
        }
    }
}

/** .cm — 댓글 */
@Composable
private fun Comments(vm: AppViewModel, momentId: Long, list: List<Comment>, byId: Map<String, Person>) {
    val c = LocalSoran.current
    var draft by remember(momentId) { mutableStateOf("") }
    Column(Modifier.fillMaxWidth()) {
        HLine(Modifier.fillMaxWidth()); Gap(10.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt("댓글", ts(16, 600, 24f)); Txt("${list.size}개", ts(13, 400, 18f), c.sub)
        }
        Column(Modifier.heightIn(max = 92.dp).verticalScroll(rememberScrollState()).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (list.isEmpty()) Txt("아직 댓글이 없어요. 첫 댓글을 남겨보세요.", ts(14, 400, 20f), c.sub)
            list.forEach { cm ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Avatar(byId[cm.authorId], 28.dp)
                    Column(Modifier.weight(1f)) {
                        Txt("${if (cm.authorId == "me") "나" else byId[cm.authorId]?.name ?: ""} · ${fmt("MM.dd a h:mm", cm.createdAt)}", ts(12, 400, 16f), c.sub)
                        Txt(cm.text, ts(15, 400, 22f))
                    }
                    if (cm.authorId == "me") Box(Modifier.size(32.dp).clip(CircleShape).tap { vm.deleteComment(cm.id) }, contentAlignment = Alignment.Center) {
                        Ic(Ico.close, 14.dp, c.sub, "내 댓글 삭제")
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).height(44.dp).clip(R12).background(c.panel).border(1.dp, c.line, R12).padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart) {
                if (draft.isEmpty()) Txt("이 순간에 댓글 남기기", ts(15, 400, 22f), c.sub)
                BasicTextField(draft, { draft = it.take(120) }, singleLine = true, textStyle = ts(15, 400, 22f).copy(color = c.ink),
                    cursorBrush = SolidColor(c.blue), modifier = Modifier.fillMaxWidth())
            }
            Box(Modifier.height(44.dp).clip(R12).background(c.blue).tap { vm.addComment(momentId, draft); draft = "" }.padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center) { Txt("등록", ts(15, 600, 22f), c.onBlue) }
        }
    }
}

