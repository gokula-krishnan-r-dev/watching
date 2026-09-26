package com.meritscreen.feature.child.data

import com.meritscreen.core.common.domain.AgeBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizPackValidatorTest {

    @Test
    fun `shouldGenerate when no AI items yet even if bank is full`() {
        assertTrue(QuizPackValidator.shouldGenerate(nearRungUnused = 20, aiCountForBand = 0))
        assertFalse(QuizPackValidator.shouldGenerate(nearRungUnused = 20, aiCountForBand = 5))
        assertTrue(QuizPackValidator.shouldGenerate(nearRungUnused = 5, aiCountForBand = 5))
    }

    @Test
    fun `accepts valid pack and fills missing whyWrong`() {
        val raw = """
            [
              {
                "topic": "addition",
                "conceptId": "add-within-20",
                "conceptTitle": "Adding by counting on",
                "difficulty": 2,
                "prompt": "What is 8 + 5?",
                "choices": [
                  {"id": "a", "text": "12", "correct": false},
                  {"id": "b", "text": "13", "correct": true},
                  {"id": "c", "text": "14", "correct": false}
                ],
                "whyCorrect": "8 + 5 is 13.",
                "whyWrongByChoice": {
                  "a": "12 is 8 + 4.",
                  "c": "14 is 8 + 6."
                },
                "conceptExplainer": "Adding joins two groups.",
                "miniLesson": {"title": "Count on", "bodyLines": ["Start at 8 and count five."]}
              }
            ]
        """.trimIndent()
        val ok = QuizPackValidator.parseAndValidate(raw, AgeBand.AGE_7_TO_9, emptySet())
        assertEquals(1, ok.size)
        assertEquals("addition", ok.first().topic)

        val filled = QuizPackValidator.parseAndValidate(
            raw.replace(",\n                  \"c\": \"14 is 8 + 6.\"", ""),
            AgeBand.AGE_7_TO_9,
            emptySet(),
        )
        assertEquals(1, filled.size)
        assertTrue(filled.first().whyWrongByChoice["c"].orEmpty().isNotBlank())
    }

    @Test
    fun `AGE_3_TO_6 with unknown tags falls back to TAP_TEXT when choices have text`() {
        val raw = """
            {
              "items": [{
                "topic": "animals",
                "conceptId": "spot-dog",
                "conceptTitle": "Find the dog",
                "difficulty": 1,
                "prompt": "Which animal is a dog?",
                "interactionType": "TAP_IMAGE",
                "promptTag": "dog",
                "choices": [
                  {"id": "a", "text": "Dog", "correct": true, "imageTag": "unicorn_fake"},
                  {"id": "b", "text": "Cat", "correct": false, "imageTag": "dragon_fake"}
                ],
                "whyCorrect": "That is the dog.",
                "whyWrongByChoice": {"b": "That is a cat."},
                "conceptExplainer": "Dogs are pets."
              }]
            }
        """.trimIndent()
        val ok = QuizPackValidator.parseAndValidate(raw, AgeBand.AGE_3_TO_6, emptySet())
        assertEquals(1, ok.size)
        assertEquals("TAP_TEXT", ok.first().interactionType)
    }

    @Test
    fun `rejects AGE_3_TO_6 items with unknown taxonomy tags and no text`() {
        val raw = """
            {
              "items": [{
                "topic": "animals",
                "conceptId": "spot-dog",
                "conceptTitle": "Find the dog",
                "difficulty": 1,
                "prompt": "Tap the dog",
                "interactionType": "TAP_IMAGE",
                "promptTag": "dog",
                "choices": [
                  {"id": "a", "text": "", "correct": true, "imageTag": "unicorn_fake"},
                  {"id": "b", "text": "", "correct": false, "imageTag": "cat"}
                ],
                "whyCorrect": "That is the dog.",
                "whyWrongByChoice": {"b": "That is a cat."},
                "conceptExplainer": "Dogs are pets."
              }]
            }
        """.trimIndent()
        assertTrue(
            QuizPackValidator.parseAndValidate(raw, AgeBand.AGE_3_TO_6, emptySet()).isEmpty(),
        )
    }
}
