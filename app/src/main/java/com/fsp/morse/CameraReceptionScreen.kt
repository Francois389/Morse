package com.fsp.morse

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

@Composable
fun CameraReceptionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Log.w("CameraReceptionScreen", "Camera permission denied.")
        }
    }

    // Effet pour demander la permission si elle n'est pas déjà accordée
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Réception par Caméra (Expérimental)", style = MaterialTheme.typography.titleMedium)

        if (hasCameraPermission) {
            Box(modifier = Modifier.weight(1f)) { // Pour que la PreviewView prenne de la place
                CameraPreview(
                    modifier = Modifier.fillMaxSize(),
                    onLuminosityChanged = { luminosity ->
                        // Ici, nous traiterons la luminosité plus tard
                        // Log.d("CameraReceptionScreen", "Luminosity: $luminosity")
                    }
                )
            }
            // TODO: Ajouter des contrôles pour démarrer/arrêter l'analyse et afficher le texte décodé
            Text("Texte Décodé: (à venir)")
        } else {
            Text("La permission caméra est requise pour cette fonctionnalité.")
            Button(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Demander la permission Caméra")
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    onLuminosityChanged: (Double) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    val imageAnalyzer = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .apply {
                setAnalyzer(Executors.newSingleThreadExecutor(), LuminosityAnalyzer { luma ->
                    onLuminosityChanged(luma)
                })
            }
    }

    LaunchedEffect(cameraProviderFuture, cameraSelector) {
        try {
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll() // Détacher les cas d'utilisation précédents
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer // Lier l'analyseur d'image
            )
        } catch (e: Exception) {
            Log.e("CameraPreview", "Failed to bind camera use cases", e)
        }
    }

    AndroidView({ previewView }, modifier = modifier)
}

// Analyseur simple de luminosité (moyenne des pixels)
private class LuminosityAnalyzer(private val listener: (Double) -> Unit) : ImageAnalysis.Analyzer {
    override fun analyze(image: androidx.camera.core.ImageProxy) {
        val buffer = image.planes[0].buffer
        val data = ByteArray(buffer.remaining())
        buffer.get(data)
        val luma = data.map { it.toInt() and 0xFF }.average() // Calcul simple de la moyenne des pixels
        listener(luma)
        image.close()
    }
}
