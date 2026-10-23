package dev.sudokucoach.app.scan

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Abre una foto de la galería como la esperan el detector y el segmentador (tarea 3.3): derecha
 * según su orientación EXIF y con el lado mayor reducido a [LONG_SIDE] px, como `prepare_photos.py`
 * hizo con las fotos de prueba, porque los umbrales de `vision-core` se ajustaron a ese tamaño.
 * Las fotos más pequeñas no se amplían.
 */
object PhotoDecoder {
    const val LONG_SIDE = 1600

    /** [longSide] más pequeño sirve para mostrar la foto mientras se lee (tarea 3.5). */
    fun decode(resolver: ContentResolver, uri: Uri, longSide: Int = LONG_SIDE): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeWithImageDecoder(resolver, uri, longSide) else decodeLegacy(resolver, uri, longSide)

    /** API ≥ 28: `ImageDecoder` aplica la orientación EXIF y reduce al decodificar. */
    @RequiresApi(Build.VERSION_CODES.P)
    internal fun decodeWithImageDecoder(resolver: ContentResolver, uri: Uri, longSide: Int = LONG_SIDE): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
            // Bitmap en memoria normal: OpenCV no puede leer los de hardware, que son el valor por defecto.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = longSide.toDouble() / max(info.size.width, info.size.height)
            if (scale < 1) decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
        }

    /**
     * API 26–27: `BitmapFactory` con submuestreo (para no cargar 12 MP en memoria), giro según el EXIF
     * con `androidx.exifinterface` y escalado final al tamaño exacto.
     */
    internal fun decodeLegacy(resolver: ContentResolver, uri: Uri, longSide: Int = LONG_SIDE): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= longSide) sample *= 2
        val decoded = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("No se pudo decodificar $uri")
        val exif = resolver.openInputStream(uri).use { ExifInterface(requireNotNull(it)) }

        val matrix = Matrix()
        val scale = (longSide.toFloat() / max(decoded.width, decoded.height)).coerceAtMost(1f)
        matrix.postScale(scale, scale)
        matrix.postRotate(exif.rotationDegrees.toFloat())
        if (exif.isFlipped) matrix.postScale(-1f, 1f)
        if (matrix.isIdentity) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }
}
