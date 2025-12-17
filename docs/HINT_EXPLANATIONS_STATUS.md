# Hint Explanations Status

This document tracks the implementation status of multi-step ELI5 (Explain Like I'm 5) explanations for each Sudoku solving technique in Nice Sudoku.

## Rating System

| Rating | Description |
|--------|-------------|
| ⭐⭐⭐⭐⭐ | Benchmark quality - Full ELI5, visual elements, step-by-step walkthrough |
| ⭐⭐⭐⭐ | Good - Multi-step with clear explanations, minor polish needed |
| ⭐⭐⭐ | Partial - Has structure but needs simpler language or better visuals |
| ⭐⭐ | Minimal - 2 steps, brief/generic descriptions |
| ⭐ | Generic fallback - Uses match.toString() or minimal explanation |

---

## Tier 1: Benchmark Quality (Reference Implementations)

### Naked Single
- **File:** `SubsetExplanations.kt` → `generateSingleSteps()`
- **Rating:** ⭐⭐⭐⭐⭐
- **Steps:** 2
- **Visual Elements:** Cell highlighting, candidate coloring, all three house regions
- **Notes:** Explains that only one candidate fits, shows peer eliminations

### Hidden Single
- **File:** `SubsetExplanations.kt` → `generateSingleSteps()`
- **Rating:** ⭐⭐⭐⭐⭐
- **Steps:** 2
- **Visual Elements:** House region highlighting, cell coloring, peer eliminations
- **Notes:** Identifies the house, explains uniqueness of placement

### Naked Pairs/Triples/Quadruples
- **File:** `SubsetExplanations.kt` → `generateSubsetSteps()`
- **Rating:** ⭐⭐⭐⭐⭐
- **Steps:** 2-3
- **Visual Elements:** Region highlighting, subset cell borders, candidate coloring
- **Notes:** Separates normal eliminations from locked candidate effects

### Hidden Pairs/Triples/Quadruples
- **File:** `SubsetExplanations.kt` → `generateSubsetSteps()`
- **Rating:** ⭐⭐⭐⭐⭐
- **Steps:** 2-3
- **Visual Elements:** Region highlighting, subset cell borders, candidate coloring
- **Notes:** Explains digits locked to specific cells

### Pointing Candidates
- **File:** `AdvancedExplanations.kt` → `generateIntersectionSteps()`
- **Rating:** ⭐⭐⭐⭐⭐
- **Steps:** 2
- **Visual Elements:** Base/cover regions, intersection cell highlighting
- **Notes:** Explains box-line intersection clearly

### Claiming Candidates (Box/Line Reduction)
- **File:** `AdvancedExplanations.kt` → `generateIntersectionSteps()`
- **Rating:** ⭐⭐⭐⭐⭐
- **Steps:** 2
- **Visual Elements:** Base/cover regions, intersection cell highlighting
- **Notes:** Explains line-box restriction

### X-Wing
- **File:** `BasicFishTechniques.kt` → `generateFishSteps()`
- **Rating:** ⭐⭐⭐⭐⭐
- **Steps:** 3
- **Visual Elements:** Base/cover regions, intersection cells, pattern candidates
- **Notes:** Pattern identification, elimination logic, specific cells named

### Swordfish
- **File:** `BasicFishTechniques.kt` → `generateFishSteps()`
- **Rating:** ⭐⭐⭐⭐⭐
- **Steps:** 3
- **Visual Elements:** Base/cover regions, intersection cells, pattern candidates
- **Notes:** Extends X-Wing explanation to 3 lines

### Jellyfish
- **File:** `BasicFishTechniques.kt` → `generateFishSteps()`
- **Rating:** ⭐⭐⭐⭐⭐
- **Steps:** 3
- **Visual Elements:** Base/cover regions, intersection cells, pattern candidates
- **Notes:** Extends X-Wing explanation to 4 lines

### 2-String Kite
- **File:** `KiteTechniques.kt` → `generateKiteSteps()`
- **Rating:** ⭐⭐⭐⭐⭐
- **Steps:** 3
- **Visual Elements:** Lines (strong/weak), groups, box region, chain notation
- **Notes:** Visual chain walkthrough, strong/weak link explanation, ELI5 language

---

## Tier 2: Now Upgraded to Benchmark Quality

### XY-Wing
- **File:** `WingTechniques.kt` → `generateWingSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Colored cells, regions, target candidates, **LineDto connections**
- **Improvements Made:** 
  - Added line connections between pivot and pincers
  - ELI5 explanation of hinge/pincer relationship
  - Step-by-step logic walkthrough

### XYZ-Wing
- **File:** `WingTechniques.kt` → `generateWingSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Colored cells, regions, target candidates, **LineDto connections**
- **Improvements Made:** 
  - Visual lines showing cell connections
  - Clear explanation of Z digit elimination
  - "Closed group" concept explained

### WXYZ-Wing
- **File:** `WingTechniques.kt` → `generateWingSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Colored cells, regions, target candidates, **LineDto connections**
- **Improvements Made:** 
  - All cells connected with lines
  - "Musical chairs" analogy for locked digit
  - Step-by-step pattern explanation

### W-Wing
- **File:** `WingTechniques.kt` → `generateWingSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Colored cells, regions, target candidates, **strong link line**
- **Improvements Made:** 
  - Strong link visualized between bivalue cells
  - "Bridge" concept explained clearly
  - ELI5 description of linking digit

### Unique Rectangle (Types 1-6)
- **File:** `RectangleExplanations.kt` → `generateUniqueRectangleSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Rectangle cells, candidate coloring, **rectangle outline lines**
- **Improvements Made:** 
  - Visual rectangle outline with LineDto
  - Detailed type-by-type ELI5 breakdown (Types 1-6)
  - "Deadly pattern" concept explained with swap example

### Empty Rectangle
- **File:** `RectangleExplanations.kt` → `generateEmptyRectangleSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** ER cells, candidate coloring, **L-shape lines**, **strong link line**
- **Improvements Made:** 
  - L-shape pattern visualized with lines
  - "Empty cross" concept explained
  - Conjugate pair connection shown visually

### AIC Chains
- **File:** `ChainExplanations.kt` → `generateChainSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Lines (strong/weak), groups with colors
- **Improvements Made:** 
  - "Domino" analogy for chain logic
  - Simplified step-by-step walkthrough
  - "Trap" concept for eliminations

### ALS-XY / ALS-XZ / ALS Chain
- **File:** `ChainExplanations.kt` → `generateALSSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** Dynamic (2+ per ALS)
- **Visual Elements:** ALS cell highlighting, colored candidates
- **Improvements Made:** 
  - "Connecting digit" instead of "RCC"
  - "Almost locked" concept explained with N+1 example
  - Step-by-step ALS identification

---

## Tier 3: Now Upgraded to Benchmark Quality

### BUG (Bivalue Universal Grave)
- **File:** `AdvancedExplanations.kt` → `generateBugSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Target cell, candidate coloring
- **Improvements Made:** 
  - "Bivalue pattern" concept explained with uniqueness
  - Exception cell clearly identified and highlighted
  - Step-by-step explanation of why BUG is forbidden

### Simple Colouring
- **File:** `CycleExplanations.kt` → `generateColouringSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Groups with colors, lines
- **Improvements Made:** 
  - Color set visualization (Green/Yellow)
  - "Color Trap" and "Color Wrap" explained separately
  - Step-by-step coloring process

### 3D Medusa
- **File:** `CycleExplanations.kt` → `generateColouringSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Groups with colors, lines
- **Improvements Made:** 
  - Multi-digit coloring explained
  - Strong link types (bivalue cells, conjugate pairs) explained
  - Shared code with Simple Colouring but different intro

### X-Cycles
- **File:** `CycleExplanations.kt` → `generateCycleSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Lines and groups
- **Improvements Made:** 
  - "Domino" chain concept explained
  - Nice Loop vs Discontinuous Loop distinction
  - Step-by-step link explanation

### Sue-de-Coq
- **File:** `AdvancedExplanations.kt` → `generateSueDeCoqSteps()`
- **Rating:** ⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Candidate coloring
- **Improvements Made:** 
  - Box-line intersection explained
  - "Partition" concept with plain language
  - "Locked within structure" explanation

### Forcing Chains
- **File:** `AdvancedExplanations.kt` → `generateForcingChainSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Candidate coloring
- **Improvements Made:** 
  - "Branch A / Branch B" visualization concept
  - "Two roads to same destination" analogy
  - Convergence explanation

### Nishio
- **File:** `AdvancedExplanations.kt` → `generateNishioSteps()`
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Candidate coloring
- **Improvements Made:** 
  - "Detective" analogy for assumption testing
  - Contradiction path explained
  - "Impossible situation" examples

---

## Tier 4: Partial Upgrades and Remaining Work

### Skyscraper
- **File:** `BasicFishTechniques.kt` → `generateSkyscraperSteps()` (NEW DEDICATED)
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Strong link lines, endpoint highlighting
- **Improvements Made:** 
  - Dedicated function with proper strong link visualization
  - "Two strong links" concept explained
  - "Roof" analogy for endpoint cells
  - Step-by-step logic walkthrough

### Finned X-Wing / Finned Swordfish / Finned Jellyfish
- **File:** `BasicFishTechniques.kt` → `generateFinnedFishSteps()` (NEW DEDICATED)
- **Rating:** ⭐⭐⭐⭐⭐ (IMPROVED)
- **Steps:** 3
- **Visual Elements:** Fin cells highlighted, pattern candidates
- **Improvements Made:** 
  - Fin concept explained step-by-step
  - Sashimi variant distinction
  - "Fin's box" restriction explained
  - Visual fin highlighting

### Franken Fish / Mutant Fish
- **File:** Falls to `generateGenericSteps()`
- **Rating:** ⭐
- **Issues:** 
  - Box+line base/cover not explained
  - No specialized visualization
- **Improvement Needed:** Base/cover type explanation, visual pattern

### Ring
- **File:** Falls to `generateGenericSteps()`
- **Rating:** ⭐
- **Issues:** 
  - Ring structure not visualized
  - Junction point eliminations not explained
- **Improvement Needed:** Dedicated ring visualization, closure explanation

### L-Wing
- **File:** Falls to `generateGenericSteps()`
- **Rating:** ⭐
- **Issues:** 
  - No dedicated explanation
  - L-shaped pattern not visualized
- **Improvement Needed:** Dedicated L-Wing steps with pattern visualization

### XY-Chain
- **File:** Falls to `generateChainLikeSteps()`
- **Rating:** ⭐⭐
- **Steps:** 3
- **Visual Elements:** Basic candidate coloring
- **Issues:** 
  - Uses generic chain explanation
  - Bivalue cell chain not visualized
- **Improvement Needed:** Dedicated XY-Chain steps, cell-by-cell explanation

---

## Implementation Progress

| Phase | Technique | Status | Notes |
|-------|-----------|--------|-------|
| 1 | Wing Techniques | ✅ Complete | Line connections, ELI5 for XY/XYZ/WXYZ/W-Wing |
| 1 | Unique Rectangle | ✅ Complete | Visual rectangle, type-by-type breakdown |
| 1 | Empty Rectangle | ✅ Complete | L-shape visualization, conjugate pair explanation |
| 2 | Simple Colouring | ✅ Complete | Color set visualization, trap/wrap explanation |
| 2 | X-Cycles | ✅ Complete | Step-through alternating links |
| 2 | BUG | ✅ Complete | Bivalue pattern explanation, exception cell |
| 2 | Sue-de-Coq | ✅ Complete | Partition visualization |
| 3 | AIC Chains | ✅ Complete | Simplified chain logic descriptions |
| 3 | ALS Techniques | ✅ Complete | "Connecting digit" instead of RCC |
| 3 | Forcing Chains | ✅ Complete | Branch visualization explanation |
| 3 | Nishio | ✅ Complete | Assumption/contradiction flow |
| 4 | Skyscraper | ✅ Complete | Dedicated explanation with strong links |
| 4 | Finned Fish | ✅ Complete | Fin concept explained step-by-step |
| 4 | Franken/Mutant Fish | ⏳ Pending | Box+line base/cover explanation needed |
| 5 | 3D Medusa | ✅ Complete | Handled via generateColouringSteps |
| 5 | Ring | ⏳ Pending | Ring structure visualization needed |
| 5 | L-Wing | ⏳ Pending | Dedicated steps needed |
| 5 | XY-Chain | ⏳ Pending | Link-by-link walkthrough needed |

---

## Benchmark Patterns to Follow

From `KiteTechniques.kt` and `BasicFishTechniques.kt`:

### Step Structure
1. **Step 1 - Pattern Identification**
   - "Look at the two green X candidates at..."
   - "Find the pattern in Row X and Column Y..."
   - Describes WHAT to see

2. **Step 2 - Logic Explanation**
   - "The solid lines indicate strong links..."
   - "Because X must be in one of these cells..."
   - Explains WHY it works

3. **Step 3 - Elimination**
   - "Remove X from: R1C1, R2C2..."
   - "Eliminate X from cells that see both endpoints"
   - Names SPECIFIC cells

### Visual Elements
```kotlin
// Regions for house highlighting
regions = listOf(ColouredRegionDto("row", 0, "primary"))

// Cell borders (warning=yellow, target=green)
colouredCells = listOf(ColouredCellDto(cellIndex, "warning"))

// Candidate highlighting
colouredCandidates = listOf(ColouredCandidateDto(row, col, digit, "target"))

// Lines for chain connections
lines = listOf(LineDto(
    from = CandidateLocationDto(r1, c1, digit),
    to = CandidateLocationDto(r2, c2, digit),
    isStrongLink = true,
    lineType = "strong",
    description = "Strong link explanation"
))

// Groups for candidate sets
groups = listOf(GroupDto(
    candidates = candidateList,
    groupType = "chain-end",
    colourIndex = 0
))
```

### Language Guidelines
- Use "Look at..." instead of "Identify..."
- Use "This means..." instead of "Therefore..."
- Use specific cell names like "R3C5" or "the green 7 in Row 3"
- Avoid jargon: "connecting digit" not "RCC", "locked" not "restricted"
- Explain cause and effect: "Because X is locked here, Y cannot be there"

