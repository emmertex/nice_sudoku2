# Hint Techniques — Improvement & Gap Plan

> Companion to `HINT_TECHNIQUES.md` (the source of truth for *why* each item is a problem).
> This file is the *what/where/how to ship it* breakdown, phased so each phase fits a
> fresh context well under ~200k tokens.
>
> **Status:** Phase 1 (EN-fallback resolver + `check_hint_keys.py` / routing-pin guardrails), Phase 2 (ALS per-set titles + UR elimination types 3-6, type-0->generic guard), Phase 3 (Sue-de-Coq / Forcing Chains / Nishio / ChainLike hardcoded->key-based, XY-Chain text rewritten weak-link-only), Phase 4 (fish s1 valid count claim + combined base/cover type + per-sector names; finned/sashimi "cells that see the fin" + empty-fin guard), Phase 5 (colouring s3 names the digit; AIC long-chain s2 no dangling intro; X-Cycle digit guard + plural-safe; localized non-AICMatch colouring/cycle fallbacks), Phase 6 (Kite empty-chain + BUG empty-cell fallbacks), Phase 7 (Routing Normalization), Phase 8 (Redundancy & shared-text), and Phase 9 (variants + AIC+ALS + Description/Priority map) are implemented and verified (`check_hint_keys.py` green, `:backend`/`:shared` tests green, `RoutingPinTest` extended).
>
> **Decisions already made with the owner:**
> - New/changed hint keys are **authored in `en.json` only**. The other 10 locales are
>   left to show English for those keys until a later translation pass.
> - To make that *actually* happen (the resolver currently loads only the current locale and
>   renders a missing key as `[key]`), **Phase 1 adds an EN-fallback to the resolver.**
>   This is the single load-bearing change that shrinks every later phase.

---

## 0. Context model — how each phase stays under ~200k

### The files involved (with the section you actually need)

| File | Lines | What a phase touches |
|------|-------|----------------------|
| `backend/src/main/kotlin/service/hint/explanations/ExplanationStepGenerator.kt` | 119 | the `when` dispatch (L42–114) — Phase 7 |
| `backend/src/main/kotlin/service/hint/helpers/LanguageKeyBuilder.kt` | 53 | `hintKey` L30, `normalizeTechniqueName` L45 — Phase 7 |
| `backend/.../explanations/AdvancedExplanations.kt` | 497 | BUG L9, SueDeCoq L66, ForcingChain L136, Nishio L204, ChainLike L271, Intersection L336 — Phases 3, 6 |
| `backend/.../explanations/ChainExplanations.kt` | 356 | Chain(AIC) L11, ALS L163, Generic L298 — Phase 3 (ALS), 9 |
| `backend/.../explanations/CycleExplanations.kt` | 260 | X-Cycle L10, Colouring L130 — Phase 5 |
| `backend/.../explanations/RectangleExplanations.kt` | 383 | UR L66, EmptyRectangle L244 — Phase 2, 5 |
| `backend/.../explanations/SubsetExplanations.kt` | 403 | Subset L9, Single L241 — Phase 8 |
| `backend/.../techniques/BasicFishTechniques.kt` | 650 | Fish L142, Finned L499 — Phase 4 |
| `backend/.../techniques/KiteTechniques.kt` | 668 | Kite L278 — Phase 6 |
| `backend/.../techniques/WingTechniques.kt` | 337 | Wing L213 — Phases 4, 8 |
| `backend/src/main/kotlin/service/hint/metadata/TechniqueMetadata.kt` | 106 | priority map L9–62, `normalizeTechniqueKey` L64 — Phase 7 |
| `shared/src/commonMain/kotlin/i18n/LanguageManager.kt` | 148 | `getString` L68, `loadLanguage` L44 — Phase 1 |
| `shared/src/commonMain/kotlin/i18n/HintStringInterpolation.kt` | 78 | `interpolate` L17 — reference only |
| `web/src/jsMain/resources/languages/en.json` | ~1011 | `hints.*` subtree only — Phases 1–10 |
| `web/src/jsMain/resources/languages/*.json` (10 others) | ~1,000–1,500 each | **never read whole; never edited** (EN-fallback covers them) |

### Three tactics that keep every phase small

- **J1 — JSON via script, never whole-file.** To *see* a key: a python one-liner that
  prints only `en.json`'s `hints.<subtree>`. To *change* keys: a python script that loads
  `en.json`, edits the `hints` subtree, and writes it back. A full 1,000+ line locale file
  is the single biggest token cost in this repo — we never put one in context. (The 10
  non-EN files are not read or edited at all, per the decision above.)
- **K1 — read the target function, not the file.** Use `read_file` with `start_line`/
  `end_line` (the line numbers above are already known) instead of loading 400–670-line
  generator files.
- **P1 — one fresh context per phase.** A fresh agent session reads only: `HINT_TECHNIQUES.md`
  (~266 lines), this plan (one phase is one screen), and the specific files listed for that
  phase. Nothing else.

### Governing facts (verified in code)

1. **No EN fallback today.** `LanguageManager.getString` (L68–96) loads one locale and
   returns `"[key]"` if any segment is missing. `HintStringInterpolation.interpolate` (L49)
   passes that through verbatim, so a missing key renders literally as `[hints.…]`.
2. **11 locale files** exist (`en, es, de, zh, hi, fr, ar, bn, ru, pt, ur` — see
   `LanguageManager.supportedLanguages`, L39). All share the same `hints` structure; key
   gaps affect every locale.
3. **Dispatch is name-substring based** (`ExplanationStepGenerator` L42–114), so routing
   depends on the exact spelling the solver emits (human `W-Wing` vs machine `W_WING`).
4. **Frontend safety net is already correct** — all three render paths use
   `steps.isNotEmpty() ? steps : fallback`, and the fallback builds 1–3 steps from
   `hints.common.*`. This covers any 0-step generator. **Do not remove it.**

---

## Gap / issue → Phase map

Legend from `HINT_TECHNIQUES.md` (✅ good, ⚠️ localized-but-wrong, ❌ hardcoded English).

| # | Issue (from HINT_TECHNIQUES.md) | Severity | Phase |
|---|---------------------------------|----------|-------|
| 1 | ALS per-ALS step titles reference `als.step2.title`… (only `step1` exists) → `[…]` | broken render | **2** |
| 2 | UR step-3 `elim_type3`–`type6` + `type0` missing → `[…]` | broken render | **2** |
| 3 | Hardcoded English steps: Sue-de-Coq, Forcing Chains, Nishio, ChainLike, non-`AICMatch` colouring/cycle fallbacks | unlocalized | **3 / 5** |
| 3b | ChainLike text is **wrong** for XY-chains (claims strong links; XY-chains are weak-link-only) | inaccurate | **3** |
| 4 | Finned/Sashimi s2/s3 claim eliminations restricted to fin's **box** (should be "cells that see the fin") — 6 techniques | inaccurate | **4** |
| 5 | Fish s1 "digit appears in exactly N lines" (false); base/cover type from first sector only (Franken mislabel) — 5 techniques | inaccurate | **4** |
| 6 | Colouring s3 "Remove the candidate(s) from {{cells}}" — no `{{digit}}` — Simple Colouring + 3D Medusa | inaccurate | **5** |
| 7 | AIC long-chain s2: `isLong` passed but template doesn't branch → dangling intro over empty `{{chainSteps}}` | inaccurate | **5** |
| 8 | Conditional steps can shrink a hint to 1 step (no eliminations) — **by design** | none (accepted) | — |
| 9 | Fabricated values on empty data: BUG→`R1C1`, X-Cycles→"digit 0", Kite→empty chain, Finned→empty fin list | silent-incorrect | **4 / 5 / 6** |
| 10 | Routing fragility: `2_STRING_KITE_FISH`→fish, `W_WING`→`generic_wing`, `UVWXYZ-Wing`→`xyz_wing` | silent degrade | **7** |
| R1 | `pointing` s1 == `claiming` s1 (byte-identical, drift risk) | cosmetic | **8** |
| R2 | `finned_fish` s2/s3 == `sashimi_fish` s2/s3 (verbatim dup) | cosmetic | **8** |
| R3 | wing s3 "pincers" inaccurate for W-Wing/WXYZ; "(or all wing cells)" hedge | cosmetic | **8** |
| R4 | `als_identify.step1` repeats "N cells … M candidates" twice | cosmetic | **8** |
| V1 | `UVWXYZ-Wing` routed to `xyz_wing` (6-cell wing described as 3-cell XYZ) | content wrong | **9** |
| V2 | `AIC+ALS` (`AIC___ALS`) routing unverified — depends on solver's emitted name | unknown | **9** |
| V3 | `XY-Chain (Type 1)`, `ALS-Chain` → hardcoded ChainLike | unlocalized | **3** |
| V4 | `Hidden XY (Type 1)` — no "Chain" substring → `aic`/`generic` depending on match type | depends | **9** |
| D1 | Kite doc said "3 or 0 steps"; real behavior is **1 or 3** (s1 unconditional) | doc-only | **6** (note) |
| — | `generic_wing` thin-but-valid for Split/Strong/Inverted-H/L/M wings + L-Wing | acceptable | — (accepted) |

### Accepted / no-action (and why)
- **Gap 8 (1-step hints):** by design; the `hints.common` fallback only fires for 0 steps. Leave it.
- **`generic_wing` thin explanations** for the rare diabolical-tier wings: localized + correct enough. Leave unless the owner wants richer text.
- **`pointing`/`claiming` identical s1 (R1):** direction-agnostic and correct today; only a *drift* risk. Handled as an *optional* consolidation in Phase 8, not required.

---

## Phase 1 — Foundation: EN-fallback resolver + guardrails

**Goal:** (a) make the EN-only-keys decision actually work, (b) build the regression net every
later phase checks against.

**Files:** `shared/.../i18n/LanguageManager.kt`; a new script `scripts/check_hint_keys.py`;
a new test (extend `backend/src/test/kotlin/service/TechniqueDescriptionTest.kt` or add a
sibling).

**Work items**
1. **EN-fallback in `LanguageManager.getString`** (L68–96): before returning `"[key]"`,
   look the key up in `en.json` and return that instead.
   - *Design constraint:* do **not** call `loadLanguage("en")` (it clobbers
     `currentLanguage`). Load `/languages/en.json` into a *separate* temp `JsonObject`
     (reuse `ResourceLoader.loadResource` + the existing `Json` parser) and resolve against it.
   - Cache the EN `JsonObject` once so repeated lookups are cheap.
   - Keep the existing `"[key]"` return for keys missing from **both** locales (truly broken).
2. **Key-completeness script** `scripts/check_hint_keys.py`:
   - Grep all `{{hints.<path>…}}` and `hintKey(...)` key paths referenced in the Kotlin
     backend (the `LanguageKeyBuilder`-built ones + any literal `{{hints.…}}`).
   - Assert each referenced leaf key path exists in `en.json` (the authoritative source now
     that non-EN fall back to it). Report any missing.
   - Also flag: variables passed by a generator that the template never consumes (e.g. the
     `isLong` in gap 7), and templates with a `{{var}}` that no branch supplies.
3. **Routing pin test:** enumerate every name variant in `TechniqueMetadata` (L9–62, 56
   variants) and assert `generateExplanationSteps` dispatches to the *intended* generator
   family (e.g. `W_WING`→wing keys, `2_STRING_KITE_FISH`→kite, `XY_CHAIN`→ChainLike).
   Implement as a dry-run: for each variant, run the generator and assert the **emitted key
   prefix** (the `hints.<family>` it produced) matches expectation. This test *red* until
   Phase 7 fixes routing.

**Validation:** script runs clean on current keys except the *known* missing ones from gaps
1/2 (expected failures — this is the net). Routing test is red for the known-misrouting
variants. `./gradlew :shared:test :backend:test` (and `:web` compile) green.

**Context budget:** small. `LanguageManager.kt` (148) + one small script + one small test +
the `TechniqueMetadata` map. **≈ well under 20k.**

**Checkpoint:** EN-fallback in place (verified: a key present only in `en.json` resolves
under a non-EN locale instead of `[…]`); guardrails exist and are wired into CI/tests.

---

## Phase 2 — Missing additive keys (gap 1, gap 2)

> ✅ **Done.** `als.step2.title` added; the per-ALS title now uses that fixed key instead of
> the unbounded `stepNum - 1` counter (whose numbers were unpredictable once "connecting"
> steps interleaved). `unique_rectangle_elim_type3…6` added; the step-3 `elim_type$type`
> template now maps `type 0` → `unique_rectangle_generic` (no dead `type0` key). The
> `als_identify` R4 redundancy is left untouched (Phase 8). Verified: `check_hint_keys.py`
> green, `:backend` tests green, and `HintKeyResolutionTest` (no `[…]` under `en` and
> non-EN locales; real `generateUniqueRectangleSteps` driven for type-3 + type-0).

**Goal:** stop the literal `[…]` renders for multi-ALS titles and UR type 3–6.
Purely additive to `en.json` + (for UR type 0) a small code map. No other locale touched.

**Files:** `en.json` (`hints.als`, `hints.unique_rectangle*`); optionally
`RectangleExplanations.kt` (L66, UR type extraction) for the type-0 map.

**Work items**
1. **Gap 1 — ALS titles.** `generateALSSteps` (ChainExplanations L163) builds per-ALS titles
   from `{{hints.als.stepN.title|alsName=…}}` for N ≥ 2; only `step1` exists.
   - Add `hints.als.step2.title` (e.g. `"Identify {{alsName}}"`) — and `step3.title`/etc. if
     the generator can emit higher N (check the max N the loop reaches).
   - *(Alt from the doc: give `als_identify.step1` a title and point the per-ALS key at it —
     prefer the explicit `stepN.title` add; it's clearer.)*
2. **Gap 2 — UR type descriptions.** Code requests
   `{{hints.unique_rectangle_elim_type$N.step1.description}}`. `en.json` has only `type1`/`type2`
   (+ `unique_rectangle_generic`).
   - Add `unique_rectangle_elim_type3` … `type6`, each with a `step1.description` accurate to
     that UR type (replicate the existing `type1`/`type2` shape: a single `step1.description`).
   - **Type 0 / generic case:** when type extraction fails the code uses `type = 0`. Prefer a
     *code* fix — map `type 0` → the existing `unique_rectangle_generic` key — rather than
     adding a `type0` key (smaller, avoids a dead key). If the code path can't be changed
     cheaply, add `unique_rectangle_elim_type0` instead.
3. Verify the **ALS identify redundancy** (R4) is *not* in this phase (it's cosmetic → Phase 8).

**Validation:** `check_hint_keys.py` now passes for ALS + UR keys. Manual: render a
multi-ALS hint (ALS-XY/XZ) and a UR type-3+ hint; confirm no `[…]`. `:backend` tests green.

**Context budget:** small. `en.json` `hints.als` + `hints.unique_rectangle*` (via python, a few KB)
+ `generateALSSteps` + `generateUniqueRectangleSteps` (targeted reads). **≈ under 20k.**

**Checkpoint:** no `[…]` from gaps 1/2 in any locale (EN fallback covers the rest).

---

## Phase 3 — Diabolical hardcoded→localized + XY-Chain rewrite (gap 3, 3b, V3)

> ✅ **Done.** The four hardcoded generators now emit key-based steps: `generateSueDeCoqSteps`
> → `hints.sue_de_coq.*` (ELI5 prose kept verbatim; s3 takes `{{digits}}`/`{{cells}}`);
> `generateForcingChainSteps` → `hints.forcing_chains.*` with the s1 text fixed so it no
> longer claims the highlighted (elimination) cells are the starting cell;
> `generateNishioSteps` → `hints.nishio.*` with s1 now naming the assumed candidate
> (`{{digits}}` in `{{cells}}`) instead of only implying it via the eliminations.
> `generateChainLikeSteps` was rewritten **first** — the XY-Chain text no longer claims
> strong links (XY-chains are weak-link-only) — then localized: the generator branches on
> whether the name contains "XY" to pick `hints.xy_chain.*` vs `hints.generic_chain.*`
> (the latter covers ALS-Chain, Alternating Inference Chains, X - Chains, Kraken Chains,
> …). The "deeper Sue-de-Coq identify-the-ALSs" improvement stays flagged as a later
> optional pass (thinness is accepted). Verified: `check_hint_keys.py` green (0 errors,
> no new warnings), `:backend`/`:shared` tests green, and a render sweep of the four
> techniques (no `[…]`, no leftover `{{var}}`, XY-Chain text factually correct).

**Goal:** convert the untranslated diabolical-tier steps to key-based steps and fix the
**incorrect** XY-Chain text. This is the biggest "content" phase but is one file + EN keys.

**Files:** `AdvancedExplanations.kt` (SueDeCoq L66, ForcingChain L136, Nishio L204,
ChainLike L271); `en.json` (new `hints.*` subtrees).

**Work items**
1. **Sue-de-Coq** (L66–134): replace the 3 hardcoded step strings with
   `hintKey("sue_de_coq", n, …)` calls; add `hints.sue_de_coq.step1/2/3.{title,description}`
   to `en.json` (keep the good ELI5 prose, just move it into keys + add the
   `{{alsName}}`/`{{cells}}`/`{{digit}}` variables the generator already computes).
   - The doc notes the steps never identify the actual ALSs/intersection (thin). Keep the
     rewrite in scope to *localizing* here; a deeper "identify the real ALS" improvement is a
     candidate for a later optional pass (flag it, don't block on it).
2. **Forcing Chains** (L136–202): same treatment → `hints.forcing_chains.*`. Note the doc's
   accuracy nit: steps describe "the starting cell" but highlight *elimination* cells. Fix the
   text to match what's actually highlighted, or pass the real start cell if available.
3. **Nishio** (L204–269): → `hints.nishio.*`. Ensure the assumed candidate is actually shown
   (currently only implied via eliminations).
4. **ChainLike / XY-Chain (3b) — REWRITE before localizing.** `generateChainLikeSteps`
   (L271–334) is hardcoded **and** wrong: it says the chain uses "• Strong links • Weak links".
   - **XY-chains are weak-link-only** (bivalue cells, no strong links). Rewrite s1 to describe
     a weak-link-only chain and the endpoint elimination, *then* move it into keys
     (`hints.xy_chain.*`). Do **not** translate the current text into 10 languages first.
   - ChainLike also serves `XY-Chain (Type 1)` and `ALS-Chain` (V3) and the human-name AIC
     ("Alternating Inference Chains") — the rewrite must be generic enough for all of them, or
     branch on `techniqueName` inside the generator to pick `xy_chain` vs `generic_chain` keys.
5. **Non-`AICMatch` fallbacks** in colouring + cycle generators → Phase 5 (they live in
   `CycleExplanations.kt`), *not* here.

**Validation:** `check_hint_keys.py` green for the new subtrees. Manual: render a Sue-de-Coq,
a Forcing Chain, a Nishio, and an **XY-Chain** hint; confirm localized + that the XY-Chain
text no longer claims strong links. `:backend` tests green; routing test unchanged (still red
for the Phase-7 variants).

**Context budget:** medium. One 497-line file read in 3–4 targeted slices (~3k tokens) +
authoring ~15 EN keys (a few KB via python). **≈ 30–50k.** Safe.

**Checkpoint:** the four diabolical techniques render localized, correct steps; XY-Chain text
is factually right.

---

## Phase 4 — Fish & Finned text accuracy (gap 4, 5, 9-partial)

> ✅ **Done.** `generateFishSteps` s1 no longer claims the digit “appears in exactly N
> lines” — it states the valid claim (within the base lines, the digit occurs only in the
> cover lines). Base/cover type text is derived from the *whole* sector set via the new
> `describeSectorTypeText` (a Franken row+box base reads “rows and boxes”), and sector
> names use each sector's real type via `formatSectorName`. Finned/Sashimi s2/s3 no longer
> claim eliminations are “restricted to {{boxName}} (where the fin is)” — they reference
> cells that *see the fin*; and s1 now falls back to a `descriptionNoFin` template when
> the fin list can't be extracted, so “The fin is at:” never renders empty. Verified:
> `check_hint_keys.py` green (fish-family unused-var warnings dropped, 0 new), `:backend`
> tests green, and 6 new `HintKeyResolutionTest` cases (mixed-base type text, per-sector
> names, empty-fin guard, reworded-fish-s1 render, “see the fin” content, en+non-en
> resolve).

**Goal:** fix the two most-cited *inaccurate* text clusters (fish s1 count claim; finned
"box" over-claim) plus the Franken base/cover mislabel and the empty-fin guard.

**Files:** `techniques/BasicFishTechniques.kt` (`generateFishSteps` L142,
`generateFinnedFishSteps` L499); `en.json` (`hints.x_wing`, `swordfish`, `jellyfish`,
`basic_fish`, `finned_fish`, `sashimi_fish`).

**Work items**
1. **Fish s1 count claim (gap 5).** Current s1: "digit {{digit}} appears in exactly {{baseCount}}
   {{baseTypeText}}" — false in general. Reword to the valid claim: *within the base lines*,
   the digit occurs only in the cover lines. Edit the `x_wing`/`swordfish`/`jellyfish`/
   `basic_fish` s1 templates (shared family — one edit each).
2. **Base/cover type from first sector only (gap 5b).** `baseType = getSectorType(baseIndices.first())`
   (L187–202). For Franken fish (row + box) this mislabels. Fix: derive a *combined* type text
   (e.g. "rows and boxes") when the set is mixed, and pass that to the template.
3. **Finned/Sashimi s2/s3 (gap 4).** "restricted to {{boxName}} (where the fin is)" is wrong —
   eliminations are restricted to **cells that see the fin** (and lie in a cover line). Reword
   `finned_fish` + `sashimi_fish` s2/s3 accordingly. *(R2's verbatim dup is cleaned up in
   Phase 8; here just make both correct.)*
4. **Empty-fin guard (gap 9, finned).** If `fins` reflection fails, s1's "The fin is at:
   {{finCells}}" renders empty. Suppress the clause or fall back to the base/cover names when
   the list is empty.

**Validation:** `check_hint_keys.py` green. Manual: render X-Wing, a Franken X-Wing (confirm
"rows and boxes"), and a Finned X-Wing (confirm "cells that see the fin"). `:backend` green.

**Context budget:** medium. `BasicFishTechniques.kt` read in 2 targeted slices (~2k) + ~8 EN
template edits via python. **≈ 30k.** Safe.

**Checkpoint:** fish + finned s1–s3 are factually correct; no empty "fin is at:" lines.

---

## Phase 5 — Colouring, Cycle & chain-edge accuracy (gap 3-fallback, 6, 7, 9-partial)

> ✅ **Done.** `coloring.step3` now names the eliminated digit(s) (`{{digit}}`), passed
> from `generateColouringSteps` as the distinct elimination digits (plural-safe for 3D
> Medusa's multi-digit eliminations). The AIC long-chain s2 no longer dangles "Let's follow
> the chain step by step:" over an empty `{{chainSteps}}` — the generator branches on
> `nodes.size > 6` to pick a new `aic.step2.descriptionLong` variant. X-Cycles s1 derives
> its digit(s) from the *chain nodes* (never a fabricated `digit 0`): an unknown set omits
> the clause, one digit reads "involving digit N", several "involving digits N, M". The
> non-`AICMatch` colouring/cycle fallbacks (which also serve the `Hidden XY (Type 1)` path)
> are now localized to new `coloring_generic` / `x_cycle_generic` keys instead of hardcoded
> English. Verified: `check_hint_keys.py` green (0 errors, 0 new warnings; the `aic.step2`
> `isLong` unused-var warning is resolved), `:backend`/`:shared` tests green, and 6 new
> `HintKeyResolutionTest` Phase-5 cases (coloring s3 digit + plural, x-cycle s1 guard +
> plural, long-AIC s2 no intro, localized colouring/cycle fallbacks render). `RoutingPinTest`
> now allows the `coloring_generic` family (the colouring fallback is reachable with a
> non-AICMatch).

**Goal:** fix the colouring s3 digit gap, the AIC long-chain dangling intro, the X-Cycles
"digit 0" fabrication, and localize the non-`AICMatch` colouring/cycle fallbacks.

**Files:** `CycleExplanations.kt` (`generateCycleSteps` L10, `generateColouringSteps` L130);
`en.json` (`hints.coloring`, `simple_coloring`, `x_cycle*`, `3d_medusa`, `aic`).

**Work items**
1. **Colouring s3 digit (gap 6).** `coloring.step3.description` ends
   "Remove the candidate(s) from: {{cells}}" with no `{{digit}}` even though the generator
   knows the digit. Add `{{digit}}` to the template **and** pass it from
   `generateColouringSteps` (confirm the AIC path actually supplies it). Affects Simple
   Colouring + 3D Medusa.
2. **AIC long-chain s2 (gap 7).** `isLong` is passed but the `en.json` s2 template doesn't
   branch on it → "Let's follow the chain step by step:" dangles over empty `{{chainSteps}}`
   for > 6-node chains. Either branch the template on `isLong` (two s2 variants) or drop the
   intro line in code when `{{chainSteps}}` is empty.
3. **X-Cycles "digit 0" (gap 9).** When no eliminations exist, s1 shows "involving digit 0"
   (L10 area, `digit = … ?: 0`). Guard: omit the digit clause when unknown. Also the
   single-digit phrasing is wrong for discontinuous/grouped loops — make it plural-safe.
4. **Non-`AICMatch` fallbacks (gap 3, V4).** In `generateColouringSteps` and `generateCycleSteps`,
   the non-`AICMatch` branches are hardcoded English. Localize them (`hints.aic.*` /
   `hints.coloring.*` / `hints.x_cycle*` generic forms). This also covers the
   `Hidden XY (Type 1)` path (routes here, not ChainLike).

**Validation:** `check_hint_keys.py` green. Manual: render Simple Colouring (s3 shows the
digit), a long AIC (> 6 nodes, no dangling intro), and an X-Cycle with no eliminations (no
"digit 0"). `:backend` green.

**Context budget:** medium. `CycleExplanations.kt` (260) in 2 slices + ~8 EN edits. **≈ 30k.** Safe.

**Checkpoint:** colouring/cycle/AIC steps accurate + localized; no fabricated "digit 0".

---

## Phase 6 — Kite + BUG empty-data guards (gap 9, D1)

> ✅ **Done.** Suppressed Kite's step 2 entirely when `chainDescription` is empty (the empty chain line fallback is gone), avoiding "The kite forms a chain: ." BUG's step 2 now falls back to a new `descriptionGeneric` template in `en.json` when there are no eliminations (target cell unknown), avoiding the fabricated "R1C1" target. The Kite doc note (D1) about "1 or 3 steps" was already updated. Verified: `check_hint_keys.py` green (0 errors, 11 unused-var warnings remaining).

**Goal:** kill the last two fabricated-value edge cases and correct the Kite doc.

**Files:** `techniques/KiteTechniques.kt` (`generateKiteSteps` L278);
`AdvancedExplanations.kt` (`generateBugSteps` L9); `en.json` (`hints.kite`, `hints.bug`);
`HINT_TECHNIQUES.md` (doc note D1).

**Work items**
1. **Kite empty chain (gap 9).** When `rowFinCell`/`colFinCell`/in-box cells can't be
   extracted, s2's `{{chain}}` is empty → "The kite forms a chain: ." Suppress s2 when the
   chain is empty (s1/s3 still make sense), or fill it from the line data.
2. **BUG fabricated `R1C1` (gap 9).** `targetCell = eliminationCells.firstOrNull() ?: 0`
   renders "R1C1" when there are no eliminations. Guard s2: only name a target cell when one
   exists; otherwise phrase s2 generically (no specific cell).
3. **Doc correction (D1).** In `HINT_TECHNIQUES.md`, change Kite's "3 or 0 steps" →
   "**1 or 3** steps" (s1 unconditional; s2–s3 only if eliminations exist). No code change.

**Validation:** `check_hint_keys.py` green. Manual: render a Kite whose geometry can't be
fully extracted (no empty chain line) and a BUG hint with no eliminations (no `R1C1`).
`:backend` green.

**Context budget:** small. Two targeted function reads (~2k) + 2 EN edits + a doc edit.
**≈ under 20k.**

**Checkpoint:** no fabricated cells/empty-chain lines in Kite or BUG; doc corrected.

---
## Phase 7 — Routing normalization (gap 10, V1)

> ✅ **Done.** Normalized `techniqueName` at the top of `generateExplanationSteps` (via `LanguageKeyBuilder.normalizeTechniqueName`) before dispatch so underscored machine names (e.g. `W_WING`) route correctly. Simplified Kite's `contains` check now that it matches the normalized name without clashing. Updated `detectWingType` to check for `uvwxyz` before `xyz`, fixing the `UVWXYZ-Wing` V1 misroute to `xyz_wing`. Removed `@Disabled` tags on Phase 7 contract pins in `RoutingPinTest`; all 56 variants route perfectly and the test is 100% green.

**Goal:** make dispatch independent of human vs machine name spelling so Kite, W-Wing and the
Franken/UVWXYZ family stop silently degrading.

**Files:** `ExplanationStepGenerator.kt` (L42–114); `LanguageKeyBuilder.kt`
(`normalizeTechniqueName` L45); `TechniqueMetadata.kt` (L9–62) if new name variants are
canonicalized.

**Work items**
1. **Normalize before dispatch.** At the top of `generateExplanationSteps`, normalize the
   solver name to the canonical machine form (e.g. `W_WING` → treat as `W-Wing`;
   `2_STRING_KITE_FISH` → kite) — reuse/extend `normalizeTechniqueName` so matching and key
   building agree. Then dispatch on the *normalized* name.
2. **Fix the specific misroutes** the routing pin test (Phase 1) flagged red:
   - `2_STRING_KITE_FISH` → must hit the **kite** branch (the current kite guard
     `contains("Kite") && !contains("String")` rejects the underscored form; `String`
     substring is the issue — match on the canonical form instead).
   - `W_WING` → must hit the **wing** `w_wing` keys, not `generic_wing`
     (`detectWingType` only matches "w-wing"/"w wing").
   - Ensure `XYZ_WING`/`XY_WING` keep their working routing.
3. **`UVWXYZ-Wing` (V1).** `detectWingType("UVWXYZ-Wing")` hits `contains("xyz")` first →
   `xyz_wing` (wrong: a 6-cell wing described as a 3-cell XYZ pattern). Add an explicit
   `uvwxyz` case *before* the `xyz` match, or a dedicated `uvwxyz_wing` key.
4. Re-run the routing pin test until **all 56 variants** are green.

**Validation:** routing pin test fully green. `:backend` green. Spot-render a W-Wing and a
2-String Kite from a machine-named solver match.

**Context budget:** small. `ExplanationStepGenerator.kt` (119) + `LanguageKeyBuilder.kt` (53)
+ the `TechniqueMetadata` map. **≈ under 15k.**

**Checkpoint:** routing pin test 56/56 green; no technique silently degrades on machine names.

---

## Phase 8 — Redundancy & shared-text cleanups (R1–R4)

> ✅ **Done.** R1: the byte-identical `pointing_candidates`/`claiming_candidates` s1 descriptions are now one shared `hints.common.boxLineInteraction` key referenced by both `generateIntersectionSteps` branches (family s1 descriptions deleted from `en.json`). R2: the verbatim-duplicate `sashimi_fish` s2/s3 deleted; the sashimi branch of `generateFinnedFishSteps` now emits the `finned_fish` s2/s3 keys (s1 stays its own). R3: the five identical wing s3 descriptions — which wrongly claimed "both pincers" for W-Wing/WXYZ/generic — consolidated into one `hints.common.wingElimination` key phrased "can see all the relevant wing cells", referenced from `generateWingSteps`. R4: `als_identify.step1` no longer states "{{cellCount}} cells … {{digitCount}} candidates" twice (other locales carrying the old text are deferred to the Phase 9 translation pass, per the plan's decision). Verified: `check_hint_keys.py` green (0 errors, same 11 pre-existing warnings), `:backend` tests green (47/47) with 4 new `HintKeyResolutionTest` Phase-8 cases (shared keys resolve under en+de, generators emit the shared keys, no "pincers", count stated once).

**Goal:** remove the duplicated/drift-prone text so a single edit updates every technique that
shares it. Pure `en.json` + (for R3) one Wing generator tweak.

**Files:** `en.json` (`hints.pointing_candidates`, `claiming_candidates`, `finned_fish`,
`sashimi_fish`, `xy_wing`, `xyz_wing`, `wxyz_wing`, `w_wing`, `generic_wing`, `als_identify`);
`techniques/WingTechniques.kt` (L213) only if R3 needs a code change.

**Work items**
1. **R1 — pointing/claiming s1:** make `claiming_candidates.s1` *reference* the shared text
   (or a `hints.common`-style shared key) so both stay in sync. *(Optional — the text is
   direction-agnostic and correct today; do this only to stop future drift.)*
2. **R2 — finned/sashimi s2/s3 verbatim dup:** consolidate into one shared key (or one family
   the other references). Both were made correct in Phase 4.
3. **R3 — wing s3 "pincers":** the shared s3 "…can see both pincers (or all wing cells)…"
   is wrong for W-Wing/WXYZ (no "pincers"). Make s3 accurate for all wings (e.g.
   "…can see all the relevant wing cells…").
4. **R4 — `als_identify.step1` redundancy:** "…{{cellCount}} cells … with {{digitCount}}
   candidates **…{{cellCount}} cells with {{digitCount}} candidates**…" — the phrase repeats
   twice in one sentence. Remove the duplicate. (Verified present in `en.json`; the other
   locales that mirror it are covered by the EN-fallback only if we also clean them — but
   since this is *existing* text, decide: clean `en.json` now, and add the other locales to
   the Phase-9 translation pass.)

**Validation:** `check_hint_keys.py` green (no dangling refs after consolidation). Manual:
render a Claiming + a Finned hint + a W-Wing; confirm text reads cleanly. `:backend` green.

**Context budget:** small. Targeted `en.json` subtree reads/edits via python + one optional
Wing function read. **≈ under 20k.**

**Checkpoint:** no verbatim-duplicate hint keys remain; wing s3 and als_identify read cleanly.

---

## Phase 9 — Language-file-only variants + AIC+ALS verification (V1–V4)

**Goal:** close out the "extra language-file-only variants" that have `en.json` text but no
priority entry, and verify the one genuinely unknown routing (`AIC+ALS`).

**Files:** `en.json` (`backend.techniques` + `hints.*` as needed); `TechniqueMetadata.kt`
(L9–62); `ExplanationStepGenerator.kt` if a new dispatch case is needed.

**Work items**
1. **`UVWXYZ-Wing` (V1):** confirm it now routes to a *correct* key (Phase 7 added the
   explicit case). If a dedicated `hints.uvwxyz_wing` was added, author its steps.
2. **`AIC+ALS` (`AIC___ALS`) — verify (V2).** Determine the *exact name the solver emits* for
   this combined technique. Then pin its routing: does it contain "Chain"? Is it an
   `AICMatch`? an `ALSMatch`? Add/adjust a dispatch case so it lands on the right steps.
   *(This is the one item that's genuinely unknown until we see the solver's name — do a
   quick repro against a real `AIC+ALS` match.)*
3. **`Hidden XY (Type 1)` (V4):** confirm it routes to the Phase-5 localized cycle/coloring
   fallback (it has no "Chain" substring). Pin it in the routing test.
4. **Priority-map completeness (optional):** the 94 `backend.techniques` keys vs the 41
   priority slots — add priority entries for the "extra variants" that should be ranked, or
   document that the rest are intentionally unranked (default priority 100).

**Validation:** routing pin test extended with V1–V4 variants, all green. Manual: render an
`AIC+ALS` hint and confirm the right family. `:backend` green.

**Context budget:** small. `TechniqueMetadata` map + `ExplanationStepGenerator` + one
solver-name repro. **≈ under 20k.**

**Checkpoint:** every language-file-only variant has a known, tested routing; `AIC+ALS`
verified.

---

## Phase 10 — Final full verification pass

✅ **Done.**

**Goal:** one end-to-end confirmation that nothing regressed and the whole hint surface is
correct + localized.

**Work items**
1. Run `check_hint_keys.py` across the **backend** (all key refs exist in `en.json`) — expect
   **zero** missing keys.
2. Run the full routing pin test — expect **56/56** + the V1–V4 variants green.
3. `./gradlew :shared:test :backend:test :web:jsBrowserTest` (or the project's test task) —
   all green.
4. **Render sweep:** for each of the 41 priority techniques, trigger a hint and confirm:
   - no literal `[…]` anywhere,
   - no fabricated values (R1C1 / digit 0 / empty chain / empty fin),
   - the step count matches the doc's "Steps" column,
   - the text is factually correct per the per-hint table in `HINT_TECHNIQUES.md`.
   Do this in **en** (authoritative) and one non-EN locale (e.g. `de`) to confirm the
   EN-fallback shows English (not `[…]`) for the newly added keys.
5. Update `HINT_TECHNIQUES.md` verdicts (⚠️/❌ → ✅) as they're fixed, and the Kite D1 note.

**Context budget:** small per check; the render sweep is a script/loop, not file reads.
**≈ under 30k.**

**Checkpoint (definition of done for the whole plan):**
- Zero missing keys in `en.json` for any referenced hint key.
- Routing pin test fully green (human + machine name forms).
- No fabricated/empty edge-case text.
- All ⚠️/❌ verdicts in `HINT_TECHNIQUES.md` upgraded to ✅ (or explicitly deferred).
- Non-EN locales show English for new keys (EN-fallback), never `[…]`.

---

## Suggested order & dependencies

```
Phase 1  (foundation: EN-fallback + guardrails)   ← everything depends on this
   │
   ├─ Phase 2  (additive keys: ALS, UR)           independent
   ├─ Phase 3  (diabolical hardcoded + XY-chain)  independent
   ├─ Phase 4  (fish + finned accuracy)           independent
   ├─ Phase 5  (colouring + cycle accuracy)       independent
   ├─ Phase 6  (kite + BUG guards)               independent
   │
   └─ Phase 7  (routing normalization)            uses Phase-1 routing test
          │
 +- Phase 8 (redundancy & shared-text) after 4/5/6
 |
 +- Phase 9 (variants + AIC+ALS) after 5/7 (routing)
          └─ Phase 10 (final verification)        last, after all
```

Phases 2–6 are mutually independent and can be interleated (each is a fresh, bounded context).
Phase 7 must come before 9. Phase 10 is the gate.

## Recommended execution cadence (to respect the < 200k constraint)
- One fresh agent session per phase (Tactic **P1**).
- Within a phase, never load a full locale JSON (Tactic **J1**) and never load a whole
  400–670-line generator (Tactic **K1**).
- If a phase ever grows (it shouldn't, per the budgets above), split it by technique — each
  technique is already isolated in its own function.
