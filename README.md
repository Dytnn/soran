# 소란 (SORAN)

차 안 대화를 “지금 대화 기록해줘” 한마디로 남기고, 위치·주행 맥락이 붙은 **스마트 순간 카드**로 만드는 Pleos Connect (Android Automotive OS) 앱입니다.

---

## 1. 실행 방법

앱을 실제 기기나 에뮬레이터에 배포하기 위해 터미널(또는 안드로이드 스튜디오의 Terminal)에서 아래 명령어를 사용하여 **Release 버전**으로 빌드 및 설치할 수 있습니다.

```bash
# 1) Pleos Connect 환경용 릴리즈 빌드 및 설치 (실제 Pleos SDK STT/TTS/내비 연동)
./gradlew :app:installPleosRelease -PpleosClientId=발급받은ID -PpleosClientSecret=발급받은Secret

# 2) 일반 AAOS 환경용 릴리즈 빌드 및 설치 (Pleos SDK 제외)
./gradlew :app:installDemoRelease
```
* `pleosClientId` 및 `pleosClientSecret`은 [Pleos Playground](https://nexus-playground.pleos.ai)에서 발급받을 수 있습니다. 매번 입력하기 번거롭다면 프로젝트 루트의 `gradle.properties`나 `local.properties`에 명시할 수도 있습니다.
* **참고**: Release 빌드를 정상적으로 기기에 설치하려면 프로젝트의 `build.gradle`에 서명(Keystore) 설정이 되어 있어야 합니다. 서명이 설정되지 않은 경우 `./gradlew :app:assembleRelease` 로 Unsigned APK를 먼저 생성한 후 수동으로 서명하여 설치해야 합니다.

---

## 2. 테스트 환경 및 필요한 설정

### 권한 허용
앱이 정상적으로 동작하려면 최초 실행 시 시스템 및 앱에서 다음 권한들에 대한 허용이 필요합니다.
- **마이크 (`RECORD_AUDIO`)**: 음성 메모 및 대화 기록
- **위치 (`ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`)**: 주행 위치 기록 및 재방문 근접 알림
- **알림 (`POST_NOTIFICATIONS`)**: 특정 위치 재방문 시 알림 수신
- **AAOS 차량 속성 권한**: `CAR_SPEED`(속도), `CAR_POWERTRAIN`(기어 및 주차 브레이크), `CAR_EXTERIOR_ENVIRONMENT`(외기온)
- **Pleos Connect 전용 권한**: `STT_SERVICE`(음성인식), `TTS_SERVICE`(음성합성), `NAVI_ROUTE`(내비 목적지), `FUSED_LOCATION`(정밀 위치)

### 차량 주행 상태 제어 (VHAL)
이 앱은 주행 상태에 따라 안전 UI(DrivingGate)가 작동하여 운전 중 불필요한 화면이 잠깁니다. ADB를 통해 차량 상태를 가상으로 변경하며 테스트할 수 있습니다.

```bash
# 주행 상태로 변경 (안전 UI 작동 - 운전 방해 금지)
adb shell dumpsys activity service com.android.car inject-vhal-event 0x11600207 15      # 속도 15 m/s
adb shell dumpsys activity service com.android.car inject-vhal-event 0x11400400 8       # D단 기어

# 정차 및 주차 상태로 변경 (전체 UI 접근 가능)
adb shell dumpsys activity service com.android.car inject-vhal-event 0x11600207 0       # 속도 0
adb shell dumpsys activity service com.android.car inject-vhal-event 0x11400400 4       # P단 기어
adb shell dumpsys activity service com.android.car inject-vhal-event 0x11200402 true    # 주차 브레이크 체결
```

---

## 3. 개발 에뮬레이터 버전 및 화면 규격

* **Pleos Connect 에뮬레이터 버전**: **Pleos Connect v2.1.0** (API 31 / Android 12 이상 지원 자동차 이미지 기반)
  * 개발 및 완벽한 테스트를 위해 `nexus-playground.pleos.ai`에서 제공하는 **Pleos Connect Emulator v2.1.0** 사용을 권장합니다.
* **화면 규격**: **984 × 608** (가로 모드 / Landscape)
  * 기획 및 디자인 시안(`soran-app-only.html`)의 984×608 픽셀 크기에 맞춰, CSS px 값을 안드로이드 dp/sp로 1:1 변환하여 레이아웃이 구현되어 있습니다.
 
## 4. Github Release 링크

https://github.com/Dytnn/soran/releases/tag/v1.0.0
