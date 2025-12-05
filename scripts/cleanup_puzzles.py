#!/usr/bin/env python3
"""
Cleanup puzzle JSON files:
- Maximum 500 puzzles per file
- Divide difficulty ratings by 10, round to 1 decimal place
- Cap ratings at 10 if over 10
"""

import json
import os
from pathlib import Path

PUZZLES_DIR = Path(__file__).parent / "../web/src/jsMain/resources/puzzles"
MAX_PUZZLES = 500

def process_difficulty(difficulty: float) -> float:
    return min(difficulty, 5.0)

def process_file(filepath: Path):
    """Process a single puzzle file."""
    print(f"Processing {filepath.name}...")
    
    with open(filepath, 'r') as f:
        data = json.load(f)
    
    puzzles = data.get('puzzles', [])
    original_count = len(puzzles)
    
    # Limit to MAX_PUZZLES
    puzzles = puzzles[:MAX_PUZZLES]
    
    # Process each puzzle's difficulty
    for puzzle in puzzles:
        if 'difficulty' in puzzle:
            puzzle['difficulty'] = process_difficulty(puzzle['difficulty'])
    
    # Write back
    output_data = {'puzzles': puzzles}
    with open(filepath, 'w') as f:
        json.dump(output_data, f, separators=(',', ':'))
    
    print(f"  {filepath.name}: {original_count} -> {len(puzzles)} puzzles")
    if puzzles:
        difficulties = [p['difficulty'] for p in puzzles if 'difficulty' in p]
        if difficulties:
            print(f"  Difficulty range: {min(difficulties)} - {max(difficulties)}")

def main():
    json_files = list(PUZZLES_DIR.glob("*.json"))
    
    if not json_files:
        print(f"No JSON files found in {PUZZLES_DIR}")
        return
    
    print(f"Found {len(json_files)} JSON files to process\n")
    
    for filepath in sorted(json_files):
        process_file(filepath)
        print()

if __name__ == "__main__":
    main()

