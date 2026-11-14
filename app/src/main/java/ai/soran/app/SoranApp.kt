package ai.soran.app

import ai.soran.app.audio.AudioPlayer
import ai.soran.app.data.DemoSeeder
import ai.soran.app.data.SoranRepository
import ai.soran.app.data.db.SoranDatabase
import ai.soran.app.location.LocationModule
import ai.soran.app.location.LocationSource
import ai.soran.app.recording.RecordingController
import ai.soran.app.recording.RecordingSettings
import ai.soran.app.session.JoinGateway
import ai.soran.app.session.LocalJoinGateway
import ai.soran.app.vehicle.VehicleMonitor
import ai.soran.app.voice.VoiceModule
import android.app.Application
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SoranApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** 수동 DI. 앱 전체에서 하나씩만 있어야 하는 것들 (차량 연결, 마이크, DB). */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val repo = SoranRepository(SoranDatabase.create(app).dao(), app.filesDir)
    val provinces by lazy { SoranRepository.loadProvinces(app) }
    val voice = VoiceModule.create(app)
    val vehicle = VehicleMonitor(app, scope)
    /** pleos: Pleos FusedLocation SDK (서비스가 없으면 표준 위치로 대체) / demo: 표준 LocationManager */
    val location: LocationSource = LocationModule.create(app)
    val player = AudioPlayer(app)
    val settings = SettingsStore(app)
    val join: JoinGateway = LocalJoinGateway()
    val recorder = RecordingController(scope, voice, vehicle, location, repo).also { it.settings = settings.load() }
    val isDemoBuild: Boolean = BuildConfig.FLAVOR == "demo" || BuildConfig.DEBUG

    init {
        if (isDemoBuild) scope.launch(Dispatchers.IO) { DemoSeeder.seedIfEmpty(repo) }
        else scope.launch(Dispatchers.IO) { DemoSeeder.PEOPLE.take(1).forEach { repo.upsertPerson(it) } }
    }
}

/** 기록 설정 저장 (SharedPreferences). */
class SettingsStore(context: Context) {
    private val p = context.getSharedPreferences("soran_settings", Context.MODE_PRIVATE)

    fun load() = RecordingSettings(
        sttEnabled = p.getBoolean("stt", true),
        saveLocation = p.getBoolean("loc", true),
        voiceGuide = p.getBoolean("tts", true),
        voiceCommand = p.getBoolean("cmd", true),
        keepAudio = p.getBoolean("audio", true),
    )

    fun save(s: RecordingSettings) = p.edit()
        .putBoolean("stt", s.sttEnabled).putBoolean("loc", s.saveLocation).putBoolean("tts", s.voiceGuide)
        .putBoolean("cmd", s.voiceCommand).putBoolean("audio", s.keepAudio).apply()
}
