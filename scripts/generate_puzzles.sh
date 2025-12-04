#!/bin/bash

# Script to generate sudoku puzzles with difficulty grading
# Requires:
# - qqwing (for puzzle generation)
# - Backend server running (default: http://localhost:8181)
# - Kotlin script runner (kscript or kotlin -script)

# Default API URL
API_URL="${1:-http://localhost:8181}"

echo "Puzzle Generator for Nice Sudoku 2"
echo "==================================="
echo ""
echo "This script will:"
echo "1. Generate puzzles using qqwing"
echo "2. Grade them using the backend solver"
echo "3. Categorize by difficulty"
echo "4. Save to JSON files in the puzzles/ directory"
echo ""
echo "Make sure the backend server is running at: $API_URL"
echo ""
read -p "Press Enter to continue or Ctrl+C to cancel..."

# Check if backend is available
if ! curl -s "$API_URL/health" > /dev/null; then
    echo "ERROR: Backend not available at $API_URL"
    echo "Please start the backend server first:"
    echo "  cd backend && ./gradlew run"
    exit 1
fi

# Check if qqwing is available
if ! command -v qqwing &> /dev/null; then
    echo "ERROR: qqwing not found in PATH"
    echo "Please install qqwing (e.g., via nix-shell or package manager)"
    exit 1
fi

# Run the Kotlin script
if command -v kotlin &> /dev/null; then
    kotlin -script scripts/generate_puzzles.kt "$API_URL"
elif command -v kscript &> /dev/null; then
    kscript scripts/generate_puzzles.kt "$API_URL"
else
    echo "ERROR: Neither 'kotlin' nor 'kscript' found in PATH"
    echo "Please install Kotlin or kscript to run this script"
    exit 1
fi

