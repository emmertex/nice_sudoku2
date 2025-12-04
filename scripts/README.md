# Puzzle Generator Script

This script generates sudoku puzzles using `qqwing` and grades them using the backend solver based on the hardest technique required to solve them.

## Requirements

1. **qqwing** - For generating puzzles
   - Available via nix-shell (already in shell.nix)
   - Or install via your package manager

2. **Backend server** - Must be running to grade puzzles
   - Default: `http://localhost:8181`
   - Start with: `cd backend && ./gradlew run`

3. **Kotlin** - To run the script
   - Available via nix-shell
   - Or install Kotlin separately

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

### Direct Kotlin Script

```bash
kotlin -script scripts/generate_puzzles.kt [API_URL]
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
      "title": null
    }
  ]
}
```

- `puzzleId`: Unique ID within the difficulty category (starts at 1)
- `difficulty`: Normalized score from 0-100
- `givens`: 81-character string (0 = empty, 1-9 = given)
- `solution`: 81-character solved puzzle string
- `title`: Optional title (null for auto-generated puzzles)

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

