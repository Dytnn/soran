package ai.soran.app.ui.home

import ai.soran.app.core.model.JoinMethod
import ai.soran.app.core.model.Moment
import ai.soran.app.core.model.Person
import ai.soran.app.core.model.TripTypes
import ai.soran.app.recording.RecState
import ai.soran.app.session.JoinCode
import ai.soran.app.ui.AppViewModel
import ai.soran.app.ui.HomeStep
import ai.soran.app.ui.LockScreen
import ai.soran.app.ui.components.Avatar
import ai.soran.app.ui.components.BtnKind
import ai.soran.app.ui.components.BtnSm
import ai.soran.app.ui.components.BtnXL
import ai.soran.app.ui.components.Chp
import ai.soran.app.ui.components.Gap
import ai.soran.app.ui.components.GlassLevel
import ai.soran.app.ui.components.Grow
import ai.soran.app.ui.components.HLine
import ai.soran.app.ui.components.Ic
import ai.soran.app.ui.components.Lnk
import ai.soran.app.ui.components.Pill
import ai.soran.app.ui.components.R12
import ai.soran.app.ui.components.R16
import ai.soran.app.ui.components.SttPill
import ai.soran.app.ui.components.TimeTile
import ai.soran.app.ui.components.Txt
import ai.soran.app.ui.components.glass
import ai.soran.app.ui.components.tap
import ai.soran.app.ui.components.todOf
import ai.soran.app.ui.hms
import ai.soran.app.ui.ms
import ai.soran.app.ui.rememberTicker
import ai.soran.app.ui.theme.Ico
import ai.soran.app.ui.theme.LocalSoran
import ai.soran.app.ui.theme.ts
import ai.soran.app.voice.NAV_LOADING
import ai.soran.app.voice.NavDestination
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun hhmm(t: Long) = SimpleDateFormat("HH:mm", Locale.KOREA).format(Date(t))
private fun tod(t: Long) = todOf(Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.HOUR_OF_DAY))

@Composable
fun HomeScreen(vm: AppViewModel, restricted: Boolean) {
    when (vm.homeStep) {
        HomeStep.LANDING, HomeStep.PREP ->
            if (restricted) LockScreen(vm, "home-setup")
            else if (vm.homeStep == HomeStep.LANDING) Landing(vm) else Prep(vm)
        HomeStep.ACTIVE -> Active(vm, restricted)
        HomeStep.ENDING -> Ending()
        HomeStep.DONE -> Done(vm, restricted)
    }
}

// ───────────────────────── 시작 전 홈 (.land.lnd2) ─────────────────────────

private val HOW = listOf(
    "“기록해줘”라고 말해요" to "남기고 싶은 대화가 나오면 이렇게 말하면 돼요.",
    "말이 끝나면 저장돼요" to "말을 텍스트로 바꿔 기록 시각·위치와 함께 저장해요.",
    "정차 후 다시 돌아봐요" to "지도와 수집에서 순간 카드를 펼쳐볼 수 있어요.",
)

@Composable
private fun Landing(vm: AppViewModel) {
    val c = LocalSoran.current
    val trips by vm.trips.collectAsStateWithLifecycle()
    val moments by vm.moments.collectAsStateWithLifecycle()
    val past = trips.filter { it.saved }.sortedByDescending { it.startedAt }.take(4)
    val locOn = vm.settings.saveLocation && !vm.c.location.demoBlocked.collectAsStateWithLifecycle().value

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 700.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // section.hero.hero-blue — 파란 소개 박스
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(R16).background(c.blue).padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(36.dp, Alignment.CenterVertically),
                ) {
                    Txt("가는 길에 일어난\n작은 소란.", ts(36, 600, 44f, androidx.compose.ui.unit.TextUnit(-0.9f, androidx.compose.ui.unit.TextUnitType.Sp)), c.onBlue)
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        BtnXL("새 여행 시작", { vm.openPrep() }, kind = BtnKind.ON_BLUE, icon = Ico.car)
                        Row(Modifier.alpha(.95f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            ReadyDot("음성 메모 ${if (vm.settings.sttEnabled) "켜짐" else "꺼짐"}", vm.settings.sttEnabled)
                            ReadyDot(if (locOn) "위치 함께 저장" else "위치 없이 저장", locOn)
                        }
                    }
                }
                // aside.howto.g-primary — 이렇게 기록해요
                if (!narrow) Column(Modifier.width(360.dp).fillMaxHeight().glass(c).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Txt("이렇게 기록해요", ts(20, 600, 28f))
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HOW.forEachIndexed { i, (h, d) ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(28.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                                    Txt("${i + 1}", ts(14, 600, 21f), c.bg)
                                }
                                Column { Txt(h, ts(17, 600, 24f)); Gap(2.dp); Txt(d, ts(15, 400, 22f), c.sub) }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Column {
                        HLine(Modifier.fillMaxWidth()); Gap(16.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Ic(Ico.mic, 16.dp, c.ink2, modifier = Modifier.padding(top = 2.dp))
                            Txt("함께 탄 모든 분께 안내하고 동의를 받은 뒤에만 기록해요.", ts(14, 400, 20f), c.ink2)
                        }
                    }
                }
            }
            // div.pastbar — 지난 여행
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Txt("지난 여행", ts(15, 600, 44f), c.sub)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (past.isEmpty()) Txt("아직 저장된 여행이 없어요", ts(16, 400, 24f), c.sub)
                    past.forEach { t ->
                        val place = moments.filter { it.tripId == t.id }.mapNotNull { it.placeLabel }
                            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                        Row(Modifier.height(44.dp).glass(c, GlassLevel.SECONDARY, 999.dp).tap { vm.openTripFromHome(t.id) }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Txt(SimpleDateFormat("MM.dd", Locale.KOREA).format(Date(t.startedAt)), ts(16, 600, 24f))
                            if (place != null) Txt("· $place", ts(16, 400, 24f), c.sub)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyDot(text: String, on: Boolean) {
    val c = LocalSoran.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) c.onBlue else c.onBlue.copy(alpha = .45f)))
        Txt(text, ts(16, 400, 24f), c.onBlue)
    }
}

// ───────────────────────── 출발 준비 (.land.prep) ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Prep(vm: AppViewModel) {
    val c = LocalSoran.current
    val people by vm.people.collectAsStateWithLifecycle()
    val dest by vm.destination.collectAsStateWithLifecycle()
    val byId = people.associateBy { it.id }
    val qr = remember(vm.joinCode) { JoinCode.qrBitmap(JoinCode.joinUri(vm.joinCode)) }
    val on = vm.prep.filter { it.value.joined }.keys.toList()
    val off = vm.prep.filter { !it.value.joined }.keys.toList()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 760.dp
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val info: @Composable (Modifier) -> Unit = { m ->
                Column(m.glass(c).padding(horizontal = 24.dp, vertical = 20.dp).verticalScroll(rememberScrollState())) {
                    Txt("여행 정보", ts(22, 600, 30f)); Gap(12.dp)
                    Txt("여행 이름", ts(16, 600, 24f), c.sub); Gap(4.dp)
                    Box(Modifier.fillMaxWidth().height(52.dp).glass(c, GlassLevel.STRONG, 12.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                        if (vm.tripName.isEmpty()) Txt("예: 금요일 밤 드라이브", ts(22, 600, 30f), c.sub)
                        BasicTextField(vm.tripName, { vm.tripName = it.take(20) }, singleLine = true,
                            textStyle = ts(22, 600, 30f).copy(color = c.ink), cursorBrush = SolidColor(c.blue), modifier = Modifier.fillMaxWidth())
                    }
                    Gap(12.dp); Txt("여행 유형", ts(16, 600, 24f), c.sub); Gap(10.dp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TripTypes.ALL.forEach { t -> Chp(t, vm.tripType == t, { vm.tripType = t }) }
                    }
                    Gap(12.dp); Txt("목적지", ts(16, 600, 24f), c.sub); Gap(4.dp)
                    DestCard(dest, vm.navNote, onChange = { vm.navNote = true }, onRetry = { vm.retryNav() })
                }
            }
            val crew: @Composable (Modifier) -> Unit = { m ->
                Column(m.glass(c).padding(horizontal = 24.dp, vertical = 20.dp).verticalScroll(rememberScrollState())) {
                    Txt("함께 탄 사람", ts(22, 600, 30f)); Gap(12.dp)
                    // div.qrbox.h.g-strong
                    Row(Modifier.fillMaxWidth().glass(c, GlassLevel.STRONG, 16.dp).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Image(qr, "참여 QR 코드", Modifier.size(112.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Txt("QR로 함께 탑승", ts(17, 600, 24f), c.sub)
                            Txt("휴대폰으로 스캔하면 바로 연결돼요", ts(14, 400, 20f), c.sub)
                            Txt("참여 코드 ${vm.joinCode}", ts(14, 600, 20f), c.ink2, Modifier.padding(top = 6.dp))
                        }
                    }
                    Gap(8.dp)
                    CrewRow(byId["me"], "${byId["me"]?.name ?: "나"} (나)", "운전자", null)
                    on.forEach { id ->
                        val p = byId[id]
                        CrewRow(p, p?.name ?: id, if (vm.prep[id]?.via == JoinMethod.QR) "QR 참여" else "직접 추가") { vm.removeMember(id) }
                    }
                    if (on.isEmpty()) Row(Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Spinner(); Txt("스캔을 기다리는 중…", ts(16, 400, 24f), c.sub)
                    }
                    if (off.isNotEmpty()) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt("QR 없이 추가", ts(16, 400, 24f), c.sub)
                        off.forEach { id -> Chp("+ ${byId[id]?.name ?: id}", onClick = { vm.addManual(id) }) }
                    }
                }
            }
            if (narrow) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                info(Modifier.fillMaxWidth().heightIn(max = 2000.dp)); crew(Modifier.fillMaxWidth().heightIn(max = 2000.dp))
            } else Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                info(Modifier.weight(393f).fillMaxHeight()); crew(Modifier.weight(451f).fillMaxHeight())
            }
            // div.prepbar
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.height(48.dp).clip(R12).tap { vm.backToLanding() }.padding(start = 4.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Ic(Ico.chevL, 18.dp, c.ink2); Txt("이전", ts(16, 600, 24f), c.ink2)
                }
                Txt(vm.prepHint, ts(16, 400, 24f), c.sub, Modifier.weight(1f), maxLines = 1)
                BtnXL("시작", { vm.startTrip() }, enabled = vm.prepReady, icon = Ico.car, height = 56.dp)
            }
        }
    }
}

@Composable
private fun DestCard(dest: NavDestination, note: Boolean, onChange: () -> Unit, onRetry: () -> Unit) {
    val c = LocalSoran.current
    Column(Modifier.fillMaxWidth().glass(c, GlassLevel.STRONG, 12.dp).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.padding(top = 5.dp).size(36.dp).clip(CircleShape).background(c.blueSoft), contentAlignment = Alignment.Center) {
                Ic(when (dest) { is NavDestination.Found -> Ico.pin; NavDestination.None -> Ico.pinOff; is NavDestination.Unavailable -> Ico.close }, 22.dp, c.blue)
            }
            Column(Modifier.weight(1f)) {
                when (dest) {
                    is NavDestination.Found -> {
                        Txt("내비게이션에서 가져온 목적지", ts(14, 400, 20f), c.sub)
                        Txt(dest.name, ts(18, 600, 26f), maxLines = 1)
                        Gap(12.dp); BtnSm("변경", onChange)
                    }
                    NavDestination.None -> {
                        Txt("설정된 목적지가 없어요", ts(18, 600, 26f))
                        Txt("내비게이션에서 정하면 여기에 보여요. 목적지 없이도 시작할 수 있어요.", ts(14, 400, 20f), c.sub)
                        Gap(12.dp); BtnSm("다시 불러오기", onRetry)
                    }
                    is NavDestination.Unavailable -> if (dest.reason == NAV_LOADING) {
                        Txt("목적지를 불러오는 중…", ts(18, 600, 26f))
                        Txt("내비게이션에서 목적지를 확인하고 있어요.", ts(14, 400, 20f), c.sub)
                    } else {
                        Txt("내비게이션과 연결하지 못했어요", ts(18, 600, 26f))
                        Txt("목적지 없이도 시작할 수 있어요. (${dest.reason})", ts(14, 400, 20f), c.sub)
                        Gap(12.dp); BtnSm("다시 시도", onRetry, kind = BtnKind.PRIMARY)
                    }
                }
            }
        }
        if (note && dest is NavDestination.Found) {
            Gap(10.dp)
            Txt("목적지 변경은 내비게이션에서 해 주세요. 바꾼 뒤 ‘다시 불러오기’를 눌러 주세요.", ts(14, 400, 20f), c.ink2)
            Lnk("다시 불러오기", onRetry)
        }
    }
}

@Composable
private fun CrewRow(p: Person?, name: String, via: String, onRemove: (() -> Unit)?) {
    val c = LocalSoran.current
    Column {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(p, 40.dp, c.panel)
            Column(Modifier.weight(1f)) {
                Txt(name, ts(18, 600, 24f), maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(c.ok)); Gap(6.dp)
                    Txt(via, ts(16, 400, 20f))
                }
            }
            if (onRemove != null) Box(Modifier.size(32.dp).clip(CircleShape).tap(onClick = onRemove), contentAlignment = Alignment.Center) {
                Ic(Ico.close, 16.dp, c.sub, "$name 빼기")
            }
        }
        HLine(Modifier.fillMaxWidth())
    }
}

@Composable
private fun Spinner() {
    val c = LocalSoran.current
    val tr = rememberInfiniteTransition(label = "spin")
    val a by tr.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = androidx.compose.animation.core.LinearEasing)), label = "a")
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val w = 3.dp.toPx()
        drawCircle(c.line, radius = size.minDimension / 2 - w / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        drawArc(c.blue, a, 90f, false, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
            size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    }
}

// ───────────────────────── 여행 중 (.hact) ─────────────────────────

/** 시안의 BARS (기록 중 파형 높이) */
private val BARS = listOf(18, 30, 22, 42, 28, 38, 18, 46, 32, 24, 40, 28, 48, 20, 36, 26, 44, 30, 20, 38, 28, 44)

@Composable
private fun Active(vm: AppViewModel, drv: Boolean) {
    val c = LocalSoran.current
    val live = vm.live ?: return
    val rec by vm.rec.collectAsStateWithLifecycle()
    val moments by vm.moments.collectAsStateWithLifecycle()
    val mine = moments.filter { it.tripId == live.tripId }.sortedByDescending { it.createdAt }
    val now = rememberTicker()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 760.dp
        val recSlot: @Composable (Modifier) -> Unit = { m ->
            Column(m.glass(c).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // div.trhead
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Box(Modifier.padding(top = 1.dp).height(28.dp).clip(Pill).background(c.panel2).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                            Txt(live.type, ts(14, 500, 24f))
                        }
                        Gap(4.dp)
                        Txt(live.name, ts(24, 600, 32f), maxLines = 1)
                    }
                    if (drv) RecStt(rec)
                    else if (!narrow) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("경과 시간", hms((now - live.startedAt) / 1000)); Stat("남긴 순간", "${mine.size}")
                    }
                }
                CallBox(vm, rec, drv, Modifier.weight(1f).fillMaxWidth())
            }
        }
        when {
            drv -> recSlot(Modifier.fillMaxSize())
            narrow -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                recSlot(Modifier.weight(1f).fillMaxWidth())
                TodayList(vm, mine, rec, Modifier.fillMaxWidth().height(170.dp))
                BtnXL("여행 종료", { vm.confirmEnd = true }, Modifier.fillMaxWidth(), kind = BtnKind.LINE, height = 52.dp)
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                recSlot(Modifier.weight(1f).fillMaxHeight())
                Column(Modifier.width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TodayList(vm, mine, rec, Modifier.weight(1f).fillMaxWidth())
                    EndRowBtn { vm.confirmEnd = true }
                }
            }
        }
    }
}

/** .endrow .btn.line — 52 높이, 16/600 */
@Composable
private fun EndRowBtn(onClick: () -> Unit) {
    val c = LocalSoran.current
    Box(Modifier.fillMaxWidth().height(52.dp).glass(c, GlassLevel.STRONG, 12.dp).tap(onClick = onClick), contentAlignment = Alignment.Center) {
        Txt("여행 종료", ts(16, 600, 24f))
    }
}

@Composable
private fun Stat(k: String, v: String) {
    val c = LocalSoran.current
    Column(Modifier.glass(c, GlassLevel.STRONG, 12.dp).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Txt(k, ts(14, 400, 20f), c.sub); Txt(v, ts(20, 600, 26f))
    }
}

@Composable
private fun RecStt(rec: RecState) {
    val c = LocalSoran.current
    when (rec) {
        RecState.Standby, RecState.Off -> SttPill("‘기록해줘’ 대기 중", c.sub, c.panel2, c.sub)
        is RecState.Recording -> SttPill("듣는 중", c.blue, c.blueSoft, c.blue)
        RecState.Processing -> SttPill("텍스트로 정리 중", c.sub, c.panel2, c.sub)
        is RecState.Saved -> SttPill("저장 완료", c.ok, c.okBg, c.ok)
        is RecState.Failed -> SttPill("인식 실패", c.sub, c.panel2, c.sub)
    }
}

/**
 * div.callbox — ‘기록해줘’ 안내 박스. 시안처럼 녹음 버튼은 두지 않고,
 * 박스 자체를 누르면 기록을 시작/종료해요 (음성 명령을 못 쓸 때의 대안, 기획서의 ‘앱 내 버튼’).
 */
@Composable
private fun CallBox(vm: AppViewModel, rec: RecState, drv: Boolean, modifier: Modifier) {
    val c = LocalSoran.current
    val locOn = vm.settings.saveLocation && !vm.c.location.demoBlocked.collectAsStateWithLifecycle().value
    val white = Color.White
    Column(
        modifier.clip(R16).background(c.blue)
            .tap(enabled = rec is RecState.Standby || rec is RecState.Recording || rec is RecState.Saved || rec is RecState.Failed) { vm.recordButton() }
            .padding(24.dp),
    ) {
        val capStyle = ts(if (drv) 18 else 16, 400, 24f)
        @Composable fun cap(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector = Ico.mic) =
            Row(Modifier.alpha(.92f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ic(icon, 18.dp, white); Txt(text, capStyle, white)
            }
        @Composable fun key(text: String) {
            val s = if (drv) 56 else 46
            Txt(text, ts(s, 600, if (drv) 64f else 54f, androidx.compose.ui.unit.TextUnit(-0.02f * s, androidx.compose.ui.unit.TextUnitType.Sp)), white, Modifier.padding(top = 10.dp))
        }
        @Composable fun keySm(text: String) {
            val s = if (drv) 36 else 30
            Txt(text, ts(s, 600, if (drv) 44f else 38f, androidx.compose.ui.unit.TextUnit(-0.02f * s, androidx.compose.ui.unit.TextUnitType.Sp)), white,
                Modifier.padding(top = 10.dp).padding(start = 18.dp))
        }
        @Composable fun desc(text: String) =
            Txt(text, ts(if (drv) 20 else 18, 400, if (drv) 30f else 28f), white.copy(alpha = .92f), Modifier.padding(top = 10.dp).widthIn(max = 440.dp))

        when (rec) {
            RecState.Standby, RecState.Off -> {
                cap("남기고 싶은 대화가 시작되면"); key("“기록해줘”")
                desc(if (vm.c.voice.demo != null) "시연 빌드는 실제 음성을 인식하지 않아요. 이 박스를 누르면 예시 대화로 기록돼요."
                    else if (vm.settings.voiceCommand) "라고 말해 보세요. 말한 순간부터 대화를 듣고, 말이 멈추면 저장해요."
                    else "라고 말하는 대신 이 박스를 눌러 기록할 수 있어요. 말이 멈추면 저장해요.")
            }
            is RecState.Recording -> {
                cap("듣는 중 · ${ms(((System.currentTimeMillis() - rec.startedAt) / 1000).toInt())}")
                if (drv) keySm("말씀하세요")
                else Box(Modifier.padding(top = 14.dp).fillMaxWidth().heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)).background(white.copy(alpha = .18f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Txt(rec.partial.ifBlank { "말씀하세요…" }, ts(18, 400, 28f), white.copy(alpha = if (rec.partial.isBlank()) .75f else 1f))
                }
            }
            RecState.Processing -> { cap("텍스트로 정리 중"); keySm("말씀을 텍스트로 바꾸고 있어요") }
            is RecState.Failed -> { cap("인식된 말이 없어요"); keySm("다시 말해 보세요"); desc("‘기록해줘’라고 말한 뒤 이야기를 이어가 주세요.") }
            is RecState.Saved -> {
                cap("저장했어요 · ${hhmm(rec.time)}", Ico.check)
                keySm(if (rec.withLocation) "위치와 함께 저장" else "위치 없이 저장")
                desc("${if (vm.settings.voiceGuide) "음성으로 안내했어요. " else ""}다음 대화도 ‘기록해줘’로 남길 수 있어요.")
            }
        }
        Spacer(Modifier.weight(1f))
        // div.cb-foot
        val footText = ts(if (drv) 18 else 16, 400, 24f)
        when (rec) {
            RecState.Standby, RecState.Off -> {
                val loc: @Composable () -> Unit = {
                    Row(Modifier.alpha(.92f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Ic(if (locOn) Ico.pin else Ico.pinOff, 16.dp, white); Txt(if (locOn) "위치와 함께 저장" else "위치 없이 저장", ts(16, 400, 24f), white)
                    }
                }
                val heard by vm.c.recorder.heard.collectAsStateWithLifecycle()
                val waitText = if (heard.isNotBlank()) "들린 말 · $heard" else "‘기록해줘’를 기다리는 중"
                if (drv) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Wave(false); Txt(waitText, footText, white, Modifier.weight(1f), maxLines = 1); loc()
                } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Wave(false); Txt(waitText, footText, white, maxLines = 1)
                    }
                    Row(Modifier.fillMaxWidth()) { Grow(); loc() }
                }
            }
            is RecState.Recording -> {
                val cancel: @Composable () -> Unit = {
                    Box(Modifier.height(40.dp).clip(R12).background(white.copy(alpha = .18f)).tap { vm.cancelRecording() }.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center) { Txt("취소", ts(16, 600, 24f), white) }
                }
                if (drv) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Wave(true); Txt("말이 멈추면 자동으로 저장해요", footText, white); Grow(); cancel()
                } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Wave(true)
                    Row(verticalAlignment = Alignment.CenterVertically) { Txt("말이 멈추면 자동으로 저장해요", footText, white); Grow(); cancel() }
                }
            }
            else -> Wave(false)
        }
    }
}

/** span.cb-wave — 22개 막대(5 폭, 4 간격). 대기: 5×5 점(불투명도 .55), 기록 중: 움직이는 막대 */
@Composable
private fun Wave(active: Boolean) {
    // 대기 중(점)에는 애니메이션을 돌리지 않아요 — 매 프레임 다시 그리면 차량·에뮬레이터에 부담이 커요.
    if (active) WaveBars() else WaveDots()
}

@Composable
private fun WaveDots() {
    Row(Modifier.width(194.dp).height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BARS.forEach { _ -> Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .55f))) }
    }
}

@Composable
private fun WaveBars() {
    val active = true
    val tr = rememberInfiniteTransition(label = "wave")
    val ph by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    Row(Modifier.width(194.dp).height(28.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        BARS.forEachIndexed { i, h ->
            if (active) {
                val k = 0.3f + 0.7f * (((ph + i * 0.37f) % 1f).let { if (it > .5f) 1 - it else it } * 2)
                Box(Modifier.width(5.dp).height((h * k).coerceAtLeast(8f).dp).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = .95f)))
            } else Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = .55f)))
        }
    }
}

/** section.today — 오늘의 순간 */
@Composable
private fun TodayList(vm: AppViewModel, mine: List<Moment>, rec: RecState, modifier: Modifier) {
    val c = LocalSoran.current
    val fresh = (rec as? RecState.Saved)?.momentId ?: vm.lastFresh
    Column(modifier.glass(c).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("오늘의 순간", ts(18, 600, 24f)); Grow(); Txt("${mine.size}개", ts(14, 500, 24f), c.sub)
        }
        if (mine.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Txt("아직 남긴 순간이 없어요.\n‘기록해줘’라고 말해 첫 순간을 남겨보세요.", ts(16, 400, 24f).copy(textAlign = TextAlign.Center), c.sub)
        } else Column(Modifier.verticalScroll(rememberScrollState()).padding(top = 4.dp)) {
            mine.forEachIndexed { i, m ->
                if (i > 0) HLine(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().tap { vm.openMoment(m.id) }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.width(56.dp).padding(top = 1.dp)) {
                        Txt(hhmm(m.createdAt), ts(16, 600, 20f)); Gap(6.dp); Txt(tod(m.createdAt), ts(14, 400, 17f), c.sub)
                    }
                    Column(Modifier.weight(1f)) {
                        Txt(m.title, ts(16, 600, 22f), if (m.id == fresh) c.blue else c.ink, maxLines = 1)
                        Gap(6.dp)
                        Txt(m.placeText, ts(14, 400, 17f), c.sub, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ───────────────────────── 정리 중 · 기록 완료 ─────────────────────────

@Composable
private fun Ending() {
    val c = LocalSoran.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt("순간 카드를 정리하고 있어요", ts(30, 600, 38f))
            Txt("텍스트 메모를 시각·위치와 묶어 카드로 저장해요", ts(19, 400, 28f), c.sub)
            LinearProgressIndicator(Modifier.padding(top = 8.dp).width(320.dp).height(6.dp).clip(Pill), color = c.blue, trackColor = c.line)
        }
    }
}

@Composable
private fun Done(vm: AppViewModel, drv: Boolean) {
    val c = LocalSoran.current
    val moments by vm.moments.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val (tripId, saved) = vm.lastEnded ?: return
    val t = trips.firstOrNull { it.id == tripId }
    val mine = moments.filter { it.tripId == tripId }.sortedByDescending { it.createdAt }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().glass(c).padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (!saved || t == null) {
                Txt("저장된 메모가 없어요", ts(30, 600, 38f))
                Txt("메모가 없어서 여행방을 만들지 않았어요.", ts(18, 400, 28f), c.ink2)
                BtnXL("새 여행 시작", { vm.newTrip() })
                return@Column
            }
            Box(Modifier.size(56.dp).clip(CircleShape).background(c.blue), contentAlignment = Alignment.Center) { Ic(Ico.check, 30.dp, c.onBlue) }
            Txt("기록 저장 완료", ts(30, 600, 38f))
            val dur = ((t.endedAt ?: t.startedAt) - t.startedAt) / 1000
            Txt("${t.name} · 순간 카드 ${mine.size}개 · 위치 ${mine.count { it.hasLocation }}개 · ${hms(dur)}", ts(18, 400, 28f), c.ink2)
            Column {
                mine.take(3).forEach { m ->
                    HLine(Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeTile(hhmm(m.createdAt), tod(m.createdAt))
                        Column(Modifier.weight(1f)) {
                            Txt(m.title, ts(18, 600, 24f), maxLines = 1)
                            Txt("${m.placeText} · ${tod(m.createdAt)} ${hhmm(m.createdAt)}", ts(14, 400, 20f), c.sub, maxLines = 1)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!drv) BtnXL("앨범 펼치기", { vm.openTripFromHome(tripId) }, icon = Ico.col)
                BtnXL("새 여행 시작", { vm.newTrip() }, kind = BtnKind.LINE)
            }
            if (drv) Txt("수집은 정차 후에 볼 수 있어요", ts(16, 400, 24f), c.sub)
        }
    }
}

