package view


val CSS_STYLES = """
    * {
        box-sizing: border-box;
        margin: 0;
        padding: 0;
    }
    
    /* Touch support fixes for Firefox and other browsers */
    html {
        touch-action: manipulation;
        -webkit-tap-highlight-color: transparent;
    }
    
    button, .cell {
        touch-action: manipulation;
        -webkit-tap-highlight-color: transparent;
        user-select: none;
        -webkit-user-select: none;
    }
    
    :root {
        /* Base size calculations - scales with viewport */
        --grid-size: min(90vw, 90vh - 200px, 600px);
        --cell-size: calc(var(--grid-size) / 9.5);
        --font-scale: min(1, var(--grid-size) / 400);

        /* Theme colors - these are set dynamically by JavaScript */
        --color-bg-primary: 26, 26, 46;
        --color-bg-secondary: 22, 33, 62;
        --color-bg-tertiary: 15, 52, 96;
        --color-accent-primary: 100, 181, 246;
        --color-accent-primary-text: 255,255,255;
        --color-accent-secondary: 255, 82, 82;
        --color-accent-tertiary: 255, 193, 7;
        --color-text-primary: 255, 255, 255;
        --color-text-secondary: 204, 204, 204;
        --color-text-tertiary: 136, 136, 136;
        --color-grid-yes: 76, 175, 80;
        --color-grid-neutral: 158, 158, 158;
        --color-grid-no: 244, 67, 54;
        /* Derived colors */
        --color-accent-success: 76, 175, 80;
        --color-accent-success-text: 255, 255, 255;
        --color-accent-info: 100, 181, 246;
        --color-accent-info-text: 255, 255, 255;
        --color-accent-warning: 255, 193, 7;
        --color-accent-warning-text: 255, 255, 255;
        --color-accent-error: 244, 67, 54;
        --color-accent-error-text: 255, 255, 255;
        --color-border: 22, 33, 62;
        --color-shadow: 0, 0, 0;
        --color-accent-desat: 255, 255, 255;
        --gradient-bg: linear-gradient(135deg, rgb(26,26,46) 0%, rgb(22,33,62) 50%, rgb(15,52,96) 100%);
    }
    
    html, body {
        height: 100%;
        overflow: hidden;
    }
    
    body {
        font-family: 'SF Pro Display', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
        background: var(--gradient-bg);
        min-height: 100vh;
        min-height: 100dvh; /* Dynamic viewport height for mobile */
        display: flex;
        justify-content: center;
        align-items: center;
        padding: clamp(8px, 2vmin, 20px);
    }
    
    #app {
        width: 100%;
        height: 100%;
        display: flex;
        justify-content: center;
        align-items: center;
    }
    
    .sudoku-container-wrapper {
        width: 100%;
        height: 100%;
        display: flex;
        justify-content: center;
        align-items: center;
        overflow: hidden;
    }
    
    .sudoku-container {
        background: rgba(var(--color-accent-desat), 0.05);
        backdrop-filter: blur(10px);
        border-radius: clamp(12px, 3vmin, 24px);
        padding: clamp(12px, 3vmin, 24px);
        box-shadow: 0 25px 50px -12px rgba(var(--color-shadow), 0.5);
        width: min(100%, calc(var(--grid-size) + 48px));
        display: flex;
        flex-direction: column;
        overflow: hidden;
        transition: width 0.2s ease, transform 0.2s ease;
        transform-origin: center center;
    }
    
    /* Expand container when hints sidebar is shown in landscape */
    .sudoku-container.hints-expanded {
        width: min(100%, calc(var(--grid-size) + 380px));
    }
    
    .header {
        text-align: center;
        margin-bottom: 4px;
        flex-shrink: 0;
        position: relative;
    }
    
    /* Position back button to the left */
    .header .back-btn {
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
    }
    
    .nav-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: clamp(6px, 1.5vmin, 12px);
    }
    
    .nav-btn, .back-btn {
        padding: clamp(6px, 1.5vmin, 12px) clamp(8px, 2vmin, 16px);
        border: none;
        border-radius: clamp(4px, 1vmin, 8px);
        font-size: clamp(0.65rem, calc(0.6rem + 0.5vmin), 0.9rem);
        font-weight: 600;
        cursor: pointer;
        background: rgba(var(--color-accent-desat), var(--color-btn-opacity));
        color: rgba(var(--color-text-primary), 0.8);
        transition: all 0.15s ease;
    }

    .nav-btn:hover, .back-btn:hover {
        background: rgba(var(--color-accent-desat), var(--color-btn-hover-opacity));
    }
    
    .header h1 {
        font-size: clamp(1rem, calc(1rem + 1.8vmin), 1.8rem);
        font-weight: 700;
        color: rgb(var(--color-accent-primary));
        letter-spacing: -0.02em;
    }

    .powered-by {
        color: rgb(var(--color-text-primary));
        display: block;
        font-size: small;
    }
    
    .game-info {
        display: flex;
        justify-content: space-between;
        flex-wrap: wrap;
        margin-top: clamp(4px, 1vmin, 8px);
        padding-left: 6px;
        padding-right: 6px;
    }
    
    .game-info span {
        align-content: center;
        font-size: clamp(0.6rem, calc(0.55rem + 0.5vmin), 0.8rem);
        padding-left: 6px;
        padding-right: 6px;
        padding-top: 2px;
        padding-bottom: 2px;
        border-radius: 4px;
        background: rgba(var(--color-bg-tertiary), 0.2);
        color: rgba(var(--color-text-primary), 0.7);
    }
    
    .category {
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.05em;
    }
    
    .category.beginner { background:  rgba(30, 30, 108, 0.8); color: rgb(142, 141, 201); }
    .category.easy { background:  rgba(14, 64, 77, 0.8); color: rgb(141, 186, 203); }
    .category.medium { background: rgba(16, 82, 16, 0.8); color: rgb(141, 200, 141); }
    .category.tough { background: rgba(98, 81, 19, 0.8); color: rgb(190, 180, 144); }
    .category.hard { background: rgba(80, 58, 16, 0.8); color: rgb(192, 173, 139); }
    .category.expert { background: rgba(80, 17, 17, 0.8); color: rgb(250, 182, 182); }
    .category.diabolical {background: rgba(37, 33, 33, 0.8); color: rgb(255, 88, 88); }
    
    /* Timer container and pause button */
    .timer-container {
        display: flex !important;
        align-items: center;
        gap: 4px;
    }
    
    .pause-btn {
        padding: 2px 6px;
        background: rgba(var(--color-accent), 0.2);
        border: 1px solid rgba(var(--color-accent), 0.3);
        border-radius: 4px;
        color: rgba(var(--color-text-primary), 0.8);
        cursor: pointer;
        font-size: clamp(0.7rem, calc(0.6rem + 0.5vmin), 0.9rem);
        transition: all 0.2s ease;
        line-height: 1;
    }
    
    .pause-btn:hover {
        background: rgba(var(--color-accent), 0.3);
        border-color: rgba(var(--color-accent), 0.5);
        transform: scale(1.05);
    }
    
    .pause-btn:active {
        transform: scale(0.95);
    }
    
    /* Info button in puzzle list */
    .info-btn {
        padding: 4px 8px;
        background: transparent;
        border: 1px solid rgba(var(--color-border), 0.3);
        border-radius: 4px;
        color: rgba(var(--color-text-primary), 0.7);
        cursor: pointer;
        font-size: 0.9em;
        transition: all 0.2s ease;
    }
    
    .info-btn:hover {
        background: rgba(var(--color-accent-info), 0.2);
        border-color: rgb(var(--color-accent-info));
        color: rgb(var(--color-accent-info));
    }
    
    /* Puzzle title in list */
    .puzzle-title, .puzzle-title-link {
        font-weight: 500;
        color: rgba(var(--color-text-primary), 0.9);
        flex: 1;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
    
    .puzzle-title-link {
        color: rgb(var(--color-accent-info));
        text-decoration: none;
    }
    
    .puzzle-title-link:hover {
        text-decoration: underline;
    }
    
    /* Puzzle info modal */
    .puzzle-info-modal {
        max-width: 450px;
    }
    
    .puzzle-info-modal h2 {
        text-align: center;
        margin-bottom: clamp(16px, 3vmin, 24px);
        color: rgb(var(--color-text-primary));
    }
    
    .puzzle-info-modal h3 {
        margin: clamp(12px, 2vmin, 16px) 0 clamp(8px, 1.5vmin, 12px);
        color: rgba(var(--color-text-primary), 0.8);
        font-size: 0.95em;
    }
    
    .info-grid {
        display: flex;
        flex-direction: column;
        gap: clamp(8px, 1.5vmin, 12px);
    }
    
    .info-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: clamp(8px, 1.5vmin, 12px);
        background: rgba(var(--color-bg-tertiary), 0.3);
        border-radius: 6px;
    }
    
    .info-label {
        color: rgba(var(--color-text-primary), 0.6);
        font-size: 0.9em;
    }
    
    .info-value {
        color: rgb(var(--color-text-primary));
        font-weight: 500;
    }
    
    .info-value.link {
        color: rgb(var(--color-accent-info));
        text-decoration: none;
    }
    
    .info-value.link:hover {
        text-decoration: underline;
    }
    
    .info-section {
        margin-top: clamp(8px, 1.5vmin, 12px);
    }
    
    .techniques-list {
        display: flex;
        flex-direction: column;
        gap: 4px;
        background: rgba(var(--color-bg-tertiary), 0.2);
        border-radius: 6px;
        padding: clamp(8px, 1.5vmin, 12px);
    }
    
    .technique-row {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 4px 0;
    }
    
    .technique-name {
        color: rgba(var(--color-text-primary), 0.8);
        font-size: 0.9em;
    }
    
    .technique-count {
        color: rgb(var(--color-accent-info));
        font-weight: 600;
        font-size: 0.85em;
    }
    
    .modal-actions {
        display: flex;
        gap: clamp(8px, 1.5vmin, 12px);
        justify-content: center;
        margin-top: clamp(16px, 3vmin, 24px);
        padding-top: clamp(12px, 2vmin, 16px);
        border-top: 1px solid rgba(var(--color-border), 0.2);
    }
    
    .modal-actions button {
        padding: clamp(10px, 2vmin, 14px) clamp(20px, 4vmin, 32px);
        border-radius: clamp(6px, 1.5vmin, 10px);
        font-size: clamp(0.9rem, calc(0.8rem + 0.4vmin), 1.05rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.2s ease;
    }
    
    .modal-actions .close-btn {
        background: rgba(var(--color-bg-tertiary), 0.5);
        border: 1px solid rgba(var(--color-border), 0.3);
        color: rgb(var(--color-text-primary));
    }
    
    .modal-actions .close-btn:hover {
        background: rgba(var(--color-bg-tertiary), 0.8);
    }
    
    .modal-actions .play-btn {
        background: rgba(var(--color-accent-success), 0.4);
        border: none;
        color: rgb(var(--color-text-primary));
    }
    
    .modal-actions .play-btn:hover {
        background: rgb(var(--color-accent-success));
        transform: translateY(-2px);
    }
    
    .game-area {
        display: flex;
        flex-direction: column;
        gap: clamp(8px, 2vmin, 16px);
        flex: 1;
        min-height: 0;
    }
    
    /* Grid container for SVG overlay positioning */
    .sudoku-grid-container {
        position: relative;
        width: var(--grid-size);
        max-width: 100%;
        margin: 0 auto;
    }
    
    /* Blur effect when paused */
    .sudoku-grid-container.paused .sudoku-grid {
        filter: blur(8px);
        pointer-events: none;
        user-select: none;
    }
    
    /* Pause overlay */
    .pause-overlay {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.3);
        backdrop-filter: blur(2px);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 100;
        cursor: pointer;
        animation: fadeIn 0.3s ease;
    }
    
    @keyframes fadeIn {
        from {
            opacity: 0;
        }
        to {
            opacity: 1;
        }
    }
    
    .pause-message {
        text-align: center;
        color: rgba(var(--color-text-primary), 0.95);
        user-select: none;
    }
    
    .pause-icon {
        font-size: clamp(3rem, 8vmin, 5rem);
        margin-bottom: 1rem;
        opacity: 0.9;
    }
    
    .pause-text {
        font-size: clamp(1.5rem, 4vmin, 2.5rem);
        font-weight: 700;
        letter-spacing: 0.2em;
        margin-bottom: 0.5rem;
    }
    
    .pause-subtext {
        font-size: clamp(0.9rem, 2vmin, 1.2rem);
        opacity: 0.7;
        animation: pulse 2s ease-in-out infinite;
    }
    
    @keyframes pulse {
        0%, 100% {
            opacity: 0.5;
        }
        50% {
            opacity: 0.9;
        }
    }
    
    /* SVG overlay container for chain lines */
    .chain-lines-container {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        pointer-events: none;
        z-index: 10;
    }
    
    /* SVG overlay for chain lines */
    .chain-lines-overlay {
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        pointer-events: none;
        z-index: 10;
    }
    
    .board-chain-line {
        fill: none;
        stroke-width: 10;
        stroke-linecap: round;
        filter: drop-shadow(0 0 2px rgba(0, 0, 0, 0.5));
    }
    
    .board-chain-line.strong-link {
        stroke: rgb(var(--color-accent-success));
        stroke-width: 10;
    }
    
    .board-chain-line.weak-link {
        stroke: rgb(var(--color-accent-warning));
        stroke-dasharray: 5 15;
    }
    
    .board-candidate-highlight {
        opacity: 0.5;
        filter: drop-shadow(0 0 3px rgba(0, 0, 0, 0.3));
    }
    
    .board-candidate-highlight.group-on {
        fill: rgba(193, 155, 249, 0.8);
    }
    
    .board-candidate-highlight.group-off {
        fill: rgba(123, 249, 155, 0.8);
    }
    
    .board-candidate-highlight.group-als {
        fill: rgba(249, 200, 123, 0.8);
    }
    
    .board-candidate-highlight.group-default {
        fill: rgba(var(--color-accent-info), 0.6);
    }
    
    /* SVG highlight states for interactive chain notation */
    .board-chain-line.svg-line-highlight {
        stroke-width: 10 !important;
        filter: drop-shadow(0 0 8px rgba(255, 255, 255, 0.9)) !important;
    }
    
    .board-candidate-highlight.svg-highlight {
        r: 18;
        opacity: 1;
        filter: drop-shadow(0 0 8px rgba(255, 255, 255, 0.9)) !important;
    }
    
    /* Interactive chain notation styles */
    .interactive-description {
        line-height: 1.6;
    }
    
    .chain-cell-ref {
        background: rgba(var(--color-accent-info), 0.2);
        padding: 2px 4px;
        border-radius: 4px;
        cursor: pointer;
        transition: all 0.15s ease;
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: 0.9em;
    }
    
    .chain-cell-ref:hover {
        background: rgba(var(--color-accent-info), 0.5);
        box-shadow: 0 0 8px rgba(var(--color-accent-info), 0.5);
    }
    
    .chain-link-ref {
        padding: 2px 6px;
        border-radius: 4px;
        cursor: pointer;
        transition: all 0.15s ease;
        font-weight: 600;
        font-size: 0.85em;
    }
    
    .chain-link-strong {
        background: rgba(var(--color-accent-success-text), 0.2);
        color: rgb(var(--color-accent-success));
    }
    
    .chain-link-strong:hover {
        background: rgba(var(--color-accent-success-text), 0.5);
        box-shadow: 0 0 8px rgba(var(--color-accent-success), 0.5);
    }
    
    .chain-link-weak {
        background: rgba(var(--color-accent-warning-text), 0.2);
        color: rgb(var(--color-accent-warning));
    }
    
    .chain-link-weak:hover {
        background: rgba(var(--color-accent-warning-text), 0.5);
        box-shadow: 0 0 8px rgba(var(--color-accent-warning), 0.5);
    }
    
    .chain-text, .desc-text {
        /* Normal text styling */
    }
    
    /* Interactive reference base styles */
    .interactive-ref {
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    /* Simple cell reference: R5C6 */
    .cell-ref {
        background: rgba(var(--color-accent-info), 0.2);
        padding: 1px 4px;
        border-radius: 4px;
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: 0.9em;
    }
    
    .cell-ref:hover, .cell-ref.ref-hovered {
        background: rgba(var(--color-accent-info), 0.5);
        box-shadow: 0 0 6px rgba(var(--color-accent-info), 0.4);
    }
    
    /* Digit reference: {5, 7, 8} */
    .digit-ref {
        background: rgba(var(--color-accent-secondary), 0.2);
        padding: 1px 4px;
        border-radius: 4px;
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: 0.9em;
        color: rgb(var(--color-accent-secondary));
    }
    
    .digit-ref:hover, .digit-ref.ref-hovered {
        background: rgba(var(--color-accent-secondary), 0.5);
        box-shadow: 0 0 6px rgba(var(--color-accent-secondary), 0.4);
        color: white;
    }
    
    /* House reference: Row 5, Column 7, Box 3 */
    .house-ref {
        background: rgba(var(--color-accent-warning), 0.15);
        padding: 1px 4px;
        border-radius: 4px;
        font-weight: 500;
    }
    
    .house-ref:hover, .house-ref.ref-hovered {
        background: rgba(var(--color-accent-warning), 0.4);
        box-shadow: 0 0 6px rgba(var(--color-accent-warning), 0.4);
    }
    
    /* Hover highlights on grid */
    .cell.hover-highlight-cell {
        box-shadow: inset 0 0 0 2px rgba(var(--color-accent-info), 0.8) !important;
        background: rgba(var(--color-accent-info), 0.15) !important;
    }
    
    .cell.hover-highlight-house {
        background: rgba(var(--color-accent-warning), 0.2) !important;
    }
    
    .cell.hover-highlight-digit-cell {
        box-shadow: inset 0 0 0 2px rgba(var(--color-accent-secondary), 0.8) !important;
    }
    
    .candidate.hover-highlight-digit {
        background: rgba(var(--color-accent-secondary), 0.6) !important;
        color: white !important;
        border-radius: 50%;
        font-weight: bold;
    }
    
    /* Cell highlight from chain interaction */
    .cell.chain-node-highlight {
        box-shadow: inset 0 0 0 3px rgba(var(--color-accent-info), 0.8), 0 0 12px rgba(var(--color-accent-info), 0.6) !important;
    }
    
    .candidate.chain-candidate-highlight {
        background: rgba(var(--color-accent-info), 0.7) !important;
        color: white !important;
        border-radius: 50%;
        font-weight: bold;
    }
    
    .sudoku-grid {
        background: rgba(var(--color-bg-primary), 0.3);
        border-radius: clamp(6px, 1.5vmin, 12px);
        padding: clamp(3px, 0.75vmin, 6px);
        display: flex;
        flex-direction: column;
        gap: clamp(1px, 0.25vmin, 2px);
        width: var(--grid-size);
        max-width: 100%;
        margin: 0 auto;
        aspect-ratio: 1;
        position: relative;
        z-index: 20;
    }
    
    .sudoku-row {
        display: flex;
        gap: clamp(1px, 0.25vmin, 2px);
        flex: 1;
    }
    
    .cell {
        aspect-ratio: 1;
        flex: 1;
        background: rgba(var(--color-bg-tertiary), 0.15);
        border-radius: clamp(2px, 0.5vmin, 4px);
        display: flex;
        justify-content: center;
        align-items: center;
        cursor: pointer;
        transition: all 0.15s ease;
        position: relative;
    }

    .cell:hover { background: rgba(var(--color-bg-tertiary), 0.3); }
    .cell.selected { background: rgba(var(--color-text-primary), 0.1); box-shadow: inset 0 0 0 2px rgba(var(--color-text-primary), 0.7); }
    .cell.given { background: rgba(var(--color-bg-tertiary), 0.25); }
    .cell.solved { background: rgba(var(--color-bg-tertiary), 0.15); }
    .cell.mistake { background: rgba(var(--color-accent-error), 0.5); }
    .cell.box-left { margin-left: clamp(2px, 0.5vmin, 4px); }
    .cell.box-top { margin-top: clamp(2px, 0.5vmin, 4px); }
    
    .cell-value {
        font-size: clamp(0.9rem, calc(var(--grid-size) / 18), 2rem);
        font-weight: 600;
        color: rgb(var(--color-text-primary));
    }

    .cell.given .cell-value { color: rgba(var(--color-text-primary), 0.95); }
    .cell.solved .cell-value { color: rgba(var(--color-text-primary), 0.8); }
    .cell:not(.given):not(.solved) .cell-value { color: rgb(var(--color-accent-secondary)); }
    .cell.mistake .cell-value { color: rgb(var(--color-accent-error)); }
    
    .candidates {
        display: grid;
        grid-template-columns: repeat(3, 1fr);
        width: 100%;
        height: 100%;
        padding: clamp(1px, 0.25vmin, 2px);
    }
    
    .candidate {
        font-size: clamp(0.85rem, calc(var(--grid-size) / 60), 1.2rem);
        color: rgba(var(--color-text-primary), 0.85);
        display: flex;
        justify-content: center;
        align-items: center;
        position: relative;
        z-index: 20;
    }
    
    .candidate.hidden { visibility: hidden; }
    
    /* Hint highlighting for cells */
    .cell.hint-cover-area {
        background: rgba(var(--color-accent-info), 0.2);
    }
    
    .cell.hint-solved-cell {
        background: rgba(var(--color-accent-success), 0.3);
    }
    
    .cell.hint-step-highlight {
        background: rgba(var(--color-accent-warning), 0.3);
        box-shadow: inset 0 0 0 2px rgba(var(--color-accent-warning), 0.6);
    }
    
    /* Hint highlighting for candidates */
    .candidate.hint-elimination {
        color: rgb(var(--color-accent-error));
        font-weight: bold;
        text-decoration: line-through;
    }
    
    .candidate.hint-matching-not-eliminated {
        color: rgb(var(--color-accent-info));
        font-weight: bold;
    }
    
    .candidate.hint-solved {
        color: rgb(var(--color-accent-success-text));
        font-weight: bold;
        background: rgba(var(--color-accent-success), 0.3);
        border-radius: 2px;
    }
    
    /* New explanation step highlighting */
    .cell.hint-region-highlight {
        background: rgba(var(--color-accent-info), 0.15);
    }
    
    .cell.hint-cell-warning {
        background: rgba(var(--color-accent-warning), 0.35);
        box-shadow: inset 0 0 0 2px rgba(var(--color-accent-warning), 0.7);
    }
    
    .cell.hint-cell-target {
        background: rgba(var(--color-accent-success), 0.35);
        box-shadow: inset 0 0 0 2px rgba(var(--color-accent-success), 0.7);
    }
    
    .cell.hint-cell-primary {
        background: rgba(var(--color-accent-info), 0.35);
        box-shadow: inset 0 0 0 2px rgba(var(--color-accent-info), 0.7);
    }
    
    .candidate.hint-candidate-target {
        color: rgb(var(--color-accent-success-text));
        font-weight: bold;
        background: rgba(var(--color-accent-success), 1.0);
        border-radius: 2px;
    }
    
    .candidate.hint-candidate-elimination {
        color: rgb(var(--color-accent-error-text));
        font-weight: bold;
        text-decoration: line-through;
        background: rgba(var(--color-accent-error), 1.0);
        border-radius: 2px;
    }
    
    .candidate.hint-candidate-highlight {
        color: rgb(var(--color-accent-warning-text));
        font-weight: bold;
        background: rgba(var(--color-accent-warning), 1.0);
        border-radius: 2px;
    }
    
    .candidate.hint-candidate-info {
        color: rgb(var(--color-accent-info-text));
        font-weight: bold;
        background: rgba(var(--color-accent-info), 1.0);
        border-radius: 2px;
    }
    
    .controls {
        display: flex;
        gap: clamp(4px, 1vmin, 8px);
        justify-content: center;
        flex-wrap: wrap;
        flex-shrink: 0;
    }
    
    .toggle-btn, .undo-btn, .hint-btn, .solve-btn {
        padding: clamp(6px, 1.5vmin, 12px) clamp(10px, 2.5vmin, 20px);
        border: none;
        border-radius: clamp(6px, 1.5vmin, 12px);
        font-size: clamp(0.65rem, calc(0.6rem + 0.5vmin), 0.9rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
        background: rgba(var(--color-accent-desat), 0.2);
        color: rgba(var(--color-text-primary), 0.8);
    }

    .toggle-btn:hover, .undo-btn:hover, .hint-btn:hover, .solve-btn:hover {
        background: rgba(var(--color-accent-desat), 0.3);
        transform: translateY(-1px);
    }

    .toggle-btn.active { background: rgb(var(--color-accent-secondary)); color: rgb(var(--color-text-primary)); }
    .undo-btn { background: rgba(var(--color-accent-desat), 0.4); color: rgba(var(--color-text-primary), 0.8); }
    .undo-btn.disabled { opacity: 0.4; cursor: not-allowed; pointer-events: none; }
    .hint-btn { background: rgba(var(--color-accent-warning), 0.4); color: rgb(var(--color-accent-warning-text)); }
    .hint-btn.active { background: rgb(var(--color-accent-warning)); color: rgb(var(--color-bg-primary)); }
    .hint-btn.disabled {
        opacity: 0.4;
        cursor: not-allowed;
        pointer-events: none;
    }
    
    /* Hint System Styles */
    .main-content {
        display: flex;
        flex-direction: column;
        flex: 1;
        min-height: 0;
    }
    
    .main-content.landscape-hints {
        flex-direction: row;
        gap: clamp(10px, 2vmin, 20px);
        align-items: flex-start;
        min-height: 0;
        overflow: hidden;
    }
    
    .main-content.landscape-hints .game-area {
        flex: 0 0 auto;
        /* Don't shrink the game area - let the container expand instead */
    }
    
    /* Landscape Hint Sidebar */
    .hint-sidebar {
        flex: 1;
        min-width: 200px;
        max-width: 320px;
        align-self: stretch;
        background: rgba(var(--color-bg-tertiary), 0.1);
        border-radius: clamp(8px, 2vmin, 16px);
        padding: clamp(10px, 2vmin, 20px);
        display: flex;
        flex-direction: column;
        overflow: hidden;
    }
    
    .hint-sidebar-header {
        display: flex;
        align-items: center;
        gap: 8px;
        margin-bottom: clamp(8px, 1.5vmin, 16px);
        flex-shrink: 0;
    }
    
    .hint-sidebar-header h3 {
        margin: 0;
        font-size: clamp(0.9rem, calc(0.8rem + 0.5vmin), 1.1rem);
        color: rgb(var(--color-accent-warning));
    }
    
    .hint-count {
        color: rgba(var(--color-text-primary), 0.5);
        font-size: clamp(0.7rem, calc(0.65rem + 0.3vmin), 0.9rem);
    }
    
    .hint-list {
        flex: 1;
        overflow-y: auto;
        display: flex;
        flex-direction: column;
        gap: clamp(6px, 1vmin, 12px);
    }
    
    .hint-item {
        background: rgba(var(--color-bg-tertiary), 0.15);
        border-radius: clamp(6px, 1vmin, 10px);
        padding: 0;
        cursor: pointer;
        transition: all 0.15s ease;
        border: 2px solid transparent;
    }

    .hint-item:hover {
        background: rgba(var(--color-bg-tertiary), 0.25);
    }

    .hint-item.selected {
        background: rgba(var(--color-accent-primary), 0.3);
        border-color: rgb(var(--color-accent-primary));
        overflow: visible;
    }
    
    .hint-item.expanded {
        background: rgba(var(--color-accent-primary), 0.3);
        border-color: rgb(var(--color-accent-primary));
        overflow: visible;
    }
    
    .hint-item-header {
        padding: clamp(8px, 1.5vmin, 14px);
        display: flex;
        flex-direction: column;
        gap: clamp(4px, 0.8vmin, 8px);
    }
    
    .hint-item-content {
        flex: 1;
    }

    .hint-technique {
        font-weight: 600;
        color: rgb(var(--color-text-primary));
        font-size: clamp(0.75rem, calc(0.7rem + 0.4vmin), 0.95rem);
        margin-bottom: 4px;
    }

    .hint-description {
        color: rgba(var(--color-text-primary), 0.7);
        font-size: clamp(0.65rem, calc(0.6rem + 0.3vmin), 0.85rem);
        line-height: 1.3;
        word-break: break-word;
    }
    
    .hint-item-expanded {
        padding: clamp(8px, 1.5vmin, 14px);
        padding-top: 0;
        border-top: 1px solid rgba(var(--color-text-primary), 0.1);
        margin-top: clamp(4px, 0.8vmin, 8px);
        display: flex;
        flex-direction: column;
        gap: clamp(8px, 1.2vmin, 12px);
    }
    
    /* Inline Explanation Styles */
    .inline-explanation {
        padding: clamp(8px, 1.5vmin, 14px);
        padding-top: 0;
        border-top: 1px solid rgba(var(--color-text-primary), 0.1);
        margin-top: clamp(4px, 0.8vmin, 8px);
    }
    
    .explanation-collapse-row {
        display: flex;
        justify-content: flex-end;
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    .explanation-collapse-btn {
        padding: clamp(4px, 0.6vmin, 6px) clamp(8px, 1.2vmin, 12px);
        border: none;
        border-radius: clamp(4px, 0.6vmin, 6px);
        background: rgba(var(--color-accent-warning), 0.2);
        color: rgb(var(--color-accent-warning-text));
        font-size: clamp(0.6rem, calc(0.55rem + 0.3vmin), 0.75rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .explanation-collapse-btn:hover {
        background: rgba(var(--color-accent-warning), 0.3);
    }
    
    .inline-eureka {
        background: rgba(var(--color-bg-tertiary), 0.15);
        border-radius: clamp(4px, 0.6vmin, 6px);
        padding: clamp(6px, 1vmin, 10px);
        margin-bottom: clamp(8px, 1.2vmin, 12px);
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: clamp(0.6rem, calc(0.55rem + 0.3vmin), 0.75rem);
        overflow-x: auto;
    }
    
    .eureka-label {
        color: rgba(var(--color-text-primary), 1.0);
    }
    
    .eureka-notation {
        color: rgb(var(--color-accent-warning));
    }
    
    .inline-step {
        background: rgba(var(--color-bg-tertiary), 0.2);
        border-radius: clamp(6px, 1vmin, 10px);
        padding: clamp(8px, 1.2vmin, 12px);
        margin-bottom: clamp(8px, 1.2vmin, 12px);
    }
    
    .step-header {
        display: flex;
        align-items: center;
        gap: clamp(6px, 1vmin, 10px);
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    .step-number {
        background: rgba(var(--color-accent-info), 0.3);
        color: rgb(var(--color-accent-info));
        padding: clamp(2px, 0.4vmin, 4px) clamp(6px, 0.8vmin, 10px);
        border-radius: clamp(3px, 0.5vmin, 5px);
        font-size: clamp(0.55rem, calc(0.5rem + 0.25vmin), 0.7rem);
        font-weight: 600;
    }
    
    .step-title {
        font-size: clamp(0.7rem, calc(0.65rem + 0.35vmin), 0.9rem);
        font-weight: 600;
        color: rgb(var(--color-text-primary));
    }
    
    .step-description {
        color: rgba(var(--color-text-primary), 0.8);
        font-size: clamp(0.6rem, calc(0.55rem + 0.3vmin), 0.8rem);
        line-height: 1.4;
    }
    
    .inline-nav {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: clamp(6px, 1vmin, 10px);
    }
    
    .inline-nav-btn {
        padding: clamp(3px, 0.6vmin, 6px) clamp(6px, 1vmin, 10px);
        border: none;
        border-radius: clamp(4px, 0.6vmin, 6px);
        background: rgba(var(--color-accent-info), 0.2);
        color: rgb(var(--color-accent-info));
        font-size: clamp(0.55rem, calc(0.5rem + 0.3vmin), 0.7rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .inline-nav-btn:hover:not(.disabled) {
        background: rgba(var(--color-accent-info), 0.3);
    }
    
    .inline-nav-btn.disabled {
        opacity: 0.4;
        cursor: not-allowed;
    }
    
    .step-indicator {
        color: rgba(var(--color-text-primary), 0.6);
        font-size: clamp(0.55rem, calc(0.5rem + 0.25vmin), 0.7rem);
    }
    
    /* Full Explanation View (replaces list in landscape sidebar) */
    .explanation-view {
        display: flex;
        flex-direction: column;
        height: 100%;
        gap: clamp(8px, 1.5vmin, 14px);
    }
    
    .explanation-view-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: clamp(8px, 1.2vmin, 12px);
        flex-shrink: 0;
    }
    
    .explanation-back-btn {
        padding: clamp(6px, 1vmin, 10px) clamp(10px, 1.5vmin, 14px);
        border: none;
        border-radius: clamp(4px, 0.8vmin, 8px);
        background: rgba(var(--color-text-primary), 0.1);
        color: rgb(var(--color-text-primary));
        font-size: clamp(0.65rem, calc(0.6rem + 0.35vmin), 0.85rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .explanation-back-btn:hover {
        background: rgba(var(--color-text-primary), 0.2);
    }
    
    .hint-position-badge {
        background: rgba(var(--color-accent-warning), 0.2);
        color: rgb(var(--color-accent-warning));
        padding: clamp(4px, 0.6vmin, 6px) clamp(8px, 1vmin, 12px);
        border-radius: clamp(4px, 0.6vmin, 6px);
        font-size: clamp(0.6rem, calc(0.55rem + 0.3vmin), 0.75rem);
        font-weight: 600;
    }
    
    .explanation-technique-info {
        background: rgba(var(--color-accent-warning), 0.15);
        border-radius: clamp(6px, 1vmin, 10px);
        padding: clamp(10px, 1.5vmin, 16px);
        border-left: 3px solid rgb(var(--color-accent-warning));
        flex-shrink: 0;
    }
    
    .explanation-technique-name {
        font-size: clamp(0.8rem, calc(0.75rem + 0.4vmin), 1rem);
        font-weight: 700;
        color: rgb(var(--color-accent-warning));
        margin-bottom: clamp(4px, 0.6vmin, 8px);
    }
    
    .explanation-technique-desc {
        font-size: clamp(0.65rem, calc(0.6rem + 0.3vmin), 0.85rem);
        color: rgba(var(--color-text-primary), 0.8);
        line-height: 1.4;
    }
    
    .explanation-eureka {
        background: rgba(var(--color-bg-tertiary), 0.3);
        border-radius: clamp(4px, 0.6vmin, 6px);
        padding: clamp(8px, 1.2vmin, 12px);
        font-family: 'JetBrains Mono', 'Fira Code', monospace;
        font-size: clamp(0.55rem, calc(0.5rem + 0.25vmin), 0.7rem);
        overflow-x: auto;
        flex-shrink: 0;
    }
    
    .explanation-step-content {
        flex: 1;
        background: rgba(var(--color-bg-tertiary), 0.2);
        border-radius: clamp(6px, 1vmin, 10px);
        padding: clamp(10px, 1.5vmin, 16px);
        overflow-y: auto;
    }
    
    .explanation-step-content .step-header {
        display: flex;
        align-items: center;
        gap: clamp(8px, 1.2vmin, 12px);
        margin-bottom: clamp(8px, 1.2vmin, 12px);
    }
    
    .explanation-step-content .step-description {
        color: rgba(var(--color-text-primary), 0.85);
        font-size: clamp(0.65rem, calc(0.6rem + 0.3vmin), 0.85rem);
        line-height: 1.5;
    }
    
    .explanation-nav {
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: clamp(8px, 1.2vmin, 12px);
        flex-shrink: 0;
    }
    
    .explanation-nav-btn {
        padding: clamp(6px, 1vmin, 10px) clamp(12px, 1.8vmin, 18px);
        border: none;
        border-radius: clamp(4px, 0.8vmin, 8px);
        background: rgba(var(--color-accent-info), 0.2);
        color: rgb(var(--color-accent-info));
        font-size: clamp(0.6rem, calc(0.55rem + 0.3vmin), 0.8rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .explanation-nav-btn:hover:not(.disabled) {
        background: rgba(var(--color-accent-info), 0.3);
    }
    
    .explanation-nav-btn.disabled {
        opacity: 0.4;
        cursor: not-allowed;
    }
    
    .hint-empty {
        text-align: center;
        color: rgba(var(--color-text-primary), 0.5);
        padding: clamp(16px, 3vmin, 32px);
    }

    .hint-close-btn {
        margin-top: clamp(8px, 1.5vmin, 16px);
        padding: clamp(8px, 1.5vmin, 14px);
        border: none;
        border-radius: clamp(6px, 1vmin, 10px);
        background: rgba(var(--color-accent-error), 0.3);
        color: rgb(var(--color-accent-error));
        font-size: clamp(0.7rem, calc(0.65rem + 0.4vmin), 0.9rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
        flex-shrink: 0;
    }

    .hint-close-btn:hover {
        background: rgba(var(--color-accent-error), 0.4);
    }
    
    /* Portrait Hint Card */
    .hint-card {
        background: rgba(var(--color-bg-tertiary), 0.1);
        border-radius: clamp(8px, 2vmin, 16px);
        padding: clamp(10px, 2vmin, 20px);
    }
    
    .hint-card-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: clamp(6px, 1vmin, 12px);
        margin-bottom: clamp(8px, 1.5vmin, 16px);
    }
    
    .hint-nav-btn {
        padding: clamp(6px, 1vmin, 10px) clamp(10px, 2vmin, 16px);
        border: none;
        border-radius: clamp(4px, 0.8vmin, 8px);
        background: rgba(var(--color-accent-warning), 0.2);
        color: rgb(var(--color-accent-warning));
        font-size: clamp(0.65rem, calc(0.6rem + 0.35vmin), 0.85rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .hint-nav-btn:hover:not(.disabled) {
        background: rgba(var(--color-accent-warning), 0.3);
    }
    
    .hint-nav-btn.disabled {
        opacity: 0.4;
        cursor: not-allowed;
    }
    
    .hint-position {
        color: rgba(var(--color-text-primary), 0.6);
        font-size: clamp(0.7rem, calc(0.65rem + 0.3vmin), 0.9rem);
    }
    
    .hint-close-btn-small {
        padding: clamp(3px, 0.6vmin, 6px) clamp(6px, 1vmin, 10px);
        border: none;
        border-radius: clamp(4px, 0.8vmin, 8px);
        background: rgba(var(--color-accent-error), 0.2);
        color: rgb(var(--color-accent-error));
        font-size: clamp(0.55rem, calc(0.5rem + 0.3vmin), 0.7rem);
        font-weight: bold;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .hint-close-btn-small:hover {
        background: rgba(var(--color-accent-error), 0.3);
    }
    
    .hint-content {
        padding: clamp(8px, 1.5vmin, 16px);
        background: rgba(var(--color-bg-tertiary), 0.05);
        border-radius: clamp(6px, 1vmin, 10px);
    }
    
    .hint-content .hint-technique {
        margin-bottom: clamp(6px, 1vmin, 12px);
    }
    
    .hint-content.hint-empty {
        text-align: center;
        color: rgba(var(--color-text-primary), 0.5);
    }
    
    /* Hint item with explain button */
    .hint-item-content {
        flex: 1;
    }
    
    .hint-explain-btn {
        margin-top: clamp(6px, 1vmin, 10px);
        padding: clamp(4px, 0.8vmin, 8px) clamp(8px, 1.5vmin, 14px);
        border: none;
        border-radius: clamp(4px, 0.8vmin, 8px);
        background: rgba(var(--color-accent-info), 0.3);
        color: rgb(var(--color-text-primary));
        font-size: clamp(0.65rem, calc(0.6rem + 0.35vmin), 0.8rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
        display: block;
        width: 100%;
    }
    
    .hint-explain-btn:hover {
        background: rgba(var(--color-accent-info), 0.4);
        transform: translateY(-1px);
    }
    
    /* Compact hint card header */
    .hint-card-header-compact {
        display: flex;
        align-items: center;
        gap: clamp(4px, 0.8vmin, 8px);
        padding-bottom: clamp(6px, 1vmin, 10px);
        border-bottom: 1px solid rgba(var(--color-text-primary), 0.1);
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    .hint-title-area {
        flex: 1;
        display: flex;
        align-items: center;
        gap: clamp(4px, 0.8vmin, 8px);
        min-width: 0;
        overflow: hidden;
    }
    
    .hint-technique-compact {
        color: rgb(var(--color-accent-warning));
        font-weight: 600;
        font-size: clamp(0.7rem, calc(0.65rem + 0.4vmin), 0.9rem);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }
    
    .hint-position-small {
        color: rgba(var(--color-text-primary), 0.5);
        font-size: clamp(0.6rem, calc(0.55rem + 0.3vmin), 0.75rem);
        white-space: nowrap;
    }
    
    .hint-nav-btn-small {
        padding: clamp(3px, 0.6vmin, 6px) clamp(6px, 1vmin, 10px);
        border: none;
        border-radius: clamp(3px, 0.6vmin, 6px);
        background: rgba(var(--color-accent-warning), 0.2);
        color: rgb(var(--color-accent-warning));
        font-size: clamp(0.55rem, calc(0.5rem + 0.3vmin), 0.7rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
        white-space: nowrap;
    }
    
    .hint-nav-btn-small:hover:not(.disabled) {
        background: rgba(var(--color-accent-warning), 0.3);
    }
    
    .hint-nav-btn-small.disabled {
        opacity: 0.4;
        cursor: not-allowed;
    }
    
    .hint-collapse-btn-small {
        padding: clamp(3px, 0.6vmin, 6px) clamp(6px, 1vmin, 10px);
        border: none;
        border-radius: clamp(3px, 0.6vmin, 6px);
        background: rgba(var(--color-accent-info), 0.2);
        color: rgb(var(--color-accent-info));
        font-size: clamp(0.6rem, calc(0.55rem + 0.3vmin), 0.75rem);
        font-weight: bold;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    .hint-collapse-btn-small:hover {
        background: rgba(var(--color-accent-info), 0.3);
    }
    
    .hint-content-compact {
        padding: clamp(4px, 0.8vmin, 8px);
    }
    
    .hint-content-compact .hint-description {
        font-size: clamp(0.65rem, calc(0.6rem + 0.35vmin), 0.85rem);
        color: rgba(var(--color-text-primary), 0.8);
        line-height: 1.4;
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    /* Compact inline explanation */
    .inline-explanation-compact {
        padding: clamp(4px, 0.8vmin, 8px);
    }
    
    .inline-explanation-compact .inline-eureka {
        font-size: clamp(0.55rem, calc(0.5rem + 0.25vmin), 0.7rem);
        margin-bottom: clamp(4px, 0.8vmin, 8px);
        padding: clamp(3px, 0.5vmin, 6px);
        background: rgba(var(--color-bg-tertiary), 0.1);
        border-radius: clamp(3px, 0.5vmin, 6px);
    }
    
    .step-header-compact {
        display: flex;
        align-items: center;
        gap: clamp(6px, 1vmin, 10px);
        margin-bottom: clamp(4px, 0.8vmin, 8px);
    }
    
    .step-nav-btn-small {
        padding: clamp(2px, 0.4vmin, 4px) clamp(4px, 0.8vmin, 8px);
        border: none;
        border-radius: clamp(3px, 0.5vmin, 6px);
        background: rgba(var(--color-accent-info), 0.2);
        color: rgb(var(--color-accent-info));
        font-size: clamp(0.55rem, calc(0.5rem + 0.25vmin), 0.7rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
        white-space: nowrap;
        flex-shrink: 0;
    }
    
    .step-nav-btn-small:hover:not(.disabled) {
        background: rgba(var(--color-accent-info), 0.3);
    }
    
    .step-nav-btn-small.disabled {
        opacity: 0.4;
        cursor: not-allowed;
    }
    
    .step-badge {
        background: rgba(var(--color-accent-info), 0.3);
        color: rgb(var(--color-accent-info));
        padding: clamp(2px, 0.4vmin, 4px) clamp(6px, 1vmin, 10px);
        border-radius: clamp(3px, 0.5vmin, 6px);
        font-size: clamp(0.55rem, calc(0.5rem + 0.25vmin), 0.7rem);
        font-weight: 600;
        white-space: nowrap;
    }
    
    .step-header-compact .step-title {
        font-weight: 600;
        font-size: clamp(0.65rem, calc(0.6rem + 0.35vmin), 0.85rem);
        color: rgba(var(--color-text-primary), 0.9);
        flex: 1;
        min-width: 0;
    }
    
    .inline-explanation-compact .step-description {
        font-size: clamp(0.65rem, calc(0.6rem + 0.35vmin), 0.85rem);
        line-height: 1.5;
        color: rgba(var(--color-text-primary), 0.8);
    }
    
    .inline-explanation-compact .inline-nav {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: clamp(8px, 1.5vmin, 14px);
        margin-top: clamp(8px, 1.5vmin, 14px);
        padding-top: clamp(6px, 1vmin, 10px);
        border-top: 1px solid rgba(var(--color-text-primary), 0.1);
    }
    
    .number-pad {
        display: grid;
        grid-template-columns: repeat(9, 1fr);
        gap: clamp(3px, 0.75vmin, 6px);
        flex-shrink: 0;
    }
    
    .num-btn {
        aspect-ratio: 1;
        border: none;
        border-radius: clamp(6px, 1.5vmin, 12px);
        font-size: clamp(0.9rem, calc(0.8rem + 1vmin), 1.5rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
        background: rgba(var(--color-accent-desat), var(--color-btn-opacity));
        color: rgb(var(--color-text-primary));
    }
    
    .num-btn:hover { background: rgba(var(--color-accent-desat), 0.25); transform: scale(1.05); }
    .num-btn:active { transform: scale(0.95); }
    
    .status {
        text-align: center;
        padding: clamp(6px, 1.5vmin, 12px);
        color: rgba(var(--color-text-primary), 0.6);
        font-size: clamp(0.7rem, calc(0.65rem + 0.4vmin), 0.9rem);
        flex-shrink: 0;
    }
    
    .toast {
        position: fixed;
        bottom: clamp(60px, 15vh, 120px);
        left: 50%;
        transform: translateX(-50%);
        background: rgba(var(--color-bg-primary), 0.9);
        color: rgb(var(--color-text-primary));
        padding: clamp(8px, 2vmin, 16px) clamp(16px, 4vmin, 32px);
        border-radius: clamp(4px, 1vmin, 8px);
        font-size: clamp(0.75rem, calc(0.7rem + 0.5vmin), 1rem);
        animation: fadeIn 0.2s ease;
        z-index: 100;
    }
    
    @keyframes fadeIn {
        from { opacity: 0; transform: translateX(-50%) translateY(10px); }
        to { opacity: 1; transform: translateX(-50%) translateY(0); }
    }
    
    /* Browser styles */
    .browser, .import-export, .settings {
        max-height: 100%;
        overflow-y: auto;
    }
    
    .browser .section, .import-export .section {
        background: rgba(var(--color-bg-tertiary), 0.1);
        border-radius: clamp(6px, 1.5vmin, 12px);
        padding: clamp(10px, 2.5vmin, 20px);
        margin-bottom: clamp(10px, 2.5vmin, 20px);
    }
    
    .section h2 {
        color: rgb(var(--color-text-primary));
        font-size: clamp(0.85rem, calc(0.8rem + 0.5vmin), 1.1rem);
        margin-bottom: clamp(8px, 2vmin, 16px);
    }
    
    .category-tabs {
        display: flex;
        gap: clamp(4px, 1vmin, 8px);
        margin-bottom: clamp(8px, 2vmin, 16px);
        flex-wrap: wrap;
    }
    
    .tab-btn {
        padding: clamp(6px, 1.5vmin, 12px) clamp(10px, 2.5vmin, 20px);
        border: none;
        border-radius: clamp(4px, 1vmin, 8px);
        font-size: clamp(0.65rem, calc(0.6rem + 0.5vmin), 0.9rem);
        font-weight: 600;
        cursor: pointer;
        background: rgba(var(--color-bg-tertiary), 0.2);
        color: rgba(var(--color-text-primary), 0.7);
        transition: all 0.15s ease;
    }

    .tab-btn:hover { background: rgba(var(--color-bg-tertiary), 0.4); }
    .tab-btn.active {
        background: rgba(var(--color-accent-primary), 0.4);
        color: rgb(var(--color-text-primary));
    }
    
    .puzzle-list, .game-list {
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-height: 300px;
        overflow-y: auto;
    }
    
    .puzzle-item, .game-item {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 10px 12px;
        background: rgba(var(--color-bg-tertiary), 0.5);
        border-radius: 8px;
        transition: all 0.15s ease;
    }

    .puzzle-item:hover, .game-item:hover {
        background: rgba(var(--color-bg-tertiary), 0.2);
    }
    
    .puzzle-item.completed { opacity: 0.6; }
    
    .puzzle-num {
        color: rgba(var(--color-text-primary), 0.5);
        font-size: 0.8rem;
        width: 30px;
    }
    
    .difficulty {
        color: rgb(var(--color-accent-warning));
        font-size: 0.8rem;
    }
    
    .status {
        font-size: 0.75rem;
        padding: 2px 6px;
        border-radius: 4px;
    }
    
    .status.completed { background: rgba(var(--color-accent-success), 1.0); color: rgb(var(--color-accent-success-text)); }
    .status.progress { background: rgba(var(--color-accent-warning), 1.0); color: rgb(var(--color-accent-warning-text)); }
    
    /* Game item text elements */
    .game-item .progress {
        color: rgba(var(--color-text-primary), 0.8);
        font-size: 0.8rem;
    }

    .game-item .time {
        color: rgba(var(--color-text-primary), 0.6);
        font-size: 0.75rem;
    }

    .game-item .mistakes {
        color: rgba(var(--color-text-primary), 0.6);
        font-size: 0.75rem;
    }
    
    .play-btn, .resume-btn {
        margin-left: auto;
        padding: 6px 12px;
        border: none;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 600;
        cursor: pointer;
        background: rgba(var(--color-accent-desat), var(--color-btn-opacity));
        color: rgba(var(--color-text-primary), 0.8);
        transition: all 0.15s ease;
    }

    .play-btn:hover, .resume-btn:hover {
        background: rgba(var(--color-accent-desat), var(--color-btn-hover-opacity));
        transform: translateY(-1px);
    }
    
    /* Import/Export styles */
    .export-option {
        margin-bottom: 12px;
    }
    
    .export-option label {
        display: block;
        color: rgba(var(--color-text-primary), 0.7);
        font-size: 0.8rem;
        margin-bottom: 4px;
    }
    
    .export-row {
        display: flex;
        gap: 8px;
    }
    
    .export-field {
        flex: 1;
        padding: 8px 12px;
        border: 1px solid rgba(var(--color-border), 0.3);
        border-radius: 6px;
        background: rgba(var(--color-bg-primary), 0.4);
        color: rgba(var(--color-text-primary), 0.8);
        font-family: monospace;
        font-size: 0.75rem;
    }
    
    .copy-btn, .paste-btn, .load-btn {
        background: rgba(var(--color-accent-desat), var(--color-btn-opacity));
        color: rgba(var(--color-text-primary), 0.8);
        padding: 8px 12px;
        border: none;
        border-radius: 6px;
        font-size: 0.75rem;
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }

    .copy-btn:hover, .paste-btn:hover, .load-btn:hover
    {         
        background: rgba(var(--color-accent-desat), var(--color-btn-hover-opacity));
    }
    
    .hint {
        color: rgba(var(--color-text-primary), 0.5);
        font-size: 0.75rem;
        margin-bottom: 8px;
    }
    
    .hint a,
    .hint a:visited,
    .hint a:hover,
    .hint a:active {
        color: rgb(var(--color-accent-primary));
        text-decoration: none;
        transition: opacity 0.15s ease;
    }
    
    .hint a:hover {
        opacity: 0.8;
        text-decoration: underline;
    }
    
    .import-field {
        width: 100%;
        height: 80px;
        padding: 12px;
        border: 1px solid rgba(var(--color-border), 0.3);
        border-radius: 8px;
        background: rgba(var(--color-bg-primary), 0.4);
        color: rgba(var(--color-text-primary), 0.8);
        font-family: monospace;
        font-size: 0.8rem;
        resize: none;
        margin-bottom: 12px;
    }
    
    .import-actions {
        display: flex;
        gap: 8px;
    }
    
    
    /* Highlight styles */
    .cell.highlight-primary { background: rgba(var(--color-accent-primary), 0.3); } /* Light blue */
    .cell.highlight-secondary { background: rgba(var(--color-accent-secondary), 0.3); } /* Light red */
    .cell.highlight-both { background: rgba(var(--color-accent-tertiary), 0.3); } /* Light purple */

    .cell.highlight-primary:hover { background: rgba(var(--color-accent-primary), 0.4); }
    .cell.highlight-secondary:hover { background: rgba(var(--color-accent-secondary), 0.4); }
    .cell.highlight-both:hover { background: rgba(var(--color-accent-tertiary), 0.4); }
    
    /* All candidates covered - striped/hashed pattern (easy to customize) */
    .cell.all-candidates-covered {
        --stripe-color: rgba(var(--color-accent-warning), 0.25);
        --stripe-size: 6px;
        --stripe-angle: 45deg;
        background-image: repeating-linear-gradient(
            var(--stripe-angle),
            var(--stripe-color),
            var(--stripe-color) 2px,
            transparent 2px,
            transparent var(--stripe-size)
        );
    }
    /* Combine with existing highlight colors */
    .cell.all-candidates-covered.highlight-primary {
        background: 
            repeating-linear-gradient(var(--stripe-angle), var(--stripe-color), var(--stripe-color) 2px, transparent 2px, transparent var(--stripe-size)),
            rgba(var(--color-accent-primary), 0.3);
    }
    .cell.all-candidates-covered.highlight-secondary {
        background: 
            repeating-linear-gradient(var(--stripe-angle), var(--stripe-color), var(--stripe-color) 2px, transparent 2px, transparent var(--stripe-size)),
            rgba(var(--color-accent-secondary), 0.3);
    }
    .cell.all-candidates-covered.highlight-both {
        background: 
            repeating-linear-gradient(var(--stripe-angle), var(--stripe-color), var(--stripe-color) 2px, transparent 2px, transparent var(--stripe-size)),
            rgba(var(--color-accent-tertiary), 0.3);
    }
    
    /* Hint cell highlighting - new system */
    .cell.hint-cover-area { 
        background: rgba(var(--color-accent-primary), 0.3); /* Light blue for cover area */
    }
    .cell.hint-cover-area:hover { 
        background: rgba(var(--color-accent-primary), 0.4); 
    }
    .cell.hint-solved-cell { 
        background: rgba(var(--color-accent-success), 0.45); /* Stronger green for solution cell */
        box-shadow: inset 0 0 0 2px rgba(var(--color-accent-success), 0.8);
    }
    .cell.hint-solved-cell:hover { 
        background: rgba(var(--color-accent-success), 0.55); 
    }
    
    /* Number button selection states */
    .num-btn.primary {
        background: rgba(var(--color-accent-primary), 0.5);
        box-shadow: inset 0 0 0 2px rgb(var(--color-accent-primary));
    }
    .num-btn.primary:hover {
        background: rgba(var(--color-accent-primary), 0.6);
    }
    .num-btn.secondary {
        background: rgba(var(--color-accent-secondary), 0.5);
        box-shadow: inset 0 0 0 2px rgb(var(--color-accent-secondary));
    }
    .num-btn.secondary:hover {
        background: rgba(var(--color-accent-secondary), 0.6);
    }
    .num-btn.both {
        background: rgba(206, 147, 216, 0.5);
        box-shadow: inset 0 0 0 2px rgb(206, 147, 216);
    }
    .num-btn.both:hover {
        background: rgba(206, 147, 216, 0.6);
    }
    
    /* Pencil mark highlighting - color coded */
    .candidate.pencil-highlight-primary {
        color: rgb(var(--color-accent-primary-text));
        font-weight: bold;
        background: rgba(var(--color-accent-primary), 0.6);
        border-radius: 2px;
    }
    .candidate.pencil-highlight-secondary {
        color: rgb(var(--color-accent-primary-text));
        font-weight: bold;
        background: rgba(var(--color-accent-secondary), 0.6);
        border-radius: 2px;
    }
    .candidate.pencil-highlight-both {
        color: rgb(var(--color-accent-primary-text));
        font-weight: bold;
        background: rgba(var(--color-accent-warning), 0.8);
        border-radius: 2px;
    }
    
    /* Hint candidate highlighting */
    .candidate.hint-elimination {
        color: rgb(var(--color-accent-error)); /* Red number */
        text-decoration: line-through;
        font-weight: bold;
        background: rgba(var(--color-grid-neutral), 1); /* Grey background */
        border-radius: 2px;
    }

    .candidate.hint-matching-not-eliminated {
        color: rgb(var(--color-accent-primary-text)); /* Green number */
        font-weight: bold;
        background: rgba(var(--color-accent-success), 1); /* Light green background */
        border-radius: 2px;
    }

    .candidate.hint-solved {
        color: rgb(var(--color-accent-primary-text));
        font-weight: bold;
        background: rgba(var(--color-accent-success), 1);
        border-radius: 2px;
        box-shadow: 0 0 0 1px rgba(var(--color-accent-success), 0.5);
    }
    
    /* Mode indicators in header */
    .mode-indicators {
        display: flex;
        gap: 6px;
    }
    
    .mode-badge {
        font-size: clamp(0.55rem, calc(0.5rem + 0.4vmin), 0.7rem);
        padding: clamp(2px, 0.5vmin, 4px) clamp(4px, 1vmin, 8px);
        border-radius: clamp(2px, 0.5vmin, 4px);
        background: rgba(var(--color-bg-tertiary), 0.15);
        color: rgba(var(--color-text-primary), 0.7);
        font-weight: 600;
    }
    
    .mode-badge.highlight-mode { background: rgba(var(--color-accent-primary), 0.3); color: rgb(var(--color-accent-primary)); }
    .mode-badge.play-mode.fast { background: rgba(var(--color-accent-success), 0.3); color: rgb(var(--color-accent-success)); }
    .mode-badge.play-mode.advanced { background: rgba(var(--color-accent-warning), 0.3); color: rgb(var(--color-accent-warning)); }
    .mode-badge.clickable { cursor: pointer; transition: all 0.15s ease; }
    .mode-badge.clickable:hover { transform: scale(1.05); filter: brightness(1.1); }
    
    /* Selected number badges - inline with mode indicators */
    .selected-num {
        font-size: clamp(0.55rem, calc(0.5rem + 0.4vmin), 0.7rem);
        font-weight: 700;
        padding: clamp(2px, 0.5vmin, 4px) clamp(4px, 1vmin, 8px);
        border-radius: clamp(2px, 0.5vmin, 4px);
        line-height: 1.2;
    }
    
    .selected-num.primary { background: rgba(var(--color-accent-primary), 0.4); color: rgb(var(--color-accent-primary)); }
    .selected-num.secondary { background: rgba(var(--color-accent-secondary), 0.4); color: rgb(var(--color-accent-secondary)); }
    
    /* Advanced mode action buttons - compact */
    .advanced-actions {
        display: flex;
        gap: clamp(4px, 1vmin, 8px);
        justify-content: center;
        flex-shrink: 0;
    }
    
    .action-btn {
        padding: clamp(4px, 1vmin, 8px) clamp(8px, 2vmin, 14px);
        border: none;
        border-radius: clamp(4px, 1vmin, 8px);
        font-size: clamp(0.6rem, calc(0.55rem + 0.4vmin), 0.8rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.15s ease;
    }
    
    /* Set buttons - primary (blue) and secondary (red) */
    .action-btn.set-btn.primary {
        background: rgba(var(--color-accent-primary), 0.3);
        color: rgb(var(--color-accent-primary));
    }
    .action-btn.set-btn.primary:hover {
        background: rgba(var(--color-accent-primary), 0.5);
    }

    .action-btn.set-btn.secondary {
        background: rgba(var(--color-accent-secondary), 0.3);
        color: rgb(var(--color-accent-secondary));
    }
    .action-btn.set-btn.secondary:hover {
        background: rgba(var(--color-accent-secondary), 0.5);
    }

    .action-btn.set-btn.both {
        background: rgba(var(--color-accent-warning), 0.3);
        color: rgb(var(--color-accent-warning));
    }
    .action-btn.set-btn.both:hover {
        background: rgba(var(--color-accent-warning), 0.5);
    }

    /* Clear pencil mark buttons - primary (blue) and secondary (red) */
    .action-btn.clr-btn.primary {
        background: rgba(var(--color-accent-primary), var(--color-btn-opacity));
        color: rgb(var(--color-accent-primary));
        border: 1px solid rgba(var(--color-accent-primary), 0.3);
    }
    .action-btn.clr-btn.primary:hover {
        background: rgba(var(--color-accent-primary), 0.3);
    }

    .action-btn.clr-btn.secondary {
        background: rgba(var(--color-accent-secondary), 0.15);
        color: rgb(var(--color-accent-secondary));
        border: 1px solid rgba(var(--color-accent-secondary), 0.3);
    }
    .action-btn.clr-btn.secondary:hover {
        background: rgba(var(--color-accent-secondary), 0.3);
    }

    .action-btn.clr-btn.other {
        background: rgba(var(--color-accent-warning), 0.15);
        color: rgb(var(--color-accent-warning));
        border: 1px solid rgba(var(--color-accent-warning), 0.3);
    }
    .action-btn.clr-btn.other:hover {
        background: rgba(var(--color-accent-warning), 0.3);
    }

    /* Clear selection button (X) */
    .action-btn.clear-btn {
        background: rgba(var(--color-bg-tertiary), 0.2);
        color: rgba(var(--color-text-primary), 0.7);
    }
    .action-btn.clear-btn:hover {
        background: rgba(var(--color-bg-tertiary), 0.3);
    }
    
    /* Deselect cell button */
    .action-btn.deselect-btn {
        background: rgba(var(--color-accent-tertiary), 0.2);
        color: rgb(var(--color-accent-tertiary));
    }
    .action-btn.deselect-btn:hover {
        background: rgba(var(--color-accent-tertiary), 0.35);
    }
    
    /* Dual number pads in advanced mode */
    .number-pad.primary {
        margin-bottom: clamp(4px, 1vmin, 8px);
    }
    .number-pad.secondary {
        margin-top: clamp(2px, 0.5vmin, 4px);
    }
    
    /* Settings screen styles */
    .settings .section {
        background: rgba(var(--color-bg-tertiary), 0.1);
        border-radius: 12px;
        padding: 16px;
        margin-bottom: 16px;
    }

    .settings .section h2 {
        color: rgb(var(--color-text-primary));
        font-size: 1rem;
        margin-bottom: 8px;
    }
    
    .setting-desc {
        color: rgba(var(--color-text-primary), 0.5);
        font-size: 0.75rem;
        margin-bottom: 12px;
    }
    
    .nav-grid {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: clamp(6px, 1.5vmin, 12px);
    }
    
    .settings-nav-btn {
        padding: clamp(10px, 2.5vmin, 20px);
        border: none;
        border-radius: clamp(6px, 1.5vmin, 12px);
        font-size: clamp(0.75rem, calc(0.7rem + 0.5vmin), 1rem);
        font-weight: 600;
        cursor: pointer;
        background: rgba(var(--color-accent-desat), var(--color-btn-opacity));
        color: rgba(var(--color-text-primary), 0.8);
        transition: all 0.15s ease;
    }

    .settings-nav-btn:hover {
        background: rgba(var(--color-accent-desat), var(--color-btn-hover-opacity));
        transform: translateY(-2px);
    }
    
    .mode-options {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: clamp(4px, 1vmin, 8px);
        margin-bottom: clamp(8px, 2vmin, 16px);
    }
    
    .mode-btn {
        padding: clamp(8px, 2vmin, 16px) clamp(10px, 2.5vmin, 20px);
        border: none;
        border-radius: clamp(4px, 1vmin, 8px);
        font-size: 0.8rem;
        font-weight: 600;
        cursor: pointer;
        background: rgba(var(--color-accent-desat), var(--color-btn-opacity));
        color: rgba(var(--color-text-primary), 0.7);
        transition: all 0.15s ease;
    }

    .mode-btn:hover {
        background: rgba(var(--color-accent-desat), var(--color-btn-hover-opacity));
    }
    
    .mode-btn.active {
        background: rgba(var(--color-accent-primary), 0.4);
        color: rgb(var(--color-text-primary));
        box-shadow: inset 0 0 0 2px rgb(var(--color-accent-primary));
    }
    
    .play-modes .mode-btn.fast.active {
        background: rgba(var(--color-accent-success), 0.4);
        color: rgb(var(--color-text-primary));
        box-shadow: inset 0 0 0 2px rgb(var(--color-accent-success));
    }
    
    .play-modes .mode-btn.advanced.active {
        background: rgba(var(--color-accent-warning), 0.4);
        color: rgb(var(--color-text-primary));
        box-shadow: inset 0 0 0 2px rgb(var(--color-accent-warning));
    }

    /* Theme options */
    .theme-options {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: clamp(4px, 1vmin, 8px);
        margin-bottom: clamp(8px, 2vmin, 16px);
    }

    .theme-btn.dark.active {
        background: rgba(0, 0, 0, 0.4);
        color: rgb(255, 255, 255);
        box-shadow: inset 0 0 0 2px rgb(51, 51, 51);
    }

    .theme-btn.blue.active {
        background: rgba(26, 26, 46, 0.4);
        color: rgb(255, 255, 255);
        box-shadow: inset 0 0 0 2px rgb(22, 33, 62);
    }

    .theme-btn.light.active {
        background: rgba(248, 249, 250, 0.4);
        color: rgb(33, 37, 41);
        box-shadow: inset 0 0 0 2px rgb(222, 226, 230);
    }

    .theme-btn.epaper.active {
        background: rgba(248, 248, 248, 0.4);
        color: rgb(0, 0, 0);
        box-shadow: inset 0 0 0 2px rgb(153, 153, 153);
    }
    
    .mode-explanation {
        background: rgba(var(--color-bg-primary), 0.2);
        border-radius: 6px;
        padding: 10px 12px;
        font-size: 0.75rem;
        color: rgba(var(--color-text-primary), 0.6);
        line-height: 1.4;
    }
    
    .highlight-info .color-legend {
        display: flex;
        flex-direction: column;
        gap: 8px;
        margin-bottom: 12px;
    }
    
    .legend-item {
        display: flex;
        align-items: center;
        gap: 10px;
        font-size: 0.8rem;
        color: rgba(var(--color-text-primary), 0.7);
    }
    
    .color-box {
        width: 24px;
        height: 24px;
        border-radius: 4px;
    }
    
    .color-box.primary { background: rgba(var(--color-accent-primary), 0.5); }
    .color-box.secondary { background: rgba(var(--color-accent-secondary), 0.5); }
    .color-box.both { background: rgba(206, 147, 216, 0.5); }
    
    /* Responsive adjustments - with clamp() above, these are mostly for edge cases */
    @media (max-width: 400px) {
        :root {
            --grid-size: min(95vw, 85vh - 180px);
        }
        .mode-indicators { display: none; }
        .controls { gap: 4px; }
        .toggle-btn, .undo-btn, .hint-btn { padding: 6px 10px; }
    }
    
    @media (max-height: 600px) {
        :root {
            --grid-size: min(90vw, 75vh - 120px);
        }
        .game-info { margin-top: 4px; }
        .game-area { gap: 8px; }
    }
    
    /* Landscape orientation on mobile */
    @media (max-height: 500px) and (orientation: landscape) {
        :root {
            --grid-size: min(50vw, 70vh);
        }
        .sudoku-container {
            flex-direction: row;
            flex-wrap: wrap;
            max-width: 100%;
            width: auto;
        }
        .header { width: 100%; }
        .sudoku-grid { margin: 0; }
    }
    
    /* Large screens */
    @media (min-width: 1200px) and (min-height: 900px) {
        :root {
            --grid-size: min(70vh, 700px);
        }
    }
    
    /* Modal styles */
    .modal-overlay {
        position: fixed;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background: rgba(var(--color-shadow), 0.85);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 1000;
        padding: clamp(16px, 4vmin, 32px);
        animation: fadeInOverlay 0.2s ease;
    }
    
    @keyframes fadeInOverlay {
        from { opacity: 0; }
        to { opacity: 1; }
    }
    
    .modal-content {
        background: var(--gradient-bg);
        border-radius: clamp(12px, 3vmin, 24px);
        padding: clamp(20px, 4vmin, 40px);
        max-width: 500px;
        width: 100%;
        max-height: 85vh;
        overflow-y: auto;
        position: relative;
        box-shadow: 0 20px 60px rgba(var(--color-shadow), 0.5);
        border: 1px solid rgba(var(--color-border), 0.1);
        animation: slideUp 0.3s ease;
    }
    
    @keyframes slideUp {
        from { opacity: 0; transform: translateY(20px); }
        to { opacity: 1; transform: translateY(0); }
    }
    
    .modal-close {
        position: absolute;
        top: clamp(12px, 2vmin, 20px);
        right: clamp(12px, 2vmin, 20px);
        background: rgba(var(--color-accent-error), 0.2);
        border: none;
        color: rgb(var(--color-accent-error));
        font-size: clamp(1rem, calc(0.9rem + 0.5vmin), 1.3rem);
        width: clamp(32px, 6vmin, 40px);
        height: clamp(32px, 6vmin, 40px);
        border-radius: 50%;
        cursor: pointer;
        transition: all 0.15s ease;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .modal-close:hover {
        background: rgba(var(--color-accent-error), 0.4);
        transform: scale(1.1);
    }
    
    .about-modal h1 {
        color: rgb(var(--color-accent-primary));
        font-size: clamp(1.5rem, calc(1.3rem + 1vmin), 2rem);
        margin-bottom: clamp(12px, 2vmin, 20px);
        text-align: center;
    }

    .about-tagline {
        color: rgb(var(--color-accent-info));
        font-size: clamp(0.95rem, calc(0.85rem + 0.5vmin), 1.15rem);
        text-align: center;
        margin-bottom: clamp(8px, 1.5vmin, 16px);
        font-weight: 500;
    }

    .about-description {
        color: rgba(var(--color-text-primary), 0.8);
        font-size: clamp(0.85rem, calc(0.75rem + 0.4vmin), 1rem);
        text-align: center;
        line-height: 1.5;
        margin-bottom: clamp(20px, 4vmin, 32px);
        padding-bottom: clamp(16px, 3vmin, 24px);
        border-bottom: 1px solid rgba(var(--color-border), 0.2);
    }
    
    .about-section {
        margin-bottom: clamp(16px, 3vmin, 24px);
    }
    
    .about-section h3 {
        color: rgb(var(--color-accent-warning));
        font-size: clamp(0.9rem, calc(0.8rem + 0.4vmin), 1.1rem);
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    .about-section p {
        color: rgba(var(--color-text-primary), 0.7);
        font-size: clamp(0.8rem, calc(0.7rem + 0.35vmin), 0.95rem);
        line-height: 1.5;
        margin-bottom: clamp(4px, 0.8vmin, 8px);
    }
    
    .about-section a {
        color: rgb(var(--color-accent-primary));
        text-decoration: none;
        transition: color 0.15s ease;
    }
    
    .about-section a:hover {
        color: rgb(var(--color-accent-info));
        text-decoration: underline;
    }
    
    .about-section strong {
        color: rgb(var(--color-accent-info));
    }
    
    .help-modal h1 {
        color: rgb(var(--color-accent-primary));
        font-size: clamp(1.5rem, calc(1.3rem + 1vmin), 2rem);
        margin-bottom: clamp(12px, 2vmin, 20px);
        text-align: center;
    }
    
    .help-intro {
        color: rgba(var(--color-text-primary), 0.8);
        font-size: clamp(0.85rem, calc(0.75rem + 0.4vmin), 1rem);
        text-align: center;
        line-height: 1.5;
        margin-bottom: clamp(20px, 4vmin, 32px);
        padding-bottom: clamp(16px, 3vmin, 24px);
        border-bottom: 1px solid rgba(var(--color-border), 0.2);
    }
    
    .help-section {
        margin-bottom: clamp(20px, 4vmin, 32px);
    }
    
    .help-section h2 {
        color: rgb(var(--color-accent-warning));
        font-size: clamp(1.1rem, calc(1rem + 0.5vmin), 1.4rem);
        margin-bottom: clamp(12px, 2vmin, 20px);
        margin-top: clamp(16px, 3vmin, 24px);
        border-bottom: 1px solid rgba(var(--color-border), 0.2);
        padding-bottom: clamp(8px, 1.5vmin, 12px);
    }
    
    .help-section h2:first-of-type {
        margin-top: 0;
    }
    
    .help-section h3 {
        color: rgb(var(--color-accent-info));
        font-size: clamp(0.95rem, calc(0.85rem + 0.4vmin), 1.15rem);
        margin-bottom: clamp(8px, 1.5vmin, 12px);
        margin-top: clamp(12px, 2vmin, 16px);
    }
    
    .help-section ul,
    .help-section ol {
        color: rgba(var(--color-text-primary), 0.7);
        font-size: clamp(0.8rem, calc(0.7rem + 0.35vmin), 0.95rem);
        line-height: 1.6;
        margin-bottom: clamp(12px, 2vmin, 16px);
        padding-left: clamp(20px, 4vmin, 30px);
    }
    
    .help-section li {
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    .help-section ul ul,
    .help-section ul ol,
    .help-section ol ul,
    .help-section ol ol {
        margin-top: clamp(6px, 1vmin, 10px);
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    .help-section strong {
        color: rgb(var(--color-accent-info));
        font-weight: 600;
    }
    
    .greeting-modal {
        max-width: 600px;
    }
    
    .greeting-modal h1 {
        color: rgb(var(--color-accent-primary));
        font-size: clamp(1.5rem, calc(1.3rem + 1vmin), 2rem);
        margin-bottom: clamp(12px, 2vmin, 20px);
        text-align: center;
    }
    
    .greeting-content {
        color: rgba(var(--color-text-primary), 0.8);
        font-size: clamp(0.85rem, calc(0.75rem + 0.4vmin), 1rem);
        line-height: 1.6;
    }
    
    .greeting-content p {
        margin-bottom: clamp(12px, 2vmin, 16px);
    }
    
    .greeting-signature {
        margin-top: clamp(20px, 4vmin, 32px);
        font-style: italic;
        color: rgba(var(--color-text-primary), 0.9);
    }
    
    .help-section .greeting-content {
        margin-top: clamp(8px, 1.5vmin, 12px);
    }
    
    /* Completion modal styles */
    .completion-modal {
        max-width: 400px;
        text-align: center;
    }
    
    .completion-icon {
        font-size: clamp(3rem, calc(2.5rem + 3vmin), 5rem);
        margin-bottom: clamp(8px, 2vmin, 16px);
    }
    
    .completion-modal h1 {
        color: rgb(var(--color-accent-success));
        font-size: clamp(1.5rem, calc(1.3rem + 1vmin), 2rem);
        margin-bottom: clamp(12px, 2vmin, 20px);
    }
    
    .completion-content p {
        color: rgba(var(--color-text-primary), 0.8);
        font-size: clamp(0.9rem, calc(0.8rem + 0.4vmin), 1.1rem);
        margin-bottom: clamp(16px, 3vmin, 24px);
    }
    
    .completion-stats {
        display: flex;
        justify-content: center;
        gap: clamp(16px, 3vmin, 32px);
        margin-bottom: clamp(20px, 4vmin, 32px);
    }
    
    .completion-stats .stat {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: 4px;
    }
    
    .completion-stats .stat-icon {
        font-size: clamp(1.5rem, calc(1.2rem + 1vmin), 2rem);
    }
    
    .completion-stats .stat-label {
        font-size: clamp(0.65rem, calc(0.6rem + 0.3vmin), 0.8rem);
        color: rgba(var(--color-text-primary), 0.5);
        text-transform: uppercase;
    }

    .completion-stats .stat-value {
        font-size: clamp(1rem, calc(0.9rem + 0.5vmin), 1.3rem);
        font-weight: 700;
        color: rgb(var(--color-text-primary));
    }
    
    .completion-actions {
        display: flex;
        justify-content: center;
        gap: clamp(8px, 2vmin, 16px);
    }
    
    .completion-actions button {
        padding: clamp(10px, 2vmin, 14px) clamp(20px, 4vmin, 32px);
        border: none;
        border-radius: clamp(6px, 1.5vmin, 10px);
        font-size: clamp(0.85rem, calc(0.75rem + 0.4vmin), 1rem);
        font-weight: 600;
        cursor: pointer;
        transition: all 0.2s ease;
    }
    
    .completion-actions .close-btn {
        background: rgba(var(--color-bg-tertiary), 0.2);
        color: rgba(var(--color-text-primary), 0.8);
    }

    .completion-actions .close-btn:hover {
        background: rgba(var(--color-bg-tertiary), 0.3);
    }
    
    .completion-actions .next-btn {
        background: rgba(var(--color-accent-success), 0.8);
        color: rgb(var(--color-text-primary));
    }
    
    .completion-actions .next-btn:hover {
        background: rgb(var(--color-accent-success));
        transform: translateY(-2px);
    }
    
    /* Number pad completed button style */
    .num-btn.completed {
        opacity: 0.3;
        pointer-events: none;
        visibility: hidden;
    }
    
    /* Category header with toggle */
    .category-header {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        justify-content: space-between;
        gap: clamp(8px, 2vmin, 16px);
        margin-bottom: clamp(8px, 2vmin, 12px);
    }
    
    /* Hide completed toggle */
    .toggle-label {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: clamp(0.7rem, calc(0.65rem + 0.3vmin), 0.85rem);
        color: rgba(var(--color-text-primary), 0.7);
        cursor: pointer;
        white-space: nowrap;
    }
    
    .toggle-checkbox {
        width: 16px;
        height: 16px;
        cursor: pointer;
        accent-color: rgb(var(--color-accent-primary));
    }
    
    /* Completion stats in puzzle list */
    .completion-stats {
        font-size: clamp(0.65rem, calc(0.6rem + 0.25vmin), 0.75rem);
        color: rgba(var(--color-text-primary), 0.5);
    }
    
    /* Delete button for saved games */
    .delete-btn {
        padding: clamp(4px, 1vmin, 8px) clamp(8px, 2vmin, 12px);
        border: none;
        border-radius: clamp(4px, 1vmin, 6px);
        font-size: clamp(0.7rem, calc(0.65rem + 0.3vmin), 0.85rem);
        cursor: pointer;
        background: rgba(var(--color-accent-error), 0.2);
        color: rgb(var(--color-accent-error));
        transition: all 0.15s ease;
    }
    
    .delete-btn:hover {
        background: rgba(var(--color-accent-error), 0.4);
    }
    
    /* Empty message for custom puzzles */
    .empty-message {
        padding: clamp(16px, 3vmin, 24px);
        text-align: center;
        color: rgba(var(--color-text-primary), 0.5);
        font-size: clamp(0.8rem, calc(0.75rem + 0.3vmin), 0.95rem);
        line-height: 1.5;
    }
    
    /* Version indicator - fixed position bottom left */
    .version-indicator {
        position: fixed;
        bottom: 12px;
        left: 12px;
        font-size: clamp(0.65rem, calc(0.6rem + 0.25vmin), 0.75rem);
        color: rgba(var(--color-text-primary), 0.35);
        cursor: pointer;
        padding: 4px 8px;
        border-radius: 4px;
        transition: all 0.15s ease;
        z-index: 100;
        user-select: none;
    }

    .version-indicator:hover {
        color: rgba(var(--color-text-primary), 0.7);
        background: rgba(var(--color-bg-tertiary), 0.2);
    }
    
    /* Version modal styles */
    .version-modal {
        max-width: 600px;
    }
    
    .version-modal h1 {
        color: rgb(var(--color-accent-primary));
        font-size: clamp(1.5rem, calc(1.3rem + 1vmin), 2rem);
        margin-bottom: clamp(12px, 2vmin, 20px);
        text-align: center;
    }
    
    .changelog-content {
        color: rgba(var(--color-text-primary), 0.85);
        font-size: clamp(0.85rem, calc(0.75rem + 0.4vmin), 1rem);
        line-height: 1.6;
        max-height: 60vh;
        overflow-y: auto;
        padding-right: 8px;
    }
    
    .changelog-content h2.changelog-version {
        color: rgb(var(--color-accent-info));
        font-size: clamp(1.1rem, calc(1rem + 0.5vmin), 1.3rem);
        margin-top: clamp(16px, 3vmin, 24px);
        margin-bottom: clamp(8px, 1.5vmin, 12px);
        padding-bottom: clamp(6px, 1vmin, 10px);
        border-bottom: 1px solid rgba(var(--color-border), 0.2);
    }
    
    .changelog-content h2.changelog-version:first-child {
        margin-top: 0;
    }
    
    .changelog-content h3.changelog-section {
        color: rgb(var(--color-accent-warning));
        font-size: clamp(0.95rem, calc(0.85rem + 0.4vmin), 1.1rem);
        margin-top: clamp(12px, 2vmin, 16px);
        margin-bottom: clamp(6px, 1vmin, 10px);
    }
    
    .changelog-content ul {
        margin: 0 0 clamp(8px, 1.5vmin, 12px) 0;
        padding-left: clamp(16px, 3vmin, 24px);
    }
    
    .changelog-content li {
        margin-bottom: clamp(4px, 0.8vmin, 8px);
        color: rgba(var(--color-text-primary), 0.8);
    }
    
    .changelog-content li.changelog-subitem {
        color: rgba(var(--color-text-primary), 0.6);
        font-size: 0.95em;
        margin-left: 16px;
    }
    
    .changelog-content strong {
        color: rgb(var(--color-accent-error));
    }
    
    .changelog-content del {
        color: rgba(var(--color-text-primary), 0.4);
        text-decoration: line-through;
    }
    
    .changelog-content code {
        background: rgba(var(--color-bg-primary), 0.5);
        padding: 2px 6px;
        border-radius: 4px;
        font-family: 'Monaco', 'Consolas', monospace;
        font-size: 0.9em;
    }
    
    .changelog-content p {
        margin-bottom: clamp(8px, 1.5vmin, 12px);
    }
    
    .version-actions {
        display: flex;
        justify-content: center;
        margin-top: clamp(16px, 3vmin, 24px);
        padding-top: clamp(12px, 2vmin, 16px);
        border-top: 1px solid rgba(var(--color-border), 0.2);
    }
    
    .version-actions .close-btn {
        padding: clamp(10px, 2vmin, 14px) clamp(24px, 5vmin, 40px);
        border: none;
        border-radius: clamp(6px, 1.5vmin, 10px);
        font-size: clamp(0.9rem, calc(0.8rem + 0.4vmin), 1.05rem);
        font-weight: 600;
        cursor: pointer;
        background: rgba(var(--color-accent-info), 0.8);
        color: rgb(var(--color-text-primary));
        transition: all 0.2s ease;
    }
    
    .version-actions .close-btn:hover {
        background: rgb(var(--color-accent-info));
        transform: translateY(-2px);
    }
""".trimIndent()
