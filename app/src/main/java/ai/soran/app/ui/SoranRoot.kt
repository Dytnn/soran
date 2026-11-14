package ai.soran.app.ui

import ai.soran.app.session.JoinCode
import ai.soran.app.ui.collection.CollectionScreen
import ai.soran.app.ui.components.BtnKind
import ai.soran.app.ui.components.BtnSm
import ai.soran.app.ui.components.BtnXL
import ai.soran.app.ui.components.Chp
import ai.soran.app.ui.components.Gap
import ai.soran.app.ui.components.GlassLevel
import ai.soran.app.ui.components.Grow
import ai.soran.app.ui.components.Ic
import ai.soran.app.ui.components.R12
import ai.soran.app.ui.components.TopChip
import ai.soran.app.ui.components.Txt
import ai.soran.app.ui.components.glass
import ai.soran.app.ui.components.shadowLg
import ai.soran.app.ui.components.tap
import ai.soran.app.ui.home.HomeScreen
import ai.soran.app.ui.map.MapScreen
import ai.soran.app.ui.moment.MomentDetail
import ai.soran.app.ui.my.MyPageScreen
import ai.soran.app.ui.theme.Ico
import ai.soran.app.ui.theme.LocalSoran
import ai.soran.app.ui.theme.ts
import ai.soran.app.voice.NavDestination
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val TAB_ICON: Map<Tab, ImageVector> = mapOf(Tab.HOME to Ico.home, Tab.MAP to Ico.map, Tab.COL to Ico.col, Tab.MY to Ico.user)

/**
 * 앱 셸 — 시안의 .app 그리드 그대로: 왼쪽 내비게이션 84 · 상단 바 56 · 화면(좌우 20, 아래 16 여백).
 * 창 폭이 760 미만이면 상단 52 · 하단 탭 64 로 바뀌어요(@container app max-width:760px).
 * 시연 도구는 차량 화면 밖 데모 바 대신, 왼쪽 위 ‘소란’ 글자를 길게 누르면 열려요 (demo·디버그 빌드).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoranRoot(vm: AppViewModel) {
    val c = LocalSoran.current
    val v by vm.vehicle.collectAsStateWithLifecycle()

    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        val narrow = maxWidth < 760.dp
        if (narrow) {
            Column(Modifier.fillMaxSize()) {
                TopBar(vm, narrow = true, restricted = v.restricted)
                Box(Modifier.weight(1f).fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) { Screen(vm, v.restricted, true) }
                Row(Modifier.fillMaxWidth().height(64.dp).background(c.g2).padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tab.entries.forEach { t -> NavItem(vm, t, v.restricted, Modifier.weight(1f).fillMaxHeight(), horizontal = true) }
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                // nav.nav: 84 폭, Glass/Secondary, 오른쪽 1px 선, 위아래 14 여백, 간격 8
                Row(Modifier.width(84.dp).fillMaxHeight()) {
                    Column(Modifier.weight(1f).fillMaxHeight().background(c.g2).padding(vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.height(24.dp).padding(bottom = 0.dp).combinedClickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = {}, onLongClick = { if (vm.c.isDemoBuild) vm.demoPanel = true },
                        )) { Txt("소란", ts(16, 700, 24f)) }
                        Gap(4.dp) // brand margin-bottom 12 = 간격 8 + 4
                        Tab.entries.forEach { t -> NavItem(vm, t, v.restricted, Modifier.size(68.dp, 78.dp), horizontal = false) }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    TopBar(vm, narrow = false, restricted = v.restricted)
                    Box(Modifier.weight(1f).fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) { Screen(vm, v.restricted, false) }
                }
            }
        }

        if (vm.confirmEnd && !v.restricted) EndDialog(vm)
        vm.qrTrip?.let { if (!v.restricted) QrDialog(vm, it) }
        if (vm.demoPanel) DemoPanel(vm)
        vm.toast?.let { msg ->
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = if (narrow) 88.dp else 24.dp)
                .shadowLg(12.dp, c).clip(R12).background(c.ink).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Txt(msg, ts(18, 500, 28f), c.bg)
            }
        }
    }
}

@Composable
private fun NavItem(vm: AppViewModel, t: Tab, restricted: Boolean, modifier: Modifier, horizontal: Boolean) {
    val c = LocalSoran.current
    val on = vm.tab == t
    val locked = restricted && t != Tab.HOME
    val fg = if (on) c.blue else c.sub
    Box(modifier.clip(R12).background(if (on) c.blueSoft else Color.Transparent).tap { vm.selectTab(t) }, contentAlignment = Alignment.Center) {
        if (horizontal) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Ic(TAB_ICON.getValue(t), 24.dp, fg); Txt(t.title, ts(14, 600, 21f), fg, maxLines = 1)
            if (locked) Ic(Ico.lock, 12.dp, c.sub, "주행 중 제한")
        } else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box {
                Ic(TAB_ICON.getValue(t), 30.dp, fg)
                if (locked) Ic(Ico.lock, 12.dp, c.sub, "주행 중 제한", Modifier.offset(x = 25.dp, y = (-2).dp))
            }
            Txt(t.title, ts(14, 600, 21f), fg, maxLines = 1)
        }
    }
}

@Composable
private fun TopBar(vm: AppViewModel, narrow: Boolean, restricted: Boolean) {
    val c = LocalSoran.current
    Row(Modifier.fillMaxWidth().height(if (narrow) 52.dp else 56.dp).padding(horizontal = if (narrow) 14.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt(vm.tab.title, ts(24, 600, 36f), maxLines = 1)
        // 여행 중이면 경과 시간 칩 (.chip.live)
        vm.live?.let { live ->
            val now = rememberTicker()
            TopChip(hms((now - live.startedAt) / 1000), c.blueSoft, c.blue) { Box(Modifier.size(10.dp).clip(CircleShape).background(c.blue)) }
        }
        Grow()
        if (restricted) TopChip("주행 중 · 일부 기능 제한", c.warnBg, c.warn) { Ic(Ico.lock, 16.dp, c.warn) }
    }
}

@Composable
private fun Screen(vm: AppViewModel, restricted: Boolean, narrow: Boolean) {
    if (restricted && vm.tab != Tab.HOME) { LockScreen(vm, vm.tab.name.lowercase()); return }
    // 순간 카드 상세는 화면 영역을 대신해요 (내비게이션·상단 바는 그대로)
    vm.sheetMoment?.let { id -> if (!restricted) { MomentDetail(vm, id, narrow); return } }
    when (vm.tab) {
        Tab.HOME -> HomeScreen(vm, restricted)
        Tab.MAP -> MapScreen(vm)
        Tab.COL -> CollectionScreen(vm)
        Tab.MY -> MyPageScreen(vm)
    }
}

/** LOCKMSG — 시안 문구 그대로 */
private val LOCK = mapOf(
    "home-setup" to ("여행 설정은 정차 후에 할 수 있어요" to "이름 입력과 함께 탄 사람 선택, 안내 확인은 주행 중에 제한돼요."),
    "map" to ("주행 중에는 지도 탐색이 제한돼요" to "핀 선택과 필터는 정차한 뒤에 사용할 수 있어요."),
    "col" to ("주행 중에는 추억 보기가 제한돼요" to "포토북과 순간 카드는 정차한 뒤에 펼쳐볼 수 있어요."),
    "my" to ("주행 중에는 설정 변경이 제한돼요" to "프로필, 참여자, 음성 메모·위치 설정은 정차한 뒤에 바꿀 수 있어요."),
)

@Composable
fun LockScreen(vm: AppViewModel, kind: String) {
    val c = LocalSoran.current
    val (h, p) = LOCK.getValue(kind)
    val extra = if (vm.live != null && vm.homeStep == HomeStep.ACTIVE) " ‘기록해줘’는 홈에서 계속 쓸 수 있어요." else ""
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(460.dp).glass(c).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(c.panel2), contentAlignment = Alignment.Center) { Ic(Ico.lock, 44.dp, c.sub) }
            Txt(h, ts(26, 700, 33.8f).copy(textAlign = TextAlign.Center))
            Txt(p + extra, ts(18, 400, 27f).copy(textAlign = TextAlign.Center), c.sub)
            if (kind != "home-setup") BtnXL("홈으로 돌아가기", { vm.selectTab(Tab.HOME) }, kind = BtnKind.LINE)
        }
    }
}

@Composable
fun Scrim(onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(LocalSoran.current.scrim).tap(onClick = onDismiss))
}

/** .dlg — 최대 500 폭, 둥글기 16, 여백 26, shadow-lg */
@Composable
fun DialogCard(title: String, body: String, actions: @Composable () -> Unit) {
    val c = LocalSoran.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 500.dp).padding(16.dp).shadowLg(16.dp, c).glass(c, GlassLevel.STRONG, 16.dp, shadow = false)
            .tap {}.padding(26.dp)) {
            Txt(title, ts(24, 700, 31f))
            Gap(10.dp)
            Txt(body, ts(18, 400, 27f), c.sub)
            Gap(20.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) { actions() }
        }
    }
}

@Composable
private fun EndDialog(vm: AppViewModel) {
    val moments by vm.moments.collectAsStateWithLifecycle()
    val n = moments.count { it.tripId == vm.live?.tripId }
    Scrim { vm.confirmEnd = false }
    DialogCard("여행을 마칠까요?", "메모 ${n}개를 순간 카드로 저장해요. 종료하면 음성 메모도 멈춰요.") {
        BtnXL("계속 달리기", { vm.confirmEnd = false }, kind = BtnKind.LINE)
        BtnXL("여행 종료", { vm.endTrip() }, kind = BtnKind.DANGER)
    }
}

@Composable
private fun QrDialog(vm: AppViewModel, tripId: Long) {
    val c = LocalSoran.current
    val trips by vm.trips.collectAsStateWithLifecycle()
    val t = trips.firstOrNull { it.id == tripId } ?: return
    val code = remember(tripId) { JoinCode.newCode() }
    val qr = remember(code) { JoinCode.qrBitmap(JoinCode.joinUri(code)) }
    Scrim { vm.qrTrip = null }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 500.dp).padding(16.dp).shadowLg(16.dp, c).glass(c, GlassLevel.STRONG, 16.dp, shadow = false).tap {}.padding(26.dp)) {
            Txt("QR로 함께 탄 사람 추가", ts(24, 700, 31f)); Gap(10.dp)
            Txt("휴대폰으로 스캔하면 ‘${t.name}’에 함께 탄 사람으로 추가돼요.", ts(18, 400, 27f), c.sub); Gap(20.dp)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(qr, "참여 QR 코드", Modifier.size(200.dp).clip(R12))
                Txt("참여 코드 $code", ts(16, 600, 24f), c.ink2)
            }
            Gap(20.dp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { BtnXL("닫기", { vm.qrTrip = null }, kind = BtnKind.LINE) }
        }
    }
}

/** 시안의 ‘데모 컨트롤’(차량 화면 밖) — 앱 안에서는 ‘소란’ 길게 누르기로 열어요 */
@Composable
private fun DemoPanel(vm: AppViewModel) {
    val c = LocalSoran.current
    val v by vm.vehicle.collectAsStateWithLifecycle()
    val blocked by vm.c.location.demoBlocked.collectAsStateWithLifecycle()
    val dest by vm.destination.collectAsStateWithLifecycle()
    Scrim { vm.demoPanel = false }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 560.dp).padding(16.dp).shadowLg(16.dp, c).glass(c, GlassLevel.STRONG, 16.dp, shadow = false).tap {}.padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt("데모 컨트롤", ts(24, 700, 31f))
            Txt("차량 화면 밖 시연용 조작이에요. ${if (v.carConnected) "차량 연결됨" else "차량 서비스 없음"}", ts(16, 400, 24f), c.sub)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt("차량", ts(15, 600, 22f), c.sub, Modifier.width(64.dp))
                Chp("실제 값", vm.c.vehicle.demoDriving.value == null, { vm.demoDriving(null) })
                Chp("주행 중", v.simulated && v.restricted, { vm.demoDriving(true) })
                Chp("정차 중", v.simulated && !v.restricted, { vm.demoDriving(false) })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt("위치", ts(15, 600, 22f), c.sub, Modifier.width(64.dp))
                Chp("수신 가능", !blocked, { vm.demoLocationBlocked(false) })
                Chp("수신 불가", blocked, { vm.demoLocationBlocked(true) })
            }
            if (vm.c.voice.demo != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Txt("목적지", ts(15, 600, 22f), c.sub, Modifier.width(64.dp))
                Chp("있음", dest is NavDestination.Found, { vm.demoDestination(NavDestination.Found("강릉 안목해변")) })
                Chp("없음", dest is NavDestination.None, { vm.demoDestination(NavDestination.None) })
                Chp("연결 실패", dest is NavDestination.Unavailable, { vm.demoDestination(NavDestination.Unavailable("연결 실패")) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BtnSm("‘기록해줘’ 시연", { vm.demoTrigger(); vm.demoPanel = false }, icon = Ico.mic)
                BtnSm("QR 스캔 시연", { vm.demoQrJoin() })
                BtnSm("닫기", { vm.demoPanel = false }, kind = BtnKind.PRIMARY)
            }
        }
    }
}

/** 1초마다 갱신되는 현재 시각 */
@Composable
fun rememberTicker(): Long {
    val state = androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) { kotlinx.coroutines.delay(1_000); value = System.currentTimeMillis() }
    }
    return state.value
}

/** fh: 0:00:00 */
fun hms(sec: Long): String = "%d:%02d:%02d".format(sec / 3600, sec % 3600 / 60, sec % 60)
/** fm: 0:00 */
fun ms(sec: Int): String = "%d:%02d".format(sec / 60, sec % 60)

