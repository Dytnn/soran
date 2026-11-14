package ai.soran.app.export

import ai.soran.app.core.model.Moment
import ai.soran.app.core.model.Trip
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 소장용 스토리 템플릿 이미지(1080×1350) 내보내기 → 사진 보관함 Pictures/Soran.
 * 저장 전에 화면에서 미리보기·확인을 거치도록 UI 에서 호출해요.
 */
object StoryExporter {

    fun render(trip: Trip, moment: Moment): Bitmap {
        val w = 1080; val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#1E293B"))
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFBA85") }
        c.drawRoundRect(RectF(w - 140f, 0f, w - 96f, 90f), 10f, 10f, accent)

        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 255, 255, 255); textSize = 36f }
        val date = SimpleDateFormat("yyyy.MM.dd a h:mm", Locale.KOREA).format(Date(moment.createdAt))
        c.drawText("${trip.name} · $date", 96f, 170f, small)

        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 84f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        var y = 230f
        y = drawBlock(c, moment.title, title, 96f, y, w - 192, 3) + 48f

        val quote = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(235, 255, 255, 255); textSize = 46f }
        val longest = moment.utterances.maxByOrNull { it.text.length }?.text ?: ""
        y = drawBlock(c, "“$longest”", quote, 96f, y, w - 192, 5) + 56f

        val chip = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 36f }
        val chipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 255, 255, 255) }
        var x = 96f
        moment.tags.forEach { t ->
            val tw = chip.measureText(t)
            c.drawRoundRect(RectF(x, y, x + tw + 48, y + 64), 32f, 32f, chipBg)
            c.drawText(t, x + 24, y + 46, chip); x += tw + 64
        }

        val meta = listOfNotNull(moment.placeText, moment.context.weather, moment.context.tripDistanceKm?.let { "출발 후 %.1fkm".format(it) })
        c.drawText(meta.joinToString(" · "), 96f, h - 180f, small)
        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8AB1FF"); textSize = 44f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        c.drawText("소란 SORAN", 96f, h - 100f, brand)
        return bmp
    }

    private fun drawBlock(c: Canvas, text: String, p: TextPaint, x: Float, y: Float, width: Int, maxLines: Int): Float {
        val l = StaticLayout.Builder.obtain(text, 0, text.length, p, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).setMaxLines(maxLines)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        c.save(); c.translate(x, y); l.draw(c); c.restore()
        return y + l.height
    }

    fun saveToGallery(context: Context, bmp: Bitmap, name: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Soran")
        }
        val r = context.contentResolver
        val uri = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        r.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return uri
    }
}
