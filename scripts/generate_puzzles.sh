#!/bin/bash

# Script to generate sudoku puzzles with difficulty grading
# Requires:
# - qqwing (for puzzle generation) OR an input file with puzzles
# - Backend server running (default: http://localhost:8181)
# - Gradle (uses project's gradlew)
#
# Usage:
#   ./scripts/generate_puzzles.sh                     # Generate with qqwing
#   ./scripts/generate_puzzles.sh puzzles.txt        # Load puzzles from file
#   ./scripts/generate_puzzles.sh http://host:port   # Custom API URL
#   ./scripts/generate_puzzles.sh http://host:port puzzles.txt  # Both
#   ./scripts/generate_puzzles.sh puzzles.txt 16     # With 16 parallel workers

# Parse arguments
API_URL="http://localhost:8181"
INPUT_FILE=""
PARALLELISM="8"  # Default 8 parallel workers

for arg in "$@"; do
    if [[ "$arg" == http* ]]; then
        API_URL="$arg"
    elif [[ "$arg" =~ ^[0-9]+$ ]]; then
        PARALLELISM="$arg"
    elif [[ -f "$arg" ]] || [[ "$arg" == *.txt ]]; then
        # Convert to absolute path for Gradle
        INPUT_FILE="$(realpath "$arg")"
    fi
done

echo "Puzzle Generator for Nice Sudoku 2"
echo "==================================="
echo ""
if [ -n "$INPUT_FILE" ]; then
    echo "Mode: Loading puzzles from file"
    echo "Input file: $INPUT_FILE"
else
    echo "Mode: Generating puzzles with qqwing"
fi
echo "Parallel workers: $PARALLELISM"
echo ""
echo "This script will:"
echo "1. Load/generate puzzles"
echo "2. Grade them using the backend solver ($PARALLELISM parallel)"
echo "3. Categorize by difficulty"
echo "4. Save to JSON files in the puzzles/ directory"
echo ""
echo "Backend server: $API_URL"
echo ""
read -p "Press Enter to continue or Ctrl+C to cancel..."

# Check if backend is available
if ! curl -s "$API_URL/health" > /dev/null; then
    echo "ERROR: Backend not available at $API_URL"
    echo "Please start the backend server first:"
    echo "  cd backend && ./gradlew run"
    exit 1
fi

# Check if input file exists (if specified)
if [ -n "$INPUT_FILE" ] && [ ! -f "$INPUT_FILE" ]; then
    echo "ERROR: Input file not found: $INPUT_FILE"
    exit 1
fi

# Check if qqwing is available (only needed if not using input file)
if [ -z "$INPUT_FILE" ]; then
    if ! command -v qqwing &> /dev/null; then
        echo "ERROR: qqwing not found in PATH"
        echo "Please install qqwing (e.g., via nix-shell or package manager)"
        echo "Or provide an input file with puzzles: ./scripts/generate_puzzles.sh puzzles.txt"
        exit 1
    fi
fi

# Run using Gradle
echo "Running puzzle generator via Gradle..."
echo "Using $PARALLELISM parallel workers"
if [ -n "$INPUT_FILE" ]; then
    ./gradlew :scripts:run -PapiUrl="$API_URL" -PinputFile="$INPUT_FILE" -Pparallelism="$PARALLELISM"
else
    ./gradlew :scripts:run -PapiUrl="$API_URL" -Pparallelism="$PARALLELISM"
fi

