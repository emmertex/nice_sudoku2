# Hint Techniques: Coverage & Explanation-Step Quality

The app's **hint system** shows Sudoku solving techniques detected by the backend, in the
**"Available Hints"** panel (toggle with **H**). Each hint carries **explanation steps**
(titled, narrated steps with cell highlighting) that walk the player through *why* the
technique works.

How steps are built:

- Backend: `backend/src/main/kotlin/service/hint/explanations/ExplanationStepGenerator.kt`
  dispatches by technique name to a per-family generator. Most generators emit step
  templates like `{{hints.naked_single.step1.title|cell=R1C1}}`; the **frontend** resolves
  them via `shared/src/commonMain/kotlin/i18n/HintStringInterpolation.kt` against
  `web/src/jsMain/resources/languages/*.json` (`hints.*` section). A missing key renders
  literally as `[hints....]` (verified in `HintStringInterpolation.interpolate`).
- A hint that comes back with **zero steps** falls back to
  `web/.../view/HintRenderer.kt → generateFallbackExplanationSteps()`, which builds 1–3
  steps from the `hints.common.*` keys (Overview / Eliminations / Solution). Verified: all
  three render paths use `hint.explanationSteps.isNotEmpty() ? steps : fallback`.

**Verdict legend** (per-hint quality, verified against the generator code + `en.json`)

| Mark | Meaning |
|------|---------|
| ✅ | Good — clear, worthwhile steps, accurate explanation, localized (all requested keys exist) |
| ⚠️ | Localized, but has content/accuracy issues or missing keys — see per-hint notes |
| ❌ | Steps are hardcoded English in the Kotlin source — **not translated** (and/or inaccurate) |

---

## Step coverage & quality per hint

| # | Technique (priority) | Steps | Verdict | Notes |
|---|----------------------|-------|---------|-------|
| 1 | Naked Singles (1) | 1–2 | ✅ | Emits **0 steps** if no solved cell → frontend `hints.common` fallback |
| 2 | Hidden Singles (2) | 1–2 | ✅ | s1 always; s2 only if peer eliminations exist |
| 3 | Pointing Candidates (3) | 1–2 | ✅ | s2 only if eliminations exist. s1 text is identical to Claiming's — correct for both directions |
| 4 | Claiming Candidates (4) | 1–2 | ✅ | see #3 |
| 5 | Naked Pairs (5) | 1–3 | ✅ | s1 always; s2 if in-house elim.; s3 if pointing elim. (s3 "locks X to house" is loose — see notes) |
| 6 | Naked Triples (6) | 1–3 | ✅ | same as #5 |
| 7 | Hidden Pairs (7) | 1–3 | ✅ | s1 "cells are 'locked' to digits" is awkward but correct |
| 8 | Hidden Triples (8) | 1–3 | ✅ | same as #7 |
| 9 | Naked Quadruples (9) | 1–3 | ✅ | same as #5 |
| 10 | Hidden Quadruples (10) | 1–3 | ✅ | same as #7 |
| 11 | X‑Wing (11) | 1–3 | ✅ | s1 claims the digit "appears in exactly N rows" — generally false (digit may also occur in other lines outside the cover). See [Fish text](#fish-family-text-issues) |
| 12 | Skyscraper (12) | 2–3 | ✅ | s1–s2 always, s3 if eliminations. Explanation is clear and correct |
| 13 | 2‑String Kite (13) | **1, or 2–3** | ✅ | **Doc correction:** step 1 is unconditional; the elimination path adds 1–2 more steps. If the kite geometry can't be extracted, the chain step is suppressed and the elimination step renumbers to step 2. See notes |
| 14 | Finned X‑Wing (14) | 1–3 | ✅ | s2/s3 claim eliminations are restricted to the fin's **box** — inaccurate (restricted to cells *seeing* the fin). See [Finned text](#finnedsashimi-text-issues) |
| 15 | Sashimi X‑Wing (15) | 1–3 | ✅ | same as #14 |
| 16 | Simple Colouring (16) | 1–3 | ✅ | AIC path: s3 "Remove the candidate(s)" has no `{{digit}}` placeholder. Non-`AICMatch` path is hardcoded English |
| 17 | Unique Rectangle (17) | 2–3 | ✅ | s3 description keys missing for Types 3–6 and the type‑0/generic case (code requests `elim_type0`). See [UR keys](#gaps-found) |
| 18 | BUG (18) | 2–3 | ✅ | s1–s2 always, s3 if eliminations. Edge case: with no eliminations, s2 fabricates target "R1C1" |
| 19 | XY‑Wing (19) | 1–3 | ✅ | Accurate, clear, colors match the text (yellow hinge, green pincers) |
| 19 | W‑Wing (19) | 1–3 | ✅ | Text is correct, but the `w_wing` keys are only used for the human name "W‑Wing"; the underscored `W_WING` misroutes to `generic_wing` — see [Routing fragility](#routing-fragility-name-form-dependent) |
| 20 | Empty Rectangle (20) | 1–3 | ✅ | s1–s2 always, s3 if eliminations. If reflection fails, s2's conjugate list can render empty |
| 21 | Swordfish (21) | 1–3 | ✅ | same fish-s1 issue as #11 |
| 22 | Finned Swordfish (22) | 1–3 | ✅ | same as #14 |
| 23 | XYZ Wing (23) | 1–3 | ✅ | Slightly conservative ("see all three" instead of "see both pincers") but correct |
| 24 | X‑Cycles (24) | 1–3 | ✅ | AIC path: digit falls back to 0 if no eliminations ("involving digit 0"); single-digit phrasing is wrong for discontinuous/grouped loops. Non-`AICMatch` path is hardcoded |
| 25 | XY‑Chain (25) | 1–3 | ✅ | Hardcoded — and the text is **wrong**: it describes strong links, but an XY‑chain is weak links only. See notes |
| 26 | WXYZ Wing (26) | 1–3 | ✅ | "Musical chairs" framing is fine; no cell names in s1 (generic by design) |
| 27 | Jellyfish (27) | 1–3 | ✅ | same fish-s1 issue as #11 |
| 28 | 3D Medusa (28) | 1–3 | ✅ | AIC path: same coloring-s3 digit gap as #16; green/yellow counts approximate by node parity. Non-`AICMatch` path is hardcoded |
| 29 | Grouped X‑Cycles (29) | 1–3 | ✅ | alias-only name; no solver enum emits it; routing verified |
| 30 | Franken X‑Wing (30) | 1–3 | ✅ | Routed to `x_wing` keys ✓, but base/cover type text is derived from the **first** sector only — a mixed box+line base is mislabeled as "rows" or "boxes" |
| 31 | Finned Franken X‑Wing (31) | 1–3 | ✅ | same as #14 (plus: if `fins` reflection fails, s1's fin list renders empty) |
| 32 | Finned Mutant X‑Wing (32) | 1–3 | ✅ | same as #14 |
| 33 | Franken Swordfish (33) | 1–3 | ✅ | same as #21 + #30 |
| 34 | Finned Jellyfish (34) | 1–3 | ✅ | same as #14 |
| 35 | AIC (35) | 1–3 | ✅ | "AIC" name → localized `aic` steps ✓. "Alternating Inference Chains" hits the hardcoded `ChainLike` (name contains "Chain" — checked *before* the `AICMatch` branch). Long chains (>6 nodes): s2 intro line dangles over an empty `{{chainSteps}}` |
| 36 | Almost Locked Sets (36) | 3–N | ✅ | 🔶 dynamic. Per-ALS titles for step ≥ 2 request **missing keys in every language file**; `als_identify` text is redundant (repeats "N cells with M candidates" twice). See [Gaps](#gaps-found) |
| 36 | ALS‑XY (36) | 3–N | ✅ | same as above |
| 36 | ALS‑XZ (36) | 3–N | ✅ | same as above |
| 37 | Sue‑de‑Coq (37) | 1–3 | ✅ | Hardcoded — and thin: steps are generic ("let's say there are N candidates"); the actual ALSs/intersection are never identified, only eliminations are highlighted |
| 38 | Forcing Chains (38) | 1–3 | ✅ | Hardcoded — and misleading: steps describe "the starting cell" but highlight the *elimination* cells; the actual start/branches are never shown |
| 39 | Nishio (39) | 1–3 | ✅ | Hardcoded. The logic (assume → contradiction → eliminate) is described correctly, but the assumed candidate is only implied via the eliminations |
| 40 | Ring (40) | 1–3 | ✅ | `AICMatch` → `aic` steps, which call the pattern an "AIC" (naming mismatch) and show "starting at X and ending at X" for a closed loop. Anything else → 3 localized `generic` steps (thin but valid) |
| 41 | L‑Wing (41) | 1–3 | ✅ | `generic_wing` s1–s3 — localized but thin (no L-shape explanation); acceptable for a diabolical-tier rare |

### Kite (13) — notes

- **Step count:** the doc previously said "3 or 0", then "1 or 3". Actual behavior:
  step 1 is unconditional; the elimination path adds the chain step (only when the kite
  geometry is extractable) plus the elimination step, which renumbers to `steps.size + 1`.
  So the hint renders **1, or 2–3 consecutive steps** — never "Step 1" followed by "Step 3".
- If the kite geometry can't be fully extracted (`rowFinCell`/`colFinCell`/in-box cells
  missing), the chain step is suppressed (Phase 6) instead of rendering
  "The kite forms a chain: ." with no content.

### XY‑Chain (25) — notes

- `generateChainLikeSteps()` is hardcoded English **and inaccurate for XY‑chains**: the
  text says the chain "uses two types of connections: • Strong links • Weak links".
  An XY‑chain is a chain of bivalue cells connected by **weak links only** — there are
  no strong links. The text was clearly adapted from AIC-style chains. Translating it
  as-is would bake the error into every locale; the text needs a rewrite (weak-link-only
  chain, target eliminated where both ends see) before it's localized.

### Fish-family text issues

Affects X‑Wing, Swordfish, Jellyfish (and via shared keys, Franken X‑Wing, Franken Swordfish):

- s1 "In {{baseNames}}, digit {{digit}} appears in exactly {{N}} {{rows/columns/boxes}}"
  is a **false claim** in general: the digit may also appear in other lines *outside the
  cover lines* without breaking the fish. The valid claim is that *within the base lines*
  the digit occurs only in the cover lines.
- s1's final sentence "it locks {{digit}} into the highlighted {{baseTypeText}}" is
  awkward.
- `baseTypeText`/`coverTypeText` are derived from **only the first** base/cover sector
  (`getSectorType(baseIndices.first())`), so mixed bases (Franken fish: row + box) are
  labeled as one type only.

### Finned/Sashimi text issues

Affects Finned X‑Wing, Sashimi X‑Wing, Finned Swordfish, Finned Franken X‑Wing,
Finned Mutant X‑Wing, Finned Jellyfish (`finned_fish` + `sashimi_fish` keys, which are
nearly verbatim duplicates of each other — see [Redundancy](#redundancy-notes)):

- s2: "This means eliminations are restricted to {{boxName}} (where the fin is)." —
  **inaccurate.** Eliminations are restricted to cells that *see the fin* (and lie in a
  cover line); those cells are not necessarily in the fin's box.
- s3: "These cells are in {{boxName}} (so they see the fin)" — same error.
- If `fins` reflection fails, s1's "The fin is at: {{finCells}}" renders with an empty
  list.

### Redundancy notes

- `pointing_candidates` s1 and `claiming_candidates` s1 are byte-identical (works, since
  the sentence is direction-agnostic — but two keys carrying the same text will drift).
- `finned_fish` s2/s3 and `sashimi_fish` s2/s3 are verbatim duplicates.
- s3 descriptions for `xy_wing`, `xyz_wing`, `wxyz_wing`, `w_wing`, `generic_wing` are
  identical: "The cell(s) at {{cells}} can see both pincers (or all wing cells)…" —
  "pincers" doesn't exist for W‑Wing/WXYZ‑Wing; the "(or all wing cells)" hedges it, but
  a shared accurate s3 would be better.
- `als_identify.step1.description` states "{{cellCount}} cells … with {{digitCount}}
  candidates" **twice** in the same sentence.
- `x_wing`/`swordfish`/`jellyfish`/`basic_fish` s2 and s3 are shared near-verbatim
  (acceptable family reuse, but the s1 count phrasing is the problem, above).

---

## Routing fragility (name-form dependent)

`ExplanationStepGenerator` dispatches on **substring matches against the solver's
technique name**, so routing depends on the exact spelling the solver emits. The
underscored "machine" forms that exist in the metadata (`2_STRING_KITE_FISH`, `W_WING`,
`XY_WING`, …) misroute:

| Solver name form | Intended generator | Actual dispatch | Problem |
|------------------|-------------------|-----------------|---------|
| `2-String Kite` (human) | Kite steps | Kite ✓ | — |
| `2_STRING_KITE_FISH` (machine) | Kite steps | **plain Fish steps** | The kite branch requires `!contains("String")`, which the underscored name fails; it then falls into `contains("Fish")` → `basic_fish` keys |
| `W-Wing` (human) | `w_wing` keys | `w_wing` ✓ | — |
| `W_WING` (machine) | `w_wing` keys | **`generic_wing`** | `detectWingType` only matches "w-wing"/"w wing", not the underscore form |
| `XY_WING` / `XY-Wing` | `xy_wing` | `xy_wing` ✓ | works in both forms (`contains("xy")`) |

Other names route identically in both forms (`XYZ_WING`, `SUE_DE_COQ`, `FORCING_CHAINS`
— "Forcing" is checked before "Chain", so Forcing Chains doesn't leak into ChainLike —
etc.). **Action:** normalize the technique name before dispatch (e.g. `W_WING` → `W-Wing`)
or add the underscored forms to the matchers; otherwise Kite and W‑Wing hints silently
degrade whenever the solver emits machine names.

---

## Extra language-file-only variants (no explicit priority)

These have `en.json` descriptions but no entry in the priority map. Routing re-verified
against `detectWingType()` and the dispatch order:

| Variant | Actual routing (verified) | Verdict |
|---------|---------------------------|---------|
| UVWXYZ‑Wing | **`xyz_wing` s1–s3** — *not* `generic_wing`: `detectWingType("UVWXYZ-Wing")` hits `contains("xyz")` first | ⚠️ content is wrong (describes a 3-cell XYZ pivot/pincer pattern for a 6-cell wing) |
| Split‑Wing | `generic_wing` s1–s3 | ✅ (thin but localized) |
| Strong‑Wing (Windmill) | `generic_wing` s1–s3 | ✅ (thin but localized) |
| Inverted H(3)‑Wing / L(3)‑Wings / M(3)‑Wing | `generic_wing` s1–s3 | ✅ (thin but localized) |
| AIC Type 1 / Type 2 | `aic` s1–3 (if `AICMatch`), else `generic` | ✅ |
| XY‑Chain (Type 1), ALS‑Chain | `ChainLike` — hardcoded (name contains "Chain") | ❌ |
| Hidden XY (Type 1) | **Not ChainLike** — no "Chain" substring; routes to `aic` if `AICMatch`, else `generic` | depends on match type |
| AIC+ALS (key `AIC___ALS`) | **unverified** — depends on the exact solver name: if it contains "Chain" → `ChainLike` ❌; if it's an `AICMatch` → `aic`; if `ALSMatch` → `als`; else `generic` | needs verification against the solver's emitted name |

---

## Frontend safety net

Verified: `renderInlineExplanation`, `renderInlineExplanationCompact` and
`renderExplanationView` all use `hint.explanationSteps.isNotEmpty() ? steps :
app.generateFallbackExplanationSteps(hint)`. The fallback renders 1–3 steps from
`hints.common.overview` / `hints.common.eliminations` / `hints.common.solution`
(elimination and solution steps only if that data exists). This covers the Naked‑Single
with no solved cell (0 backend steps) and any future 0‑step generator.

---

## Gaps found

Verified against `ExplanationStepGenerator.kt` + per-family generators + `en.json`
(spot-checked `de.json` — it has the same structure, so key gaps affect **all** locales):

1. **ALS step titles broken for multi-ALS hints** — `generateALSSteps()` builds the
   per-ALS step title from `{{hints.als.stepN.title|alsName=...}}` with N ≥ 2, but
   `en.json` (and the other locale files) only define `hints.als.step1.*`. Missing keys
   render literally as `[hints.als.step2.title]` etc. (affects Almost Locked Sets,
   ALS‑XY, ALS‑XZ). Fix: add `als.step2.title` (e.g. "Identify {{alsName}}") — or give
   `als_identify.step1` a title and point the per-ALS title key at it.
2. **Unique Rectangle step-3 description missing for Types 3–6 and the generic case** —
   the code requests `{{hints.unique_rectangle_elim_type$N.step1.description}}`;
   `en.json` only has `elim_type1` and `elim_type2`. Types 3–6 render the raw key, and
   the "generic" case (type extraction failed → `type = 0`) requests
   `unique_rectangle_elim_type0`, which is also missing. Fix: add `elim_type3`–`elim_type6`
   plus a shared text for the type‑0 case (or map type 0 to an existing generic
   description in code).
3. **Hardcoded English (untranslated) step text** for: Sue‑de‑Coq, Forcing Chains, Nishio,
   and everything whose name contains "Chain" (XY‑Chain, XY‑Chain (Type 1), ALS‑Chain,
   "Alternating Inference Chains"). Also the non‑`AICMatch` fallbacks in the colouring and
   cycle generators. These are the diabolical-tier hints, so they're the least likely to
   appear in casual play — but they break as soon as they do. **Additionally, the
   `ChainLike` text is inaccurate for XY‑chains** (it claims strong links; see
   [XY‑Chain notes](#xy-chain-25--notes)) — so this gap needs a rewrite, not just
   translation.
4. **Finned/Sashimi steps over-claim the fin's box** — s2/s3 say eliminations are
   restricted to the fin's box; the correct restriction is *cells that see the fin* (see
   [Finned text issues](#finnedsashimi-text-issues)). Affects 6 techniques.
5. **Fish s1 count claim is false** — "digit appears in exactly N lines" (see
   [Fish-family text issues](#fish-family-text-issues)); also base/cover type text uses
   only the first sector (Franken mislabel). Affects 5 techniques.
6. **`coloring` s3 lacks a digit placeholder** — "Remove the candidate(s) from:
   {{cells}}" though the digit is known to the generator. Affects Simple Colouring and
   3D Medusa.
7. **AIC long-chain s2 dangles** — for chains > 6 nodes, `{{chainSteps}}` is empty but
   the template still leads with "Let's follow the chain step by step:". The `isLong`
   variable is passed to the template but not used by `en.json`. Fix: branch the template
   on `isLong`, or drop the intro line when empty.
8. **Conditional steps can shrink a hint to 1 step** (e.g. Hidden Single with no peer
   eliminations, BUG without eliminations, any fish with no eliminations) — by design,
   but worth knowing when assessing "how well" a hint is explained.
9. **Fabricated values on empty-data edge cases** — defensive paths that can render
   nonsense if a match carries no eliminations/reflection data: BUG s2 uses cell
   "R1C1" (`targetCell = eliminationCells.firstOrNull() ?: 0`); X‑Cycles s1 shows
   "involving digit 0"; Kite s2 can show an empty chain; Finned s1 can show an empty fin
   list. Unlikely, but each is a silent-incorrect-text risk.
10. **Routing fragility** — underscored solver names misroute Kite (→ plain fish) and
    W‑Wing (→ `generic_wing`); UVWXYZ‑Wing hits `xyz_wing`; see
    [Routing fragility](#routing-fragility-name-form-dependent).
   *(Update: fixed in Phase 10 - generators now safely return 0 steps on empty edges rather than fabricating R1C1/digit 0)*

---

## Appendix: the available set (all supported techniques)

The canonical priority-ordered list lives in
[`backend/src/main/kotlin/service/hint/metadata/TechniqueMetadata.kt`](backend/src/main/kotlin/service/hint/metadata/TechniqueMetadata.kt)
(**41 priority slots** covering **56 distinct name variants**; `en.json`'s
`backend.techniques` carries 94 keys including aliases, some of which — the "extra
variants" above — have no priority entry). Grouped by tier:

- **Beginner (1–4):** Naked Singles, Hidden Singles, Pointing Candidates, Claiming Candidates
- **Easy (5–7):** Naked Pairs, Naked Triples, Hidden Pairs
- **Medium (8–10):** Hidden Triples, Naked Quadruples, Hidden Quadruples
- **Tough (11–15):** X‑Wing, Skyscraper, 2‑String Kite, Finned X‑Wing, Sashimi X‑Wing
- **Hard (16–22):** Simple Colouring, Unique Rectangle, BUG, XY‑Wing, W‑Wing, Empty Rectangle, Swordfish, Finned Swordfish
- **Expert (23–28):** XYZ Wing, X‑Cycles, XY‑Chain, WXYZ Wing, Jellyfish, 3D Medusa
- **Extreme (29–34):** Grouped X‑Cycles, Franken X‑Wing, Finned Franken X‑Wing, Finned Mutant X‑Wing, Franken Swordfish, Finned Jellyfish
- **Diabolical (35–41):** AIC, Almost Locked Sets, ALS‑XY, ALS‑XZ, Sue‑de‑Coq, Forcing Chains, Nishio, Ring, L‑Wing

> **Note on "tutorial":** this app has no tutorial feature and no tracking of
> completed/learned techniques, so every hint above is in the same "available" state.
