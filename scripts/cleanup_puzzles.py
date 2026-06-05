#!/usr/bin/env python3
"""
Puzzle processing / curation script.

For each difficulty tier it:
  - Loads the existing in-game file (kept verbatim) and the generation pool.
  - Deduplicates pool candidates by 'givens'.
  - Scores every puzzle with a composite QUALITY metric (see compute_quality).
  - Recomputes a fine-grained 'difficulty' within the tier's band.
  - Selects puzzles top-by-quality up to the tier target (existing always kept),
    or keeps everything available (for the hardest tiers).
  - Stamps collection metadata (author / contact / description / title).
  - Writes one puzzle per line.

Typical use rebuilds every tier at once:

    python3 cleanup_puzzles.py --all \
        --pool-dir scripts/puzzles \
        --out-dir  web/src/jsMain/resources/puzzles

A single tier can still be processed directly with --in / --out / --target.
"""

import argparse
import json
import math
import random
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any, Optional

# ---------------------------------------------------------------------------
# Technique priority mapping from SudokuService.kt (lower = easier).
# Used for difficulty and for locating each puzzle's "hardest band".
# ---------------------------------------------------------------------------
TECHNIQUE_PRIORITY: dict[str, int] = {
    # BEGINNER (1-4): Singles + Intersection
    "NAKED_SINGLE": 1, "Naked Singles": 1,
    "HIDDEN_SINGLE": 2, "Hidden Singles": 2,
    "POINTING_CANDIDATES": 3, "Pointing Candidates": 3, "Pointing Pairs": 3,
    "CLAIMING_CANDIDATES": 4, "Claiming Candidates": 4, "Box/Line Reduction": 4,
    # EASY (5-7): Basic subsets
    "NAKED_PAIR": 5, "Naked Pairs": 5,
    "NAKED_TRIPLE": 6, "Naked Triples": 6,
    "HIDDEN_PAIR": 7, "Hidden Pairs": 7,
    # MEDIUM (8-10): Harder subsets
    "HIDDEN_TRIPLE": 8, "Hidden Triples": 8,
    "NAKED_QUADRUPLE": 9, "Naked Quadruples": 9,
    "HIDDEN_QUADRUPLE": 10, "Hidden Quadruples": 10,
    # TOUGH (11-15): Fish & single-digit patterns
    "X_WING_FISH": 11, "X-Wing": 11,
    "SKYSCRAPER_FISH": 12, "Skyscraper": 12,
    "TWO_STRING_KITE_FISH": 13, "2-String Kite": 13,
    "FINNED_X_WING_FISH": 14, "Finned X-Wing": 14,
    "SASHIMI_X_WING_FISH": 15, "Sashimi X-Wing": 15,
    # HARD (16-22): Colouring, uniqueness, wings, swordfish
    "SIMPLE_COLOURING": 16, "Simple Colouring": 16,
    "UNIQUE_RECTANGLE": 17, "Unique Rectangles": 17,
    "BUG": 18, "Bivalue Universal Grave": 18,
    "Y_WING": 19, "XY-Wing": 19, "XY_WING": 19,
    "EMPTY_RECTANGLE": 20, "Empty Rectangles": 20, "Empty Rectangle": 20,
    "SWORDFISH_FISH": 21, "Swordfish": 21,
    "FINNED_SWORDFISH_FISH": 22, "Finned Swordfish": 22,
    # EXPERT (23-28): Advanced wings, chains, 3D Medusa
    "XYZ_WING": 23, "XYZ Wing": 23, "XYZ-Wing": 23,
    "X_CYCLES": 24, "X-Cycles": 24, "X-Cycle": 24,
    "XY_CHAIN": 25, "XY-Chain": 25,
    "WXYZ_WING": 26, "WXYZ Wing": 26, "WXYZ-Wing": 26,
    "JELLYFISH_FISH": 27, "Jellyfish": 27,
    "MEDUSA_3D": 28, "3D Medusa": 28, "THREE_D_MEDUSA": 28,
    # EXTREME (29-34): Franken/mutant fish, grouped techniques
    "GROUPED_X_CYCLES": 29, "Grouped X-Cycles": 29,
    "FRANKEN_X_WING_FISH": 30, "Franken X-Wing": 30,
    "FINNED_FRANKEN_X_WING_FISH": 31,
    "FINNED_MUTANT_X_WING_FISH": 32,
    "FRANKEN_SWORDFISH_FISH": 33,
    "FINNED_JELLYFISH_FISH": 34,
    # DIABOLICAL (35-39): AIC, ALS, Sue-de-Coq, Forcing Chains
    "AIC": 35, "Alternating Inference Chains": 35,
    "ALMOST_LOCKED_SETS": 36, "Almost Locked Sets": 36,
    "SUE_DE_COQ": 37, "Sue-de-Coq": 37,
    "FORCING_CHAINS": 38, "Forcing Chains": 38,
    "NISHIO": 39, "Nishio": 39,
}

# ---------------------------------------------------------------------------
# Collection metadata (stamped onto every puzzle).
# ---------------------------------------------------------------------------
DEFAULT_AUTHOR = "Emmertex"
DEFAULT_CONTACT = "sudoku.emmertex.com"
DEFAULT_DESCRIPTION = "Nice Sudoku - Included Puzzle Collection"

# ---------------------------------------------------------------------------
# Tier table for --all mode.
#   (pool/out filename, title prefix, target). target=None -> keep everything.
#   Easiest three tiers -> 200, tough -> 100, hardest three -> all available.
# ---------------------------------------------------------------------------
TIERS: list[tuple[str, str, Optional[int]]] = [
    ("beginner.json", "Beginner", 200),
    ("easy.json", "Easy", 200),
    ("medium.json", "Medium", 200),
    ("tough.json", "Tough", 100),
    ("hard.json", "Hard", None),
    ("expert.json", "Expert", None),
    ("diabolical.json", "Diabolical", None),
]

# ---------------------------------------------------------------------------
# Quality metric.
#
# Difficulty already answers "how hard". Quality answers "how *nice* a puzzle
# is at that difficulty" and is deliberately orthogonal to difficulty. It is a
# weighted blend of five components, each normalised to 0..1, and is ABSOLUTE
# (it does not depend on which other puzzles are in the batch). The previous
# metric was just "distinct technique count / best-in-batch", which mostly
# tracked puzzle length.
# ---------------------------------------------------------------------------
QUALITY_WEIGHTS = {
    "variety": 0.30,    # how many distinct techniques the solve path uses
    "balance": 0.20,    # how evenly the solving moves are spread (entropy)
    "signature": 0.25,  # how often the tier's hallmark (hardest) techniques recur
    "antigrind": 0.15,  # how little the solve is dominated by naked-single filler
    "elegance": 0.10,   # symmetry of the givens + givens economy
}
VARIETY_TARGET = 6          # distinct techniques that earns full variety credit
SIGNATURE_BAND = 3          # priorities below max counted as "hallmark" moves
SIGNATURE_SCALE = 2.5       # hallmark moves for ~0.67 signature credit
TRIVIAL_PRIORITY = 1        # naked single == pure filler
ANTIGRIND_TARGET = 0.35     # non-trivial move fraction earning full anti-grind
ECONOMY_MAX_GIVENS = 40.0   # givens at/above this earn no economy credit
ECONOMY_MIN_GIVENS = 22.0   # givens at/below this earn full economy credit

# Verbosity level (0 quiet, 1 stats, 2 debug)
VERBOSITY = 0


def log_stats(msg: str) -> None:
    if VERBOSITY >= 1:
        print(f"[STATS] {msg}")


def log_debug(msg: str) -> None:
    if VERBOSITY >= 2:
        print(f"[DEBUG] {msg}")


def _clamp(x: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return max(lo, min(hi, x))


def get_technique_priority(name: str) -> int:
    """Get priority for a technique name, trying a few spelling variants."""
    if name in TECHNIQUE_PRIORITY:
        return TECHNIQUE_PRIORITY[name]
    if name.upper() in TECHNIQUE_PRIORITY:
        return TECHNIQUE_PRIORITY[name.upper()]
    if name.replace("_", " ") in TECHNIQUE_PRIORITY:
        return TECHNIQUE_PRIORITY[name.replace("_", " ")]
    log_debug(f"Unknown technique: {name}, assigning priority 100")
    return 100


def load_puzzles(filepath: Path) -> list[dict[str, Any]]:
    """Load puzzles from a JSON file (missing file -> empty list)."""
    if not filepath.exists():
        log_debug(f"File does not exist: {filepath}")
        return []
    with open(filepath, "r") as f:
        data = json.load(f)
    puzzles = data.get("puzzles", [])
    log_debug(f"Loaded {len(puzzles)} puzzles from {filepath}")
    return puzzles


# ---------------------------------------------------------------------------
# Difficulty (fine-grained, within the tier band).
# ---------------------------------------------------------------------------
def round_difficulty(difficulty: float) -> float:
    """Floor difficulty to a 2.5-wide band so a puzzle never leaves its tier."""
    return math.floor(difficulty / 2.5) * 2.5


def calculate_complexity(puzzle: dict[str, Any]) -> int:
    """Raw complexity = sum of (priority * count) across techniques."""
    techniques = puzzle.get("techniques", {}) or {}
    return sum(get_technique_priority(name) * count for name, count in techniques.items())


def recompute_difficulty(puzzles: list[dict[str, Any]]) -> None:
    """difficulty = band floor + complexity normalised into the band (0..2.4)."""
    if not puzzles:
        return
    for p in puzzles:
        p["_band"] = round_difficulty(p.get("difficulty", 0.0))
        p["_raw_complexity"] = calculate_complexity(p)

    complexities = [p["_raw_complexity"] for p in puzzles]
    lo, hi = min(complexities), max(complexities)
    span = hi - lo
    log_stats(f"Complexity range: {lo} - {hi}")

    for p in puzzles:
        normalized = ((p["_raw_complexity"] - lo) / span) * 2.4 if span > 0 else 0.0
        p["difficulty"] = round(p["_band"] + normalized, 2)


# ---------------------------------------------------------------------------
# Quality.
# ---------------------------------------------------------------------------
def compute_quality(puzzle: dict[str, Any]) -> float:
    """Composite 0..10 quality score (see module docstring / QUALITY_WEIGHTS)."""
    techniques = puzzle.get("techniques", {}) or {}
    moves = [(get_technique_priority(n), c) for n, c in techniques.items() if c > 0]
    total = sum(c for _, c in moves)
    if total == 0:
        return 0.0

    distinct = len(moves)
    max_p = max(p for p, _ in moves)

    # 1. Variety -- distinct technique types in the solve path.
    variety = _clamp(distinct / VARIETY_TARGET)

    # 2. Balance -- normalised Shannon entropy of the move distribution.
    if distinct > 1:
        probs = [c / total for _, c in moves]
        entropy = -sum(p * math.log(p) for p in probs if p > 0)
        balance = entropy / math.log(distinct)
    else:
        balance = 0.0

    # 3. Signature -- recurrence of the puzzle's hardest-band techniques.
    #    Rewards puzzles that genuinely exercise their hallmark techniques,
    #    not ones that scrape into a tier via a single lucky advanced move.
    band_lo = max(max_p - SIGNATURE_BAND, TRIVIAL_PRIORITY + 1)
    signature_moves = sum(c for p, c in moves if p >= band_lo and p > TRIVIAL_PRIORITY)
    signature = 1.0 - math.exp(-signature_moves / SIGNATURE_SCALE)

    # 4. Anti-grind -- penalise solves dominated by naked-single filler.
    trivial_moves = sum(c for p, c in moves if p <= TRIVIAL_PRIORITY)
    nontrivial_frac = 1.0 - trivial_moves / total
    antigrind = _clamp(nontrivial_frac / ANTIGRIND_TARGET)

    # 5. Elegance -- givens symmetry + economy.
    elegance = compute_elegance(puzzle.get("givens", ""))

    score = (
        QUALITY_WEIGHTS["variety"] * variety
        + QUALITY_WEIGHTS["balance"] * balance
        + QUALITY_WEIGHTS["signature"] * signature
        + QUALITY_WEIGHTS["antigrind"] * antigrind
        + QUALITY_WEIGHTS["elegance"] * elegance
    )
    log_debug(
        f"  quality {round(score * 10, 2)}: var={variety:.2f} bal={balance:.2f} "
        f"sig={signature:.2f} grind={antigrind:.2f} eleg={elegance:.2f}"
    )
    return round(score * 10.0, 2)


def compute_elegance(givens: str) -> float:
    """Elegance = 180-degree rotational symmetry + givens economy (0..1)."""
    if len(givens) != 81:
        return 0.0
    is_given = [c != "0" for c in givens]
    symmetry = sum(1 for i in range(81) if is_given[i] == is_given[80 - i]) / 81.0
    num_givens = sum(is_given)
    economy = _clamp(
        (ECONOMY_MAX_GIVENS - num_givens) / (ECONOMY_MAX_GIVENS - ECONOMY_MIN_GIVENS)
    )
    return 0.6 * symmetry + 0.4 * economy


# ---------------------------------------------------------------------------
# Per-tier processing.
# ---------------------------------------------------------------------------
def dedupe_against(
    candidates: list[dict[str, Any]], existing: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    """Drop candidates whose 'givens' already appear (in existing or earlier)."""
    seen: set[str] = {p["givens"] for p in existing if "givens" in p}
    out: list[dict[str, Any]] = []
    for p in candidates:
        g = p.get("givens", "")
        if g in seen:
            continue
        seen.add(g)
        out.append(p)
    return out


def stamp_metadata(
    puzzles: list[dict[str, Any]],
    title_prefix: str,
    author: str,
    contact: str,
    description: str,
) -> None:
    """Assign sequential IDs (existing order preserved), titles and metadata."""
    for new_id, p in enumerate(puzzles, start=1):
        p["puzzleId"] = new_id
        p["title"] = f"{title_prefix} #{new_id}"
        p["author"] = author
        p["authorContact"] = contact
        p["description"] = description


def write_puzzles(filepath: Path, puzzles: list[dict[str, Any]]) -> None:
    """Write puzzles, one compact object per line, stripping internal fields."""
    field_order = [
        "puzzleId", "difficulty", "givens", "solution", "techniques",
        "quality", "title", "author", "authorContact", "description",
    ]
    cleaned: list[dict[str, Any]] = []
    for p in puzzles:
        obj = {k: p[k] for k in field_order if k in p}
        cleaned.append(obj)

    with open(filepath, "w") as f:
        f.write('{"puzzles": [\n')
        for i, obj in enumerate(cleaned):
            line = json.dumps(obj, separators=(",", ":"))
            f.write(f"  {line}" + (",\n" if i < len(cleaned) - 1 else "\n"))
        f.write("]}\n")


def process_tier(
    pool_path: Path,
    out_path: Path,
    title_prefix: str,
    target: Optional[int],
    author: str,
    contact: str,
    description: str,
) -> None:
    """Rebuild a single tier file: keep existing, add best new, score, stamp."""
    existing = load_puzzles(out_path)
    pool = load_puzzles(pool_path)
    new_candidates = dedupe_against(pool, existing)

    # Score everything consistently within the tier.
    combined = existing + new_candidates
    recompute_difficulty(combined)
    for p in combined:
        p["quality"] = compute_quality(p)

    # Existing puzzles are always retained; fill the rest with the highest
    # quality new puzzles. target=None keeps every available puzzle.
    new_candidates.sort(key=lambda p: p["quality"], reverse=True)
    if target is None:
        selected_new = new_candidates
    else:
        selected_new = new_candidates[: max(target - len(existing), 0)]

    final = existing + selected_new
    stamp_metadata(final, title_prefix, author, contact, description)
    write_puzzles(out_path, final)

    print(
        f"  {title_prefix:<11} {len(final):>4} puzzles "
        f"({len(existing)} kept + {len(selected_new)} added"
        f"{f', target {target}' if target is not None else ', all available'})"
    )


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------
def main() -> int:
    global VERBOSITY

    parser = argparse.ArgumentParser(
        description="Curate Sudoku puzzles: composite quality scoring + metadata."
    )
    parser.add_argument("--all", action="store_true",
                        help="Rebuild every tier from --pool-dir into --out-dir.")
    parser.add_argument("--pool-dir", help="Directory of generation pool files (for --all).")
    parser.add_argument("--out-dir", help="Directory of in-game puzzle files (for --all).")
    # Single-tier mode
    parser.add_argument("--in", dest="input_file", help="Source pool JSON file.")
    parser.add_argument("--out", dest="output_file", help="Destination in-game JSON file.")
    parser.add_argument("--target", type=int, default=None,
                        help="Total puzzles to keep (omit to keep all available).")
    parser.add_argument("--title-prefix", default="Puzzle",
                        help="Title prefix, e.g. 'Beginner' -> 'Beginner #1'.")
    parser.add_argument("--author", default=DEFAULT_AUTHOR)
    parser.add_argument("--contact", default=DEFAULT_CONTACT)
    parser.add_argument("--description", default=DEFAULT_DESCRIPTION)
    parser.add_argument("--seed", type=int, default=None, help="RNG seed (reproducibility).")
    parser.add_argument("-v", action="count", default=0,
                        help="Verbosity (-v stats, -vv debug).")

    args = parser.parse_args()
    VERBOSITY = args.v
    if args.seed is not None:
        random.seed(args.seed)

    if args.all:
        if not args.pool_dir or not args.out_dir:
            print("Error: --all requires --pool-dir and --out-dir", file=sys.stderr)
            return 1
        pool_dir = Path(args.pool_dir).resolve()
        out_dir = Path(args.out_dir).resolve()
        print(f"Rebuilding tiers: {pool_dir} -> {out_dir}")
        for filename, prefix, target in TIERS:
            process_tier(
                pool_dir / filename, out_dir / filename, prefix, target,
                args.author, args.contact, args.description,
            )
        print("Done.")
        return 0

    if not args.input_file or not args.output_file:
        print("Error: provide --all, or both --in and --out", file=sys.stderr)
        return 1

    process_tier(
        Path(args.input_file).resolve(), Path(args.output_file).resolve(),
        args.title_prefix, args.target,
        args.author, args.contact, args.description,
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
