package ai.soran.app.data

import ai.soran.app.core.model.DriveContext
import ai.soran.app.core.model.DrivingState
import ai.soran.app.core.model.GeoPoint
import ai.soran.app.core.model.Moment
import ai.soran.app.core.model.Person
import ai.soran.app.core.model.SeatPosition
import ai.soran.app.core.model.Utterance
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 시연용 예시 데이터 — HTML 시안의 여행 5개·순간 카드 15개와 같은 내용.
 * 첫 실행 때 DB 가 비어 있으면 넣어요 (마이페이지 > 데이터 관리에서 지울 수 있어요).
 */
object DemoSeeder {

    val PEOPLE = listOf(
        Person("me", "서윤", 0xFF8DB4FF, isMe = true),
        Person("jihu", "지후", 0xFFFFD27A),
        Person("harin", "하린", 0xFF7FE0BC),
        Person("mom", "엄마", 0xFFFFA9C6),
    )

    private class T(val name: String, val type: String, val date: String, val people: List<String>)
    private class M(
        val trip: Int, val label: String?, val time: String, val geo: GeoPoint?, val dlg: List<Triple<Int, String, String>>,
        val title: String, val sum: String, val dur: Int, val weather: String, val tags: List<String>,
    )

    private val TRIPS = listOf(
        T("동해 일출 드라이브", "드라이브", "2026-11-08", listOf("me", "jihu", "harin")),
        T("엄마랑 두물머리", "주말 나들이", "2026-10-19", listOf("me", "mom")),
        T("제주 렌터카 이틀", "드라이브", "2026-09-27", listOf("me", "jihu")),
        T("서울 야경 드라이브", "드라이브", "2026-10-03", listOf("me", "harin")),
        T("부산 바다 드라이브", "주말 나들이", "2026-08-15", listOf("me", "mom")),
    )

    private fun d(vararg l: Triple<Int, String, String>) = l.toList()
    private fun l(t: Int, who: String, s: String) = Triple(t, who, s)

    private val MOMENTS = listOf(
        M(0, "홍천 휴게소", "05:52", GeoPoint(37.697, 127.888), d(l(3, "me", "다들 안 졸려? 휴게소 잠깐 들를까?"), l(11, "jihu", "호두과자 내기 하자. 일출 시간 맞히는 사람이 이기는 걸로."), l(22, "harin", "나는 7시 10분."), l(31, "jihu", "나는 12분! 진 사람이 커피 사기다.")),
            "새벽 휴게소 호두과자 내기", "졸음을 쫓으려던 호두과자 내기가 일출 시각 맞히기로 번졌어요.", 48, "맑음 2°", listOf("#휴게소", "#맛집", "#웃음")),
        M(0, "대관령 전망대", "06:48", GeoPoint(37.704, 128.751), d(l(4, "me", "구름이 너무 많은데 해 보이긴 하려나?"), l(15, "harin", "기다리는 것도 여행이지. 올해 안에 하고 싶은 거 하나씩 말해볼래?"), l(30, "jihu", "나는 겨울 바다에서 라면 끓여 먹기.")),
            "구름 사이로 해를 기다리며 꺼낸 올해 소원", "구름이 걷히길 기다리며 올해 안에 하고 싶은 일을 하나씩 말했어요.", 55, "구름 1°", listOf("#여행", "#일상")),
        M(0, "경포 해변", "07:13", GeoPoint(37.8057, 128.9086), d(l(2, "jihu", "떴다! 떴다!"), l(9, "harin", "7시 11분! 내가 이겼지?"), l(17, "me", "아깝다, 나는 7시 15분 걸었는데.")),
            "해가 뜨는 순간 셋이 동시에 외침", "수평선 위로 해가 올라오자 다 같이 소리를 질렀어요.", 38, "맑음 4°", listOf("#웃음", "#바다", "#여행")),
        M(0, "경포 해변", "08:05", GeoPoint(37.804, 128.910), d(l(3, "jihu", "왜 내가 졌지. 아메리카노 세 잔이지?"), l(14, "me", "나는 따뜻한 걸로."), l(24, "harin", "사진 잘 나왔다, 이거 단톡에 올리자.")),
            "진 사람이 커피를 사러 가는 길", "내기에 진 사람이 투덜대며 커피를 사러 갔어요.", 42, "맑음 6°", listOf("#웃음", "#맛집")),
        M(0, null, "09:20", GeoPoint(37.7725, 128.947), d(l(3, "harin", "이거 올해 마신 커피 중에 제일 맛있어."), l(12, "me", "과장 아니야? 한 입만."), l(20, "jihu", "다음엔 여름에 오자. 바다 보면서.")),
            "인생 커피라는 한마디", "올해 마신 커피 중 최고라는 선언이 나왔고 다음엔 여름에 다시 오자고 약속했어요.", 36, "맑음 8°", listOf("#맛집", "#바다")),
        M(0, "주문진 수산시장", "12:30", GeoPoint(37.8917, 128.8264), d(l(5, "jihu", "돌아가는 길은 내 플레이리스트로 가자."), l(17, "harin", "가위바위보 하자. 이긴 사람이 선곡."), l(33, "me", "운전자는 거부권 있는 거 아니야?")),
            "회 한 접시와 돌아가는 길 선곡 전쟁", "돌아가는 길에 누가 노래를 고를지로 한참 다퉜어요.", 61, "맑음 9°", listOf("#맛집", "#음악", "#웃음")),
        M(1, "두물머리", "11:20", GeoPoint(37.546, 127.315), d(l(4, "mom", "아빠 처음 만난 날 비가 엄청 왔어."), l(16, "me", "진짜? 그건 처음 들어."), l(28, "mom", "우산이 하나뿐이라 같이 쓰고 갔지.")),
            "엄마가 처음 들려준 연애 이야기", "연잎통밥을 먹다가 엄마가 아빠를 처음 만난 날 이야기를 꺼냈어요.", 52, "맑음 14°", listOf("#일상")),
        M(1, "두물머리", "13:05", GeoPoint(37.5468, 127.3156), d(l(3, "me", "엄마, 좀 더 자연스럽게!"), l(10, "mom", "이게 자연스러운 거야."), l(18, "me", "하하하 알겠어, 그냥 그대로 찍을게.")),
            "사진 찍어주다 웃음이 터진 순간", "느티나무 앞에서 사진을 찍어주다가 둘 다 웃음이 터졌어요.", 33, "맑음 16°", listOf("#웃음", "#일상")),
        M(1, "팔당대교 근처", "15:40", null, d(l(2, "mom", "어머, 이 노래 오랜만이다."), l(11, "me", "엄마 가사 다 알아? 같이 불러봐."), l(24, "mom", "당연하지, 이건 내가 제일 잘 불러.")),
            "엄마 최애곡을 같이 따라 부른 드라이브", "좋아하는 노래가 나와 함께 따라 불렀어요. 위치를 받지 못해 위치 없이 저장됐어요.", 44, "맑음 15°", listOf("#음악", "#웃음")),
        M(2, "협재 해변", "10:10", GeoPoint(33.394, 126.2395), d(l(3, "jihu", "내비가 우리를 바다로 보내려고 해."), l(13, "me", "잠깐, 직진 아니고 우회전이래."), l(25, "jihu", "그냥 지도 앱 켜자.")),
            "렌터카 첫 시동, 내비가 바다로 안내한 순간", "내비가 계속 바다 쪽으로 안내해서 한참 웃었어요.", 40, "맑음 24°", listOf("#웃음", "#바다")),
        M(2, "성산일출봉", "17:30", GeoPoint(33.458, 126.9425), d(l(5, "me", "일출은 못 봤지만 노을도 괜찮다."), l(22, "jihu", "다음엔 일출 보러 다시 오자."), l(40, "me", "좋아. 그땐 내기 없이.")),
            "노을이 일출봉 뒤로 지는 걸 보며", "노을을 바라보다가 다음엔 일출을 보러 다시 오자고 약속했어요.", 57, "노을 21°", listOf("#야경", "#여행")),
        M(3, "남산타워", "21:40", GeoPoint(37.5512, 126.9882), d(l(4, "harin", "야경 진짜 예쁘다."), l(12, "me", "저기 불 켜진 데가 우리 동네야?"), l(22, "harin", "맞아, 한강 따라 쭉 달리자.")),
            "남산에서 내려다본 서울 야경", "야경을 내려다보며 사는 동네를 찾아봤어요.", 62, "맑음 14°", listOf("#야경", "#여행")),
        M(3, "반포대교", "22:15", GeoPoint(37.5133, 126.9966), d(l(3, "me", "분수 쇼 시간 맞춰서 왔다."), l(11, "harin", "창문 열어도 돼?"), l(18, "me", "잠깐만 열어보자.")),
            "분수 쇼 시간에 맞춰 건넌 다리", "분수 쇼 시간에 맞춰 다리를 건너며 잠깐 구경했어요.", 48, "맑음 14°", listOf("#야경")),
        M(4, "광안대교", "20:05", GeoPoint(35.1478, 129.1307), d(l(4, "mom", "다리 위에서 보니까 바다가 끝이 없네."), l(16, "me", "불빛이 물에 다 비친다.")),
            "광안대교 위에서 본 불빛", "다리 위에서 바다에 비친 불빛을 한참 바라봤어요.", 70, "맑음 26°", listOf("#바다", "#야경")),
        M(4, "해운대 해수욕장", "18:40", GeoPoint(35.1587, 129.1604), d(l(3, "me", "파도 소리 들려?"), l(10, "mom", "창문 내리면 들릴 것 같은데."), l(20, "me", "아이스크림부터 먹고 가자.")),
            "창문 너머로 들린 파도 소리", "창문을 내리고 파도 소리를 들었고 아이스크림부터 먹기로 했어요.", 55, "맑음 27°", listOf("#바다", "#맛집")),
    )

    private val COMMENTS = mapOf(
        2 to listOf("jihu" to "이 날 일출 진짜 대박이었다", "harin" to "내가 이긴 거 꼭 기억해줘 ㅋㅋ"),
        11 to listOf("harin" to "야경 보고 있으면 시간 가는 줄 모르겠어"),
        6 to listOf("mom" to "이 얘기 또 하게 될 줄은 몰랐네"),
    )

    private fun epoch(date: String, time: String): Long =
        LocalDateTime.parse("${date}T$time:00").atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    suspend fun seedIfEmpty(repo: SoranRepository) {
        PEOPLE.forEach { repo.upsertPerson(it) }
        if (!repo.isEmpty()) return
        val tripIds = TRIPS.map { t ->
            val ms = MOMENTS.filter { TRIPS[it.trip] === t }
            val start = epoch(t.date, ms.minOf { it.time })
            val id = repo.startTrip(t.name, t.type, start, null, t.people)
            id to t
        }
        MOMENTS.forEachIndexed { idx, m ->
            val (tripId, t) = tripIds[m.trip]
            val seats = listOf(SeatPosition.DRIVER, SeatPosition.FRONT_PASSENGER, SeatPosition.REAR_LEFT).take(t.people.size)
            val momentId = repo.addMoment(
                Moment(
                    id = 0, tripId = tripId, createdAt = epoch(t.date, m.time), location = m.geo, placeLabel = m.label,
                    autoTitle = m.title, customTitle = null, summary = m.sum,
                    utterances = m.dlg.map { Utterance(it.first, it.third, it.second) },
                    tags = m.tags, audioPath = null, durationSec = m.dur,
                    context = DriveContext(drivingState = DrivingState.MOVING, weather = m.weather,
                        outsideTempC = m.weather.filter { it.isDigit() || it == '-' }.toFloatOrNull(), occupiedSeats = seats),
                )
            )
            COMMENTS[idx]?.forEach { (who, text) -> repo.addComment(momentId, who, text) }
        }
        tripIds.forEach { (id, t) ->
            val last = MOMENTS.filter { TRIPS[it.trip] === t }.maxOf { it.time }
            repo.endTrip(id, epoch(t.date, last), null)
        }
    }
}
