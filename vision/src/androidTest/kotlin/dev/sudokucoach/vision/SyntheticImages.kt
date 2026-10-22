package dev.sudokucoach.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/** Imágenes dibujadas con Canvas para probar ML Kit sin fotos reales. */
object SyntheticImages {

    private fun digitPaint(size: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = size
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) // como los impresos del libro
        textAlign = Paint.Align.CENTER
    }

    /** Un dígito centrado en un cuadrado blanco de [side] px. */
    fun digit(d: Int, side: Int = 50): Bitmap {
        val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val paint = digitPaint(side * 0.75f)
        val y = side / 2f - (paint.descent() + paint.ascent()) / 2
        Canvas(bitmap).drawText("$d", side / 2f, y, paint)
        return bitmap
    }

    /** Tablero rectificado de 450×450 con rejilla y los dígitos de [digits] (0 = vacía). */
    fun board(digits: IntArray): Bitmap {
        val bitmap = Bitmap.createBitmap(450, 450, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(235, 235, 235)) }
        val canvas = Canvas(bitmap)
        val line = Paint().apply { color = Color.rgb(30, 30, 30) }
        for (i in 0..9) {
            line.strokeWidth = if (i % 3 == 0) 4f else 1.5f
            val p = (i * 50f).coerceAtMost(449f)
            canvas.drawLine(p, 0f, p, 450f, line)
            canvas.drawLine(0f, p, 450f, p, line)
        }
        val paint = digitPaint(36f)
        for (i in 0 until 81) {
            if (digits[i] == 0) continue
            val cx = (i % 9) * 50f + 25f
            val cy = (i / 9) * 50f + 25f - (paint.descent() + paint.ascent()) / 2
            canvas.drawText("${digits[i]}", cx, cy, paint)
        }
        return bitmap
    }
}
