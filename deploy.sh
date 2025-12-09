# Script to pull latest code from git and rebuild for production deployment

# Check if we're in a nix-shell, if not, enter one
if [ -z "$IN_NIX_SHELL" ]; then
    echo "Entering nix-shell..."
    exec nix-shell --run "$0 $*"
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

echo "🔄 Pulling latest changes from git..."
if ! git pull; then
    git stash
    git pull
    git stash pop
fi

if [ $? -ne 0 ]; then
    echo "❌ Git pull failed! Please resolve any conflicts and try again."
    exit 1
fi

echo ""
echo "🧹 Cleaning previous builds..."
./gradlew :web:clean

echo ""
echo "🔨 Building production bundle..."
./gradlew :web:jsBrowserProductionWebpack

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo ""
echo "✅ Production build complete!"
echo ""

# Check if tmux session exists
if tmux has-session -t sudoku-prod 2>/dev/null; then
    echo "🔄 Restarting servers in existing tmux session..."
    
    # Kill frontend window and recreate
    tmux kill-window -t sudoku-prod:frontend 2>/dev/null
    tmux new-window -t sudoku-prod -n frontend -c "$PROJECT_DIR/web/build/dist/js/productionExecutable"
    tmux send-keys -t sudoku-prod:frontend "python3 -m http.server $FRONTEND_PORT" C-m
    
    # Optionally restart backend
    read -p "Restart backend? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        tmux kill-window -t sudoku-prod:backend 2>/dev/null
        tmux new-window -t sudoku-prod -n backend -c "$PROJECT_DIR"
        tmux send-keys -t sudoku-prod:backend "./gradlew :backend:run" C-m
    fi
    
    echo ""
    echo "✅ Deployment complete!"
    echo "   Frontend restarted on port $FRONTEND_PORT"
    echo ""
    echo "Attach to session with: tmux attach-session -t sudoku-prod"
else
    echo "ℹ️  No running tmux session found."
    echo ""
    echo "📦 Production files ready at:"
    echo "   web/build/dist/js/productionExecutable/"
    echo ""
    echo "🚀 To start production servers, run:"
    echo "   ./prod.sh"
fi

