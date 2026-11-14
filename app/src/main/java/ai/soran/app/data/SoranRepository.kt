package ai.soran.app.data

import ai.soran.app.core.geo.Province
import ai.soran.app.core.model.Comment
import ai.soran.app.core.model.DriveContext
import ai.soran.app.core.model.DrivingState
import ai.soran.app.core.model.GeoPoint
import ai.soran.app.core.model.Moment
import ai.soran.app.core.model.Person
import ai.soran.app.core.model.SeatPosition
import ai.soran.app.core.model.Trip
import ai.soran.app.core.model.Utterance
import ai.soran.app.data.db.CommentEntity
import ai.soran.app.data.db.MomentEntity
import ai.soran.app.data.db.PersonEntity
import ai.soran.app.data.db.SoranDao
import ai.soran.app.data.db.TripEntity
import ai.soran.app.data.db.UtteranceEntity
import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.File

class SoranRepository(private val dao: SoranDao, private val filesDir: File) {

    val people: Flow<List<Person>> = dao.people().map { l -> l.map { Person(it.id, it.name, it.colorArgb, it.isMe) } }

    val trips: Flow<List<Trip>> = dao.trips().map { l -> l.map { it.toDomain() } }

    val moments: Flow<List<Moment>> = combine(dao.moments(), dao.utterances()) { ms, us ->
        val byMoment = us.groupBy { it.momentId }
        ms.map { it.toDomain(byMoment[it.id].orEmpty()) }
    }

    val comments: Flow<Map<Long, List<Comment>>> = dao.comments().map { l ->
        l.map { Comment(it.id, it.momentId, it.authorId, it.createdAt, it.text) }.groupBy { it.momentId }
    }

    fun audioFileFor(token: String) = File(filesDir, "audio/$token.wav")

    // ---- 사람 ----
    suspend fun upsertPerson(p: Person) = dao.upsertPerson(PersonEntity(p.id, p.name, p.colorArgb, p.isMe))

    // ---- 여행 ----
    suspend fun startTrip(name: String, type: String, startedAt: Long, destination: String?, participants: List<String>): Long =
        dao.insertTrip(TripEntity(0, name, type, startedAt, null, destination, participants.joinToString(","), saved = false, distanceKm = null))

    /** 여행 종료. 남긴 순간이 없으면 여행방을 만들지 않고 지워요. @return 저장 여부 */
    suspend fun endTrip(tripId: Long, endedAt: Long, distanceKm: Double?): Boolean {
        val t = dao.trip(tripId) ?: return false
        return if (dao.momentsOf(tripId).isEmpty()) { dao.deleteTrip(tripId); false }
        else { dao.upsertTrip(t.copy(endedAt = endedAt, saved = true, distanceKm = distanceKm)); true }
    }

    suspend fun setTripParticipants(tripId: Long, ids: List<String>) {
        dao.trip(tripId)?.let { dao.upsertTrip(it.copy(participantIds = ids.joinToString(","))) }
    }

    suspend fun deleteTrip(tripId: Long) {
        dao.momentsOf(tripId).forEach { m -> m.audioPath?.let { File(it).delete() } }
        dao.deleteTrip(tripId)
    }

    // ---- 순간 카드 ----
    suspend fun addMoment(m: Moment): Long = dao.insertMomentWithLines(
        m.toEntity(),
        m.utterances.map { UtteranceEntity(0, 0, it.offsetSec, it.text, it.speakerId) },
    )

    suspend fun updateMomentMeta(id: Long, customTitle: String?, placeLabel: String?, tags: List<String>?) {
        val e = dao.moment(id) ?: return
        val updated = e.copy(
            customTitle = customTitle ?: e.customTitle,
            placeLabel = placeLabel ?: e.placeLabel,
            tags = tags?.joinToString("|") ?: e.tags,
        )
        dao.upsertMoment(updated)
    }

    suspend fun clearPlaceLabel(id: Long) { dao.moment(id)?.let { dao.upsertMoment(it.copy(placeLabel = null)) } }

    suspend fun deleteMoment(id: Long) {
        dao.moment(id)?.audioPath?.let { File(it).delete() }
        dao.deleteMoment(id)
    }

    suspend fun addComment(momentId: Long, authorId: String, text: String) =
        dao.insertComment(CommentEntity(0, momentId, authorId, System.currentTimeMillis(), text))

    suspend fun deleteComment(id: Long) = dao.deleteComment(id)

    /** 개인정보 관리: 위치 데이터 모두 삭제. */
    suspend fun wipeLocations() = dao.clearAllLocations()

    suspend fun isEmpty() = dao.tripCount() == 0

    // ---- 매핑 ----
    private fun TripEntity.toDomain() = Trip(
        id, name, type, startedAt, endedAt, destination,
        participantIds.split(",").filter { it.isNotBlank() }, saved, distanceKm,
    )

    private fun MomentEntity.toDomain(lines: List<UtteranceEntity>) = Moment(
        id = id, tripId = tripId, createdAt = createdAt,
        location = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
        placeLabel = placeLabel, autoTitle = autoTitle, customTitle = customTitle, summary = summary,
        utterances = lines.map { Utterance(it.offsetSec, it.text, it.speakerId) },
        tags = tags.split("|").filter { it.isNotBlank() },
        audioPath = audioPath, durationSec = durationSec,
        context = DriveContext(
            tripElapsedSec = elapsedSec, tripDistanceKm = distanceKm, speedKmh = speedKmh,
            drivingState = runCatching { DrivingState.valueOf(drivingState) }.getOrDefault(DrivingState.UNKNOWN),
            outsideTempC = outsideTempC, weather = weather,
            occupiedSeats = seats.split(",").mapNotNull { s -> runCatching { SeatPosition.valueOf(s) }.getOrNull() },
            navDestination = navDestination,
        ),
    )

    private fun Moment.toEntity(): MomentEntity {
        val e = MomentEntity(
            0, tripId, createdAt, location?.lat, location?.lng, placeLabel, autoTitle, customTitle, summary,
            tags.joinToString("|"), audioPath, durationSec,
            context.tripElapsedSec, context.tripDistanceKm, context.speedKmh, context.drivingState.name,
            context.outsideTempC, context.weather, context.occupiedSeats.joinToString(",") { it.name },
            context.navDestination, searchText = transcript,
        )
        return e
    }

    companion object {
        /** assets/korea_provinces.json (HTML 시안의 시·도 경계 데이터) */
        fun loadProvinces(context: Context): List<Province> {
            val json = context.assets.open("korea_provinces.json").bufferedReader().use { it.readText() }
            val arr = JSONObject(json).getJSONArray("provinces")
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val rings = o.getJSONArray("rings")
                Province(
                    code = o.getString("code"), name = o.getString("name"),
                    labelX = o.getDouble("labelX"), labelY = o.getDouble("labelY"), colorIndex = o.getInt("colorIndex"),
                    rings = (0 until rings.length()).map { r ->
                        val ring = rings.getJSONArray(r)
                        (0 until ring.length()).map { k -> ring.getJSONArray(k).let { it.getDouble(0) to it.getDouble(1) } }
                    },
                )
            }
        }
    }
}
