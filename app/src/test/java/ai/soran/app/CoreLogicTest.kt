package ai.soran.app

import ai.soran.app.core.geo.Clusterer
import ai.soran.app.core.geo.GeoProjection
import ai.soran.app.core.geo.Province
import ai.soran.app.core.geo.ViewBox
import ai.soran.app.core.model.GeoPoint
import ai.soran.app.core.model.Utterance
import ai.soran.app.core.text.KeywordTagger
import ai.soran.app.core.text.MomentTitler
import ai.soran.app.core.text.PiiMasker
import ai.soran.app.core.text.TriggerPhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreLogicTest {

    // ---- 키워드 태깅 ----
    @Test fun tagsFoodAndRestStop() {
        val t = KeywordTagger.tags("다들 안 졸려? 휴게소 잠깐 들를까? 호두과자 내기 하자.")
        assertTrue(t.contains("#휴게소"))
        assertTrue(t.contains("#맛집"))
    }

    @Test fun tagsLaughAndSea() {
        val t = KeywordTagger.tags("하하하 바다다! 창문 열어도 돼?")
        assertEquals(listOf("#웃음", "#바다"), t.take(2))
    }

    @Test fun fallbackTagIsDaily() {
        assertEquals(listOf("#일상"), KeywordTagger.tags("응 알겠어"))
    }

    @Test fun findsPlaceMentions() {
        val p = KeywordTagger.placeMentions("이따 경포해변 들렀다가 주문진수산시장 가자")
        assertTrue(p.contains("경포해변"))
        assertTrue(p.any { it.endsWith("수산시장") })
    }

    // ---- 개인정보 마스킹 ----
    @Test fun masksPhoneNumbers() {
        val r = PiiMasker.mask("내 번호 010-1234-5678 이고 집은 02 345 6789 야")
        assertEquals("내 번호 [전화번호] 이고 집은 [전화번호] 야", r.text)
        assertEquals(2, r.found[PiiMasker.Kind.PHONE])
    }

    @Test fun masksRrnBeforePhone() {
        val r = PiiMasker.mask("주민번호 900101-1234567")
        assertEquals("주민번호 [주민번호]", r.text)
        assertNull(r.found[PiiMasker.Kind.PHONE])
    }

    @Test fun masksCardAndEmail() {
        val r = PiiMasker.mask("카드 1234 5678 9012 3456, 메일 a.b@soran.ai")
        assertEquals("카드 [카드번호], 메일 [이메일]", r.text)
    }

    @Test fun keepsOrdinaryNumbers() {
        val r = PiiMasker.mask("7시 11분! 내가 이겼지? 아메리카노 3잔")
        assertFalse(r.masked)
    }

    // ---- 음성 명령 ----
    @Test fun detectsStartWithCarryOver() {
        val c = TriggerPhrase.detect("지금 대화 기록해 줘, 저 단풍 봐!")
        assertTrue(c is TriggerPhrase.Command.Start)
        assertEquals("저 단풍 봐!", (c as TriggerPhrase.Command.Start).carryOver)
    }

    @Test fun detectsAlternateStart() {
        assertTrue(TriggerPhrase.detect("이거 남겨줘") is TriggerPhrase.Command.Start)
    }

    @Test fun detectsStop() {
        assertEquals(TriggerPhrase.Command.Stop, TriggerPhrase.detect("기록 그만"))
    }

    @Test fun ignoresNormalTalk() {
        assertNull(TriggerPhrase.detect("어제 기록은 몇 등이었어?"))
    }

    // ---- 제목·요약 ----
    @Test fun titlePicksSalientSentence() {
        val u = listOf(Utterance(0, "응"), Utterance(5, "바다다! 창문 열어도 돼?"), Utterance(9, "추우니까 딱 10초만."))
        assertEquals("바다다! 창문 열어도 돼", MomentTitler.title(u))
    }

    @Test fun titleIsTruncated() {
        val t = MomentTitler.title(listOf(Utterance(0, "올해 마신 커피 중에 이게 진짜 제일 맛있는 커피라고 생각해")))
        assertTrue(t.endsWith("…"))
        assertTrue(t.length <= 21)
    }

    // ---- 지도 ----
    @Test fun haversineSeoulBusan() {
        val d = GeoProjection.haversineKm(GeoPoint(37.5665, 126.978), GeoPoint(35.1796, 129.0756))
        assertTrue(d in 320.0..330.0)
    }

    @Test fun clustersNearbyPoints() {
        val items = listOf(
            Clusterer.Item("a", 0.0, 0.0), Clusterer.Item("b", 5.0, 5.0), Clusterer.Item("c", 300.0, 300.0),
        )
        val c = Clusterer.cluster(items, unitsPerPx = 1.0, radiusPx = 40.0)
        assertEquals(2, c.size)
        assertEquals(2, c.first { it.size == 2 }.size)
    }

    @Test fun provinceHitTest() {
        val square = Province("99", "테스트", 0.0, 0.0, 0, listOf(listOf(0.0 to 0.0, 10.0 to 0.0, 10.0 to 10.0, 0.0 to 10.0)))
        assertTrue(square.contains(5.0, 5.0))
        assertFalse(square.contains(15.0, 5.0))
    }

    @Test fun viewBoxFitsAspect() {
        val v = ViewBox(0.0, 0.0, 100.0, 100.0).fitAspect(2.0)
        assertEquals(200.0, v.w, 1e-9)
        assertEquals(100.0, v.h, 1e-9)
    }
}
