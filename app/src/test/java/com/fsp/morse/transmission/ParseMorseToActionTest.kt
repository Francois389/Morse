package com.fsp.morse.transmission

import com.fsp.morse.translateToMorse
import com.fsp.morse.transmission.Action.*
import org.junit.Test
import org.junit.Assert.*

class ParseMorseToActionTest {

    data class Situation(
        val word: String,
        val expectedMorse: String,
        val expectedAction: List<Action>,
        val description: String = "test of \"$word\" ($expectedMorse)"
    )

    val situation = listOf(
        Situation(
            "test", "-/./.../-", listOf(
                FLASH_DASH,
                PAUSE_INTER_LETTER,
                FLASH_DOT,
                PAUSE_INTER_LETTER,
                FLASH_DOT,
                PAUSE_INTRA_CHAR,
                FLASH_DOT,
                PAUSE_INTRA_CHAR,
                FLASH_DOT,
                PAUSE_INTER_LETTER,
                FLASH_DASH
            )
        ),
        Situation(
            "", "", emptyList(),
            "empty string"
        ),
        Situation(
            "a n", ".-//-.", listOf(
                FLASH_DOT,
                PAUSE_INTRA_CHAR,
                FLASH_DASH,
                PAUSE_INTER_WORD,
                FLASH_DASH,
                PAUSE_INTRA_CHAR,
                FLASH_DOT
            )
        )
    )

    @Test
    fun `test situation`() {
        situation.forEach {
            val morse = translateToMorse(it.word)
            assertEquals(it.description, it.expectedMorse, morse)
            assertEquals(it.description, it.expectedAction, parseMorseToAction(morse))
        }

    }


}