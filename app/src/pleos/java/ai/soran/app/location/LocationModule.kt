package ai.soran.app.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import ai.soran.app.core.model.GeoPoint
import ai.soran.app.core.model.Moment
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap

/*
 * pleos 플레이버: Pleos FusedLocation SDK 1.1.3
 * (Pleos 공식 샘플 pleos-sample-fusedlocation-app 의 호출 방식과 같아요)
 *
 *   isSupported() → initialize() → registerFusedLocationCallback() / getLastKnownLocation()
 *   addProximityAlert(ProximityAlertRequest, ProximityAlertListener) → onRegistered(alertId) / onTransition(ENTER·EXIT)
 *   removeProximityAlert(alertId) / unregisterFusedLocationCallback() → release()
 *
 * FusedLocation 서비스가 없는 차량·에뮬레이터에서는 표준 LocationManager 로 자동 대체해요.
 */
object LocationModule {
    fun create(context: Context): LocationSource {
        val supported = runCatching { FusedLocation(context.applicationContext).isSupported() }.getOrDefault(false)
        Log.i(TAG, "Pleos FusedLocation supported: $supported")
        return if (supported) PleosFusedLocationSource(context) else AndroidLocationSource(context)
    }
}

private const val TAG = "SoranFusedLocation"
private const val FUSED_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"

class PleosFusedLocationSource(context: Context) : LocationSource {
    private val app = context.applicationContext
    private val fused = FusedLocation(app)
    private var initialized = false
    private var subscribed = false

    override val last = MutableStateFlow<GeoPoint?>(null)
    override val demoBlocked = MutableStateFlow(false)
    override val requiredPermissions = listOf(FUSED_LOCATION_PERMISSION)

    /** momentId → 등록 완료된 alertId (등록 중이면 null) */
    private val alerts = ConcurrentHashMap<Long, String>()
    private val pending = ConcurrentHashMap.newKeySet<Long>()
    /** 알림 문구용: momentId → (제목, 장소) */
    private val labels = ConcurrentHashMap<Long, Pair<String, String>>()

    private val locationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location?.let { last.value = GeoPoint(it.latitude, it.longitude) }
        }
        override fun onError(exception: Exception) { Log.w(TAG, "location error", exception) }
    }

    override fun hasPermission() =
        ContextCompat.checkSelfPermission(app, FUSED_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun ensureInit(): Boolean {
        if (initialized) return true
        if (!hasPermission()) return false
        runCatching { fused.initialize(); initialized = true }.onFailure { Log.w(TAG, "initialize failed", it) }
        return initialized
    }

    override fun start() {
        if (subscribed || !ensureInit()) return
        runCatching {
            fused.getLastKnownLocation(locationListener) // 첫 위치를 빨리 받기 위해
            fused.registerFusedLocationCallback(locationListener)
            subscribed = true
        }.onFailure { Log.w(TAG, "register failed", it) }
    }

    override fun stop() {
        if (subscribed) runCatching { fused.unregisterFusedLocationCallback(locationListener) }
        subscribed = false
    }

    override fun syncProximityAlerts(moments: List<Moment>) {
        if (!ensureInit()) return
        val anchors = Proximity.anchors(moments)
        val wanted = anchors.associateBy { it.id }

        // 지워진 카드의 알림은 해제
        alerts.keys.filter { it !in wanted }.forEach { id ->
            alerts.remove(id)?.let { alertId -> runCatching { fused.removeProximityAlert(alertId) } }
            labels.remove(id)
        }
        // 새 장소 등록 — 카드마다 리스너를 따로 둬서 alertId 와 카드를 정확히 짝지어요.
        anchors.filter { !alerts.containsKey(it.id) && it.id !in pending }.forEach { m -> register(m) }
        anchors.forEach { labels[it.id] = it.title to it.placeText }
    }

    private fun register(m: Moment) {
        val p = m.location ?: return
        pending += m.id
        labels[m.id] = m.title to m.placeText
        val listener = object : ProximityAlertListener {
            override fun onRegistered(alertId: String) {
                pending -= m.id
                alerts[m.id] = alertId
            }
            override fun onTransition(event: ProximityAlertEvent) {
                if (event.transition != ProximityTransition.ENTER) return
                val (title, place) = labels[m.id] ?: return
                RevisitNotifier.notify(app, m.id, title, place)
            }
            override fun onError(exception: Exception) {
                pending -= m.id
                Log.w(TAG, "proximity alert failed for moment ${m.id}", exception)
            }
        }
        runCatching {
            fused.addProximityAlert(
                ProximityAlertRequest(
                    latitude = p.lat,
                    longitude = p.lng,
                    radiusMeters = Proximity.RADIUS_M,
                    transitionTypes = setOf(ProximityTransition.ENTER),
                ),
                listener,
            )
        }.onFailure { pending -= m.id; Log.w(TAG, "addProximityAlert failed", it) }
    }

    fun release() {
        stop()
        alerts.values.forEach { runCatching { fused.removeProximityAlert(it) } }
        alerts.clear()
        if (initialized) runCatching { fused.release() }
        initialized = false
    }
}
