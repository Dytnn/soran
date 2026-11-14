package ai.soran.app.core.text

import ai.soran.app.core.model.Utterance

/**
 * LLM 없이 제목·한 줄 요약을 만드는 규칙 기반 정리기.
 * 사용자는 정차 후 순간 카드에서 제목을 직접 고칠 수 있어요.
 */
object MomentTitler {

    private const val MAX_TITLE = 20

    fun title(utterances: List<Utterance>): String {
        val sentences = utterances.map { it.text.trim() }.filter { it.isNotEmpty() }
        if (sentences.isEmpty()) return "음성 메모"
        val best = sentences.withIndex()
            .maxWith(compareBy<IndexedValue<String>>({ KeywordTagger.salience(it.value) }, { -it.index }))
            .value
        val clean = best.trimEnd('?', '!', '.', '~', ' ')
        return if (clean.length > MAX_TITLE) clean.take(MAX_TITLE).trimEnd() + "…" else clean
    }

    fun summary(utterances: List<Utterance>, place: String?, tags: List<String>): String {
        val n = utterances.size
        val where = place?.let { "$it 에서 " } ?: ""
        val about = tags.filter { it != KeywordTagger.FALLBACK }.take(2).joinToString(" ") { it.removePrefix("#") }
        return buildString {
            append(where)
            append(if (about.isNotEmpty()) "$about 이야기를 나눴어요" else "나눈 대화예요")
            append(" · ${n}문장")
        }
    }
}
