package ai.soran.app

import ai.soran.app.ui.AppViewModel
import ai.soran.app.ui.DesignFrame
import ai.soran.app.ui.SoranRoot
import ai.soran.app.ui.theme.SoranTheme
import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        (application as SoranApp).container.location.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val location = (application as SoranApp).container.location
        permissions.launch(
            (listOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.POST_NOTIFICATIONS,
                "android.car.permission.CAR_SPEED",
            ) + location.requiredPermissions + ai.soran.app.voice.VoiceModule.permissions).distinct().toTypedArray()
        )
        handle(intent)
        setContent { SoranTheme { DesignFrame { SoranRoot(vm) } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** 재방문 근접 알림에서 들어오면 해당 순간 카드를 열어요 (정차 중일 때만). */
    private fun handle(intent: Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_MOMENT, -1L) ?: -1L
        if (id > 0) vm.openMomentFromNotification(id)
    }

    companion object {
        const val EXTRA_OPEN_MOMENT = "openMoment"
    }
}
