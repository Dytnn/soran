package ai.soran.app.ui.my

import ai.soran.app.ui.AppViewModel
import ai.soran.app.ui.MySection
import ai.soran.app.ui.components.Avatar
import ai.soran.app.ui.components.BtnDanger
import ai.soran.app.ui.components.BtnKind
import ai.soran.app.ui.components.BtnSm
import ai.soran.app.ui.components.Gap
import ai.soran.app.ui.components.GlassLevel
import ai.soran.app.ui.components.HLine
import ai.soran.app.ui.components.Ic
import ai.soran.app.ui.components.R16
import ai.soran.app.ui.components.Sw
import ai.soran.app.ui.components.Txt
import ai.soran.app.ui.components.glass
import ai.soran.app.ui.components.tap
import ai.soran.app.ui.theme.Ico
import ai.soran.app.ui.theme.LocalSoran
import ai.soran.app.ui.theme.Swatches
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SEC_ICON = mapOf(MySection.PROFILE to Ico.user, MySection.PEOPLE to Ico.car, MySection.SETTINGS to Ico.mic,
    MySection.DATA to Ico.col, MySection.INFO to Ico.pin)

@Composable
fun MyPageScreen(vm: AppViewModel) {
    val c = LocalSoran.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth < 760.dp
        val menu: @Composable () -> Unit = {
            MySection.entries.forEach { s ->
                val on = vm.mySection == s
                val m = if (on) Modifier.clip(R16).background(c.blue) else Modifier.glass(c, GlassLevel.SECONDARY, 16.dp)
                Row(Modifier.height(if (narrow) 48.dp else 56.dp).then(if (narrow) Modifier else Modifier.fillMaxWidth())
                    .then(m).tap { vm.mySection = s }.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Ic(SEC_ICON.getValue(s), 24.dp, if (on) c.onBlue else c.ink2)
                    Txt(s.title, ts(18, 600, 27f), if (on) c.onBlue else c.ink, maxLines = 1)
                }
            }
        }
        val body: @Composable (Modifier) -> Unit = { m ->
            Column(m.glass(c, GlassLevel.STRONG, 16.dp).padding(18.dp).verticalScroll(rememberScrollState())) {
                Txt(vm.mySection.title, ts(21, 600, 30f)); Gap(6.dp)
                when (vm.mySection) {
                    MySection.PROFILE -> Profile(vm)
                    MySection.PEOPLE -> People(vm)
                    MySection.SETTINGS -> Settings(vm)
                    MySection.DATA -> Data(vm)
                    MySection.INFO -> Info()
                }
            }
        }
        if (narrow) Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { menu() }
            body(Modifier.fillMaxWidth().weight(1f))
        } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { menu() }
            body(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

/** .trow — 제목 18/600 + 설명 15 · 아래 선 */
@Composable
private fun TRow(title: String, sub: String? = null, trailing: @Composable () -> Unit = {}) {
    val c = LocalSoran.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Txt(title, ts(18, 600, 27f))
            if (sub != null) Txt(sub, ts(15, 400, 22.5f), c.sub)
        }
        trailing()
    }
    HLine(Modifier.fillMaxWidth())
}

@Composable
private fun H3(text: String) = Txt(text, ts(18, 600, 27f), modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Profile(vm: AppViewModel) {
    val c = LocalSoran.current
    val people by vm.people.collectAsStateWithLifecycle()
    val me = people.firstOrNull { it.isMe } ?: return
    var name by remember(me.name) { mutableStateOf(me.name) }
    TRow("프로필 이미지", "순간 카드에 함께 표시돼요") { Avatar(me, 64.dp) }
    TRow("색상") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Swatches.forEach { col ->
                val on = me.colorArgb == col
                Box(Modifier.size(48.dp).border(if (on) 3.dp else 2.dp, if (on) c.blue else c.line, CircleShape).padding(if (on) 3.dp else 2.dp)
                    .clip(CircleShape).background(Color.White).padding(4.dp).clip(CircleShape).background(Color(col)).tap { vm.updateProfile(color = col) })
            }
        }
    }
    TRow("이름") {
        Box(Modifier.widthIn(max = 300.dp).width(240.dp).height(52.dp).glass(c, GlassLevel.STRONG, 12.dp, shadow = false).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart) {
            BasicTextField(name, { name = it.take(6) }, singleLine = true, textStyle = ts(18, 400, 27f).copy(color = c.ink), cursorBrush = SolidColor(c.blue),
                modifier = Modifier.fillMaxWidth())
        }
        if (name != me.name) BtnSm("저장", { vm.updateProfile(name = name) }, kind = BtnKind.PRIMARY)
    }
}

@Composable
private fun People(vm: AppViewModel) {
    val c = LocalSoran.current
    val people by vm.people.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    Txt("자주 함께 타는 사람이에요. 앱 안의 목록이고 계정과 연결되지 않아요.", ts(15, 400, 22.5f), c.sub)
    Gap(4.dp)
    people.filter { !it.isMe }.forEach { p ->
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(p, 48.dp)
            Column(Modifier.weight(1f)) {
                Txt(p.name, ts(18, 600, 27f))
                Txt("함께한 여행 ${trips.count { it.saved && p.id in it.participantIds }}개", ts(15, 400, 22.5f), c.sub)
            }
        }
        HLine(Modifier.fillMaxWidth())
    }
    TRow("공유 권한", "다른 사람과 카드를 공유하려면 서버와 계정 연동이 필요해서 이번 범위에서 제외했어요.")
}

@Composable
private fun Settings(vm: AppViewModel) {
    val s = vm.settings
    TRow("음성 메모", "끄면 여행을 시작할 수 없어요") { Sw(s.sttEnabled, { vm.updateSettings(s.copy(sttEnabled = !s.sttEnabled)) }) }
    TRow("‘기록해줘’ 음성 명령", "끄면 홈의 파란 안내 박스를 눌러서만 기록해요") {
        Sw(s.voiceCommand, { vm.updateSettings(s.copy(voiceCommand = !s.voiceCommand)) }, s.sttEnabled)
    }
    TRow("원본 음성 저장", "정차 후 다시 들을 수 있어요. 끄면 텍스트만 저장해요") {
        Sw(s.keepAudio, { vm.updateSettings(s.copy(keepAudio = !s.keepAudio)) }, s.sttEnabled)
    }
    TRow("위치 함께 저장", "메모를 저장할 때 위치도 함께 저장해요") { Sw(s.saveLocation, { vm.updateSettings(s.copy(saveLocation = !s.saveLocation)) }) }
    TRow("완료 음성 안내", "저장이 끝나면 소리로 알려줘요") { Sw(s.voiceGuide, { vm.updateSettings(s.copy(voiceGuide = !s.voiceGuide)) }) }
}

@Composable
private fun Data(vm: AppViewModel) {
    val c = LocalSoran.current
    val trips by vm.trips.collectAsStateWithLifecycle()
    val moments by vm.moments.collectAsStateWithLifecycle()
    var armed by remember { mutableStateOf<Long?>(null) }
    var wipeArmed by remember { mutableStateOf(false) }
    H3("저장된 여행")
    trips.filter { it.saved }.sortedByDescending { it.startedAt }.forEach { t ->
        TRow(t.name, "${SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(Date(t.startedAt))} · 기록 ${moments.count { it.tripId == t.id }}개") {
            BtnDanger(if (armed == t.id) "한 번 더 눌러 삭제" else "삭제", { if (armed == t.id) { vm.deleteTrip(t.id); armed = null } else armed = t.id })
        }
    }
    if (trips.none { it.saved }) Txt("저장된 여행이 없어요.", ts(15, 400, 22.5f), c.sub)
    H3("개인정보 관리")
    Txt("저장되는 데이터는 전사 텍스트, 기록 시각, 위치예요. 원본 음성은 설정을 켰을 때만 저장해요. 전화번호·주민번호·카드번호·이메일·계좌번호는 저장 전에 자동으로 가려요.",
        ts(15, 400, 22.5f), c.sub)
    Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        BtnSm("내 기록 내보내기", { vm.exportAll() }, icon = Ico.share)
        BtnDanger(if (wipeArmed) "한 번 더 눌러 삭제" else "위치 데이터 모두 삭제", { if (wipeArmed) { vm.wipeLocations(); wipeArmed = false } else wipeArmed = true })
    }
}

@Composable
private fun Info() {
    val c = LocalSoran.current
    H3("이용 안내")
    listOf(
        "정차 중에 함께 탄 사람을 고르고 음성 메모 안내를 확인해요.",
        "주행 중에는 ‘기록해줘’라고 말해 대화를 텍스트로 남겨요. 말이 멈추면 자동으로 저장돼요.",
        "저장된 메모는 기록 시각과 위치가 연결된 순간 카드가 돼요.",
        "정차 후 지도와 수집에서 장소 이름을 붙이고 추억을 다시 펼쳐봐요.",
    ).forEachIndexed { i, s ->
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(c.blue), contentAlignment = Alignment.Center) { Txt("${i + 1}", ts(17, 600, 24f), c.onBlue) }
            Txt(s, ts(18, 400, 27f), modifier = Modifier.padding(top = 2.dp))
        }
    }
    H3("기술 구성")
    listOf(
        "AAOS Car API" to "주행 중 UI 제한, 속도·기어·주차 브레이크·외기온",
        "Pleos SpeechToText · TextToSpeech" to "‘기록해줘’ 인식, 대화 받아쓰기, 음성 안내",
        "Pleos NaviHelper · FusedLocation" to "목적지 조회, 차량 위치와 재방문 알림",
    ).forEach { (k, v) -> TRow(k, v) }
}
