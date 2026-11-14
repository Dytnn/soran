package ai.soran.app.core.text

/**
 * 정규식 기반 개인정보 마스킹. 전사 텍스트를 저장하기 전에 적용해요.
 * 순서가 중요해요: 더 긴/구체적인 패턴(주민번호, 카드번호)을 먼저 가려야
 * 전화번호·계좌번호 패턴이 그 일부를 잘못 잡지 않아요.
 */
object PiiMasker {

    enum class Kind(val placeholder: String) {
        RRN("[주민번호]"), CARD("[카드번호]"), PHONE("[전화번호]"), EMAIL("[이메일]"), ACCOUNT("[계좌번호]")
    }

    data class Result(val text: String, val found: Map<Kind, Int>) {
        val masked: Boolean get() = found.isNotEmpty()
    }

    private val SEP = "[-.\\s]?"
    private val PATTERNS: List<Pair<Kind, Regex>> = listOf(
        Kind.RRN to Regex("(?<!\\d)\\d{6}$SEP[1-4]\\d{6}(?!\\d)"),
        Kind.CARD to Regex("(?<!\\d)\\d{4}$SEP\\d{4}$SEP\\d{4}$SEP\\d{4}(?!\\d)"),
        Kind.PHONE to Regex("(?<!\\d)(?:01[016789]|0[2-6][0-9]?|070)$SEP\\d{3,4}$SEP\\d{4}(?!\\d)"),
        Kind.EMAIL to Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
        Kind.ACCOUNT to Regex("(?<!\\d)\\d{2,6}-\\d{2,6}-\\d{2,8}(?:-\\d{1,3})?(?!\\d)"),
    )

    fun mask(input: String): Result {
        var text = input
        val found = linkedMapOf<Kind, Int>()
        for ((kind, re) in PATTERNS) {
            var n = 0
            text = re.replace(text) { n++; kind.placeholder }
            if (n > 0) found[kind] = n
        }
        return Result(text, found)
    }
}
