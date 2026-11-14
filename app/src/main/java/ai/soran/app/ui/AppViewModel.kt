package ai.soran.app.ui

import ai.soran.app.SoranApp
import ai.soran.app.core.model.Comment
import ai.soran.app.core.model.ConsentStatus
import ai.soran.app.core.model.JoinMethod
import ai.soran.app.core.model.Moment
import ai.soran.app.core.model.Person
import ai.soran.app.core.model.SeatPosition
import ai.soran.app.core.model.Trip
import ai.soran.app.export.StoryExporter
import ai.soran.app.recording.LiveTrip
import ai.soran.app.recording.RecState
import ai.soran.app.recording.RecordingSettings
import ai.soran.app.session.JoinCode
import ai.soran.app.voice.NavDestination
import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val title: String) { HOME("홈"), MAP("지도"), COL("수집"), MY("마이페이지") }
enum class HomeStep { LANDING, PREP, ACTIVE, ENDING, DONE }
enum class ColMode { ALBUMS, MOMENTS }
enum class MySection(val title: String) { PROFILE("내 프로필"), PEOPLE("참여자 관리"), SETTINGS("음성 메모·위치 설정"), DATA("데이터 관리"), INFO("서비스 정보") }

/** 출발 준비 화면의 동승자 한 명. */
data class PrepMember(val joined: Boolean, val via: JoinMethod, val consent: ConsentStatus, val seat: SeatPosition?)

data class TripView(val tripId: Long, val selected: Long? = null, val from: Tab)

data class MomentFilter(
    val query: String = "",
    val who: String? = null,
    val from: Long? = null,
    val to: Long? = null,
    val tag: String? = null,
) {
    val active get() = who != null || from != null
}

/** 시안의 TAGS (모든 순간 태그 줄) */
val TAGS = listOf("웃음", "휴게소", "맛집", "여행", "일상")

/** 날짜 필터 팝오버 상태 (특정 날짜 / 기간) */
data class DatePick(val range: Boolean = false, val year: Int, val month: Int, val start: Long? = null, val end: Long? = null)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val c = (app as SoranApp).container

    // ---- 데이터 ----
    val trips: StateFlow<List<Trip>> = c.repo.trips.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val moments: StateFlow<List<Moment>> = c.repo.moments.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val people: StateFlow<List<Person>> = c.repo.people.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val comments: StateFlow<Map<Long, List<Comment>>> = c.repo.comments.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val vehicle = c.vehicle.state
    val rec = c.recorder.state
    val destination = c.voice.nav.destination
    val playback = c.player.progress
    val provinces get() = c.provinces

    // ---- 화면 상태 ----
    var tab by mutableStateOf(Tab.HOME); private set
    var homeStep by mutableStateOf(HomeStep.LANDING); private set
    var tripName by mutableStateOf("금요일 밤 드라이브")
    var tripType by mutableStateOf("드라이브")
    val prep = mutableStateMapOf<String, PrepMember>()
    var joinCode by mutableStateOf(JoinCode.newCode()); private set
    var live by mutableStateOf<LiveTrip?>(null); private set
    var lastEnded by mutableStateOf<Pair<Long, Boolean>?>(null); private set // tripId, saved
    var confirmEnd by mutableStateOf(false)
    var sheetMoment by mutableStateOf<Long?>(null); private set
    var sheetEdit by mutableStateOf(false)
    var tripView by mutableStateOf<TripView?>(null); private set
    var colMode by mutableStateOf(ColMode.ALBUMS)
    var filter by mutableStateOf(MomentFilter())
    var mapProvince by mutableStateOf<String?>(null)
    var mySection by mutableStateOf(MySection.PROFILE)
    var settings by mutableStateOf(c.recorder.settings); private set
    var qrTrip by mutableStateOf<Long?>(null)
    var toast by mutableStateOf<String?>(null); private set
    var demoPanel by mutableStateOf(false)
    /** 출발 준비 · 목적지 ‘변경’ 안내 */
    var navNote by mutableStateOf(false)
    /** 방금 저장된 순간 (오늘의 순간 목록에서 파랗게) */
    var lastFresh by mutableStateOf<Long?>(null)
    /** 모든 순간 · 열린 팝오버 ("date" / "who") */
    var colPop by mutableStateOf<String?>(null)
    var datePick by mutableStateOf(java.util.Calendar.getInstance().let { DatePick(false, it.get(java.util.Calendar.YEAR), it.get(java.util.Calendar.MONTH)) })
    /** 지도 · 위치 없는 기록 팝업 */
    var noLocPop by mutableStateOf(false)

    init {
        resetPrep()
        // 정차·주차 브레이크 체결 → 오늘의 타임라인 완성 안내 / 주행 시작 → 상세 화면 닫기
        viewModelScope.launch {
            var wasRestricted = vehicle.value.restricted
            vehicle.distinctUntilChangedBy { it.restricted }.collect { v ->
                if (v.restricted) { sheetMoment = null; confirmEnd = false; qrTrip = null; c.player.stop() }
                else if (wasRestricted && live != null) say("정차했어요 · 지도와 수집에서 오늘의 순간을 볼 수 있어요")
                wasRestricted = v.restricted
            }
        }
        viewModelScope.launch { rec.collect { if (it is RecState.Saved) lastFresh = it.momentId } }
        // 음성 인식 오류를 화면에 알려요 (같은 오류는 30초에 한 번만)
        viewModelScope.launch {
            var last = ""; var at = 0L
            c.voice.speech.events.collect { e ->
                if (e is ai.soran.app.voice.SpeechEvent.Error) {
                    val now = System.currentTimeMillis()
                    if (e.message != last || now - at > 30_000) { last = e.message; at = now; say("음성 인식 오류 · ${e.message}") }
                }
            }
        }
        // 위치가 있는 기록마다 재방문 근접 알림 등록
        viewModelScope.launch {
            moments.distinctUntilChangedBy { l -> l.count { it.location != null } }.collect { c.location.syncProximityAlerts(it) }
        }
    }

    fun say(msg: String) {
        toast = msg
        viewModelScope.launch { delay(2_800); if (toast == msg) toast = null }
    }

    // ---- 탭 ----
    fun selectTab(t: Tab) { tab = t; tripView = null; sheetMoment = null }

    // ---- 출발 준비 ----
    private fun resetPrep() {
        prep.clear()
        listOf("jihu", "harin", "mom").forEach { prep[it] = PrepMember(false, JoinMethod.MANUAL, ConsentStatus.NOT_INFORMED, null) }
        joinCode = JoinCode.newCode()
    }

    fun openPrep() {
        homeStep = HomeStep.PREP
        c.voice.nav.refresh()
        c.join.open(joinCode) { id, consented -> onJoined(id, JoinMethod.QR, consented) }
    }

    fun backToLanding() { homeStep = HomeStep.LANDING; c.join.close() }

    private fun onJoined(id: String, via: JoinMethod, consented: Boolean) {
        val seat = nextFreeSeat()
        prep[id] = PrepMember(true, via, if (consented) ConsentStatus.AGREED else ConsentStatus.INFORMED, seat)
    }

    private fun nextFreeSeat(): SeatPosition? {
        val used = prep.values.filter { it.joined }.mapNotNull { it.seat }.toSet() + SeatPosition.DRIVER
        return SeatPosition.entries.firstOrNull { it !in used }
    }

    fun addManual(id: String) = onJoined(id, JoinMethod.MANUAL, consented = false)
    fun removeMember(id: String) { prep[id] = PrepMember(false, JoinMethod.MANUAL, ConsentStatus.NOT_INFORMED, null) }

    fun retryNav() { navNote = false; c.voice.nav.refresh() }

    /** 시작 버튼 옆 안내 (.prepbar .hint) */
    val prepHint: String
        get() = when {
            !settings.sttEnabled -> "마이페이지에서 음성 메모를 켜 주세요"
            tripName.isBlank() -> "여행 이름을 입력해 주세요"
            else -> "시작하면 함께 탄 분들께 기록 안내를 음성으로 알려요"
        }
    val prepReady get() = settings.sttEnabled && tripName.isNotBlank()

    fun startTrip() {
        if (!prepReady) return
        val ids = listOf("me") + prep.filter { it.value.joined }.keys
        val seats = listOf(SeatPosition.DRIVER) + prep.values.filter { it.joined }.mapNotNull { it.seat }
        val dest = (destination.value as? NavDestination.Found)?.name
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = c.repo.startTrip(tripName.trim(), tripType, now, dest, ids)
            val t = LiveTrip(id, tripName.trim(), tripType, now, vehicle.value.odometerKm, ids, seats)
            live = t
            c.join.close()
            c.recorder.attach(t)
            homeStep = HomeStep.ACTIVE
            // 기획서: 녹음 전 함께 탄 모든 분께 안내 — 시작할 때 음성으로 알려요
            if (settings.voiceGuide) c.voice.speaker.speak("여행을 시작했어요. 남기고 싶은 대화가 나오면 ‘기록해줘’라고 말해 주세요. 그때부터 말이 멈출 때까지의 대화를 기록해요.")
            say("여행을 시작했어요. ‘기록해줘’라고 말해 보세요")
        }
    }

    fun recordButton() = c.recorder.toggleByButton()
    fun cancelRecording() = c.recorder.cancel()

    fun endTrip() {
        val t = live ?: return
        confirmEnd = false
        c.recorder.attach(null)
        homeStep = HomeStep.ENDING
        viewModelScope.launch {
            delay(1_500)
            val km = (vehicle.value.odometerKm - t.startOdometerKm).takeIf { it > 0.05 }
            val saved = c.repo.endTrip(t.tripId, System.currentTimeMillis(), km)
            lastEnded = t.tripId to saved
            live = null
            homeStep = HomeStep.DONE
        }
    }

    fun newTrip() {
        tripName = "금요일 밤 드라이브"; tripType = "드라이브"; resetPrep()
        lastEnded = null; homeStep = HomeStep.LANDING
    }

    // ---- 순간 카드 ----
    fun openMoment(id: Long) {
        if (vehicle.value.restricted) { say("정차 후에 열어볼 수 있어요"); return }
        c.player.stop(); sheetEdit = false; sheetMoment = id
    }

    fun openMomentFromNotification(id: Long) { viewModelScope.launch { delay(600); openMoment(id) } }

    fun closeMoment() { c.player.stop(); sheetMoment = null; sheetEdit = false }

    fun stepMoment(dir: Int) {
        val cur = moments.value.firstOrNull { it.id == sheetMoment } ?: return
        val sib = moments.value.filter { it.tripId == cur.tripId }.sortedBy { it.createdAt }
        sib.getOrNull(sib.indexOfFirst { it.id == cur.id } + dir)?.let { openMoment(it.id) }
    }

    fun saveMomentEdits(id: Long, title: String, place: String) {
        viewModelScope.launch {
            c.repo.updateMomentMeta(id, title.trim().ifBlank { null }, place.trim().ifBlank { null }, null)
            if (place.isBlank()) c.repo.clearPlaceLabel(id)
            sheetEdit = false; say("순간 카드를 고쳤어요")
        }
    }

    fun setTags(id: Long, tags: List<String>) = viewModelScope.launch { c.repo.updateMomentMeta(id, null, null, tags) }

    fun deleteMoment(id: Long) = viewModelScope.launch {
        closeMoment(); c.repo.deleteMoment(id); say("순간 카드를 삭제했어요")
    }

    fun togglePlay(m: Moment) {
        val path = m.audioPath ?: run { say("이 기록은 원본 음성이 없어요"); return }
        c.player.toggle(m.id, path)
    }

    fun addComment(momentId: Long, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { c.repo.addComment(momentId, "me", text.trim()) }
    }

    fun deleteComment(id: Long) = viewModelScope.launch { c.repo.deleteComment(id) }

    fun exportStory(m: Moment) {
        val trip = trips.value.firstOrNull { it.id == m.tripId } ?: return
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                StoryExporter.saveToGallery(getApplication<Application>(), StoryExporter.render(trip, m), "soran_${m.id}")
            }
            say(if (uri != null) "사진 보관함 Pictures/Soran 에 저장했어요" else "이미지를 저장하지 못했어요")
        }
    }

    /** 마이페이지 · 내 기록 내보내기 — 모든 순간을 스토리 이미지로 저장 */
    fun exportAll() {
        val tripById = trips.value.associateBy { it.id }
        val list = moments.value.filter { tripById[it.tripId]?.saved == true }
        if (list.isEmpty()) { say("내보낼 기록이 없어요"); return }
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                list.count { m ->
                    StoryExporter.saveToGallery(getApplication<Application>(), StoryExporter.render(tripById.getValue(m.tripId), m), "soran_${m.id}") != null
                }
            }
            say("순간 카드 ${n}개를 Pictures/Soran 에 저장했어요")
        }
    }

    // ---- 여행 상세 ----
    fun openTrip(tripId: Long, select: Long? = null) { tripView = TripView(tripId, select, tab) }
    fun openTripFromHome(tripId: Long) { tab = Tab.COL; colMode = ColMode.ALBUMS; tripView = TripView(tripId, null, Tab.COL) }
    fun selectInTrip(id: Long?) { tripView = tripView?.copy(selected = if (tripView?.selected == id) null else id) }
    fun closeTrip() { tripView = null }

    fun addCrewByQr(tripId: Long) { qrTrip = tripId }

    fun toggleCrew(tripId: Long, personId: String) = viewModelScope.launch {
        val t = trips.value.firstOrNull { it.id == tripId } ?: return@launch
        val ids = if (personId in t.participantIds) t.participantIds - personId else t.participantIds + personId
        c.repo.setTripParticipants(tripId, ids)
    }

    // ---- 모든 순간 필터 ----
    fun filtered(): List<Moment> {
        val f = filter
        val tripById = trips.value.associateBy { it.id }
        return moments.value.filter { m ->
            val t = tripById[m.tripId] ?: return@filter false
            if (!t.saved) return@filter false
            if (f.query.isNotBlank() && f.query.trim() !in (m.title + " " + m.transcript + " " + (m.placeLabel ?: ""))) return@filter false
            if (f.who != null && f.who !in t.participantIds) return@filter false
            if (f.from != null && m.createdAt < f.from) return@filter false
            if (f.to != null && m.createdAt > f.to) return@filter false
            if (f.tag != null && "#${f.tag}" !in m.tags) return@filter false
            true
        }.sortedByDescending { it.createdAt }
    }

    // ---- 설정·데이터 ----
    fun updateSettings(s: RecordingSettings) {
        settings = s; c.recorder.settings = s; c.settings.save(s)
    }

    fun updateProfile(name: String? = null, color: Long? = null) = viewModelScope.launch {
        val me = people.value.firstOrNull { it.isMe } ?: return@launch
        c.repo.upsertPerson(me.copy(name = name?.take(6)?.ifBlank { null } ?: me.name, colorArgb = color ?: me.colorArgb))
    }

    fun deleteTrip(id: Long) = viewModelScope.launch {
        if (tripView?.tripId == id) tripView = null
        c.repo.deleteTrip(id); say("여행을 삭제했어요")
    }

    fun wipeLocations() = viewModelScope.launch { c.repo.wipeLocations(); say("위치 데이터를 모두 지웠어요") }

    // ---- 시연 도구 ----
    fun demoDriving(v: Boolean?) { c.vehicle.demoDriving.value = v }
    fun demoLocationBlocked(b: Boolean) { c.location.demoBlocked.value = b }
    fun demoTrigger() {
        val hooks = c.voice.demo
        when {
            live == null -> say("먼저 여행을 시작해 주세요")
            hooks != null -> hooks.simulateTrigger()
            else -> recordButton()
        }
    }
    fun demoQrJoin() {
        val id = prep.entries.firstOrNull { !it.value.joined }?.key ?: run { say("모두 연결됐어요"); return }
        if (homeStep != HomeStep.PREP) { say("출발 준비 화면에서 시연해 주세요"); return }
        onJoined(id, JoinMethod.QR, consented = true)
        say("${people.value.firstOrNull { it.id == id }?.name}님이 QR로 연결되고 휴대폰에서 동의했어요")
    }
    fun demoDestination(d: NavDestination) { c.voice.demo?.setDestination(d) }

    override fun onCleared() { c.join.close() }
}
