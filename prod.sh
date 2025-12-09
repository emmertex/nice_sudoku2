# Script to build and run production build locally

# Check if we're in a nix-shell, if not, enter one
if [ -z "$IN_NIX_SHELL" ]; then
    echo "Entering nix-shell..."
    exec nix-shell --run "$0 $*"
    exit $?
fi

# Check if tmux is already running with our session
if tmux has-session -t sudoku-prod 2>/dev/null; then
    echo "tmux session 'sudoku-prod' already exists. Attaching..."
    echo "Note: If servers show 'Address already in use', they are likely already running in another window."
    tmux attach-session -t sudoku-prod
    exit $?
fi

PROJECT_DIR="$(pwd)"
FRONTEND_PORT=8081
BACKEND_PORT=8181

# Function to check if a port is in use
check_port() {
    local port=$1
    lsof -ti:$port >/dev/null 2>&1
}

echo "Building production bundle..."
./gradlew :web:jsBrowserProductionWebpack

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

# Create distribution directory by combining webpack output and resources
PROD_DIR="$PROJECT_DIR/web/build/distributions"
echo "Assembling production distribution..."
rm -rf "$PROD_DIR"
mkdir -p "$PROD_DIR"

# Copy resources (HTML, manifest, service worker, puzzles)
cp -r "$PROJECT_DIR/web/build/processedResources/js/main/"* "$PROD_DIR/"

# Copy compiled JS
cp "$PROJECT_DIR/web/build/kotlin-webpack/js/productionExecutable/web.js" "$PROD_DIR/"
cp "$PROJECT_DIR/web/build/kotlin-webpack/js/productionExecutable/web.js.map" "$PROD_DIR/"

# Verify the production directory exists and has files
if [ ! -d "$PROD_DIR" ] || [ ! -f "$PROD_DIR/index.html" ] || [ ! -f "$PROD_DIR/web.js" ]; then
    echo "❌ Production distribution assembly failed"
    exit 1
fi

echo "✅ Production build complete!"
echo "   Distribution ready at: $PROD_DIR"
echo "Creating new tmux session 'sudoku-prod'..."

# Create session with first window (backend)
tmux new-session -d -s sudoku-prod -n backend -c "$PROJECT_DIR"
if check_port $BACKEND_PORT; then
    echo "Backend already running on port $BACKEND_PORT, skipping startup..."
    tmux send-keys -t sudoku-prod:backend "echo 'Backend already running on port $BACKEND_PORT. If you need to restart it, stop the existing process first.'" C-m
else
    tmux send-keys -t sudoku-prod:backend "./gradlew :backend:run" C-m
fi

# Create second window (web frontend) - serve static files
tmux new-window -t sudoku-prod -n frontend -c "$PROJECT_DIR"
if check_port $FRONTEND_PORT; then
    echo "Frontend already running on port $FRONTEND_PORT, skipping startup..."
    tmux send-keys -t sudoku-prod:frontend "echo 'Frontend already running on port $FRONTEND_PORT. If you need to restart it, stop the existing process first.'" C-m
else
    # Change to production directory and start server (all in one command to ensure correct directory)
    tmux send-keys -t sudoku-prod:frontend "cd web/build/distributions && python3 -m http.server $FRONTEND_PORT" C-m
fi

# Create third window (spare) - just a shell
tmux new-window -t sudoku-prod -n spare -c "$PROJECT_DIR"

# Select the first window
tmux select-window -t sudoku-prod:backend

echo ""
echo "🚀 Production servers starting..."
echo "   Frontend: http://localhost:$FRONTEND_PORT"
echo "   Backend:  http://localhost:$BACKEND_PORT"
echo ""

# Attach to the session
tmux attach-session -t sudoku-prod

