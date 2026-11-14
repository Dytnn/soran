package ai.soran.app.core.text

/**
 * 음성 명령 트리거 판별.
 * "지금 대화 기록해줘", "기록해 줘", "남겨줘" → 기록 시작
 * "기록 그만", "그만 기록해" → 기록 종료
 * STT 결과는 띄어쓰기·문장부호가 들쭉날쭉하므로 정규화한 뒤 비교해요.
 */
object TriggerPhrase {

    sealed interface Command {
        /** [carryOver] = 같은 발화 안에서 트리거 뒤에 이어진 말 (기록에 포함). */
        data class Start(val carryOver: String) : Command
        data object Stop : Command
    }

    private val START = listOf("대화기록해줘", "기록해줘", "기록해주세요", "기록해죠", "기록해조", "기록해쥬", "기록하자", "기록시작", "대화남겨줘", "남겨줘", "녹음해줘")
    private val STOP = listOf("기록그만", "그만기록", "기록끝", "기록종료", "녹음그만")

    private val IGNORED = Regex("[\\s.,!?~·'\"“”‘’]")

    fun detect(utterance: String): Command? {
        // 정규화 문자열의 각 글자가 원문의 몇 번째 글자였는지 기억해 둬요.
        val map = ArrayList<Int>(utterance.length)
        val sb = StringBuilder()
        utterance.forEachIndexed { i, c -> if (!IGNORED.matches(c.toString())) { sb.append(c); map.add(i) } }
        val n = sb.toString()
        if (STOP.any { it in n }) return Command.Stop
        val (pos, hit) = START.asSequence()
            .map { n.indexOf(it) to it }
            .firstOrNull { it.first >= 0 } ?: return null
        val endInOriginal = map[pos + hit.length - 1] + 1
        val carry = utterance.substring(endInOriginal).trimStart(' ', ',', '.', '!', '?', '~')
        return Command.Start(carryOver = carry)
    }
}
