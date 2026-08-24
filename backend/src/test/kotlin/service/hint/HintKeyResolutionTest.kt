package service.hint

import dto.EliminationDto
import i18n.HintStringInterpolation
import i18n.LanguageConfig
import service.hint.explanations.generateUniqueRectangleSteps
import service.hint.explanations.generateColouringSteps
import service.hint.explanations.generateCycleSteps
import sudoku.match.SubsetMatch
import java.util.BitSet
import kotlin.test.Test
import kotlin.test.assertTrue
import service.hint.helpers.describeSectorTypeText
import service.hint.helpers.formatSectorName
import service.hint.techniques.generateFinnedFishSteps
import service.hint.techniques.generateFishSteps
import sudoku.match.FishMatch
import kotlin.test.assertEquals

/**
 * Phase 2 regression net.
 *
 * The keys that used to render as the literal missing-key marker `[...]` —
 * the per-ALS "Identify …" titles (gap 1) and the Unique-Rectangle elimination
 * templates for types 3–6 (gap 2) — must now resolve through the real
 * [LanguageManager] + English-fallback pipeline.
 *
 * New hint keys are authored in `en.json` only (per the plan); every other
 * locale must fall back to English (Phase 1) and never show the bracketed
 * marker. The last two tests drive the real [generateUniqueRectangleSteps]
 * generator to confirm a type-3 match emits the type-3 template and a match
 * with no parseable type falls back to the generic key.
 */
class HintKeyResolutionTest {

    /** A rendered hint string is broken when it is the literal `[key]` marker. */
    private fun isMissingMarker(s: String) = s.startsWith("[") && s.endsWith("]")

    private fun render(locale: String, keyWithVars: String): String {
        assertTrue(LanguageConfig.setLanguage(locale), "could not load locale '$locale'")
        return HintStringInterpolation.interpolate(keyWithVars)
    }

    private fun assertResolves(locale: String, keyWithVars: String) {
        val out = render(locale, keyWithVars)
        assertTrue(!isMissingMarker(out),
            "[$locale] $keyWithVars rendered as the missing-key marker: $out")
    }

    // A match that declares no `type` field, so the generator falls back to
    // parsing the type from the technique name ("… Type N").
    private val dummyMatch = SubsetMatch.naked(
        BitSet().apply { set(1) },            // one digit
        BitSet().apply { set(0); set(1) },    // two cells
        BitSet().apply { set(0) }             // one sector
    )
    private val dummyEliminations = listOf(EliminationDto(digit = 7, cells = listOf(1, 2, 3)))

    /**
     * A real StormDoku FishMatch: digit is 0-based; sectors are 0-8 rows,
     * 9-17 columns, 18-26 boxes (see formatSectorName). The finned generator
     * now bails out on matches with no extractable base/cover sectors, so the
     * fish tests must drive it with real fish data.
     */
    private fun fishMatch(
        digit: Int = 6,
        base: IntArray = intArrayOf(0, 4),
        cover: IntArray = intArrayOf(9, 12),
        fins: IntArray = intArrayOf()
    ) = FishMatch(
        "X-Wing", digit,
        BitSet().apply { base.forEach { set(it) } },
        BitSet().apply { cover.forEach { set(it) } },
        BitSet().apply { fins.forEach { set(it) } }
    )

    @Test
    fun `gap1 als per-set title resolves in en`() {
        // Per-ALS titles now use the fixed hints.als.step2.title key.
        assertResolves("en", "{{hints.als.step2.title|alsName=ALS A}}")
        assertResolves("en", "{{hints.als.step2.title|alsName=ALS B}}")
    }

    @Test
    fun `gap2 ur elimination types 3 to 6 resolve in en`() {
        // Written as explicit concrete keys (not `$t` interpolation) so the
        // guardrail's literal `{{hints…}}` scan sees real leaf keys, not a
        // truncated template fragment.
        assertResolves("en", "{{hints.unique_rectangle_elim_type3.step1.description|digits=7|cells=R1C1}}")
        assertResolves("en", "{{hints.unique_rectangle_elim_type4.step1.description|digits=7|cells=R1C1}}")
        assertResolves("en", "{{hints.unique_rectangle_elim_type5.step1.description|digits=7|cells=R1C1}}")
        assertResolves("en", "{{hints.unique_rectangle_elim_type6.step1.description|digits=7|cells=R1C1}}")
    }

    @Test
    fun `gap2 ur type-0 falls back to the generic key`() {
        assertResolves("en", "{{hints.unique_rectangle_generic.step1.description}}")
    }

    @Test
    fun `gap1 and gap2 new en-only keys resolve under non-en locales via the english fallback`() {
        // Phase 2 checkpoint: "no [...] from gaps 1/2 in any locale".
        // The new keys exist only in en.json, so every other locale must render
        // them via the English fallback (Phase 1) — never the bracketed marker.
        for (locale in listOf("de", "es", "fr")) {
            assertResolves(locale, "{{hints.als.step2.title|alsName=ALS A}}")
            assertResolves(locale, "{{hints.unique_rectangle_elim_type3.step1.description|digits=7|cells=R1C1}}")
            assertResolves(locale, "{{hints.unique_rectangle_elim_type4.step1.description|digits=7|cells=R1C1}}")
            assertResolves(locale, "{{hints.unique_rectangle_elim_type5.step1.description|digits=7|cells=R1C1}}")
            assertResolves(locale, "{{hints.unique_rectangle_elim_type6.step1.description|digits=7|cells=R1C1}}")
            assertResolves(locale, "{{hints.unique_rectangle_generic.step1.description}}")
        }
    }

    @Test
    fun `ur type-3 match emits the type-3 elimination template that resolves`() {
        val steps = generateUniqueRectangleSteps("Unique Rectangle Type 3", dummyMatch, dummyEliminations)
        val step3 = steps.firstOrNull { it.stepNumber == 3 }
        assertTrue(step3 != null, "expected an elimination step for a UR with eliminations")
        assertTrue(step3!!.description.contains("unique_rectangle_elim_type3"),
            "expected the type-3 elimination template, got: ${step3.description}")
        val rendered = HintStringInterpolation.interpolate(step3.description)
        assertTrue(!isMissingMarker(rendered), "UR type-3 elimination rendered as a missing-key marker: $rendered")
    }

    @Test
    fun `ur with no parseable type falls back to the generic key and still resolves`() {
        val steps = generateUniqueRectangleSteps("Unique Rectangle", dummyMatch, dummyEliminations)
        val step3 = steps.firstOrNull { it.stepNumber == 3 }
        assertTrue(step3 != null, "expected an elimination step for a UR with eliminations")
        assertTrue(step3!!.description.contains("unique_rectangle_generic"),
            "expected the generic fallback template, got: ${step3.description}")
        val rendered = HintStringInterpolation.interpolate(step3.description)
        assertTrue(!isMissingMarker(rendered), "UR type-0 fallback rendered as a missing-key marker: $rendered")
    }

    // --- Phase 4: fish & finned text accuracy (gaps 4, 5, 9-fin) ---

    @Test
    fun `phase4 a mixed base reads as rows and boxes not the first sector only`() {
        // A Franken fish's base is a line + a box; the type text must reflect the
        // whole set, not just its first sector (gap 5b).
        assertEquals("rows and boxes", describeSectorTypeText(listOf(0, 18)))
        assertEquals("columns", describeSectorTypeText(listOf(9, 14)))
        assertEquals("rows", describeSectorTypeText(listOf(0, 5)))
        assertEquals("lines", describeSectorTypeText(emptyList()))
    }

    @Test
    fun `phase4 sector names use each sector's real type`() {
        assertEquals("Row 3", formatSectorName(2))
        assertEquals("Column 5", formatSectorName(13))
        assertEquals("Box 8", formatSectorName(25))
    }

    @Test
    fun `phase4 finned s1 suppresses the fin-is-at clause when there are no fins`() {
        // A finned fish whose fin list is empty (real match, no fin cells) must
        // suppress the "fin is at:" clause (gap 9), never render it dangling.
        LanguageConfig.setLanguage("en")
        val steps = generateFinnedFishSteps("Finned X-Wing", fishMatch(), dummyEliminations)
        val s1 = steps.first { it.stepNumber == 1 }
        assertTrue(s1.description.contains("descriptionNoFin"),
            "expected the no-fin s1 variant when the fin list is empty, got: ${s1.description}")
        val rendered = HintStringInterpolation.interpolate(s1.description)
        assertTrue(!rendered.contains("fin is at"),
            "empty fin list must not render a dangling 'fin is at:' clause: $rendered")
        assertTrue(!isMissingMarker(rendered),
            "finned no-fin s1 rendered as a missing-key marker: $rendered")
    }

    @Test
    fun `phase4 finned and plain fish return no steps when sectors cannot be extracted`() {
        // Partial/total reflection failure (non-fish match) must not fabricate
        // "Look at ." — the generators emit 0 steps and the frontend
        // hints.common fallback covers the render.
        assertTrue(generateFinnedFishSteps("Finned X-Wing", dummyMatch, dummyEliminations).isEmpty(),
            "finned generator must return no steps for a non-fish match")
        assertTrue(generateFishSteps("X-Wing", dummyMatch, dummyEliminations).isEmpty(),
            "fish generator must return no steps for a non-fish match")
    }

    @Test
    fun `phase4 reworded fish s1 renders without the missing-key marker`() {
        LanguageConfig.setLanguage("en")
        val steps = generateFishSteps("X-Wing", fishMatch(), dummyEliminations)
        val s1 = steps.first { it.stepNumber == 1 }
        val rendered = HintStringInterpolation.interpolate(s1.description)
        assertTrue(!isMissingMarker(rendered),
            "reworded x-wing s1 rendered as a missing-key marker: $rendered")
        assertTrue(rendered.contains("digit 7"), "x-wing s1 should name the digit: $rendered")
    }

    @Test
    fun `phase4 finned and sashimi s2 no longer claim eliminations are inside the fin box`() {
        // gap 4: the old text said eliminations are "restricted to {{boxName}}
        // (where the fin is)" — wrong. The corrected text must reference cells that
        // SEE the fin (and lie in a cover line).
        LanguageConfig.setLanguage("en")
        for (name in listOf("Finned X-Wing", "Sashimi X-Wing")) {
            val steps = generateFinnedFishSteps(name, fishMatch(fins = intArrayOf(2)), dummyEliminations)
            val s2 = steps.first { it.stepNumber == 2 }
            val rendered = HintStringInterpolation.interpolate(s2.description)
            assertTrue(rendered.contains("see the fin"),
                "[$name] finned s2 must reference cells that see the fin: $rendered")
            assertTrue(!rendered.contains("where the fin is"),
                "[$name] finned s2 must not claim eliminations are inside the fin's box: $rendered")
        }
    }

    @Test
    fun `phase4 reworded fish s1 and finned no-fin keys resolve under en and a non-en locale`() {
        val keys = listOf(
            "{{hints.x_wing.step1.description|baseNames=Row 2 and Row 7|digit=5|baseTypeText=rows|coverNames=Column 3 and Column 7|coverTypeText=columns}}",
            "{{hints.swordfish.step1.description|baseNames=Row 1 and Row 5 and Row 9|digit=7|baseTypeText=rows|coverNames=Column 2 and Column 4 and Column 6|coverTypeText=columns}}",
            "{{hints.jellyfish.step1.description|baseNames=Row 1 and Row 2 and Row 3 and Row 4|digit=9|baseTypeText=rows|coverNames=Column 1 and Column 2 and Column 3 and Column 4|coverTypeText=columns}}",
            "{{hints.basic_fish.step1.description|digit=5|baseTypeText=rows and boxes|baseNames=Row 3 and Box 4|coverTypeText=columns|coverNames=Column 2 and Column 5}}",
            "{{hints.finned_fish.step1.descriptionNoFin|fishType=X-Wing|baseNames=Row 2|digit=5|coverNames=Column 3}}",
            "{{hints.finned_fish.step2.description|digit=5}}",
            "{{hints.finned_fish.step3.description|coverTypeText=columns|digit=5|cells=R1C1, R1C9}}",
            "{{hints.sashimi_fish.step1.descriptionNoFin|fishType=X-Wing|baseNames=Row 2|digit=5|coverNames=Column 3}}",
            "{{hints.finned_fish.step3.description|coverTypeText=columns|digit=5|cells=R1C1}}"
        )
        for (locale in listOf("en", "de")) {
            for (k in keys) assertResolves(locale, k)
        }
    }

    // --- Phase 5: colouring, cycle & chain-edge accuracy (gaps 6, 7, 9; non-AICMatch fallbacks) ---

    @Test
    fun `phase5 coloring s3 names the eliminated digit`() {
        LanguageConfig.setLanguage("en")
        val rendered = HintStringInterpolation.interpolate(
            "{{hints.coloring.step3.description|digit=7|cells=R1C1, R1C2}}"
        )
        assertTrue(!isMissingMarker(rendered), "coloring s3 rendered as a missing-key marker: $rendered")
        assertTrue(rendered.contains("Remove 7 from"), "coloring s3 must name the digit: $rendered")
        assertTrue(!rendered.contains("{{"), "coloring s3 left an unresolved placeholder: $rendered")
    }

    @Test
    fun `phase5 coloring s3 is plural-safe for multiple digits`() {
        LanguageConfig.setLanguage("en")
        val rendered = HintStringInterpolation.interpolate(
            "{{hints.coloring.step3.description|digit=3, 7|cells=R1C1, R1C2}}"
        )
        assertTrue(rendered.contains("Remove 3, 7 from"), "coloring s3 should list both digits: $rendered")
        assertTrue(!rendered.contains("{{"), "coloring s3 left an unresolved placeholder: $rendered")
    }

    @Test
    fun `phase5 x-cycle s1 guards the digit clause and is plural-safe`() {
        LanguageConfig.setLanguage("en")
        // Unknown digit set -> empty clause -> no fabricated "digit 0".
        val noDigit = HintStringInterpolation.interpolate(
            "{{hints.x_cycle.step1.description|cycleSize=6|digitClause=|cells=R1C1, R1C2}}"
        )
        assertTrue(!noDigit.contains("digit 0"), "x-cycle s1 must not fabricate 'digit 0': $noDigit")
        assertTrue(!noDigit.contains("{{"), "x-cycle s1 left an unresolved placeholder: $noDigit")

        val single = HintStringInterpolation.interpolate(
            "{{hints.x_cycle.step1.description|cycleSize=6|digitClause= involving digit 7|cells=R1C1, R1C2}}"
        )
        assertTrue(single.contains("involving digit 7"), "x-cycle s1 should name the single digit: $single")

        val multi = HintStringInterpolation.interpolate(
            "{{hints.x_cycle.step1.description|cycleSize=7|digitClause= involving digits 3, 7|cells=R1C1}}"
        )
        assertTrue(multi.contains("involving digits 3, 7"), "x-cycle s1 should be plural-safe: $multi")
    }

    @Test
    fun `phase5 long aic s2 drops the dangling step-by-step intro`() {
        LanguageConfig.setLanguage("en")
        // Long chain (>6 nodes) -> the generator picks descriptionLong, which must not
        // keep the "step by step:" intro that would dangle over an empty {{chainSteps}}.
        val long = HintStringInterpolation.interpolate(
            "{{hints.aic.step2.descriptionLong|nodeCount=12}}"
        )
        assertTrue(!long.contains("step by step"), "long AIC s2 must not keep the step-by-step intro: $long")
        assertTrue(long.contains("12"), "long AIC s2 should state the link count: $long")
        assertTrue(!long.contains("{{"), "long AIC s2 left an unresolved placeholder: $long")

        val short = HintStringInterpolation.interpolate(
            "{{hints.aic.step2.description|chainSteps=If 5 is NOT in R1C1, then 5 MUST be in R1C9}}"
        )
        assertTrue(short.contains("step by step"), "short AIC s2 should keep the step-by-step intro: $short")
        assertTrue(short.contains("If 5 is NOT in R1C1"), "short AIC s2 should list the chain steps: $short")
    }

    @Test
    fun `phase5 non-AICMatch colouring fallback is localized and resolves`() {
        LanguageConfig.setLanguage("en")
        val steps = generateColouringSteps("Simple Colouring", dummyMatch, dummyEliminations)
        val s1 = steps.first { it.stepNumber == 1 }
        assertTrue(s1.description.contains("coloring_generic"), "expected the generic colouring fallback: ${s1.description}")
        val rendered = HintStringInterpolation.interpolate(s1.description)
        assertTrue(!isMissingMarker(rendered), "colouring fallback s1 rendered as a missing-key marker: $rendered")
        assertTrue(!rendered.contains("{{"), "colouring fallback s1 left an unresolved placeholder: $rendered")
    }

    @Test
    fun `phase5 non-AICMatch cycle fallback guards the digit and localizes`() {
        LanguageConfig.setLanguage("en")
        // No eliminations -> digit unknown -> the digit clause is omitted (no "digit 0").
        val noElim = generateCycleSteps("X-Cycle", dummyMatch, emptyList())
        val s1 = noElim.first { it.stepNumber == 1 }
        assertTrue(s1.description.contains("x_cycle_generic"), "expected the generic cycle fallback: ${s1.description}")
        val rendered = HintStringInterpolation.interpolate(s1.description)
        assertTrue(!rendered.contains("digit 0"), "cycle fallback s1 must not fabricate 'digit 0': $rendered")
        assertTrue(!rendered.contains("{{"), "cycle fallback s1 left an unresolved placeholder: $rendered")

        // With an elimination -> the digit clause is present.
        val withElim = generateCycleSteps("X-Cycle", dummyMatch, dummyEliminations)
        val s1b = withElim.first { it.stepNumber == 1 }
        val renderedB = HintStringInterpolation.interpolate(s1b.description)
        assertTrue(renderedB.contains("digit 7"), "cycle fallback s1 should name the digit when known: $renderedB")
    }

    // --- Phase 6: Kite + BUG empty-data guards (gap 9, D1) ---

    @Test
    fun `phase6 bug s2 uses descriptionGeneric when no eliminations and does not fabricate R1C1`() {
        LanguageConfig.setLanguage("en")
        // Empty eliminations means targetCell cannot be determined (null).
        val steps = service.hint.explanations.generateBugSteps(emptyList())
        val s2 = steps.first { it.stepNumber == 2 }
        assertTrue(s2.description.contains("descriptionGeneric"), "expected generic description for unknown target cell: ${s2.description}")
        val rendered = HintStringInterpolation.interpolate(s2.description)
        assertTrue(!rendered.contains("R1C1"), "bug s2 generic must not fabricate 'R1C1': $rendered")
        assertTrue(!rendered.contains("{{"), "bug s2 generic left an unresolved placeholder: $rendered")
        assertTrue(!isMissingMarker(rendered), "bug s2 generic rendered as missing-key marker: $rendered")
    }

    @Test
    fun `phase6 kite s2 is suppressed when chainDescription is empty`() {
        LanguageConfig.setLanguage("en")
        // A match with empty base/cover sectors will cause chainDescription to be empty,
        // so step 2 should not be generated even if there are eliminations.
        // We can pass a subset match to generateKiteSteps, which will fail the field reflections.
        val steps = service.hint.techniques.generateKiteSteps("2-String Kite", dummyMatch, dummyEliminations, "")
        // With the chain step suppressed, no step may reference the kite.step2
        // template — and the elimination step must renumber to step 2 (never
        // render "Step 1" followed by "Step 3").
        assertTrue(steps.none { it.description.contains("kite.step2") },
            "expected kite step 2 to be suppressed when chain is empty")
        assertEquals(listOf(1, 2), steps.map { it.stepNumber },
            "kite steps must be consecutively numbered after suppression: ${steps.map { it.stepNumber }}")
        // Step 1 should still exist and render correctly without fabricating data.
        val s1 = steps.first { it.stepNumber == 1 }
        val rendered = HintStringInterpolation.interpolate(s1.description)
        assertTrue(!rendered.contains("{{"), "kite s1 left an unresolved placeholder: $rendered")
        assertTrue(!isMissingMarker(rendered), "kite s1 rendered as missing-key marker: $rendered")
    }

    // --- Phase 8: redundancy & shared-text consolidation (R1–R4) ---

    @Test
    fun `phase8 pointing and claiming s1 share one boxLineInteraction key`() {
        // R1: the two s1 texts were byte-identical; both now resolve through the
        // single hints.common.boxLineInteraction key so they can't drift.
        for (locale in listOf("en", "de")) {
            assertResolves(locale, "{{hints.common.boxLineInteraction|baseHouse=Box 1|digit=5|coverHouse=Row 3}}")
        }
        LanguageConfig.setLanguage("en")
        for (name in listOf("Pointing Candidates", "Claiming Candidates")) {
            val s1 = service.hint.explanations.generateIntersectionSteps(name, dummyMatch, dummyEliminations)
                .first { it.stepNumber == 1 }
            assertTrue(s1.description.contains("boxLineInteraction"),
                "[$name] s1 must use the shared key, got: ${s1.description}")
            val rendered = HintStringInterpolation.interpolate(s1.description)
            assertTrue(!isMissingMarker(rendered), "[$name] s1 rendered as missing-key marker: $rendered")
            assertTrue(!rendered.contains("{{"), "[$name] s1 left an unresolved placeholder: $rendered")
        }
    }

    @Test
    fun `phase8 sashimi s2 and s3 reuse the shared finned_fish keys`() {
        // R2: finned_fish and sashimi_fish s2/s3 were verbatim duplicates; the
        // sashimi generator now emits the finned_fish keys (its s1 stays its own).
        LanguageConfig.setLanguage("en")
        val steps = generateFinnedFishSteps("Sashimi X-Wing", fishMatch(fins = intArrayOf(2)), dummyEliminations)
        val s2 = steps.first { it.stepNumber == 2 }
        val s3 = steps.first { it.stepNumber == 3 }
        assertTrue(s2.description.contains("finned_fish.step2"),
            "sashimi s2 must use the shared finned_fish key: ${s2.description}")
        assertTrue(s3.description.contains("finned_fish.step3"),
            "sashimi s3 must use the shared finned_fish key: ${s3.description}")
        for (s in listOf(s2, s3)) {
            val rendered = HintStringInterpolation.interpolate(s.description)
            assertTrue(!isMissingMarker(rendered), "sashimi shared step rendered as missing-key marker: $rendered")
        }
    }

    @Test
    fun `phase8 wing s3 is one shared key and no longer mentions pincers`() {
        // R3: the five wing families shared an s3 that said "both pincers" (wrong for
        // W-Wing/WXYZ/generic); all now resolve through the single
        // hints.common.wingElimination key.
        for (locale in listOf("en", "de")) {
            assertResolves(locale, "{{hints.common.wingElimination|cells=R1C1, R1C9|digit=5}}")
        }
        LanguageConfig.setLanguage("en")
        val steps = service.hint.techniques.generateWingSteps("W-Wing", dummyMatch, dummyEliminations)
        val s3 = steps.first { it.stepNumber == 3 }
        assertTrue(s3.description.contains("wingElimination"),
            "wing s3 must use the shared key: ${s3.description}")
        val rendered = HintStringInterpolation.interpolate(s3.description)
        assertTrue(!rendered.contains("pincers"), "wing s3 must not claim pincers: $rendered")
        assertTrue(rendered.contains("see all the relevant wing cells"),
            "wing s3 must reference the wing cells: $rendered")
        assertTrue(!isMissingMarker(rendered), "wing s3 rendered as missing-key marker: $rendered")
    }

    @Test
    fun `phase8 als_identify s1 no longer repeats the cells-and-candidates phrase`() {
        // R4: the old text stated "{{cellCount}} cells … with {{digitCount}} candidates"
        // twice in one sentence; the duplicate is removed.
        LanguageConfig.setLanguage("en")
        val rendered = HintStringInterpolation.interpolate(
            "{{hints.als_identify.step1.description|alsName=ALS A|cellCount=3|cells=R1C1, R1C2, R1C3|digitCount=4|digits=1, 2, 3, 4}}"
        )
        assertEquals(1, rendered.split("with 4 candidates").size - 1,
            "als_identify s1 must state the count once, not twice: $rendered")
        assertTrue(!rendered.contains("{{"), "als_identify s1 left an unresolved placeholder: $rendered")
    }
}
