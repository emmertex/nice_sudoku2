#!/usr/bin/env python3
"""
Puzzle processing script:
- Deduplicate puzzles by 'givens' string
- Calculate quality score based on technique variety
- Calculate complexity score from technique priorities
- Weighted random selection prioritizing quality
- Append to output with sequential puzzle IDs
- Optional trim of source file to remove used puzzles
"""

import argparse
import json
import math
import random
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any

# Technique priority mapping from SudokuService.kt (lower = easier)
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
    # HARD (16-22): Coloring, uniqueness, wings, swordfish
    "SIMPLE_COLOURING": 16, "Simple Colouring": 16, "Simple Coloring": 16,
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

# Verbosity levels
VERBOSITY = 0


def log_stats(msg: str) -> None:
    """Print statistics (requires -v)."""
    if VERBOSITY >= 1:
        print(f"[STATS] {msg}")


def log_debug(msg: str) -> None:
    """Print debug info (requires -vv)."""
    if VERBOSITY >= 2:
        print(f"[DEBUG] {msg}")


def load_puzzles(filepath: Path) -> list[dict[str, Any]]:
    """Load puzzles from a JSON file."""
    if not filepath.exists():
        log_debug(f"File does not exist: {filepath}")
        return []
    
    with open(filepath, 'r') as f:
        data = json.load(f)
    
    puzzles = data.get('puzzles', [])
    log_debug(f"Loaded {len(puzzles)} puzzles from {filepath}")
    return puzzles


def get_max_puzzle_id(puzzles: list[dict[str, Any]]) -> int:
    """Get the maximum puzzleId from a list of puzzles."""
    if not puzzles:
        return 0
    return max(p.get('puzzleId', 0) for p in puzzles)


def deduplicate_puzzles(
    input_puzzles: list[dict[str, Any]], 
    existing_puzzles: list[dict[str, Any]]
) -> list[dict[str, Any]]:
    """Remove duplicates by 'givens' string."""
    # Build set of existing givens
    existing_givens: set[str] = {p['givens'] for p in existing_puzzles if 'givens' in p}
    log_debug(f"Existing unique givens: {len(existing_givens)}")
    
    # Track seen givens for input deduplication
    seen_givens: set[str] = set(existing_givens)
    deduplicated: list[dict[str, Any]] = []
    duplicates_in_input = 0
    duplicates_with_existing = 0
    
    for puzzle in input_puzzles:
        givens = puzzle.get('givens', '')
        if givens in seen_givens:
            if givens in existing_givens:
                duplicates_with_existing += 1
            else:
                duplicates_in_input += 1
            continue
        seen_givens.add(givens)
        deduplicated.append(puzzle)
    
    log_stats(f"Deduplication: {len(input_puzzles)} -> {len(deduplicated)} puzzles")
    log_debug(f"  Duplicates within input: {duplicates_in_input}")
    log_debug(f"  Duplicates with existing output: {duplicates_with_existing}")
    
    return deduplicated


def round_difficulty(difficulty: float) -> float:
    """Round difficulty to 2.5 steps (floor)."""
    return math.floor(difficulty / 2.5) * 2.5


def group_by_difficulty(puzzles: list[dict[str, Any]]) -> dict[float, list[dict[str, Any]]]:
    """Group puzzles by rounded difficulty."""
    groups: dict[float, list[dict[str, Any]]] = defaultdict(list)
    
    for puzzle in puzzles:
        original_diff = puzzle.get('difficulty', 0.0)
        rounded_diff = round_difficulty(original_diff)
        puzzle['_rounded_difficulty'] = rounded_diff
        groups[rounded_diff].append(puzzle)
    
    log_stats(f"Grouped into {len(groups)} difficulty levels")
    for diff in sorted(groups.keys()):
        log_debug(f"  Difficulty {diff}: {len(groups[diff])} puzzles")
    
    return groups


def count_technique_types(puzzle: dict[str, Any]) -> int:
    """Count unique technique types used in a puzzle."""
    techniques = puzzle.get('techniques', {})
    return len(techniques)


def calculate_quality_scores(groups: dict[float, list[dict[str, Any]]]) -> None:
    """Calculate quality score (technique variety) per difficulty group."""
    for diff, puzzles in groups.items():
        if not puzzles:
            continue
        
        # Count technique types for each puzzle
        technique_counts = [count_technique_types(p) for p in puzzles]
        max_count = max(technique_counts) if technique_counts else 1
        
        log_debug(f"Difficulty {diff}: max technique types = {max_count}")
        
        # Normalize to 0-10 scale
        for puzzle, count in zip(puzzles, technique_counts):
            if max_count > 0:
                quality = (count / max_count) * 10.0
            else:
                quality = 0.0
            puzzle['quality'] = round(quality, 2)
            log_debug(f"  Puzzle {puzzle.get('puzzleId', '?')}: {count} techniques -> quality {puzzle['quality']}")


def get_technique_priority(name: str) -> int:
    """Get priority for a technique name, trying various formats."""
    if name in TECHNIQUE_PRIORITY:
        return TECHNIQUE_PRIORITY[name]
    if name.upper() in TECHNIQUE_PRIORITY:
        return TECHNIQUE_PRIORITY[name.upper()]
    if name.replace("_", " ") in TECHNIQUE_PRIORITY:
        return TECHNIQUE_PRIORITY[name.replace("_", " ")]
    # Unknown techniques get high priority (harder)
    log_debug(f"Unknown technique: {name}, assigning priority 100")
    return 100


def calculate_complexity(puzzle: dict[str, Any]) -> int:
    """Calculate complexity score as sum of (priority * count) for each technique."""
    techniques = puzzle.get('techniques', {})
    complexity = 0
    for technique_name, count in techniques.items():
        priority = get_technique_priority(technique_name)
        complexity += priority * count
    return complexity


def calculate_complexity_scores(puzzles: list[dict[str, Any]]) -> None:
    """Calculate and normalize complexity scores for all puzzles."""
    if not puzzles:
        return
    
    # Calculate raw complexity for each puzzle
    for puzzle in puzzles:
        puzzle['_raw_complexity'] = calculate_complexity(puzzle)
    
    # Find min/max for normalization
    complexities = [p['_raw_complexity'] for p in puzzles]
    min_complexity = min(complexities)
    max_complexity = max(complexities)
    complexity_range = max_complexity - min_complexity
    
    log_stats(f"Complexity range: {min_complexity} - {max_complexity}")
    
    # Normalize to 0-2.4 and add to rounded difficulty
    for puzzle in puzzles:
        if complexity_range > 0:
            normalized = ((puzzle['_raw_complexity'] - min_complexity) / complexity_range) * 2.4
        else:
            normalized = 0.0
        
        # Update difficulty = rounded_difficulty + normalized_complexity
        puzzle['difficulty'] = round(puzzle['_rounded_difficulty'] + normalized, 2)
        log_debug(f"  Puzzle: raw_complexity={puzzle['_raw_complexity']}, "
                  f"normalized={normalized:.2f}, final_difficulty={puzzle['difficulty']}")


def weighted_random_selection(
    puzzles: list[dict[str, Any]], 
    limit: int
) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    """Select puzzles using weighted random where higher quality = higher chance."""
    if not puzzles:
        return [], []
    
    if limit >= len(puzzles):
        log_stats(f"Limit ({limit}) >= available puzzles ({len(puzzles)}), selecting all")
        return puzzles.copy(), []
    
    # Create weighted selection
    # Weight = quality^2 to strongly favor higher quality
    # Add small epsilon to avoid zero weights
    weights = [(p.get('quality', 0) + 0.1) ** 2 for p in puzzles]
    
    available = list(range(len(puzzles)))
    selected_indices: set[int] = set()
    
    while len(selected_indices) < limit and available:
        # Calculate weights for remaining puzzles
        remaining_weights = [weights[i] for i in available]
        total_weight = sum(remaining_weights)
        
        # Weighted random selection
        r = random.random() * total_weight
        cumulative = 0.0
        chosen_idx = available[0]
        
        for idx, w in zip(available, remaining_weights):
            cumulative += w
            if r <= cumulative:
                chosen_idx = idx
                break
        
        selected_indices.add(chosen_idx)
        available.remove(chosen_idx)
    
    selected = [puzzles[i] for i in sorted(selected_indices)]
    unused = [puzzles[i] for i in range(len(puzzles)) if i not in selected_indices]
    
    log_stats(f"Selected {len(selected)} puzzles, {len(unused)} remaining unused")
    
    # Log quality distribution of selected puzzles
    if VERBOSITY >= 1:
        qualities = [p.get('quality', 0) for p in selected]
        if qualities:
            avg_quality = sum(qualities) / len(qualities)
            log_stats(f"Selected puzzles avg quality: {avg_quality:.2f}")
    
    return selected, unused


def write_output(
    filepath: Path, 
    existing_puzzles: list[dict[str, Any]], 
    new_puzzles: list[dict[str, Any]],
    start_id: int
) -> None:
    """Write puzzles to output file with sequential IDs."""
    # Assign new puzzle IDs
    current_id = start_id
    for puzzle in new_puzzles:
        current_id += 1
        puzzle['puzzleId'] = current_id
        # Clean up internal fields
        puzzle.pop('_rounded_difficulty', None)
        puzzle.pop('_raw_complexity', None)
    
    # Combine existing and new puzzles
    all_puzzles = existing_puzzles + new_puzzles
    
    # Write as JSON array with each puzzle on its own line
    with open(filepath, 'w') as f:
        f.write('{"puzzles": [\n')
        for i, puzzle in enumerate(all_puzzles):
            puzzle_json = json.dumps(puzzle, separators=(',', ':'))
            if i < len(all_puzzles) - 1:
                f.write(f'  {puzzle_json},\n')
            else:
                f.write(f'  {puzzle_json}\n')
        f.write(']}\n')
    
    log_stats(f"Wrote {len(all_puzzles)} puzzles to {filepath}")
    log_debug(f"  New puzzles: {len(new_puzzles)} (IDs {start_id + 1} - {current_id})")


def write_trim(filepath: Path, puzzles: list[dict[str, Any]]) -> None:
    """Write unused puzzles back to source file."""
    # Clean up internal fields
    for puzzle in puzzles:
        puzzle.pop('_rounded_difficulty', None)
        puzzle.pop('_raw_complexity', None)
    
    with open(filepath, 'w') as f:
        f.write('{"puzzles": [\n')
        for i, puzzle in enumerate(puzzles):
            puzzle_json = json.dumps(puzzle, separators=(',', ':'))
            if i < len(puzzles) - 1:
                f.write(f'  {puzzle_json},\n')
            else:
                f.write(f'  {puzzle_json}\n')
        f.write(']}\n')
    
    log_stats(f"Trimmed source file to {len(puzzles)} unused puzzles")


def main() -> int:
    global VERBOSITY
    
    parser = argparse.ArgumentParser(
        description='Process and select Sudoku puzzles with quality-based weighting'
    )
    parser.add_argument(
        '--in', 
        dest='input_file',
        required=True, 
        help='Source JSON file path'
    )
    parser.add_argument(
        '--out', 
        dest='output_file',
        required=True, 
        help='Destination JSON file path'
    )
    parser.add_argument(
        '--limit', 
        type=int, 
        required=True, 
        help='Number of puzzles to add to output'
    )
    parser.add_argument(
        '--trim', 
        action='store_true', 
        help='Overwrite source file with unused puzzles'
    )
    parser.add_argument(
        '-v', 
        action='count', 
        default=0, 
        help='Verbosity level (-v for stats, -vv for debug)'
    )
    
    args = parser.parse_args()
    VERBOSITY = args.v
    
    input_path = Path(args.input_file).resolve()
    output_path = Path(args.output_file).resolve()
    
    log_stats(f"Input file: {input_path}")
    log_stats(f"Output file: {output_path}")
    log_stats(f"Limit: {args.limit}")
    log_stats(f"Trim: {args.trim}")
    
    # Step 1: Load data
    log_stats("Loading puzzles...")
    input_puzzles = load_puzzles(input_path)
    if not input_puzzles:
        print(f"Error: No puzzles found in {input_path}", file=sys.stderr)
        return 1
    
    existing_puzzles = load_puzzles(output_path)
    max_id = get_max_puzzle_id(existing_puzzles)
    log_stats(f"Max existing puzzle ID: {max_id}")
    
    # Step 2: Deduplicate
    log_stats("Deduplicating...")
    deduplicated = deduplicate_puzzles(input_puzzles, existing_puzzles)
    if not deduplicated:
        print("No new unique puzzles to process after deduplication")
        return 0
    
    # Step 3 & 4: Round difficulty and group
    log_stats("Grouping by difficulty...")
    groups = group_by_difficulty(deduplicated)
    
    # Step 5: Calculate quality scores (per difficulty group)
    log_stats("Calculating quality scores...")
    calculate_quality_scores(groups)
    
    # Flatten groups back to single list
    all_puzzles = [p for puzzles in groups.values() for p in puzzles]
    
    # Step 6 & 7: Calculate and normalize complexity scores
    log_stats("Calculating complexity scores...")
    calculate_complexity_scores(all_puzzles)
    
    # Step 8: Weighted random selection
    log_stats("Selecting puzzles...")
    selected, unused = weighted_random_selection(all_puzzles, args.limit)
    
    if not selected:
        print("No puzzles selected")
        return 0
    
    # Step 9: Write output
    log_stats("Writing output...")
    write_output(output_path, existing_puzzles, selected, max_id)
    
    # Step 10: Trim if enabled
    if args.trim:
        log_stats("Trimming source file...")
        write_trim(input_path, unused)
    
    print(f"Successfully processed {len(selected)} puzzles")
    return 0


if __name__ == "__main__":
    sys.exit(main())
