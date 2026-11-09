package com.example.finanzecasa.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.finanzecasa.backup.receiptsDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private const val MAX_SIDE = 1600

/** Copia (ridimensionata e raddrizzata) la foto scelta nella memoria dell'app. Restituisce il nome del file. */
fun importReceipt(context: Context, uri: Uri): String? {
    return try {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded: Bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null
        val rotation: Float = resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = MAX_SIDE.toFloat() / maxOf(decoded.width, decoded.height)
        val matrix = Matrix()
        if (scale < 1f) matrix.postScale(scale, scale)
        if (rotation != 0f) matrix.postRotate(rotation)
        val finalBitmap: Bitmap =
            if (matrix.isIdentity) decoded
            else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val name = "${UUID.randomUUID()}.jpg"
        File(receiptsDir(context), name).outputStream().use { finalBitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    } catch (_: Exception) {
        null
    }
}

fun deleteReceiptFile(context: Context, name: String?) {
    if (name.isNullOrBlank()) return
    runCatching { File(receiptsDir(context), File(name).name).delete() }
}

private fun loadBitmap(context: Context, name: String): ImageBitmap? {
    val file = File(receiptsDir(context), File(name).name)
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

/** Mostra la foto dello scontrino salvata. */
@Composable
fun ReceiptImage(name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, name) {
        value = withContext(Dispatchers.IO) { loadBitmap(context, name) }
    }
    val img = image
    if (img != null) {
        Image(
            bitmap = img, contentDescription = "Foto dello scontrino",
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxWidth().heightIn(max = 320.dp)
        )
    } else {
        Text("Foto non disponibile")
    }
}
