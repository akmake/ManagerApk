package com.sterni.dailystudy.ui.screens.join

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QrScannerScreen(
    onCodeScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraPermission.status.isGranted) {
            CameraPreview(onCodeScanned = onCodeScanned)
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("נדרשת הרשאת מצלמה לסריקת QR")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                    Text("אפשר מצלמה")
                }
            }
        }

        // Scanning frame overlay
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.Center)
                .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
        )

        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "חזרה",
                tint = Color.White
            )
        }

        Text(
            text = "כוון את המצלמה לקוד QR",
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        )
    }
}

@Composable
private fun CameraPreview(onCodeScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var scanned by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = Executors.newSingleThreadExecutor()
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val scanner = BarcodeScanning.getClient()
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                analysis.setAnalyzer(executor) { imageProxy ->
                    if (scanned) { imageProxy.close(); return@setAnalyzer }
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue?.let { raw ->
                                    val code = extractCode(raw)
                                    if (code != null && !scanned) {
                                        scanned = true
                                        onCodeScanned(code)
                                    }
                                }
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    } else {
                        imageProxy.close()
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )
                } catch (e: Exception) {
                    Log.e("QrScanner", "Camera bind failed: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}

private fun extractCode(raw: String): String? {
    return try {
        val json = JSONObject(raw)
        if (json.optString("app") == "tether") json.getString("code") else null
    } catch (e: Exception) {
        // פורמט ישיר — קוד בלבד
        if (raw.matches(Regex("[A-Z0-9]{6,12}"))) raw else null
    }
}
