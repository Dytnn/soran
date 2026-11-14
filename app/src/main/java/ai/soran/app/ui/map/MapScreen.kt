package ai.soran.app.ui.map

import ai.soran.app.core.geo.GeoProjection
import ai.soran.app.core.geo.provinceOf
import ai.soran.app.ui.AppViewModel
import ai.soran.app.ui.Tab
import ai.soran.app.ui.collection.TripDetail
import ai.soran.app.ui.components.BtnSm
import ai.soran.app.ui.components.GlassLevel
import ai.soran.app.ui.components.Grow
import ai.soran.app.ui.components.HLine
import ai.soran.app.ui.components.Ic
import ai.soran.app.ui.components.Txt
import ai.soran.app.ui.components.glass
import ai.soran.app.ui.components.shadowLg
import ai.soran.app.ui.components.tap
import ai.soran.app.ui.theme.Ico
import ai.soran.app.ui.theme.LocalSoran
import ai.soran.app.ui.theme.ts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 지도 탭 (.mapbox): 행정구역을 눌러 그 지역의 기록 핀을 보고, 핀을 누르면 여행 지도(경로·타임라인)로 가요.
 * 시안처럼 시·도 이름·개수는 그리지 않고 색으로만 구분해요.
 */
@Composable
fun MapScreen(vm: AppViewModel) {
    if (vm.tripView?.from == Tab.MAP) { TripDetail(vm); return }
    val c = LocalSoran.current
    val moments by vm.moments.collectAsStateWithLifecycle()
    val trips by vm.trips.collectAsStateWithLifecycle()
    val saved = remember(trips) { trips.filter { it.saved }.map { it.id }.toSet() }
    val provinces = vm.provinces
    val located = moments.filter { it.tripId in saved && it.location != null }
    val noLoc = moments.filter { it.tripId in saved && it.location == null }.sortedByDescending { it.createdAt }
    val provOf = remember(located, provinces) {
        located.associate { m -> m.id to provinces.provinceOf(GeoProjection.x(m.location!!), GeoProjection.y(m.location!!))?.code }
    }
    val pins = located.filter { vm.mapProvince != null && provOf[it.id] == vm.mapProvince }
        .map { MapPin(it.id, GeoProjection.x(it.location!!), GeoProjection.y(it.location!!), it.placeText) }

    Box(Modifier.fillMaxSize()) {
        KoreaMap(
            provinces, pins, Modifier.fillMaxSize(),
            regionMode = true, selectedProvince = vm.mapProvince,
            onProvince = { vm.mapProvince = it },
            onPin = { id -> moments.firstOrNull { it.id == id }?.let { vm.openTrip(it.tripId, it.id) } },
        )
        // .mapnote — 오른쪽 위 안내 + 위치 없는 기록
        Column(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.glass(c, GlassLevel.SECONDARY, 16.dp).padding(horizontal = 14.dp, vertical = 8.dp)) {
                Txt(if (vm.mapProvince != null) "이 지역의 기록 핀만 보여요" else "행정구역을 눌러 기록을 확인해요", ts(14, 400, 20f), c.sub)
            }
            if (noLoc.isNotEmpty() && !vm.noLocPop) BtnSm("위치 없는 기록 ${noLoc.size}개", { vm.noLocPop = true }, icon = Ico.pinOff, height = 40.dp)
        }
        if (vm.noLocPop) Column(
            Modifier.align(Alignment.TopEnd).padding(14.dp).width(320.dp).heightIn(max = 420.dp)
                .shadowLg(16.dp, c).glass(c, GlassLevel.STRONG, 16.dp, shadow = false).padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("위치 없는 기록", ts(17, 600, 24f)); Grow()
                Box(Modifier.size(36.dp).tap { vm.noLocPop = false }, contentAlignment = Alignment.Center) { Ic(Ico.close, 20.dp, c.sub, "닫기") }
            }
            Txt("위치를 받지 못해 지도에 표시되지 않아요", ts(14, 400, 20f), c.sub)
            Column(Modifier.padding(top = 6.dp).verticalScroll(rememberScrollState())) {
                noLoc.forEachIndexed { i, m ->
                    if (i > 0) HLine(Modifier.fillMaxWidth())
                    Column(Modifier.fillMaxWidth().tap { vm.noLocPop = false; vm.openMoment(m.id) }.padding(vertical = 10.dp)) {
                        Txt(SimpleDateFormat("MM.dd HH:mm", Locale.KOREA).format(Date(m.createdAt)), ts(13, 400, 18f), c.sub)
                        Txt(m.title, ts(16, 600, 22f), maxLines = 1)
                    }
                }
            }
        }
    }
}
