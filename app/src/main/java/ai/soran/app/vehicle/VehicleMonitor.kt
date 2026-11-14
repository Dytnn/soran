package ai.soran.app.vehicle

import ai.soran.app.core.model.DrivingState
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** 앱이 쓰는 차량 상태 요약. */
data class VehicleState(
    /** 주행 중 화면 조작 제한 (CarUxRestrictions.isRequiresDistractionOptimization). */
    val restricted: Boolean = false,
    val drivingState: DrivingState = DrivingState.UNKNOWN,
    val speedKmh: Float? = null,
    val parkingBrakeOn: Boolean? = null,
    val outsideTempC: Float? = null,
    /** 앱 실행 후 누적 주행 거리 (속도 적분). 여행 거리 = 종료값 − 시작값. */
    val odometerKm: Double = 0.0,
    val carConnected: Boolean = false,
    val simulated: Boolean = false,
)

/**
 * AAOS 표준 Car API 로 주행 맥락을 모아요.
 * Pleos SDK 정책(AAOS Alignment)상 속도·기어·주차 브레이크·외기온 같은 표준 데이터는 AAOS Car API 를 써요.
 *
 *  - CarUxRestrictionsManager : 주행 중 UI 제한 여부 → 주행/정차 화면 전환
 *  - PERF_VEHICLE_SPEED       : 속도 (m/s → km/h), 여행 거리 적분
 *  - GEAR_SELECTION           : P 단 여부
 *  - PARKING_BRAKE_ON         : 주차 브레이크 체결 → 정차 후 타임라인 완성
 *  - ENV_OUTSIDE_TEMPERATURE  : 카드에 붙는 외기온
 */
class VehicleMonitor(context: Context, scope: CoroutineScope) {

    private val raw = MutableStateFlow(VehicleState())
    /** 데모 컨트롤: null = 실제 차량 값, true = 주행 중, false = 정차 중 */
    val demoDriving = MutableStateFlow<Boolean?>(null)

    val state: StateFlow<VehicleState> = combine(raw, demoDriving) { r, demo ->
        when (demo) {
            null -> r
            true -> r.copy(restricted = true, drivingState = DrivingState.MOVING, speedKmh = r.speedKmh ?: 50f,
                parkingBrakeOn = false, simulated = true)
            false -> r.copy(restricted = false, drivingState = DrivingState.PARKED, speedKmh = 0f,
                parkingBrakeOn = true, simulated = true)
        }
    }.stateIn(scope, SharingStarted.Eagerly, VehicleState())

    private var car: Car? = null
    private var gearPark: Boolean? = null
    private var lastSpeedTs = 0L

    init {
        runCatching {
            car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (ready) onCarReady(c) else raw.value = raw.value.copy(carConnected = false)
            }
        }.onFailure { Log.w(TAG, "Car service unavailable — demo controls only", it) }
    }

    private fun onCarReady(c: Car) {
        raw.value = raw.value.copy(carConnected = true)
        val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        ux?.let { m ->
            onUx(m.currentCarUxRestrictions)
            m.registerListener { r -> onUx(r) }
        }
        val pm = c.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager ?: return
        subscribe(pm, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
        subscribe(pm, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        subscribe(pm, VehiclePropertyIds.PARKING_BRAKE_ON, CarPropertyManager.SENSOR_RATE_ONCHANGE)
        subscribe(pm, VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE, CarPropertyManager.SENSOR_RATE_ONCHANGE)
    }

    private fun onUx(r: CarUxRestrictions?) {
        raw.value = raw.value.copy(restricted = r?.isRequiresDistractionOptimization ?: false)
        recompute()
    }

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(v: CarPropertyValue<*>) {
            when (v.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                    val kmh = ((v.value as? Float) ?: 0f) * 3.6f
                    val ts = v.timestamp // ns
                    val add = if (lastSpeedTs > 0) kmh * ((ts - lastSpeedTs) / 3.6e12) else 0.0
                    lastSpeedTs = ts
                    raw.value = raw.value.copy(speedKmh = kmh, odometerKm = raw.value.odometerKm + add.coerceAtLeast(0.0))
                }
                VehiclePropertyIds.GEAR_SELECTION -> gearPark = (v.value as? Int) == GEAR_PARK
                VehiclePropertyIds.PARKING_BRAKE_ON -> raw.value = raw.value.copy(parkingBrakeOn = v.value as? Boolean)
                VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE -> raw.value = raw.value.copy(outsideTempC = v.value as? Float)
            }
            recompute()
        }
        override fun onErrorEvent(propertyId: Int, zone: Int) {
            Log.w(TAG, "property error ${VehiclePropertyIds.toString(propertyId)}")
        }
    }

    private fun subscribe(pm: CarPropertyManager, id: Int, rate: Float) {
        try {
            if (!pm.registerCallback(callback, id, rate)) Log.i(TAG, "not supported: ${VehiclePropertyIds.toString(id)}")
        } catch (e: SecurityException) {
            Log.w(TAG, "no permission for ${VehiclePropertyIds.toString(id)}")
        } catch (e: IllegalArgumentException) {
            Log.i(TAG, "unavailable: ${VehiclePropertyIds.toString(id)}")
        }
    }

    private fun recompute() {
        val r = raw.value
        val parked = r.parkingBrakeOn == true || gearPark == true
        val moving = (r.speedKmh ?: 0f) > 1f
        val ds = when {
            moving -> DrivingState.MOVING
            parked -> DrivingState.PARKED
            r.speedKmh != null || gearPark != null -> DrivingState.IDLING
            r.restricted -> DrivingState.MOVING
            else -> DrivingState.UNKNOWN
        }
        raw.value = r.copy(drivingState = ds)
    }

    fun release() {
        runCatching { (car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager)?.unregisterCallback(callback) }
        runCatching { (car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager)?.unregisterListener() }
        car?.disconnect()
    }

    private companion object {
        const val TAG = "SoranVehicle"
        const val GEAR_PARK = 0x0004 // android.car.VehicleGear.GEAR_PARK
    }
}
