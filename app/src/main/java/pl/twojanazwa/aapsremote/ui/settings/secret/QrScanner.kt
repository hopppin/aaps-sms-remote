package pl.twojanazwa.aapsremote.ui.settings.secret

import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.atomic.AtomicBoolean
import pl.twojanazwa.aapsremote.R

/**
 * Warstwa kamery (UI-09, UI-10) – bez testów automatycznych (W3, W4). Skanuje tylko format QR,
 * offline (model w paczce). Pierwsza niepusta wartość trafia do [onQrValue] dokładnie raz;
 * klatki nie są zapisywane, a wartości nie są logowane.
 */
@Composable
fun QrScanner(
    onQrValue: (String) -> Unit,
    onCameraError: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQrValue = rememberUpdatedState(onQrValue)
    val currentOnCameraError = rememberUpdatedState(onCameraError)
    val controller = remember { LifecycleCameraController(context) }

    DisposableEffect(controller, lifecycleOwner) {
        val executor = ContextCompat.getMainExecutor(context)
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        // Jedna dostawa wartości: kolejne rozpoznania (ta sama klatka, następne klatki) są pomijane.
        val delivered = AtomicBoolean(false)
        var released = false
        var cameraObserver: Observer<CameraState>? = null

        fun fail() {
            if (!released && delivered.compareAndSet(false, true)) currentOnCameraError.value()
        }

        // Pojedyncze błędy detektora to utracone klatki; trwały błąd (kolejne klatki z rzędu) = błąd kamery.
        var detectorFailures = 0

        try {
            controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            controller.setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            controller.setImageAnalysisAnalyzer(
                executor,
                MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, executor) { result ->
                    if (released || delivered.get()) return@MlKitAnalyzer
                    if (result?.getThrowable(scanner) != null) {
                        detectorFailures++
                        if (detectorFailures >= MAX_DETECTOR_FAILURES) fail()
                        return@MlKitAnalyzer
                    }
                    detectorFailures = 0
                    val value = result?.getValue(scanner)
                        ?.firstNotNullOfOrNull { barcode -> barcode.rawValue?.takeIf { it.isNotEmpty() } }
                    if (value != null && delivered.compareAndSet(false, true)) {
                        currentOnQrValue.value(value)
                    }
                },
            )
            controller.bindToLifecycle(lifecycleOwner)

            val initialization = controller.initializationFuture
            initialization.addListener(
                {
                    if (!released) {
                        val ready = try {
                            initialization.get()
                            controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            false
                        }
                        if (!ready) {
                            fail()
                        } else {
                            // Aparat zajęty / wyłączony (UI-09, W4): błąd stanu kamery = onCameraError, nie czarny podgląd.
                            val observer = Observer<CameraState> { state -> if (state.error != null) fail() }
                            cameraObserver = observer
                            controller.cameraInfo?.cameraState?.observe(lifecycleOwner, observer)
                        }
                    }
                },
                executor,
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // Np. IllegalStateException z bindToLifecycle bez tylnej kamery; bez logowania i bez treści wyjątku.
            executor.execute { fail() }
        }

        onDispose {
            released = true
            cameraObserver?.let { controller.cameraInfo?.cameraState?.removeObserver(it) }
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
            scanner.close()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    this.controller = controller
                }
            },
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.secret_scanner_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

private const val MAX_DETECTOR_FAILURES = 5
