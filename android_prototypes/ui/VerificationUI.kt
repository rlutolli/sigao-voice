package com.sigao.prototypes.ui

import android.graphics.Bitmap
import android.graphics.Color

/**
 * UI Helper for Identity Verification.
 * Formats the "Safety Number" for manual comparison.
 */
object VerificationUI {

    /**
     * Formats the raw 30-digit Safety Number into readable 5-digit blocks.
     * Example: "12345 67890 12345 67890 12345 67890"
     */
    fun formatSafetyNumber(rawSafetyNumber: String): String {
        return rawSafetyNumber.chunked(5).joinToString(" ")
    }

    /**
     * Generates a QR Code Bitmap for the Safety Number.
     * (Stub implementation - requires ZXing or MLS library in prod).
     */
    fun generateSafetyNumberQR(safetyNumber: String): Bitmap {
        // Placeholder: Return a blank 200x200 bitmap
        // Real impl would: BarcodeEncoder().encodeBitmap(safetyNumber, BarcodeFormat.QR_CODE, 400, 400)
        return Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLACK) 
        }
    }
}
