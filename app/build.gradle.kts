plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "ai.soran.app"
    compileSdk = 35      // Pleos FusedLocation 샘플과 같은 값

    defaultConfig {
        applicationId = "ai.soran.app"
        minSdk = 31          // Pleos Connect = AAOS 기반. 대상 차량의 API 레벨에 맞춰 조정하세요.
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // Pleos Playground > My Project 에서 발급받은 값 (서버 기반 STT/TTS 사용 시 필요)
        // local.properties 또는 CI 환경변수로 주입하는 것을 권장해요.
        buildConfigField("String", "PLEOS_CLIENT_ID", "\"${project.findProperty("pleosClientId") ?: ""}\"")
        buildConfigField("String", "PLEOS_CLIENT_SECRET", "\"${project.findProperty("pleosClientSecret") ?: ""}\"")
    }

    // pleos: Pleos Connect SDK(STT·TTS·NaviHelper)와 연결 — Pleos Connect Emulator / 실차용
    // demo : Pleos SDK 없이 빌드 — 일반 AAOS 에뮬레이터에서 화면·흐름 확인용 (시연용 음성 인식)
    flavorDimensions += "sdk"
    productFlavors {
        create("pleos") { dimension = "sdk" }
        create("demo") {
            dimension = "sdk"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
        }
    }

    // AAOS Car API (CarPropertyManager, CarUxRestrictionsManager)
    useLibrary("android.car")

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Pleos Connect SDK (pleos 플레이버에만) — Pleos Connect Emulator 요구 버전 기준
    "pleosImplementation"("ai.pleos.playground:SpeechToText:3.3.1")
    "pleosImplementation"("ai.pleos.playground:TextToSpeech:3.3.1")
    "pleosImplementation"("ai.pleos.playground:NaviHelper:3.0.5")
    "pleosImplementation"("ai.pleos.playground:FusedLocation:1.1.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.google.zxing:core:3.5.3") // 동승자 참여 QR

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
