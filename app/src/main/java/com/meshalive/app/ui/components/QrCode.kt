package com.meshalive.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Renders [text] as a QR code (ZXing core, drawn on a Compose Canvas). */
@Composable
fun QrCode(text: String, modifier: Modifier) {
    val matrix = remember(text) {
        runCatching { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1)) }.getOrNull()
    }
    Canvas(modifier) {
        drawRect(Color.White)
        val m = matrix ?: return@Canvas
        val cell = minOf(size.width / m.width, size.height / m.height)
        val ox = (size.width - cell * m.width) / 2
        val oy = (size.height - cell * m.height) / 2
        for (y in 0 until m.height) for (x in 0 until m.width) {
            if (m.get(x, y)) drawRect(Color.Black, Offset(ox + x * cell, oy + y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
