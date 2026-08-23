package service.hint

import dto.EliminationDto
import dto.ExplanationStepDto
import i18n.HintStringInterpolation
import i18n.LanguageConfig
import service.hint.explanations.generateChainLikeSteps
import service.hint.explanations.generateForcingChainSteps
import service.hint.explanations.generateNishioSteps
import service.hint.explanations.generateSueDeCoqSteps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Phase 3 regression net.
 *
 * The diabolical-tier generators (Sue-de-Coq, Forcing Chains, Nishio, and the
 * ChainLike family — XY-chains / AICs / ALS-chains / …) used to emit hardcoded
 * English strings, and the ChainLike text was *wrong* for XY-chains (it claimed
 * strong links; XY-chains are weak-link-only). They now emit `{{hints…}}` keys
 * authored in `en.json` only. These tests drive the real generators and render
 * every step through the real [LanguageManager] pipeline (English fallback for
 * non-EN locales, Phase 1) and assert:
 *
 *  - no step renders as the literal missing-key marker `[key]`,
 *  - no step leaves an unsubstituted `{{…}}` placeholder behind,
 *  - the elimination data actually lands in the rendered text,
 *  - the XY-Chain text no longer claims strong links,
 *  - non-XY chain names route to the `generic_chain` family instead.
 */
class DiabolicalHintsTest {
    /** A rendered hint string is broken when it is the literal `[key]` marker. */
    private fun isMissingMarker(s: String) = s.startsWith("[") && s.endsWith("]")

    /** A rendered string is broken when a `{{var}}` placeholder was never substituted. */
    private fun isUnsubstituted(s: String) = s.contains("{{") && s.contains("}}")

    private fun setLocale(locale: String) {
        assertTrue(LanguageConfig.setLanguage(locale), "could not load locale '$locale'")
    }

    /** Render every step's title + description of [steps] under the current locale. */
    private fun renderAll(steps: List<ExplanationStepDto>): List<String> =
        steps.flatMap { listOf(it.title, it.description) }.map { HintStringInterpolation.interpolate(it) }

    /** Assert the whole hint renders clean: no markers, no leftover placeholders. */
    private fun assertRendersClean(
        tag: String,
        steps: List<ExplanationStepDto>,
    ) {
        for (text in renderAll(steps)) {
            assertTrue(
                !isMissingMarker(text),
                "$tag rendered as a missing-key marker: $text",
            )
            assertTrue(
                !isUnsubstituted(text),
                "$tag left an unsubstituted placeholder: $text",
            )
        }
    }

    // digit 7 eliminated from R1C2..R1C4 — the data the text must land on.
    private val dummyEliminations = listOf(EliminationDto(digit = 7, cells = listOf(1, 2, 3)))

    @Test
    fun `sue de coq steps are localized and carry the elimination data`() {
        setLocale("en")
        val steps = generateSueDeCoqSteps(dummyEliminations)
        assertEquals(3, steps.size, "expected 3 steps for a Sue-de-Coq with eliminations")
        for (s in steps) {
            assertTrue(s.title.contains("sue_de_coq"), "step ${s.stepNumber} is not key-based: ${s.title}")
        }
        assertRendersClean("sue-de-coq", steps)
        val s3 = HintStringInterpolation.interpolate(steps[2].description)
        assertTrue(
            s3.contains("7") && s3.contains("R1C2"),
            "s3 must name the eliminated digit and cells: $s3",
        )
    }

    @Test
    fun `forcing chain steps are localized and carry the elimination data`() {
        setLocale("en")
        val steps = generateForcingChainSteps("Forcing Chains", dummyEliminations)
        assertEquals(3, steps.size, "expected 3 steps for a forcing chain with eliminations")
        for (s in steps) {
            assertTrue(s.title.contains("forcing_chains"), "step ${s.stepNumber} is not key-based: ${s.title}")
        }
        assertRendersClean("forcing-chains", steps)
        val s2 = HintStringInterpolation.interpolate(steps[1].description)
        assertTrue(
            s2.contains("7") && s2.contains("R1C2"),
            "s2 must name the converging conclusion digit and cells: $s2",
        )
    }

    @Test
    fun `nishio s1 names the assumed candidate instead of only implying it`() {
        setLocale("en")
        val steps = generateNishioSteps(dummyEliminations)
        assertEquals(3, steps.size, "expected 3 steps for a Nishio with eliminations")
        for (s in steps) {
            assertTrue(s.title.contains("nishio"), "step ${s.stepNumber} is not key-based: ${s.title}")
        }
        assertRendersClean("nishio", steps)
        val s1 = HintStringInterpolation.interpolate(steps[0].description)
        assertTrue(
            s1.contains("7") && s1.contains("R1C2"),
            "s1 must state the assumed candidate (digit in cell): $s1",
        )
    }

    @Test
    fun `xy chain renders the weak-link-only text and never claims strong links`() {
        setLocale("en")
        val steps = generateChainLikeSteps("XY-Chains", dummyEliminations)
        assertEquals(3, steps.size)
        // The generator must pick the xy_chain family for XY names.
        for (s in steps) {
            assertTrue(s.title.contains("xy_chain"), "expected the xy_chain family, got: ${s.title}")
        }
        assertRendersClean("xy-chain", steps)
        val s1 = HintStringInterpolation.interpolate(steps[0].description)
        val s1Lower = s1.lowercase()
        assertTrue(
            s1Lower.contains("weak links only"),
            "xy-chain s1 must say weak links only: $s1",
        )
        assertTrue(
            !s1.contains("• Strong links"),
            "xy-chain s1 must not list strong links as a connection type: $s1",
        )
        val s2 = HintStringInterpolation.interpolate(steps[1].description)
        assertTrue(
            s2.contains("7") && s2.contains("R1C2"),
            "s2 must name the eliminated digit and cells: $s2",
        )
    }

    @Test
    fun `non-xy chain names route to the generic chain family`() {
        setLocale("en")
        for (name in listOf("Alternating Inference Chains", "ALS-Chain", "X - Chains", "Kraken Chains")) {
            val steps = generateChainLikeSteps(name, dummyEliminations)
            for (s in steps) {
                assertTrue(
                    s.title.contains("generic_chain"),
                    "'$name' should use the generic_chain family, got: ${s.title}",
                )
            }
            assertRendersClean("chain-like ($name)", steps)
        }
    }

    @Test
    fun `diabolical en-only keys resolve under non-en locales via the english fallback`() {
        // Phase 3 checkpoint: the new keys exist only in en.json, so every other
        // locale must render them via the English fallback (Phase 1) — never the
        // bracketed marker, never a leftover placeholder.
        for (locale in listOf("de", "es", "fr")) {
            setLocale(locale)
            assertRendersClean("$locale sue-de-coq", generateSueDeCoqSteps(dummyEliminations))
            assertRendersClean("$locale forcing-chains", generateForcingChainSteps("Forcing Chains", dummyEliminations))
            assertRendersClean("$locale nishio", generateNishioSteps(dummyEliminations))
            assertRendersClean("$locale xy-chain", generateChainLikeSteps("XY-Chains", dummyEliminations))
            assertRendersClean("$locale generic-chain", generateChainLikeSteps("Alternating Inference Chains", dummyEliminations))
        }
    }
}
