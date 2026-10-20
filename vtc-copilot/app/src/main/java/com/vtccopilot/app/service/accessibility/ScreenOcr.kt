package com.vtccopilot.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Lecture de l'écran par OCR embarqué (ML Kit, 100 % hors ligne) — plan B quand l'app VTC
 * n'expose pas les textes de sa carte d'offre au service d'accessibilité.
 *
 * Capture via AccessibilityService.takeScreenshot (Android 11+), au plus 1 fois par seconde.
 * Les zones de nos propres fenêtres (pop-up, bulle) sont exclues pour ne pas relire nos chiffres.
 */
class ScreenOcr(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "ScreenOcr"
        private const val MIN_INTERVAL_MS = 1_000L
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var lastShotAt = 0L

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    /**
     * Lignes de texte lues à l'écran (triées de haut en bas).
     * null = pas tenté (trop tôt depuis la dernière capture) ; liste vide = rien lu / non supporté.
     */
    suspend fun readLines(exclude: List<Rect>): List<String>? {
        if (!isSupported) return emptyList()
        val now = SystemClock.elapsedRealtime()
        if (now - lastShotAt < MIN_INTERVAL_MS) return null
        lastShotAt = now

        val bitmap = takeShot() ?: return emptyList()
        return try {
            val text = recognize(bitmap) ?: return emptyList()
            text.textBlocks.flatMap { it.lines }
                .filter { line -> val box = line.boundingBox; box == null || exclude.none { Rect.intersects(it, box) } }
                .sortedBy { it.boundingBox?.top ?: 0 }
                .map { it.text.trim() }
                .filter { it.isNotEmpty() }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun takeShot(): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) takeShotR() else null

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeShotR(): Bitmap? = suspendCancellableCoroutine { cont ->
        runCatching {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val bmp = runCatching {
                        Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)?.let { hw ->
                            hw.copy(Bitmap.Config.ARGB_8888, false).also { hw.recycle() }
                        }
                    }.getOrNull()
                    buffer.close()
                    if (cont.isActive) cont.resume(bmp)
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Capture d'écran refusée (code $errorCode)")
                    if (cont.isActive) cont.resume(null)
                }
            })
        }.onFailure {
            Log.w(TAG, "takeScreenshot indisponible", it)
            if (cont.isActive) cont.resume(null)
        }
    }

    private suspend fun recognize(bitmap: Bitmap): Text? = suspendCancellableCoroutine { cont ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
            .addOnFailureListener {
                Log.w(TAG, "OCR échoué", it)
                if (cont.isActive) cont.resume(null)
            }
    }

    fun release() {
        runCatching { recognizer.close() }
        executor.shutdownNow()
    }
}
