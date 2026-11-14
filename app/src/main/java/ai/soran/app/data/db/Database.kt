package ai.soran.app.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/*
 * 로컬 DB (Room) — 오프라인에서도 지연 없이 텍스트·태그를 검색하고, 서버 없이 기기 안에만 저장해요.
 * 목록형 값은 TypeConverter 없이 구분자 문자열로 저장해요 (태그 '|', 좌석·참여자 ',').
 */

@Entity(tableName = "person")
data class PersonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorArgb: Long,
    val isMe: Boolean,
)

@Entity(tableName = "trip")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val startedAt: Long,
    val endedAt: Long?,
    val destination: String?,
    val participantIds: String,
    val saved: Boolean,
    val distanceKm: Double?,
)

@Entity(tableName = "moment", indices = [Index("tripId")])
data class MomentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val createdAt: Long,
    val lat: Double?,
    val lng: Double?,
    val placeLabel: String?,
    val autoTitle: String,
    val customTitle: String?,
    val summary: String,
    val tags: String,
    val audioPath: String?,
    val durationSec: Int,
    // 기록 시점 주행 맥락
    val elapsedSec: Long,
    val distanceKm: Double?,
    val speedKmh: Float?,
    val drivingState: String,
    val outsideTempC: Float?,
    val weather: String?,
    val seats: String,
    val navDestination: String?,
    /** 전사 텍스트 원문 (PII 마스킹 후). 추후 FTS 인덱스용. */
    val searchText: String,
)

@Entity(tableName = "utterance", indices = [Index("momentId")])
data class UtteranceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val momentId: Long,
    val offsetSec: Int,
    val text: String,
    val speakerId: String?,
)

@Entity(tableName = "comment", indices = [Index("momentId")])
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val momentId: Long,
    val authorId: String,
    val createdAt: Long,
    val text: String,
)

@Dao
abstract class SoranDao {
    @Query("SELECT * FROM person") abstract fun people(): Flow<List<PersonEntity>>
    @Query("SELECT * FROM trip ORDER BY startedAt DESC") abstract fun trips(): Flow<List<TripEntity>>
    @Query("SELECT * FROM moment ORDER BY createdAt") abstract fun moments(): Flow<List<MomentEntity>>
    @Query("SELECT * FROM utterance ORDER BY momentId, offsetSec") abstract fun utterances(): Flow<List<UtteranceEntity>>
    @Query("SELECT * FROM comment ORDER BY createdAt") abstract fun comments(): Flow<List<CommentEntity>>

    @Query("SELECT COUNT(*) FROM trip") abstract suspend fun tripCount(): Int
    @Query("SELECT * FROM trip WHERE id = :id") abstract suspend fun trip(id: Long): TripEntity?
    @Query("SELECT * FROM moment WHERE id = :id") abstract suspend fun moment(id: Long): MomentEntity?
    @Query("SELECT * FROM moment WHERE tripId = :tripId") abstract suspend fun momentsOf(tripId: Long): List<MomentEntity>

    @Upsert abstract suspend fun upsertPerson(p: PersonEntity)
    @Insert abstract suspend fun insertTrip(t: TripEntity): Long
    @Upsert abstract suspend fun upsertTrip(t: TripEntity)
    @Insert abstract suspend fun insertMoment(m: MomentEntity): Long
    @Upsert abstract suspend fun upsertMoment(m: MomentEntity)
    @Insert abstract suspend fun insertUtterances(u: List<UtteranceEntity>)
    @Insert abstract suspend fun insertComment(c: CommentEntity): Long

    @Query("DELETE FROM comment WHERE id = :id") abstract suspend fun deleteComment(id: Long)
    @Query("DELETE FROM utterance WHERE momentId = :momentId") abstract suspend fun deleteUtterancesOf(momentId: Long)
    @Query("DELETE FROM comment WHERE momentId = :momentId") abstract suspend fun deleteCommentsOf(momentId: Long)
    @Query("DELETE FROM moment WHERE id = :id") abstract suspend fun deleteMomentRow(id: Long)
    @Query("DELETE FROM trip WHERE id = :id") abstract suspend fun deleteTripRow(id: Long)
    @Query("UPDATE moment SET lat = NULL, lng = NULL") abstract suspend fun clearAllLocations()

    @Transaction
    open suspend fun insertMomentWithLines(m: MomentEntity, lines: List<UtteranceEntity>): Long {
        val id = insertMoment(m)
        insertUtterances(lines.map { it.copy(momentId = id) })
        return id
    }

    @Transaction
    open suspend fun deleteMoment(id: Long) {
        deleteUtterancesOf(id); deleteCommentsOf(id); deleteMomentRow(id)
    }

    @Transaction
    open suspend fun deleteTrip(id: Long) {
        momentsOf(id).forEach { deleteMoment(it.id) }
        deleteTripRow(id)
    }
}

@Database(
    entities = [PersonEntity::class, TripEntity::class, MomentEntity::class, UtteranceEntity::class, CommentEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SoranDatabase : RoomDatabase() {
    abstract fun dao(): SoranDao

    companion object {
        fun create(context: Context) =
            Room.databaseBuilder(context, SoranDatabase::class.java, "soran.db").build()
    }
}
