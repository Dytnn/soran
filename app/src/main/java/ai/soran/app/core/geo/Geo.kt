package ai.soran.app.core.geo

import ai.soran.app.core.model.GeoPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** HTML 시안과 같은 평면 투영: x=(lng-127.8)*81, y=(36-lat)*100 (경도 보정한 등각 근사). */
object GeoProjection {
    fun x(p: GeoPoint) = (p.lng - 127.8) * 81.0
    fun y(p: GeoPoint) = (36.0 - p.lat) * 100.0

    fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val t = Math.PI / 180
        val dLat = (b.lat - a.lat) * t
        val dLng = (b.lng - a.lng) * t
        val h = sin(dLat / 2).let { it * it } + cos(a.lat * t) * cos(b.lat * t) * sin(dLng / 2).let { it * it }
        return 2 * r * asin(sqrt(h))
    }
}

/** 평면 좌표 영역 [x, y, w, h]. */
data class ViewBox(val x: Double, val y: Double, val w: Double, val h: Double) {
    val cx get() = x + w / 2
    val cy get() = y + h / 2
    fun contains(px: Double, py: Double) = px >= x && px <= x + w && py >= y && py <= y + h

    /** 화면 비율 [aspect]=W/H 에 맞게 넓혀요. */
    fun fitAspect(aspect: Double): ViewBox {
        var nw = w; var nh = h
        if (nw / nh < aspect) nw = nh * aspect else nh = nw / aspect
        return ViewBox(cx - nw / 2, cy - nh / 2, nw, nh)
    }

    fun zoom(factor: Double, ax: Double = cx, ay: Double = cy) =
        ViewBox(ax - (ax - x) * factor, ay - (ay - y) * factor, w * factor, h * factor)

    fun pan(dx: Double, dy: Double) = copy(x = x + dx, y = y + dy)

    companion object {
        fun around(points: List<Pair<Double, Double>>, pad: Double = 70.0, minSize: Double = 170.0): ViewBox? {
            if (points.isEmpty()) return null
            val x0 = points.minOf { it.first }; val x1 = points.maxOf { it.first }
            val y0 = points.minOf { it.second }; val y1 = points.maxOf { it.second }
            val w = max(x1 - x0 + pad * 2, minSize); val h = max(y1 - y0 + pad * 2, minSize)
            return ViewBox((x0 + x1) / 2 - w / 2, (y0 + y1) / 2 - h / 2, w, h)
        }
    }
}

/**
 * 화면 크기 기준 클러스터링 — 화면에서 [radiusPx] 안에 겹치는 핀을 하나로 묶어요.
 * [unitsPerPx] = 현재 viewBox 너비 / 화면 너비.
 */
object Clusterer {
    data class Item<T>(val key: T, val x: Double, val y: Double)
    data class Cluster<T>(val items: List<Item<T>>, val x: Double, val y: Double) {
        val size get() = items.size
    }

    fun <T> cluster(items: List<Item<T>>, unitsPerPx: Double, radiusPx: Double = 40.0): List<Cluster<T>> {
        val r = radiusPx * unitsPerPx
        val groups = mutableListOf<MutableList<Item<T>>>()
        val centers = mutableListOf<Pair<Double, Double>>()
        for (it in items) {
            val idx = centers.indexOfFirst { c -> hypot(c.first - it.x, c.second - it.y) < r }
            if (idx >= 0) {
                groups[idx].add(it)
                val g = groups[idx]
                centers[idx] = g.sumOf { q -> q.x } / g.size to g.sumOf { q -> q.y } / g.size
            } else {
                groups.add(mutableListOf(it)); centers.add(it.x to it.y)
            }
        }
        return groups.mapIndexed { i, g -> Cluster(g, centers[i].first, centers[i].second) }
    }
}

/** 시·도 경계 (통계청 2018 행정구역 단순화본, HTML 시안의 PROV 데이터). */
data class Province(
    val code: String,
    val name: String,
    val labelX: Double,
    val labelY: Double,
    val colorIndex: Int,
    val rings: List<List<Pair<Double, Double>>>,
) {
    val shortName: String get() = SHORT[code] ?: name
    val isMetro: Boolean get() = code.toIntOrNull()?.let { it < 30 } ?: false
    val bounds: ViewBox by lazy {
        var x0 = Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var x1 = -Double.MAX_VALUE; var y1 = -Double.MAX_VALUE
        rings.forEach { r -> r.forEach { (x, y) -> x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y) } }
        ViewBox(x0, y0, x1 - x0, y1 - y0)
    }

    /** even-odd 규칙으로 점이 경계 안에 있는지. */
    fun contains(px: Double, py: Double): Boolean {
        if (!bounds.contains(px, py)) return false
        var inside = false
        for (r in rings) if (ringContains(r, px, py)) inside = !inside
        return inside
    }

    fun distanceTo(px: Double, py: Double): Double {
        var best = Double.MAX_VALUE
        for (r in rings) for (i in r.indices) best = min(best, segDist(px, py, r[i], r[(i + 1) % r.size]))
        return best
    }

    companion object {
        private val SHORT = mapOf(
            "11" to "서울", "21" to "부산", "22" to "대구", "23" to "인천", "24" to "광주", "25" to "대전",
            "26" to "울산", "29" to "세종", "31" to "경기도", "32" to "강원도", "33" to "충청북도", "34" to "충청남도",
            "35" to "전라북도", "36" to "전라남도", "37" to "경상북도", "38" to "경상남도", "39" to "제주도",
        )

        fun ringContains(r: List<Pair<Double, Double>>, x: Double, y: Double): Boolean {
            var c = false
            var j = r.size - 1
            for (i in r.indices) {
                val (ax, ay) = r[i]; val (bx, by) = r[j]
                if ((ay > y) != (by > y) && x < (bx - ax) * (y - ay) / (by - ay) + ax) c = !c
                j = i
            }
            return c
        }

        private fun segDist(x: Double, y: Double, a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
            val dx = b.first - a.first; val dy = b.second - a.second
            val l = dx * dx + dy * dy
            val t = if (l == 0.0) 0.0 else (((x - a.first) * dx + (y - a.second) * dy) / l).coerceIn(0.0, 1.0)
            return hypot(x - a.first - t * dx, y - a.second - t * dy)
        }
    }
}

/** 기록 좌표가 속한 시·도. 단순화된 해안선 밖이면 가장 가까운 시·도로 보정해요. */
fun List<Province>.provinceOf(x: Double, y: Double): Province? =
    firstOrNull { it.contains(x, y) } ?: minByOrNull { it.distanceTo(x, y) }
