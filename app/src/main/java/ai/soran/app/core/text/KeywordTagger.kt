package ai.soran.app.core.text

/**
 * 경량 키워드 추출 — 외부 LLM 없이 전사 텍스트에서 장소 명사·감탄사·주제어를 찾아
 * #맛집, #휴게소, #웃음 같은 추천 태그를 만들어요. 오프라인에서도 즉시 동작해요.
 */
object KeywordTagger {

    /** 태그 → 텍스트에 포함되면 태그를 붙이는 단서 목록. 순서가 우선순위예요. */
    private val RULES: List<Pair<String, List<String>>> = listOf(
        "웃음" to listOf("ㅋㅋ", "ㅎㅎ", "하하", "히히", "깔깔", "웃겨", "웃기", "웃음", "빵 터", "터졌"),
        "휴게소" to listOf("휴게소", "졸음쉼터"),
        "맛집" to listOf("맛집", "맛있", "먹자", "먹고", "먹어", "점심", "저녁", "아침밥", "커피", "카페",
            "라면", "막국수", "닭갈비", "회 ", "호두과자", "아이스크림", "간식"),
        "바다" to listOf("바다", "해변", "해수욕장", "파도", "수평선", "해안"),
        "야경" to listOf("야경", "불빛", "노을", "별"),
        "음악" to listOf("노래", "플레이리스트", "선곡", "가사", "따라 불러"),
        "여행" to listOf("여행", "일출", "단풍", "드라이브", "전망대", "다음엔", "또 오자"),
    )

    /** "OO해변", "OO휴게소" 처럼 장소를 나타내는 접미사. */
    private val PLACE_SUFFIX = Regex(
        "([가-힣]{1,8}(?:해변|해수욕장|휴게소|전망대|시장|수산시장|카페거리|카페|공원|대교|항|역|터널|타워|사거리|IC))"
    )

    private val STOP_PREFIX = setOf("이", "그", "저", "우리", "이번", "다음", "아까")

    const val FALLBACK = "#일상"

    /** 최대 [limit]개의 태그 (예: ["#웃음", "#맛집"]). 단서가 없으면 #일상. */
    fun tags(text: String, limit: Int = 4): List<String> {
        val t = text.lowercase()
        val out = RULES.filter { (_, cues) -> cues.any { it in t } }.map { "#${it.first}" }
        return out.take(limit).ifEmpty { listOf(FALLBACK) }
    }

    /** 대화 속에서 언급된 장소 이름 후보 (예: "경포 해변" → "경포해변"이 아닌 원문 그대로). */
    fun placeMentions(text: String): List<String> =
        PLACE_SUFFIX.findAll(text)
            .map { it.groupValues[1] }
            .filter { m -> STOP_PREFIX.none { m.startsWith(it) && m.length <= it.length + 3 } }
            .distinct()
            .toList()

    /** 문장의 '이야깃거리' 점수 — 제목으로 쓸 문장을 고를 때 사용해요. */
    fun salience(sentence: String): Int {
        var s = 0
        RULES.forEach { (_, cues) -> if (cues.any { it in sentence }) s += 2 }
        if (PLACE_SUFFIX.containsMatchIn(sentence)) s += 2
        if (sentence.contains('!')) s += 1
        if (sentence.length in 8..30) s += 1
        return s
    }
}
