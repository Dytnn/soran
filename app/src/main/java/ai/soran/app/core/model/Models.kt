package ai.soran.app.core.model

/** 위경도 (WGS84). */
data class GeoPoint(val lat: Double, val lng: Double)

/** 차량 좌석 위치. AAOS VehicleAreaSeat 값과 매핑돼요. */
enum class SeatPosition(val label: String) {
    DRIVER("운전석"), FRONT_PASSENGER("조수석"),
    REAR_LEFT("뒷좌석 왼쪽"), REAR_CENTER("뒷좌석 가운데"), REAR_RIGHT("뒷좌석 오른쪽")
}

/** 주행 상태. AAOS CarUxRestrictions / 기어 / 주차 브레이크에서 계산해요. */
enum class DrivingState { PARKED, IDLING, MOVING, UNKNOWN }

/** 함께 탄 사람. 계정 연동 없이 앱 안의 목록이에요. */
data class Person(
    val id: String,
    val name: String,
    val colorArgb: Long,
    val isMe: Boolean = false,
)

enum class JoinMethod { DRIVER, QR, MANUAL }
enum class ConsentStatus { NOT_INFORMED, INFORMED, AGREED }

data class Participant(
    val person: Person,
    val joinedVia: JoinMethod,
    val consent: ConsentStatus,
    val seat: SeatPosition? = null,
)

object TripTypes {
    val ALL = listOf("드라이브", "출퇴근", "주말 나들이", "가족 여행")
}

data class Trip(
    val id: Long,
    val name: String,
    val type: String,
    val startedAt: Long,
    val endedAt: Long?,
    val destinationName: String?,
    val participantIds: List<String>,
    val saved: Boolean,
    val distanceKm: Double? = null,
)

/** 기록 시점의 차량·주행 맥락 — 스마트폰만으로는 알 수 없는 메타데이터. */
data class DriveContext(
    val tripElapsedSec: Long = 0,
    val tripDistanceKm: Double? = null,
    val speedKmh: Float? = null,
    val drivingState: DrivingState = DrivingState.UNKNOWN,
    val outsideTempC: Float? = null,
    val weather: String? = null,
    val occupiedSeats: List<SeatPosition> = emptyList(),
    val navDestination: String? = null,
)

/** 대화 한 줄. speakerId 는 화자를 알 수 있을 때만 채워요 (자동 화자 분리는 범위 밖). */
data class Utterance(val offsetSec: Int, val text: String, val speakerId: String? = null)

data class Comment(val id: Long, val momentId: Long, val authorId: String, val createdAt: Long, val text: String)

/** 스마트 순간 카드. */
data class Moment(
    val id: Long,
    val tripId: Long,
    val createdAt: Long,
    val location: GeoPoint?,
    val placeLabel: String?,
    val autoTitle: String,
    val customTitle: String?,
    val summary: String,
    val utterances: List<Utterance>,
    val tags: List<String>,
    val audioPath: String?,
    val durationSec: Int,
    val context: DriveContext,
) {
    val title: String get() = customTitle?.takeIf { it.isNotBlank() } ?: autoTitle
    val hasLocation: Boolean get() = location != null
    val placeText: String get() = placeLabel ?: if (hasLocation) "이름 없는 위치" else "위치 없음"
    val transcript: String get() = utterances.joinToString(" ") { it.text }
}
