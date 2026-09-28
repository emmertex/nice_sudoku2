package service.hint

import dto.EliminationDto
import i18n.HintStringInterpolation
import i18n.LanguageConfig
import service.hint.explanations.*
import service.hint.techniques.generateWingSteps
import sudoku.displayFormating.SimpleAIC
import sudoku.match.AICMatch
import sudoku.match.TechniqueMatch
import sudoku.solvingtechClassifier.Technique
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HintQualityRegressionTest {
    private val puzzle = "0".repeat(81)
    private val removals = listOf(EliminationDto(5, listOf(8)), EliminationDto(7, listOf(17)))
    private val genericMatch = TechniqueMatch.create("Unknown", "", 5, 6)

    private fun render(text: String): String {
        LanguageConfig.setLanguage("en")
        return HintStringInterpolation.interpolate(text)
    }

    private fun chain(firstStrong: Boolean) = AICMatch(
        "Test chain",
        SimpleAIC.Builder(firstStrong).addNode(4, 5).addNode(4, 6).addNode(4, 15).addNode(4, 14).build()
    )

    @Test
    fun `chain text and diagram agree on the first link in both orientations`() {
        for (firstStrong in listOf(true, false)) {
            val steps = generateChainSteps("Test chain", chain(firstStrong), removals)
            val text = render(steps[1].description)
            assertTrue(text.contains(if (firstStrong) "If 5 is NOT in R1C6" else "If 5 IS in R1C6"), text)
            assertEquals(listOf(firstStrong, !firstStrong, firstStrong), steps[1].lines.map { it.isStrongLink })
        }
    }

    @Test
    fun `named chains and windmill keep the actual solver nodes`() {
        for (technique in listOf(Technique.XY_CHAINS, Technique.STRONG_WING)) {
            val steps = generateExplanationSteps(technique, chain(true), removals, emptyList(), puzzle)
            assertTrue(steps[1].lines.isNotEmpty())
            val text = render(steps[1].description)
            assertTrue(text.contains("R1C6") && text.contains("R2C6"), text)
        }
    }

    @Test
    fun `generic fallback uses a known technique description and exact removals`() {
        val known = generateGenericSteps("XYZ Wing", genericMatch, removals, emptyList())
        assertTrue(known[0].description.startsWith("{{backend.techniques.XYZ_WING"))
        val unknown = generateGenericSteps("Unknown", genericMatch, removals, emptyList())
        assertTrue(render(unknown[0].description).contains("Every row, column and box"))
        val text = render(unknown[1].description)
        assertTrue(text.contains("5: R1C9") && text.contains("7: R2C9"), text)
        assertFalse(text.contains("5: R1C9, R2C9"), text)
        assertFalse(text.contains("{{"), text)
    }

    @Test
    fun `generic chain highlights only actual eliminated candidates`() {
        val steps = generateChainLikeSteps("Generic Chain", removals)
        assertEquals(2, steps[1].colouredCandidates.size)
        val text = render(steps.last().description)
        assertTrue(text.contains("5: R1C9") && text.contains("7: R2C9"), text)
        assertFalse(text.contains("{{"), text)
    }

    @Test
    fun `XYZ wing explains all three hinge choices and requires visibility of the hinge`() {
        val steps = generateWingSteps("XYZ-Wing", genericMatch, removals.take(1))
        assertTrue(steps[0].highlightCells.isEmpty(), "Missing metadata must not turn a removal cell into the hinge")
        assertTrue(render(steps[0].description).contains("pattern-cell details are unavailable"))
        val proof = render(steps[1].description)
        for (choice in listOf("If it is X", "If it is Y", "If it is Z")) assertTrue(proof.contains(choice), proof)
        val action = render(steps.last().description)
        assertTrue(action.contains("hinge AND each pincer"), action)
        assertTrue(action.contains("R1C9"), action)
        assertFalse(action.contains("{{"), action)
    }

    @Test
    fun `windmill uses its explanation only for an open chain with strong ends`() {
        val open = generateChainSteps("Strong-Wing (Windmill)", chain(true), removals)
        assertTrue(open[0].title.contains("hints.windmill."))
        val weakStart = generateChainSteps("Strong-Wing (Windmill)", chain(false), removals)
        assertTrue(weakStart[0].title.contains("hints.aic."))
        for (locale in listOf("en", "de")) {
            LanguageConfig.setLanguage(locale)
            for (step in open) {
                for (text in listOf(step.title, step.description)) {
                    val rendered = HintStringInterpolation.interpolate(text)
                    assertFalse(rendered.contains("{{"), rendered)
                    assertFalse(rendered.contains("[hints."), rendered)
                }
            }
        }
    }

    @Test
    fun `WXYZ targets see candidate positions rather than every pattern cell`() {
        val steps = generateWingSteps("WXYZ-Wing", genericMatch, removals.take(1))
        assertTrue(steps[0].highlightCells.isEmpty())
        val action = render(steps.last().description)
        assertTrue(action.contains("EVERY cell in the four-cell pattern that still has candidate 5"), action)
        assertTrue(action.contains("without that candidate does not need to be seen"), action)
        assertTrue(action.contains("R1C9"), action)
    }

    @Test
    fun `Sue de Coq preserves the digit to cell mapping`() {
        val steps = generateSueDeCoqSteps(removals)
        assertEquals(2, steps[1].colouredCandidates.size)
        val action = render(steps.last().description)
        assertTrue(action.contains("5: R1C9") && action.contains("7: R2C9"), action)
        assertFalse(action.contains("{{"), action)
    }

}
