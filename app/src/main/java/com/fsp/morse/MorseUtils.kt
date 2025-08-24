package com.fsp.morse

// Durations for Morse code transmission
const val DOT_DURATION = 200L // Base duration for a dot
const val DASH_DURATION = 600L // Typically 3x DOT_DURATION
const val PAUSE_INTRA_CHAR = 200L // Pause between signals of the same letter (1x DOT_DURATION)
const val PAUSE_INTER_LETTER = 600L // Pause between letters (3x DOT_DURATION)
const val PAUSE_INTER_WORD = 1400L // Pause between words (7x DOT_DURATION)

// Durations for Morse code reception
const val LONG_PRESS_THRESHOLD_MS = 300L
const val LETTER_TIMEOUT_MS = 1000L // Time of inactivity to consider a letter finished
const val WORD_TIMEOUT_MS = 2000L  // Time of inactivity to consider a word finished (includes LETTER_TIMEOUT_MS)

val morseCodeMap = mapOf(
    'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".", 'F' to "..-.",
    'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---", 'K' to "-.-", 'L' to ".-..",
    'M' to "--", 'N' to "-.", 'O' to "---", 'P' to ".--.", 'Q' to "--.-", 'R' to ".-.",
    'S' to "...", 'T' to "-", 'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-",
    'Y' to "-.--", 'Z' to "--..",
    '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-", '5' to ".....",
    '6' to "-....", '7' to "--...",'8' to "---..", '9' to "----.", '0' to "-----"
)
