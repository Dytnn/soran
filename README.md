# 소란 SORAN — Android Automotive OS (Pleos Connect)

차 안 대화를 “지금 대화 기록해줘” 한마디로 남기고, 위치·주행 맥락이 붙은 **스마트 순간 카드**로 만드는 Pleos Connect 앱이에요.
HTML 시안(`soran-app-only.html`)의 화면과 기획서 내용을 Kotlin + Jetpack Compose 로 옮겼어요.

---

## 1. 빌드·실행

| 항목 | 값 |
|---|---|
| IDE | Android Studio Ladybug 이상, JDK 17 |
| 에뮬레이터 | **Pleos Connect Emulator** (pleos 플레이버) / 일반 AAOS 에뮬레이터 (demo 플레이버) |
| SDK | compileSdk 35, minSdk 31 |
| Pleos SDK | SpeechToText 3.2.1 · TextToSpeech 3.2.1 · NaviHelper 2.2.7 · FusedLocation 1.1.3 (`nexus-playground.pleos.ai`) |

```bash
# Pleos Connect Emulator 용 (실제 Pleos STT/TTS/내비 연결)
./gradlew :app:installPleosDebug -PpleosClientId=XXXX -PpleosClientSecret=YYYY

# Pleos SDK 없이 화면·흐름만 확인 (예시 대화로 음성 인식을 흉내 냄)
./gradlew :app:installDemoDebug
```

- `pleosClientId/Secret` 은 명령줄 `-P` 대신 `gradle.properties` 에 `pleosClientId=…`, `pleosClientSecret=…` 로 넣어도 돼요. 이 값은 Pleos Playground > My Project 에서 발급받아요. 비워 두면 서버 인식 없이 **온디바이스 인식만** 써요.
- 디버그·demo 빌드는 첫 실행 때 HTML 시안과 같은 예시 여행 5개·순간 카드 15개를 넣고, 왼쪽 위 ‘소란’ 글자를 길게 누르면 **데모 컨트롤**(주행/정차, 위치 수신, 내비 목적지, ‘기록해줘’, QR 스캔 흉내)이 열려요.

### 에뮬레이터에서 주행·정차 바꾸기 (VHAL)
```bash
adb shell dumpsys activity service com.android.car inject-vhal-event 0x11600207 15      # PERF_VEHICLE_SPEED = 15 m/s (주행)
adb shell dumpsys activity service com.android.car inject-vhal-event 0x11400400 8       # GEAR_SELECTION = D
adb shell dumpsys activity service com.android.car inject-vhal-event 0x11600207 0       # 정지
adb shell dumpsys activity service com.android.car inject-vhal-event 0x11400400 4       # GEAR_SELECTION = P → 정차
adb shell dumpsys activity service com.android.car inject-vhal-event 0x11200402 true    # PARKING_BRAKE_ON
```
(속도가 0 보다 크면 CarUxRestrictions 가 ‘주행 중’이 되어 앱이 기록 화면만 남기고 나머지를 잠가요.)

---

## 2. 기획서 → 코드

| 기획서 | 구현 | 파일 |
|---|---|---|
| 음성 명령 “지금 대화 기록해줘” / 앱 내 녹음 버튼 | 여행 중 STT 대기 → 트리거 문구 감지 → 기록. “기록 그만”으로 종료. 화면의 원형 버튼도 같은 동작 | `recording/RecordingController.kt`, `core/text/TriggerPhrase.kt` |
| 동승자 사전 동의 + 음성 안내 | 출발 준비에서 동승자별 ‘안내 확인 → 동의 완료’를 모두 마쳐야 시작. 시작·저장을 TTS 로 안내 | `ui/home/HomeScreen.kt`, `ui/AppViewModel.kt` |
| QR 로 동승자 연결 | 참여 코드 + QR(zxing) 표시, `JoinGateway` 로 휴대폰 동의 결과 수신 | `session/PassengerSession.kt` |
| HYBRID ↔ ON_DEVICE 자동 전환 | Pleos STT `Mode.HYBRID` (통신 음영에서 SDK 가 온디바이스로 전환) | `src/pleos/.../VoiceModule.kt` |
| 주행 소음 정제 · 원본 다시 듣기 | 앱 마이크(VOICE_RECOGNITION 소스)로 16kHz PCM 녹음 → WAV 저장 + `sendAudio()` 로 온디바이스 인식 | `audio/Audio.kt` |
| 핵심 키워드 태깅 (#맛집 #휴게소 #웃음) | 외부 LLM 없이 규칙 기반 태그·장소 언급 추출, 제목·요약 자동 생성 | `core/text/KeywordTagger.kt`, `MomentTitler.kt` |
| PII 마스킹 | 주민번호·카드·전화·이메일·계좌를 저장 전에 가림 | `core/text/PiiMasker.kt` |
| 스마트 순간 카드 (거리·시간·좌석·GPS·날씨) | 기록 시점의 경과 시간, 여행 거리(속도 적분), 속도, 주행 상태, 외기온, 탑승 좌석, 내비 목적지, 위치를 결합 | `recording/RecordingController.kt#buildMoment` |
| 주차 브레이크 체결 시 타임라인 | `PARKING_BRAKE_ON`/P단/UX 제한 해제를 받아 정차 안내 → 지도·수집에서 경로선 + 번호 핀 + 타임라인 | `vehicle/VehicleMonitor.kt`, `ui/collection/CollectionScreen.kt#TripDetail` |
| FusedLocation SDK 정밀 위치 · 재방문 근접 알림 | `registerFusedLocationCallback` 으로 위치 수신, 위치 있는 카드마다(400m 안은 하나로 묶어) `addProximityAlert` → ENTER 시 알림 → 탭하면 그 카드 열기. 서비스가 없는 차량은 표준 `LocationManager` 로 대체 | `src/pleos/.../location/LocationModule.kt`, `location/Location.kt` |
| 주행/정차별 안전 UI | 주행 중: 홈의 ‘기록해줘’ 박스(큰 글자)·버튼만. 지도·수집·마이페이지·카드 상세·재생은 정차 중에만 | `ui/SoranRoot.kt` |
| NaviHelper 경로·목적지 | 출발 준비 화면에서 목적지 조회(변경은 내비에서), 카드 메타데이터로 저장 | `src/pleos/.../VoiceModule.kt` |
| Audio Focus Policy | 재생 시 `AUDIOFOCUS_GAIN_TRANSIENT`, 내비 안내 등으로 포커스를 잃으면 즉시 멈춤 | `audio/Audio.kt#AudioPlayer` |
| 통합 검색·필터 (장소·날씨·동승자·키워드·날짜) | ‘모든 순간’: 제목·대화·장소 검색 + 날짜 기간 + 함께한 사람 + 해시태그 | `ui/collection/CollectionScreen.kt` |
| 로컬 DB | Room (여행·카드·대화 줄·댓글·사람). 모두 차량 안에만 저장 | `data/db/Database.kt` |
| 카드 수동 편집 | 제목·장소 이름 수정, 태그 삭제, 카드 삭제, 댓글 | `ui/moment/MomentDetail.kt` |
| 스토리 템플릿 이미지 내보내기 | 1080×1350 PNG → Pictures/Soran | `export/StoryExporter.kt` |
| 사생활 보호 (동의 관리·데이터 삭제) | 여행 삭제(두 번 눌러 확인), 위치 데이터 일괄 삭제 | `ui/my/MyPageScreen.kt` |

### 구조
```
app/src/main/java/ai/soran/app
├─ core/        순수 Kotlin (모델, 태깅, PII, 트리거, 지도 투영·클러스터)  ← 단위 테스트
├─ data/        Room DB, 저장소, 예시 데이터
├─ voice/       SpeechEngine / Speaker / NavInfo 인터페이스
├─ vehicle/     AAOS Car API (UX 제한, 속도, 기어, 주차 브레이크, 외기온)
├─ location/    LocationSource 인터페이스, 표준 위치 구현, 재방문 알림
├─ audio/       PCM 녹음·WAV·재생(오디오 포커스)
├─ recording/   기록 상태 기계
├─ session/     동승자 QR
├─ export/      스토리 이미지
└─ ui/          Compose 화면 (home, map, collection, moment, my)
app/src/pleos/  Pleos SDK 연결부 (STT·TTS·NaviHelper·FusedLocation) + FUSED_LOCATION 권한
app/src/demo/   SDK 없는 시연 구현
```

---

## 3. 꼭 확인할 것 (이 저장소에서 검증하지 못한 부분)

이 코드는 Android SDK 를 내려받을 수 없는 환경에서 작성했어요. **Android 빌드는 아직 한 번도 돌리지 않았어요.**
`core/` 로직은 실제로 컴파일하고 단위 테스트 18개를 통과시켰고, 나머지 파일은 Kotlin 컴파일러로 문법 오류가 없는지만 확인했어요.
처음 Gradle 동기화·빌드에서 아래 항목을 먼저 보세요.

1. **Pleos SDK 호출부** — `app/src/pleos/java/ai/soran/app/voice/VoiceModule.kt` 한 파일에만 있어요. 공식 문서 예시로 작성했지만 문서 일부가 렌더링되지 않아 아래는 추정이에요.
   - import 경로: `ai.pleos.playground.stt.SpeechToText`, `stt.constant.Mode`, `tts.TextToSpeech`, `tts.constant.Mode`
   - `ResultListener` 콜백 이름·인자 (`onUpdated(stt, completed)` 등)
   - `registerApp(..., OnServerConnectionListener)` 의 실패 콜백 이름 (`onFailed`)
   - `DestinationInfo` 의 목적지 이름 필드 (`poiName`)
   - TextToSpeech 의존성 좌표 (`ai.pleos.playground:TextToSpeech:3.2.1`)
2. **원본 음성 저장 vs HYBRID** — Pleos 문서상 `sendAudio()`(앱이 녹음한 PCM 인식)는 **ON_DEVICE 전용**이에요. 그래서
   - ‘원본 음성 저장’ 켬(기본): 앱 마이크 + 온디바이스 인식 → 다시 듣기 가능, 서버 인식은 안 씀
   - 끔: SDK 마이크 + HYBRID → 터널에서 자동 전환, 원본 음성은 없음 (대화 텍스트만)
   기획서의 “HYBRID 자동 전환”과 “오디오 다시 듣기”를 한 녹음에서 동시에 쓰려면 Pleos 기술 지원에 SDK 마이크 오디오를 앱이 받을 수 있는지 문의가 필요해요.
3. **‘기록해줘’ 상시 대기** — 여행 중(동의 완료 후) STT 를 짧게 반복 호출해 트리거를 찾아요. 차량 마이크 상시 사용이 Pleos 앱 심사 정책에 맞는지, 시스템 음성 비서와 충돌하지 않는지 확인하세요. 마이페이지에서 끌 수 있고, 그때는 화면 버튼으로만 시작해요.
4. **FusedLocation SDK** — Pleos 공식 샘플(`pleos-sample-fusedlocation-app`)과 같은 클래스·메서드로 연결했어요. 확인할 점:
   - 근접 알림은 SDK 리스너로 받기 때문에 **앱 프로세스가 살아 있을 때만** 알림이 떠요. 앱이 꺼진 상태에서도 받으려면 포그라운드 서비스로 옮겨야 해요.
   - 권한 `pleos.car.permission.FUSED_LOCATION` 은 런타임에 요청해요. 거부하면 위치 없이 저장돼요.
   - `isSupported()` 가 false 인 환경에서는 표준 `LocationManager` 로 자동 대체돼요.
   - Pleos 에뮬레이터에서 근접 알림 시험: 샘플 README 처럼 위치를 먼저 받은 뒤 등록하고, 목 위치로 반경 안팎을 옮겨 보세요.
5. **탑승 좌석** — AAOS `SEAT_OCCUPANCY` 는 시스템 권한이 필요해서, 지금은 출발 준비에서 동승자 좌석을 고르는 방식이에요. Pleos Vehicle SDK 의 Seat 도메인(`CAR_SEATS`)을 쓸 수 있으면 그걸로 바꾸세요.
6. **날씨** — 날씨 API 는 없어서 차량 외기온(`ENV_OUTSIDE_TEMPERATURE`)만 붙여요. 날씨 상태가 필요하면 외부 날씨 API 를 `DriveContext.weather` 에 채우세요.
7. **동승자 QR** — QR·참여 코드는 동작하지만, 휴대폰 동반 앱과 세션 중계 서버는 범위 밖이에요. `JoinGateway` 를 구현해 연결하세요. 그 전에는 ‘QR 없이 추가’ + 차량 화면에서 안내 확인·동의를 받아요.
8. **화자 구분** — 자동 화자 분리는 하지 않아요. 직접 기록한 카드의 대화는 화자 없이 ‘대화’로 보이고, 예시 데이터만 화자가 있어요.

## 4. 테스트
```bash
./gradlew :app:testDemoDebugUnitTest    # core 로직: 태깅·PII·트리거·제목·지도 (18개)
```

## 5. 시안(soran-app-only.html)과 맞춘 부분

- 시안을 브라우저에서 화면별로 열어 요소마다 위치·크기·글자 크기·굵기·줄 간격·색·여백을 측정하고, 그 값을 그대로 옮겼어요 (CSS px = dp/sp, 앱 창 984×608 기준).
- 글꼴: Pretendard (앱에 포함, SIL OFL 1.1 — `licenses/Pretendard-OFL.txt`). 아이콘: 시안의 SVG 아이콘을 같은 선 굵기로 옮긴 `ui/theme/Icons.kt`.
- 색·유리 효과: 시안의 토큰(배경·패널·파랑·강조색·Glass/Primary·Secondary·Strong, 그림자)을 `ui/theme/Theme.kt` 에, 그림자(`0 8px 24px`, `0 12px 32px`)는 `setShadowLayer` 로 같은 모양을 그려요.
- 문구: 홈·출발 준비·기록 상태·종료·잠금·지도·수집·순간 카드·마이페이지 문구를 시안 그대로 썼어요.

시안과 다른 부분 (이유)
- **배경 흐림(backdrop blur)**: Compose 는 뒤 화면을 흐리게 하는 효과를 지원하지 않아서, 반투명 흰색 + 테두리 + 그림자만 재현했어요. 배경이 단색이라 차이는 작아요.
- **기록 시작 버튼**: 시안에는 버튼이 없고 음성으로만 시작해요. 앱에서는 파란 ‘기록해줘’ 안내 박스 자체를 누르면 시작·종료돼요 (음성 명령을 끈 경우의 대안).
- **좌석·동의**: 시안처럼 출발 준비 화면에서 뺐어요. 좌석은 참여 순서대로 정하고, 녹음 안내는 여행 시작 때 음성(TTS)으로 알려요. QR 참여자는 휴대폰에서 동의해요.
- **추가 설정 2개**: ‘기록해줘’ 음성 명령, 원본 음성 저장 — 기획서에 있어 시안 스타일의 행으로 넣었어요.
- **주행 정보**: 순간 카드의 날짜 줄에 ‘출발 후 경과·거리’를 덧붙였어요. 이미지 내보내기는 마이페이지 → 데이터 관리 → ‘내 기록 내보내기’로 옮겼어요.
- **시연 컨트롤**: 시안의 화면 밖 데모 바 대신, 왼쪽 위 ‘소란’을 길게 누르면 열려요 (demo·디버그 빌드만).
#   s o r a n  
 #   s o r a n  
 