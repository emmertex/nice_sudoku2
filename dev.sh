#!/usr/bin/env bash

# Check if we're in a nix-shell, if not, enter one
if [ -z "$IN_NIX_SHELL" ]; then
    echo "Entering nix-shell..."
    exec nix-shell --run "$0 $*"
    exit $?
fi

# Check if tmux is already running with our session
if tmux has-session -t sudoku-dev 2>/dev/null; then
    echo "tmux session 'sudoku-dev' already exists. Attaching..."
    echo "Note: If backend shows 'Address already in use', the backend is likely already running in another window."
    tmux attach-session -t sudoku-dev
    exit $?
fi

# Create new tmux session with 3 windows
echo "Creating new tmux session 'sudoku-dev'..."

PROJECT_DIR="$(pwd)"

# Function to check if a port is in use
check_port() {
    local port=$1
    lsof -ti:$port >/dev/null 2>&1
}

# Create session with first window (backend) - start with a shell, then send the command
tmux new-session -d -s sudoku-dev -n backend -c "$PROJECT_DIR"
if check_port 8181; then
    echo "Backend already running on port 8181, skipping startup..."
    tmux send-keys -t sudoku-dev:backend "echo 'Backend already running on port 8181. If you need to restart it, stop the existing process first.'" C-m
else
    tmux send-keys -t sudoku-dev:backend "./gradlew :backend:run" C-m
fi

# Create second window (web)
tmux new-window -t sudoku-dev -n web -c "$PROJECT_DIR"
# Check for web dev server port (typically 8080 or similar, but jsBrowserDevelopmentRun might use a different port)
# For now, just try to start it - if it fails, the user will see the error
tmux send-keys -t sudoku-dev:web "./gradlew :web:jsBrowserDevelopmentRun" C-m

# Create third window (spare) - just a shell
tmux new-window -t sudoku-dev -n spare -c "$PROJECT_DIR"

# Select the first window
tmux select-window -t sudoku-dev:backend

# Attach to the session
tmux attach-session -t sudoku-dev

