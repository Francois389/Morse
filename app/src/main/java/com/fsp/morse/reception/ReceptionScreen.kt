package com.fsp.morse.reception

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.GestureCancellationException
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fsp.morse.LETTER_TIMEOUT_MS
import com.fsp.morse.LONG_PRESS_THRESHOLD_MS
import com.fsp.morse.WORD_TIMEOUT_MS
import com.fsp.morse.morseCodeMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReceptionScreen(modifier: Modifier = Modifier) {
    var decodedText by remember { mutableStateOf("") }
    var morseInputSequence by remember { mutableStateOf("") }
    var currentLetterMorse by remember { mutableStateOf("") }
    var pressStartTime by remember { mutableLongStateOf(0L) }
    var inputTimeoutJob: Job? by remember { mutableStateOf(null) }
    var longPressVibrationJob: Job? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    val context = LocalContext.current
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    val invertedMorseCodeMap =
        remember { morseCodeMap.entries.associateBy({ it.value }) { it.key } }

    fun processNewSignal(signal: String) {
        inputTimeoutJob?.cancel() // Cancel previous timeout
        currentLetterMorse += signal
        morseInputSequence += signal

        inputTimeoutJob = coroutineScope.launch {
            delay(LETTER_TIMEOUT_MS)
            // Letter ended
            val char = invertedMorseCodeMap[currentLetterMorse]
            val previousCurrentLetterMorse = currentLetterMorse // Store before reset
            currentLetterMorse = "" // Reset for next letter input

            if (char != null) {
                decodedText += char
            } else {
                if (previousCurrentLetterMorse.isNotEmpty()) {
                    Log.w("ReceptionScreen", "Unrecognized Morse: $previousCurrentLetterMorse")
                }
            }
            val letterJustFormed = previousCurrentLetterMorse.isNotEmpty()
            if (letterJustFormed) { // only add separator if a letter was formed
                morseInputSequence += " / "
            }


            if (letterJustFormed) { // Only proceed to word timeout if a letter was just processed
                delay(WORD_TIMEOUT_MS - LETTER_TIMEOUT_MS) // Additional delay for word gap
                // Word ended
                if (decodedText.isNotEmpty() && !decodedText.endsWith(" ")) {
                    decodedText += " "
                }
                if (morseInputSequence.endsWith(" / ")) {
                    morseInputSequence = morseInputSequence.removeSuffix(" / ") + " // "
                } else if (morseInputSequence.isNotEmpty() && !morseInputSequence.endsWith("// ")) {
                    // This case might happen if multiple word timeouts occur without new letters
                    morseInputSequence += "// "
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.Companion.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Texte Décodé:", style = MaterialTheme.typography.titleMedium)
        TextField(
            value = decodedText,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.Companion.fillMaxWidth(),
            minLines = 3
        )

        Text("Séquence Morse Entrée:", style = MaterialTheme.typography.titleMedium)
        TextField(
            value = morseInputSequence,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.Companion.fillMaxWidth(),
            minLines = 2
        )

        Box(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .height(100.dp)
                .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { _ ->
                            Log.d("ReceptionScreen", "Press detected on Box")
                            pressStartTime = System.currentTimeMillis()
                            inputTimeoutJob?.cancel()
                            longPressVibrationJob?.cancel() // Cancel previous vibration job

                            longPressVibrationJob = coroutineScope.launch {
                                delay(LONG_PRESS_THRESHOLD_MS)
                                Log.d("ReceptionScreen", "Long press threshold reached, vibrating.")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    vibrator.vibrate(
                                        VibrationEffect.createOneShot(
                                            50,
                                            VibrationEffect.DEFAULT_AMPLITUDE
                                        )
                                    )
                                } else {
                                    @Suppress("DEPRECATION")
                                    vibrator.vibrate(50)
                                }
                            }

                            try {
                                tryAwaitRelease() // Wait for the press to be released
                                longPressVibrationJob?.cancel() // Press released, cancel vibration job regardless of state
                                val pressDuration = System.currentTimeMillis() - pressStartTime
                                val signal =
                                    if (pressDuration < LONG_PRESS_THRESHOLD_MS) "." else "-"
                                Log.d("ReceptionScreen", "Signal: $signal ($pressDuration ms)")
                                processNewSignal(signal)
                            } catch (e: GestureCancellationException) {
                                longPressVibrationJob?.cancel() // Gesture cancelled, ensure vibration job is also cancelled
                                Log.i("ReceptionScreen", "Press gesture cancelled on Box", e)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Companion.Center
        ) {
            Text(
                text = "Appuyer pour Morse \n(Court = . / Long = -)",
                textAlign = TextAlign.Companion.Center,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }

        Row(
            modifier = Modifier.Companion.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = {
                    inputTimeoutJob?.cancel()
                    longPressVibrationJob?.cancel()

                    if (currentLetterMorse.isNotEmpty()) {
                        // Deleting the last signal of the currently forming letter
                        if (morseInputSequence.isNotEmpty()) {
                            morseInputSequence = morseInputSequence.dropLast(1)
                        }
                        currentLetterMorse = currentLetterMorse.dropLast(1)
                    } else if (decodedText.isNotEmpty()) {
                        // Deleting a completed letter or a space
                        if (decodedText.endsWith(" ")) {
                            // Last thing added was a space
                            decodedText = decodedText.dropLast(1) // Remove the space
                            if (morseInputSequence.endsWith(" // ")) {
                                // It was a full word separator, revert to letter separator
                                morseInputSequence = morseInputSequence.removeSuffix(" // ") + " / "
                            }
                        } else {
                            // Last thing added was a letter
                            val lastChar = decodedText.last()
                            val morseForLastChar =
                                morseCodeMap[lastChar.toString().uppercase().get(0)]
                            decodedText = decodedText.dropLast(1) // remove char from decoded text

                            if (morseForLastChar != null) {
                                if (morseInputSequence.endsWith("$morseForLastChar / ")) {
                                    morseInputSequence =
                                        morseInputSequence.removeSuffix("$morseForLastChar / ")
                                } else if (morseInputSequence.endsWith(morseForLastChar)) {
                                    // Fallback for cases like a single letter without trailing " / "
                                    // (should ideally not happen if processNewSignal is consistent)
                                    morseInputSequence =
                                        morseInputSequence.removeSuffix(morseForLastChar)
                                }
                            }
                        }
                    }
                },
                enabled = decodedText.isNotEmpty() || currentLetterMorse.isNotEmpty()
            ) {
                Text("Corriger")
            }

            Spacer(modifier = Modifier.Companion.width(8.dp)) // Optional: add some space between buttons

            Button(onClick = {
                inputTimeoutJob?.cancel()
                longPressVibrationJob?.cancel()
                decodedText = ""
                morseInputSequence = ""
                currentLetterMorse = ""
                pressStartTime = 0L
            }) {
                Text("Effacer Tout")
            }
        }
    }
}