package ai.soran.app.location

import ai.soran.app.MainActivity
import ai.soran.app.R
import ai.soran.app.core.geo.GeoProjection
import ai.soran.app.core.model.GeoPoint
import ai.soran.app.core.model.Moment
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 차량 위치 + 재방문 근접 알림.
 *  - pleos 플레이버: Pleos FusedLocation SDK (src/pleos/.../location/LocationModule.kt)
 *  - demo 플레이버, 또는 FusedLocation 서비스가 없는 차량: 표준 LocationManager ([AndroidLocationSource])
 */
interface LocationSource {
    val last: StateFlow<GeoPoint?>
    /** 데모 컨트롤 '위치 수신 불가' — true 면 위치 없이 저장돼요. */
    val demoBlocked: MutableStateFlow<Boolean>
    /** MainActivity 가 요청할 런타임 권한 */
    val requiredPermissions: List<String>
    fun hasPermission(): Boolean
    fun start()
    fun stop()
    /** 위치가 있는 순간 카드마다 반경 안에 다시 들어오면 알림을 띄우도록 등록해요. */
    fun syncProximityAlerts(moments: List<Moment>)

    /** 지금 저장할 위치. 수신 불가·권한 없음이면 null → '위치 없이 저장'. */
    fun current(): GeoPoint? = if (demoBlocked.value) null else last.value
}

object Proximity {
    const val RADIUS_M = 400f

    /** 같은 장소(반경 안)에 여러 카드가 있으면 가장 먼저 남긴 카드 하나만 알림 대상으로 골라요. */
    fun anchors(moments: List<Moment>, radiusM: Float = RADIUS_M): List<Moment> {
        val out = mutableListOf<Moment>()
        moments.filter { it.location != null }.sortedBy { it.createdAt }.forEach { m ->
            if (out.none { GeoProjection.haversineKm(it.location!!, m.location!!) * 1000 < radiusM }) out += m
        }
        return out
    }
}

/** 재방문 알림 표시 (두 구현이 함께 써요). 탭하면 그 순간 카드를 열어요. */
object RevisitNotifier {
    private const val CHANNEL = "revisit"

    fun notify(context: Context, momentId: Long, title: String, place: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_revisit), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_revisit_desc) }
        )
        val open = PendingIntent.getActivity(
            context, momentId.toInt(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_MOMENT, momentId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("$place 근처예요")
            .setContentText("그때 나눈 이야기: $title")
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(momentId.toInt(), n) } // POST_NOTIFICATIONS 미허용 시 조용히 무시
    }
}

/** 표준 Android 위치 (FUSED_PROVIDER → GPS → NETWORK) + LocationManager.addProximityAlert. */
class AndroidLocationSource(private val context: Context) : LocationSource {

    private val lm = context.getSystemService(LocationManager::class.java)
    override val last = MutableStateFlow<GeoPoint?>(null)
    override val demoBlocked = MutableStateFlow(false)
    override val requiredPermissions = listOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    private val listener = LocationListener { loc: Location -> last.value = GeoPoint(loc.latitude, loc.longitude) }
    private var started = false

    override fun hasPermission() =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override fun start() {
        if (started || !hasPermission()) return
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return
        runCatching {
            lm.getLastKnownLocation(provider)?.let { last.value = GeoPoint(it.latitude, it.longitude) }
            lm.requestLocationUpdates(provider, 2_000L, 5f, listener, Looper.getMainLooper())
            started = true
        }.onFailure { Log.w("SoranLocation", "location updates failed", it) }
    }

    override fun stop() { if (started) lm.removeUpdates(listener); started = false }

    @SuppressLint("MissingPermission")
    override fun syncProximityAlerts(moments: List<Moment>) {
        if (!hasPermission()) return
        Proximity.anchors(moments).forEach { m ->
            val p = m.location!!
            val pi = PendingIntent.getBroadcast(
                context, m.id.toInt(),
                Intent(context, ProximityReceiver::class.java).putExtra(EXTRA_MOMENT_ID, m.id)
                    .putExtra(EXTRA_TITLE, m.title).putExtra(EXTRA_PLACE, m.placeText),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE, // 진입 여부 extra 를 시스템이 채워요
            )
            runCatching { lm.addProximityAlert(p.lat, p.lng, Proximity.RADIUS_M, -1L, pi) }
        }
    }

    companion object {
        const val EXTRA_MOMENT_ID = "momentId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_PLACE = "place"
    }
}

/** LocationManager 근접 알림 수신 (AndroidLocationSource 전용). */
class ProximityReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)) return
        val id = intent.getLongExtra(AndroidLocationSource.EXTRA_MOMENT_ID, -1)
        val title = intent.getStringExtra(AndroidLocationSource.EXTRA_TITLE) ?: return
        RevisitNotifier.notify(context, id, title, intent.getStringExtra(AndroidLocationSource.EXTRA_PLACE) ?: "")
    }
}
