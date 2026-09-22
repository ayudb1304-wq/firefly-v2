package com.firefly.app.ui.qr

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.firefly.app.R
import com.firefly.app.core.group.GroupCode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Payload written into the QR. Scanners also accept a bare 6-character code. */
object JoinLink {
    private const val PREFIX = "firefly://join/"

    fun encode(code: String): String = PREFIX + code

    /** @return the normalised group code carried by [text], or null if it is not a Firefly join payload. */
    fun decode(text: String): String? {
        val raw = text.trim().removePrefix(PREFIX).removePrefix(PREFIX.uppercase())
        return GroupCode.normalise(raw)
    }
}

fun qrBitmap(text: String, sizePx: Int): Bitmap {
    val matrix = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, sizePx, sizePx,
        mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(sizePx * sizePx) { i -> if (matrix.get(i % sizePx, i / sizePx)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.ARGB_8888)
}

/** PRD A1: the group's code as a QR so friends can join in one scan. */
@Composable
fun GroupQrDialog(code: String, onDismiss: () -> Unit) {
    val image = remember(code) { qrBitmap(JoinLink.encode(code), 512).asImageBitmap() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.qr_close)) } },
        title = { Text(stringResource(R.string.qr_title, code)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Image(
                    bitmap = image, contentDescription = code, filterQuality = FilterQuality.None,
                    modifier = Modifier.size(240.dp).background(Color.White).padding(8.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(code, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.qr_hint), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            }
        },
    )
}
