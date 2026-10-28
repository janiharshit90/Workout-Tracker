package com.example.mentzertracker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import com.example.mentzertracker.R
import java.io.File

/**
 * Draws a workout summary as a 1080-px-wide image in the app's "Iron" look, for sharing to a chat
 * or a story. Plain android.graphics so it works off the UI thread and needs no screenshot tricks.
 */
object ShareCard {
    private const val W = 1080
    private const val PAD = 72f
    private const val ROW_H = 168f
    private const val MAX_ROWS = 9

    private const val INK = 0xFF0A0A0A.toInt()
    private const val PANEL = 0xFF151515.toInt()
    private const val LINE = 0xFF2A2A2A.toInt()
    private const val TEXT = 0xFFF5F5F5.toInt()
    private const val MUTED = 0xFFA6A6A6.toInt()
    private const val LIME = 0xFFD7FF3F.toInt()

    /** Renders [summary] and writes it to the app's share cache; returns the PNG file. */
    fun writePng(ctx: Context, summary: WorkoutSummary, unit: WeightUnit): File {
        val bmp = render(ctx, summary, unit)
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val stamp = formatDate(if (summary.date > 0) summary.date else System.currentTimeMillis(), "yyyyMMdd_HHmm")
        val file = File(dir, "workout_$stamp.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    fun render(ctx: Context, summary: WorkoutSummary, unit: WeightUnit): Bitmap {
        val bold = font(ctx, R.font.barlow_condensed_bold, Typeface.DEFAULT_BOLD)
        val semi = font(ctx, R.font.barlow_condensed_semibold, Typeface.DEFAULT_BOLD)
        val rows = summary.exercises.take(MAX_ROWS)
        val extra = summary.exercises.size - rows.size

        val height = (PAD + 50 + 150 + 60 + 48 + 176 + 48 + rows.size * (ROW_H + 16) +
            (if (extra > 0) 60 else 0) + 40 + 60 + PAD).toInt()
        val bmp = Bitmap.createBitmap(W, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(INK)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val right = W - PAD
        var y = PAD

        // Accent bar + label
        p.color = LIME
        c.drawRect(PAD, y, PAD + 96f, y + 10f, p)
        y += 58f
        text(c, p, "WORKOUT COMPLETE", PAD, y, 36f, LIME, semi, spacing = 0.18f)

        // Workout name, shrunk to fit one line
        y += 140f
        val name = summary.name.uppercase()
        var size = 128f
        p.typeface = bold
        p.letterSpacing = 0f
        p.textSize = size
        while (p.measureText(name) > right - PAD && size > 64f) { size -= 4f; p.textSize = size }
        text(c, p, ellipsize(p, name, right - PAD), PAD, y, size, TEXT, bold)

        y += 58f
        val date = if (summary.date > 0) summary.date else System.currentTimeMillis()
        text(c, p, formatDate(date, "EEEE, MMM d yyyy").uppercase(), PAD, y, 36f, MUTED, semi, spacing = 0.08f)

        // Stat blocks
        y += 48f
        val stats = listOf(
            "TIME" to (if (summary.durationSec > 0) formatDuration(summary.durationSec) else "—"),
            "EXERCISES" to "${summary.exercises.size}",
            "SETS" to "${summary.workingSets}",
            "PRS" to "${summary.prCount}"
        )
        val gap = 16f
        val bw = (right - PAD - gap * (stats.size - 1)) / stats.size
        stats.forEachIndexed { i, (label, value) ->
            val x = PAD + i * (bw + gap)
            box(c, p, RectF(x, y, x + bw, y + 176f), PANEL, LINE)
            val highlight = label == "PRS" && summary.prCount > 0
            var vs = 76f
            p.typeface = bold; p.letterSpacing = 0f; p.textSize = vs
            while (p.measureText(value) > bw - 40f && vs > 40f) { vs -= 4f; p.textSize = vs }
            text(c, p, value, x + 24f, y + 100f, vs, if (highlight) LIME else TEXT, bold)
            text(c, p, label, x + 24f, y + 146f, 28f, MUTED, semi, spacing = 0.12f)
        }
        y += 176f + 48f

        // One row per exercise
        rows.forEach { ex ->
            val r = RectF(PAD, y, right, y + ROW_H)
            box(c, p, r, PANEL, if (ex.isPr) LIME else LINE)
            val inner = right - 28f
            var nameRight = inner
            if (ex.isPr) {
                val badge = "NEW PR"
                p.typeface = bold; p.textSize = 32f; p.letterSpacing = 0.1f
                val bwid = p.measureText(badge) + 32f
                p.color = LIME
                c.drawRect(inner - bwid, y + 26f, inner, y + 72f, p)
                text(c, p, badge, inner - bwid + 16f, y + 62f, 32f, INK, bold, spacing = 0.1f)
                nameRight = inner - bwid - 20f
            }
            p.typeface = semi; p.textSize = 44f; p.letterSpacing = 0f
            text(c, p, ellipsize(p, ex.name, nameRight - (PAD + 28f)), PAD + 28f, y + 64f, 44f, TEXT, semi)

            val main = ex.top?.let { "${formatWeight(lbsToDisplay(it.weight, unit))} ${unit.label} × ${it.reps}" }
                ?: "Warm-up only"
            text(c, p, main, PAD + 28f, y + 136f, 60f, if (ex.isPr) LIME else TEXT, bold)

            val beyond = ex.sets.firstOrNull { !it.isWarmup && !it.beyond.isEmpty }?.beyond?.describe()
            val side = listOfNotNull(
                ex.change?.takeIf { it.kind != ChangeKind.FIRST }?.describe(unit)?.uppercase(),
                beyond?.uppercase()
            ).joinToString("  ·  ")
            if (side.isNotEmpty()) {
                p.typeface = semi; p.textSize = 30f; p.letterSpacing = 0.06f
                val mainW = run { p.typeface = bold; p.textSize = 60f; p.letterSpacing = 0f; p.measureText(main) }
                p.typeface = semi; p.textSize = 30f; p.letterSpacing = 0.06f
                val room = inner - (PAD + 28f + mainW + 32f)
                if (room > 80f) {
                    val t = ellipsize(p, side, room)
                    val up = ex.change?.kind == ChangeKind.WEIGHT_UP || ex.change?.kind == ChangeKind.REPS_UP
                    text(c, p, t, inner - p.measureText(t), y + 130f, 30f, if (up) LIME else MUTED, semi, spacing = 0.06f)
                }
            }
            y += ROW_H + 16f
        }
        if (extra > 0) {
            text(c, p, "+ $extra more exercise${if (extra == 1) "" else "s"}", PAD, y + 36f, 34f, MUTED, semi)
            y += 60f
        }

        // Footer
        y += 40f
        p.color = LINE
        c.drawRect(PAD, y, right, y + 2f, p)
        text(c, p, "HEAVY DUTY · ONE SET TO FAILURE", PAD, y + 56f, 30f, MUTED, semi, spacing = 0.16f)
        return bmp
    }

    private fun font(ctx: Context, id: Int, fallback: Typeface): Typeface =
        try { ResourcesCompat.getFont(ctx, id) ?: fallback } catch (_: Exception) { fallback }

    private fun text(
        c: Canvas, p: Paint, s: String, x: Float, y: Float, size: Float, color: Int, face: Typeface,
        spacing: Float = 0f
    ) {
        p.style = Paint.Style.FILL
        p.color = color
        p.typeface = face
        p.textSize = size
        p.letterSpacing = spacing
        c.drawText(s, x, y, p)
    }

    private fun box(c: Canvas, p: Paint, r: RectF, fill: Int, stroke: Int) {
        p.style = Paint.Style.FILL
        p.color = fill
        c.drawRoundRect(r, 8f, 8f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3f
        p.color = stroke
        c.drawRoundRect(r, 8f, 8f, p)
        p.style = Paint.Style.FILL
    }

    /** Cuts [s] with an ellipsis so it fits [maxWidth] at the paint's current size. */
    private fun ellipsize(p: Paint, s: String, maxWidth: Float): String {
        if (p.measureText(s) <= maxWidth) return s
        var end = s.length
        while (end > 1 && p.measureText(s.substring(0, end) + "…") > maxWidth) end--
        return s.substring(0, end).trimEnd() + "…"
    }
}
