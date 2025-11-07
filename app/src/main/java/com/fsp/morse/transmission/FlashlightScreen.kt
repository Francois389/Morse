package com.fsp.morse.transmission

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fsp.morse.DASH_DURATION
import com.fsp.morse.DOT_DURATION
import com.fsp.morse.PAUSE_INTER_LETTER
import com.fsp.morse.PAUSE_INTER_WORD
import com.fsp.morse.PAUSE_INTRA_CHAR
import com.fsp.morse.morseCodeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.text.format

fun translateToMorse(text: String): String {
    val words = text.uppercase().split(" ")
    return words.joinToString(separator = "//") { word ->
        word.mapNotNull { char ->
            morseCodeMap[char]
        }.joinToString(separator = "/")
    }
}

internal suspend fun performMorseTransmission(
    morseCodeToTransmit: String,
    cameraManager: CameraManager,
    cameraId: String,
    onTorchVisualStateChange: (Boolean) -> Unit,
    speedMultiplier: Float
) {
    try {
        val words = morseCodeToTransmit.split("//")
        for ((wordIndex, word) in words.withIndex()) {
            if (word.isEmpty() && words.size > 1) { // Handle potential empty strings from multiple separators
                if (wordIndex < words.size - 1) delay((PAUSE_INTER_WORD * speedMultiplier).toLong())
                continue
            }
            val letters = word.split("/")
            for ((letterIndex, letter) in letters.withIndex()) {
                if (letter.isEmpty() && letters.size > 1) {
                    if (letterIndex < letters.size - 1) delay((PAUSE_INTER_LETTER * speedMultiplier).toLong())
                    continue
                }
                for ((signalIndex, signalChar) in letter.withIndex()) {
                    try { // Inner try for individual signal torch ops
                        when (signalChar) {
                            '.' -> {
                                onTorchVisualStateChange(true)
                                cameraManager.setTorchMode(cameraId, true)
                                delay((DOT_DURATION * speedMultiplier).toLong())
                                onTorchVisualStateChange(false)
                                cameraManager.setTorchMode(cameraId, false)
                            }

                            '-' -> {
                                onTorchVisualStateChange(true)
                                cameraManager.setTorchMode(cameraId, true)
                                delay((DASH_DURATION * speedMultiplier).toLong())
                                onTorchVisualStateChange(false)
                                cameraManager.setTorchMode(cameraId, false)
                            }
                        }
                    } catch (e: CameraAccessException) {
                        Log.e("FlashlightScreen", "Error setting torch mode for signal", e)
                        onTorchVisualStateChange(false)
                        try {
                            cameraManager.setTorchMode(cameraId, false)
                        } catch (_: Exception) { /* ignore nested */ }
                        throw e // Re-throw to be handled by the caller's coroutine
                    }

                    if (signalIndex < letter.length - 1) {
                        delay((PAUSE_INTRA_CHAR * speedMultiplier).toLong())
                    }
                }
                if (letterIndex < letters.size - 1) {
                    delay((PAUSE_INTER_LETTER * speedMultiplier).toLong())
                }
            }
            if (wordIndex < words.size - 1) {
                delay((PAUSE_INTER_WORD * speedMultiplier).toLong())
            }
        }
        onTorchVisualStateChange(false) // Ensure visual state is off at the end
    } catch (e: CameraAccessException) {
        Log.e("FlashlightScreen", "performMorseTransmission caught CameraAccessException", e)
        onTorchVisualStateChange(false) // Ensure visual state is off
        try {
            cameraManager.setTorchMode(cameraId, false) // Ensure torch is off
        } catch (_: Exception) { /* ignore nested */ }
        throw e // Re-throw to be handled by the caller
    } catch (e: Exception) { // Catch other exceptions like InterruptedException if coroutine is cancelled
        Log.e("FlashlightScreen", "performMorseTransmission caught other Exception", e)
        onTorchVisualStateChange(false) // Ensure visual state is off
        try {
            cameraManager.setTorchMode(cameraId, false) // Ensure torch is off
        } catch (_: Exception) { /* ignore nested */ }
        throw e // Re-throw to be handled by the caller
    }
}

@Composable
fun FlashlightScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cameraManager =
        remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    var cameraId: String? by remember { mutableStateOf(null) }
    var textToTranslate by remember { mutableStateOf("") }
    val morseResult = remember(textToTranslate) { translateToMorse(textToTranslate) }
    val coroutineScope = rememberCoroutineScope()
    var isTransmitting by remember { mutableStateOf(false) }
    var torchVisualState by remember { mutableStateOf(false) }
    var speedMultiplier by remember { mutableFloatStateOf(1.0f) } // 1.0f for normal speed
    var transmissionJob: Job? by remember { mutableStateOf(null) }


    // Attempt to get camera ID once
    LaunchedEffect(Unit) {
        try {
            val availableCameras = cameraManager.cameraIdList
            if (availableCameras.isNotEmpty()) {
                cameraId = availableCameras.find { camId ->
                    val characteristics = cameraManager.getCameraCharacteristics(camId)
                    characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                            characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } ?: availableCameras[0] // Fallback to first camera if no back-facing flash
            } else {
                Log.e("FlashlightScreen", "No cameras available")
            }
        } catch (e: CameraAccessException) {
            Log.e("FlashlightScreen", "Error accessing camera list", e)
        }
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.Companion.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TextField(
            value = textToTranslate,
            onValueChange = { textToTranslate = it },
            label = { Text("Entrez le texte à traduire") },
            modifier = Modifier.Companion.fillMaxWidth(),
            readOnly = isTransmitting
        )

        TextField(
            value = morseResult,
            onValueChange = {},
            label = { Text("Code Morse") },
            modifier = Modifier.Companion.fillMaxWidth(),
            readOnly = true
        )

        Spacer(modifier = Modifier.Companion.height(10.dp))

        // Speed Control
        Text("Vitesse de transmission: x${"%.2f".format(1 / speedMultiplier)}")
        Slider(
            value = speedMultiplier,
            onValueChange = { speedMultiplier = it },
            valueRange = 0.25f..2.0f, // 0.25f (4x speed) to 2.0f (0.5x speed)
            steps = 6, // (2.0 - 0.25) / 0.25 = 7 intervals = 6 steps
            enabled = !isTransmitting,
            modifier = Modifier.Companion.fillMaxWidth()
        )
        Row(
            modifier = Modifier.Companion.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Rapide (x4.0)")
            Text("Lent (x0.5)")
        }


        Spacer(modifier = Modifier.Companion.height(10.dp))

        if (isTransmitting) {
            Button(
                onClick = {
                    transmissionJob?.cancel()
                    // The finally block in the coroutine will handle cleanup
                },
                modifier = Modifier.Companion.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Companion.Red)
            ) {
                Text("Arrêter Transmission")
            }
        } else {
            Button(
                onClick = {
                    val currentCameraId = cameraId
                    if (currentCameraId != null && morseResult.isNotBlank()) {
                        transmissionJob = coroutineScope.launch {
                            isTransmitting = true
                            try {
                                performMorseTransmission(
                                    morseCodeToTransmit = morseResult,
                                    cameraManager = cameraManager,
                                    cameraId = currentCameraId,
                                    onTorchVisualStateChange = { visualState ->
                                        torchVisualState = visualState
                                    },
                                    speedMultiplier = speedMultiplier
                                )
                            } catch (e: Exception) {
                                if (e is CancellationException) {
                                    Log.i("FlashlightScreen", "Transmission cancelled by user.")
                                    // Make sure visual state is off if transmission is cancelled.
                                    torchVisualState = false
                                    try {
                                        cameraManager.setTorchMode(currentCameraId, false)
                                    } catch (_: Exception) {
                                    }
                                    // No need to re-throw if it's a cancellation initiated by the user
                                } else {
                                    Log.e(
                                        "FlashlightScreen",
                                        "Transmission failed or was interrupted",
                                        e
                                    )
                                    torchVisualState = false // Ensure visual state is off
                                    try {
                                        cameraManager.setTorchMode(
                                            currentCameraId,
                                            false
                                        ) // Ensure torch is off
                                    } catch (ex: CameraAccessException) {
                                        Log.e(
                                            "FlashlightScreen",
                                            "Error turning off torch in Button's catch block",
                                            ex
                                        )
                                    }
                                }
                            } finally {
                                isTransmitting = false
                                torchVisualState = false // Also reset here for safety
                                transmissionJob = null
                                try { // Final attempt to turn off torch
                                    cameraManager.setTorchMode(currentCameraId, false)
                                } catch (e: CameraAccessException) {
                                    // Log.e("FlashlightScreen", "Error turning off torch in Button's finally block", e)
                                    // Avoid logging if already off or other minor issues after cancellation/completion
                                }
                            }
                        }
                    }
                },
                enabled = cameraId != null && morseResult.isNotBlank(),
                modifier = Modifier.Companion.fillMaxWidth()
            ) {
                Text("Transmettre")
            }
        }

        Spacer(modifier = Modifier.Companion.height(16.dp))

        val indicatorText = if (torchVisualState) "Lampe : Allumée" else "Lampe : Éteinte"
        val backgroundColor =
            if (torchVisualState) Color.Companion.Yellow else Color.Companion.DarkGray
        val textColor = if (torchVisualState) Color.Companion.Black else Color.Companion.White

        Text(
            text = indicatorText,
            color = textColor,
            modifier = Modifier.Companion
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(8.dp),
            textAlign = TextAlign.Companion.Center
        )

        if (cameraId == null) {
            Text(
                "Lampe torche non disponible ou non trouvée.",
                color = Color.Companion.Red,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}