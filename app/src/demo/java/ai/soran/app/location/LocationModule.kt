package ai.soran.app.location

import android.content.Context

/** demo 플레이버: Pleos SDK 없이 표준 LocationManager 만 써요. */
object LocationModule {
    fun create(context: Context): LocationSource = AndroidLocationSource(context)
}
