# Puzzle Generator Script

This script generates sudoku puzzles using `qqwing` and grades them using the backend solver based on the hardest technique required to solve them.

## Requirements

1. **qqwing** - For generating puzzles
   - Available via nix-shell (already in shell.nix)
   - Or install via your package manager

2. **Backend server** - Must be running to grade puzzles
   - Default: `http://localhost:8181`
   - Start with: `cd backend && ./gradlew run`

3. **Gradle** - To build and run the script
   - Uses the project's Gradle wrapper (gradlew)

## Usage

### Quick Start

```bash
# Make sure backend is running first
cd backend && ./gradlew run &
cd ..

# Run the generator
./scripts/generate_puzzles.sh

# Or specify a custom API URL
./scripts/generate_puzzles.sh http://localhost:8181
```

### Direct Gradle

```bash
# Default (http://localhost:8181)
./gradlew :scripts:run

# Custom API URL
./gradlew :scripts:run -PapiUrl="http://localhost:8181"
```

## How It Works

1. **Generation**: Uses `qqwing` to generate random valid sudoku puzzles
2. **Grading**: 
   - Loads puzzle into backend
   - Solves step-by-step using techniques
   - Tracks the maximum technique priority needed
   - Normalizes to 0-100 score (Jellyfish = 50)
3. **Categorization**: Groups puzzles into difficulty categories:
   - **Basic** (priority 1-2): 50 puzzles
   - **Easy** (priority 3-4): 250 puzzles
   - **Tough** (priority 5-9): 250 puzzles
   - **Hard** (priority 10-16): 100 puzzles
   - **Diabolical** (priority 17-25): 25-100 puzzles
   - **Extreme** (priority 26-40): 10-50 puzzles
4. **Storage**: Saves to JSON files in `puzzles/` directory:
   - `basic.json`
   - `easy.json`
   - `tough.json`
   - `hard.json`
   - `diabolical.json`
   - `extreme.json`

## Curating the in-game collection (cleanup_puzzles.py)

`generate_puzzles.sh` fills the generation pool in `scripts/puzzles/`. To turn that
pool into the curated, metadata-stamped files the game ships
(`web/src/jsMain/resources/puzzles/`), use `cleanup_puzzles.py`.

Rebuild every tier at once:

```bash
python3 scripts/cleanup_puzzles.py --all \
    --pool-dir scripts/puzzles \
    --out-dir  web/src/jsMain/resources/puzzles
```

Per-tier behaviour (`TIERS` table in the script):

- Beginner / Easy / Medium -> top **200** by quality
- Tough -> top **100** by quality
- Hard / Expert / Diabolical -> **all available**, in quality order

For every tier the existing in-game puzzles are kept verbatim (same order, same
IDs); the remaining slots are filled with the highest-quality *new* pool puzzles.
Each puzzle is then (re)scored, given a fine-grained difficulty, and stamped with
collection metadata (author / contact / description / title).

### Quality metric

`quality` (0-10, shown to players) is **orthogonal to difficulty** — difficulty
says how hard, quality says how *nice* a puzzle is for its tier. It is an
**absolute** composite (it does not depend on the rest of the batch), weighting:

- **Variety** (0.30) — distinct techniques in the solve path
- **Balance** (0.20) — Shannon entropy of the move distribution
- **Signature** (0.25) — recurrence of the tier's hallmark (hardest) techniques
- **Anti-grind** (0.15) — penalty for solves dominated by naked-single filler
- **Elegance** (0.10) — 180° givens symmetry + givens economy

Weights and thresholds are named constants at the top of the script. A pure
naked-single grind scores ~1; a varied, well-balanced puzzle scores ~8.

## Output Format

Each puzzle file contains a JSON array of puzzles:

```json
{
  "puzzles": [
    {
      "puzzleId": 1,
      "difficulty": 12.5,
      "givens": "530070000600195000098000060800060003400803001700020006060000280000419005000080079",
      "solution": "534678912672195348198342567859761423426853791713924856961537284287419635345286179",
      "techniques": {"Naked Singles": 40, "Hidden Singles": 13},
      "quality": 7.6,
      "title": "Easy #1",
      "author": "Emmertex",
      "authorContact": "sudoku.emmertex.com",
      "description": "Nice Sudoku - Included Puzzle Collection"
    }
  ]
}
```

- `puzzleId`: Unique ID within the difficulty category (starts at 1)
- `difficulty`: Normalized score from 0-100
- `givens`: 81-character string (0 = empty, 1-9 = given)
- `solution`: 81-character solved puzzle string
- `techniques`: technique name -> count needed to solve
- `quality`: composite 0-10 niceness score (see above)
- `title` / `author` / `authorContact` / `description`: collection metadata

## Notes

- The script will continue generating until all categories reach their target counts
- Progress is saved after each puzzle is generated
- Existing puzzles are loaded on startup (won't regenerate duplicates)
- Puzzle IDs are unique per difficulty file and increment from 1

## Troubleshooting

**Backend not available:**
- Make sure the backend is running: `cd backend && ./gradlew run`
- Check the API URL matches your backend port

**qqwing not found:**
- Run `nix-shell` to get qqwing
- Or install qqwing via your package manager

**Kotlin not found:**
- Run `nix-shell` to get Kotlin
- Or install Kotlin separately

