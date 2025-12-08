package view

import SudokuApp
import adapter.ExplanationStepDto
import adapter.TechniqueMatchInfo
import htmlEscape
import kotlinx.browser.document
import kotlinx.html.*
import org.w3c.dom.Element
import org.w3c.dom.HTMLElement
import org.w3c.dom.asList

/**
 * Render interactive hint description with clickable/hoverable elements
 * Parses patterns like:
 * - (3)R5C6 - cell/candidate reference (for chains)
 * - R5C6 or R1C8, R1C9 - simple cell references
 * - {5, 7, 8} or {7} - digit sets
 * - Row 5, Column 7, Box 3 - house references
 * - --[strong]--> or --[weak]--> - link indicators
 */
internal fun TagConsumer<HTMLElement>.renderInteractiveDescription(app: SudokuApp, description: String, hint: TechniqueMatchInfo) {
    var linkIndex = 0
    val result = StringBuilder()

    // Find all matches and their positions
    data class Match(val start: Int, val end: Int, val type: String, val content: String, val data: Any?)
    val matches = mutableListOf<Match>()

    // Pattern for chain notation: (3)R5C6 or (3)R5C6,R5C7
    val chainCellPattern = Regex("""\((\d)\)([Rr])(\d)[Cc](\d)(?:,([Rr])(\d)[Cc](\d))*""")
    chainCellPattern.findAll(description).forEach { match ->
        val digit = match.groupValues[1].toInt()
        val cells = mutableListOf<Pair<Int, Int>>()
        val singleCellPattern = Regex("""[Rr](\d)[Cc](\d)""")
        singleCellPattern.findAll(match.value).forEach { cellMatch ->
            val row = cellMatch.groupValues[1].toInt() - 1
            val col = cellMatch.groupValues[2].toInt() - 1
            cells.add(row to col)
        }
        matches.add(Match(match.range.first, match.range.last + 1, "chain-cell", match.value, digit to cells))
    }

    // Pattern for simple cell references: R5C6 or R1C8, R1C9 (but not preceded by digit in parens)
    // Match cell lists like "R1C7, R3C7" or single cells like "R5C5"
    val simpleCellPattern = Regex("""(?<!\(\d\))([Rr]\d[Cc]\d(?:\s*,\s*[Rr]\d[Cc]\d)*)""")
    simpleCellPattern.findAll(description).forEach { match ->
        // Skip if this overlaps with a chain-cell match
        val overlaps = matches.any { m ->
            m.type == "chain-cell" &&
            ((match.range.first >= m.start && match.range.first < m.end) ||
             (match.range.last >= m.start && match.range.last < m.end))
        }
        if (!overlaps) {
            val cells = mutableListOf<Pair<Int, Int>>()
            val singleCellPattern = Regex("""[Rr](\d)[Cc](\d)""")
            singleCellPattern.findAll(match.value).forEach { cellMatch ->
                val row = cellMatch.groupValues[1].toInt() - 1
                val col = cellMatch.groupValues[2].toInt() - 1
                cells.add(row to col)
            }
            if (cells.isNotEmpty()) {
                matches.add(Match(match.range.first, match.range.last + 1, "simple-cell", match.value, cells))
            }
        }
    }

    // Pattern for digit sets: {5, 7, 8} or {7}
    val digitSetPattern = Regex("""\{(\d(?:\s*,\s*\d)*)\}""")
    digitSetPattern.findAll(description).forEach { match ->
        val digits = match.groupValues[1].split(",").mapNotNull { it.trim().toIntOrNull() }
        if (digits.isNotEmpty()) {
            matches.add(Match(match.range.first, match.range.last + 1, "digit-set", match.value, digits))
        }
    }

    // Pattern for single digits in context (e.g., "eliminate 7 from" or "candidate 5 only")
    // Match digits after keywords like "candidate", "digit", "eliminate", or before certain words
    val digitContextPatterns = listOf(
        // Digit after keyword: "candidate 5", "digit 7", "eliminate 5"
        Regex("""(?:candidate|digit|eliminate|Eliminate)\s+(\d)(?!\d)""", RegexOption.IGNORE_CASE),
        // Digit before action word: "5 can", "7 from", "5 in", "5 must", "5 is", "5 only"
        Regex("""(?<=\s|^)(\d)(?=\s+(?:can|from|in|must|is|only|appears?|be)\b)""", RegexOption.IGNORE_CASE),
        // Digit at end of sentence or before comma
        Regex("""(?<=\s)(\d)(?=\s*[.,]|\s*$)""")
    )

    for (pattern in digitContextPatterns) {
        pattern.findAll(description).forEach { match ->
            val digit = match.groupValues[1].toIntOrNull()
            if (digit != null && digit in 1..9) {
                // Find the actual position of the digit within the match
                val digitIndex = match.value.indexOfFirst { it.isDigit() }
                val digitStart = match.range.first + digitIndex
                val digitEnd = digitStart + 1

                // Skip if this overlaps with other matches
                val overlaps = matches.any { m ->
                    (digitStart >= m.start && digitStart < m.end) ||
                    (digitEnd > m.start && digitEnd <= m.end)
                }
                if (!overlaps) {
                    matches.add(Match(digitStart, digitEnd, "single-digit", digit.toString(), digit))
                }
            }
        }
    }

    // Pattern for house references: Row 5, Column 7, Box 3
    val housePattern = Regex("""(Row|Column|Box)\s+(\d)""", RegexOption.IGNORE_CASE)
    housePattern.findAll(description).forEach { match ->
        val houseType = match.groupValues[1].lowercase()
        val houseIndex = match.groupValues[2].toInt() - 1  // Convert to 0-indexed
        if (houseIndex in 0..8) {
            matches.add(Match(match.range.first, match.range.last + 1, "house", match.value, houseType to houseIndex))
        }
    }

    // Pattern for link indicators: --[strong]--> or --[weak]-->
    val linkPattern = Regex("""--\[(strong|weak)\]-->""")
    linkPattern.findAll(description).forEach { match ->
        val linkType = match.groupValues[1]
        matches.add(Match(match.range.first, match.range.last + 1, "link", match.value, linkIndex++ to linkType))
    }

    // Sort matches by position and remove overlaps (keep longer/earlier matches)
    matches.sortBy { it.start }
    val filteredMatches = mutableListOf<Match>()
    for (match in matches) {
        val overlaps = filteredMatches.any { existing ->
            (match.start >= existing.start && match.start < existing.end) ||
            (match.end > existing.start && match.end <= existing.end)
        }
        if (!overlaps) {
            filteredMatches.add(match)
        }
    }

    // Build HTML with interactive spans
    var currentPos = 0
    filteredMatches.forEach { match ->
        // Add text before this match
        if (match.start > currentPos) {
            result.append("""<span class="desc-text">${htmlEscape(description.substring(currentPos, match.start))}</span>""")
        }

        when (match.type) {
            "chain-cell" -> {
                @Suppress("UNCHECKED_CAST")
                val data = match.data as Pair<Int, List<Pair<Int, Int>>>
                val digit = data.first
                val cells = data.second
                val cellIndices = cells.map { pair -> pair.first * 9 + pair.second }
                val dataAttr = cellIndices.joinToString(",")
                result.append("""<span class="chain-cell-ref interactive-ref" data-cells="$dataAttr" data-candidate="$digit">${match.content}</span>""")
            }
            "simple-cell" -> {
                @Suppress("UNCHECKED_CAST")
                val cells = match.data as List<Pair<Int, Int>>
                val cellIndices = cells.map { pair -> pair.first * 9 + pair.second }
                val dataAttr = cellIndices.joinToString(",")
                result.append("""<span class="cell-ref interactive-ref" data-cells="$dataAttr">${match.content}</span>""")
            }
            "digit-set" -> {
                @Suppress("UNCHECKED_CAST")
                val digits = match.data as List<Int>
                val dataAttr = digits.joinToString(",")
                result.append("""<span class="digit-ref interactive-ref" data-digits="$dataAttr">${match.content}</span>""")
            }
            "single-digit" -> {
                val digit = match.data as Int
                result.append("""<span class="digit-ref interactive-ref" data-digits="$digit">${match.content}</span>""")
            }
            "house" -> {
                @Suppress("UNCHECKED_CAST")
                val data = match.data as Pair<String, Int>
                val houseType = data.first
                val houseIndex = data.second
                result.append("""<span class="house-ref interactive-ref" data-house-type="$houseType" data-house-index="$houseIndex">${match.content}</span>""")
            }
            "link" -> {
                @Suppress("UNCHECKED_CAST")
                val data = match.data as Pair<Int, String>
                val idx = data.first
                val linkType = data.second
                result.append("""<span class="chain-link-ref interactive-ref chain-link-$linkType" data-link-index="$idx">[$linkType]</span>""")
            }
        }
        currentPos = match.end
    }

    // Add remaining text
    if (currentPos < description.length) {
        result.append("""<span class="desc-text">${htmlEscape(description.substring(currentPos))}</span>""")
    }

    // If no interactive elements found, just show plain text
    if (filteredMatches.isEmpty()) {
        span { +description }
    } else {
        // Generate unique ID first
        val containerId = "desc-${kotlin.js.Date().getTime().toLong()}"

        div("interactive-description") {
            id = containerId
            unsafe { +result.toString() }
        }

        // Use setTimeout to attach listeners after DOM is updated
        kotlinx.browser.window.setTimeout({
            app.attachDescriptionInteractionListeners(containerId, hint)
        }, 50)  // Small delay to ensure DOM is ready
    }
}

/**
 * Render SVG overlay for chain lines on the main game board
 */
internal fun FlowContent.renderChainLinesSvg(app: SudokuApp,
    hint: TechniqueMatchInfo,
    currentStep: ExplanationStepDto? = null
) {
    // Use step-specific lines/groups if available, otherwise use hint's full data
    val linesToDraw = currentStep?.lines?.takeIf { it.isNotEmpty() } ?: hint.lines
    val groupsToDraw = currentStep?.groups?.takeIf { it.isNotEmpty() } ?: hint.groups

    // SVG viewBox is set to match a 9x9 grid where each cell is 100 units
    // This allows us to position lines relative to cell/candidate positions
    div("chain-lines-container") {
        val svgContent = buildString {
            append("""<svg class="chain-lines-overlay" viewBox="0 0 900 900" preserveAspectRatio="xMidYMid meet">""")

            // Draw lines first (behind candidate highlights)
            linesToDraw.forEach { line ->
                // Calculate positions
                val fromCellX = line.from.col * 100
                val fromCellY = line.from.row * 100
                val fromCandCol = (line.from.candidate - 1) % 3
                val fromCandRow = (line.from.candidate - 1) / 3
                val fromX = fromCellX + 20 + fromCandCol * 30
                val fromY = fromCellY + 20 + fromCandRow * 30

                val toCellX = line.to.col * 100
                val toCellY = line.to.row * 100
                val toCandCol = (line.to.candidate - 1) % 3
                val toCandRow = (line.to.candidate - 1) / 3
                val toX = toCellX + 20 + toCandCol * 30
                val toY = toCellY + 20 + toCandRow * 30

                val strokeClass = if (line.isStrongLink) "strong-link" else "weak-link"

                val curveXVal = line.curveX
                val curveYVal = line.curveY
                if (curveXVal != null && curveYVal != null) {
                    // Curved line using quadratic bezier
                    val midX = (fromX + toX) / 2 + (curveXVal * 100).toInt()
                    val midY = (fromY + toY) / 2 + (curveYVal * 100).toInt()
                    append("""<path class="board-chain-line $strokeClass" d="M$fromX,$fromY Q$midX,$midY $toX,$toY" />""")
                } else {
                    // Straight line
                    append("""<line class="board-chain-line $strokeClass" x1="$fromX" y1="$fromY" x2="$toX" y2="$toY" />""")
                }
            }

            // Draw group highlights on top of lines
            groupsToDraw.forEach { group ->
                val colorClass = when (group.groupType) {
                    "chain-on" -> "group-on"
                    "chain-off" -> "group-off"
                    "als" -> "group-als"
                    else -> "group-default"
                }
                group.candidates.forEach { loc ->
                    // Calculate position within the cell
                    // Each cell is 100x100 units, candidates are in a 3x3 grid within
                    val cellX = loc.col * 100
                    val cellY = loc.row * 100
                    // Candidate position within cell (1-9 maps to 3x3 grid)
                    val candCol = (loc.candidate - 1) % 3
                    val candRow = (loc.candidate - 1) / 3
                    val cx = cellX + 20 + candCol * 30
                    val cy = cellY + 20 + candRow * 30
                    append("""<circle class="board-candidate-highlight $colorClass" cx="$cx" cy="$cy" r="12" />""")
                }
            }

            append("</svg>")
        }
        unsafe {
            +svgContent
        }
    }
}

/**
 * Set up global event delegation for interactive hint description elements
 * This uses event delegation so we don't need to re-attach listeners on each render
 */
internal fun SudokuApp.setupChainInteractionDelegation() {
    // Mouseover delegation for all interactive elements
    document.addEventListener("mouseover", { event ->
        val target = (event.target as? HTMLElement) ?: return@addEventListener
        val interactiveRef = target.closest(".interactive-ref") as? HTMLElement
        if (interactiveRef == null || interactiveRef.classList.contains("ref-hovered")) {
            return@addEventListener
        }

        interactiveRef.classList.add("ref-hovered")

        when {
            // Chain cell reference: (3)R5C6
            interactiveRef.classList.contains("chain-cell-ref") -> {
                val cellsAttr = interactiveRef.getAttribute("data-cells") ?: return@addEventListener
                val candidateAttr = interactiveRef.getAttribute("data-candidate") ?: return@addEventListener
                val cells = cellsAttr.split(",").mapNotNull { it.toIntOrNull() }
                val candidate = candidateAttr.toIntOrNull() ?: return@addEventListener
                val hint = currentHintList.getOrNull(selectedHintIndex)
                if (hint != null) {
                    updateChainHighlights(cells, candidate, hint)
                }
            }
            // Simple cell reference: R5C6, R1C8
            interactiveRef.classList.contains("cell-ref") -> {
                val cellsAttr = interactiveRef.getAttribute("data-cells") ?: return@addEventListener
                val cells = cellsAttr.split(",").mapNotNull { it.toIntOrNull() }
                highlightCells(cells)
            }
            // Digit reference: {5, 7, 8} or single digit
            interactiveRef.classList.contains("digit-ref") -> {
                val digitsAttr = interactiveRef.getAttribute("data-digits") ?: return@addEventListener
                val digits = digitsAttr.split(",").mapNotNull { it.toIntOrNull() }
                highlightDigits(digits)
            }
            // House reference: Row 5, Column 7, Box 3
            interactiveRef.classList.contains("house-ref") -> {
                val houseType = interactiveRef.getAttribute("data-house-type") ?: return@addEventListener
                val houseIndex = interactiveRef.getAttribute("data-house-index")?.toIntOrNull() ?: return@addEventListener
                highlightHouse(houseType, houseIndex)
            }
            // Link reference: --[strong]-->
            interactiveRef.classList.contains("chain-link-ref") -> {
                val linkIdx = interactiveRef.getAttribute("data-link-index")?.toIntOrNull() ?: return@addEventListener
                updateLinkHighlight(linkIdx, true)
            }
        }
    })

    // Mouseout delegation
    document.addEventListener("mouseout", { event ->
        val target = (event.target as? HTMLElement) ?: return@addEventListener
        if (!target.classList.contains("interactive-ref") || !target.classList.contains("ref-hovered")) {
            return@addEventListener
        }

        target.classList.remove("ref-hovered")

        when {
            target.classList.contains("chain-cell-ref") -> {
                if (highlightedNodeCell == null) {
                    clearChainHighlights()
                }
            }
            target.classList.contains("cell-ref") -> {
                clearCellHighlights()
            }
            target.classList.contains("digit-ref") -> {
                clearDigitHighlights()
            }
            target.classList.contains("house-ref") -> {
                clearHouseHighlights()
            }
            target.classList.contains("chain-link-ref") -> {
                val linkIdx = target.getAttribute("data-link-index")?.toIntOrNull() ?: return@addEventListener
                if (highlightedLinkIndex == null) {
                    updateLinkHighlight(linkIdx, false)
                }
            }
        }
    })

    // Click delegation for toggle behavior
    document.addEventListener("click", { event ->
        val target = (event.target as? HTMLElement) ?: return@addEventListener
        val interactiveRef = target.closest(".interactive-ref") as? HTMLElement ?: return@addEventListener

        event.stopPropagation()

        when {
            interactiveRef.classList.contains("chain-cell-ref") -> {
                val cellsAttr = interactiveRef.getAttribute("data-cells") ?: return@addEventListener
                val candidateAttr = interactiveRef.getAttribute("data-candidate") ?: return@addEventListener
                val cells = cellsAttr.split(",").mapNotNull { it.toIntOrNull() }
                val candidate = candidateAttr.toIntOrNull() ?: return@addEventListener

                if (highlightedNodeCell == cells.firstOrNull() && highlightedNodeCandidate == candidate) {
                    highlightedNodeCell = null
                    highlightedNodeCandidate = null
                    clearChainHighlights()
                } else {
                    highlightedNodeCell = cells.firstOrNull()
                    highlightedNodeCandidate = candidate
                    val hint = currentHintList.getOrNull(selectedHintIndex)
                    if (hint != null) {
                        updateChainHighlights(cells, candidate, hint)
                    }
                }
            }
            interactiveRef.classList.contains("chain-link-ref") -> {
                val linkIdx = interactiveRef.getAttribute("data-link-index")?.toIntOrNull() ?: return@addEventListener
                if (highlightedLinkIndex == linkIdx) {
                    highlightedLinkIndex = null
                    updateLinkHighlight(linkIdx, false)
                } else {
                    highlightedLinkIndex = linkIdx
                    updateLinkHighlight(linkIdx, true)
                }
            }
        }
    })
}

/**
 * Highlight specific cells on hover
 */
private fun SudokuApp.highlightCells(cellIndices: List<Int>) {
    clearCellHighlights()
    val grid = document.querySelector(".sudoku-grid") ?: return
    val rows = grid.querySelectorAll(".sudoku-row")

    for (cellIndex in cellIndices) {
        val row = cellIndex / 9
        val col = cellIndex % 9
        val rowElement = rows.item(row) ?: continue
        val cellElement = rowElement.childNodes.item(col) as? HTMLElement ?: continue
        cellElement.classList.add("hover-highlight-cell")
    }
}

/**
 * Clear cell highlights
 */
private fun SudokuApp.clearCellHighlights() {
    document.querySelectorAll(".hover-highlight-cell").asList().forEach { element ->
        (element as? HTMLElement)?.classList?.remove("hover-highlight-cell")
    }
}

/**
 * Highlight digits across the grid
 */
private fun SudokuApp.highlightDigits(digits: List<Int>) {
    clearDigitHighlights()
    val grid = document.querySelector(".sudoku-grid") ?: return

    // Highlight all candidates and solved cells with these digits
    for (digit in digits) {
        // Highlight candidates
        grid.querySelectorAll(".candidate").asList().forEach { element ->
            val candidateElement = element as? HTMLElement ?: return@forEach
            val candidateText = candidateElement.textContent?.trim()?.toIntOrNull()
            if (candidateText == digit && !candidateElement.classList.contains("hidden")) {
                candidateElement.classList.add("hover-highlight-digit")
            }
        }
        // Highlight solved cells
        grid.querySelectorAll(".cell-value").asList().forEach { element ->
            val valueElement = element as? HTMLElement ?: return@forEach
            val value = valueElement.textContent?.trim()?.toIntOrNull()
            if (value == digit) {
                valueElement.parentElement?.classList?.add("hover-highlight-digit-cell")
            }
        }
    }
}

/**
 * Clear digit highlights
 */
private fun SudokuApp.clearDigitHighlights() {
    document.querySelectorAll(".hover-highlight-digit").asList().forEach { element ->
        (element as? HTMLElement)?.classList?.remove("hover-highlight-digit")
    }
    document.querySelectorAll(".hover-highlight-digit-cell").asList().forEach { element ->
        (element as? HTMLElement)?.classList?.remove("hover-highlight-digit-cell")
    }
}

/**
 * Highlight a house (row, column, or box)
 */
private fun SudokuApp.highlightHouse(houseType: String, houseIndex: Int) {
    clearHouseHighlights()
    val grid = document.querySelector(".sudoku-grid") ?: return
    val rows = grid.querySelectorAll(".sudoku-row")

    val cellIndices = when (houseType) {
        "row" -> (0..8).map { col -> houseIndex * 9 + col }
        "column" -> (0..8).map { row -> row * 9 + houseIndex }
        "box" -> {
            val boxRow = houseIndex / 3
            val boxCol = houseIndex % 3
            val startRow = boxRow * 3
            val startCol = boxCol * 3
            (0..2).flatMap { r -> (0..2).map { c -> (startRow + r) * 9 + (startCol + c) } }
        }
        else -> emptyList()
    }

    for (cellIndex in cellIndices) {
        val row = cellIndex / 9
        val col = cellIndex % 9
        val rowElement = rows.item(row) ?: continue
        val cellElement = rowElement.childNodes.item(col) as? HTMLElement ?: continue
        cellElement.classList.add("hover-highlight-house")
    }
}

/**
 * Clear house highlights
 */
private fun SudokuApp.clearHouseHighlights() {
    document.querySelectorAll(".hover-highlight-house").asList().forEach { element ->
        (element as? HTMLElement)?.classList?.remove("hover-highlight-house")
    }
}

/**
 * Attach listeners for interactive description elements (uses global delegation)
 */
internal fun SudokuApp.attachDescriptionInteractionListeners(containerId: String, hint: TechniqueMatchInfo) {
    // Event delegation is handled globally in setupChainInteractionDelegation()
    // This function is kept for potential future use
}

/**
 * Legacy alias for attachDescriptionInteractionListeners
 */
private fun SudokuApp.attachChainInteractionListeners(containerId: String, hint: TechniqueMatchInfo) {
    attachDescriptionInteractionListeners(containerId, hint)
}

/**
 * Update visual highlights for cells and candidates
 */
private fun SudokuApp.updateChainHighlights(cells: List<Int>, candidate: Int, hint: TechniqueMatchInfo) {
    // First clear any existing highlights
    clearChainHighlights()

    // Highlight the cells - cells are in rows, so we need to find them properly
    cells.forEach { cellIndex ->
        val row = cellIndex / 9
        val col = cellIndex % 9
        // Selector: .sudoku-grid > .sudoku-row:nth-child(row+1) > .cell:nth-child(col+1)
        val cellElement = document.querySelector(
            ".sudoku-grid > .sudoku-row:nth-child(${row + 1}) > .cell:nth-child(${col + 1})"
        ) as? HTMLElement
        cellElement?.classList?.add("chain-node-highlight")

        // Highlight the specific candidate within the cell
        val candidateElement = cellElement?.querySelector(".candidate:nth-child($candidate)") as? HTMLElement
        candidateElement?.classList?.add("chain-candidate-highlight")
    }

    // Also highlight corresponding SVG circles if they exist
    val svgContainer = document.querySelector(".chain-lines-container svg") as? Element
    svgContainer?.querySelectorAll(".board-candidate-highlight")?.asList()?.forEach { circle ->
        val circleEl = circle as? Element ?: return@forEach
        val cx = circleEl.getAttribute("cx")?.toDoubleOrNull() ?: return@forEach
        val cy = circleEl.getAttribute("cy")?.toDoubleOrNull() ?: return@forEach

        // Check if this circle matches any of our cells and candidate
        cells.forEach { cellIndex ->
            val row = cellIndex / 9
            val col = cellIndex % 9
            val candCol = (candidate - 1) % 3
            val candRow = (candidate - 1) / 3
            val expectedCx = col * 100.0 + 20.0 + candCol * 30.0
            val expectedCy = row * 100.0 + 20.0 + candRow * 30.0

            if (kotlin.math.abs(cx - expectedCx) < 5 && kotlin.math.abs(cy - expectedCy) < 5) {
                circleEl.classList.add("svg-highlight")
            }
        }
    }
}

/**
 * Clear all chain highlights
 */
private fun SudokuApp.clearChainHighlights() {
    document.querySelectorAll(".chain-node-highlight").asList().forEach {
        (it as? Element)?.classList?.remove("chain-node-highlight")
    }
    document.querySelectorAll(".chain-candidate-highlight").asList().forEach {
        (it as? Element)?.classList?.remove("chain-candidate-highlight")
    }
    document.querySelectorAll(".svg-highlight").asList().forEach {
        (it as? Element)?.classList?.remove("svg-highlight")
    }
    document.querySelectorAll(".svg-line-highlight").asList().forEach {
        (it as? Element)?.classList?.remove("svg-line-highlight")
    }
}

/**
 * Update SVG line highlight
 */
private fun SudokuApp.updateLinkHighlight(linkIndex: Int, highlight: Boolean) {
    val svgContainer = document.querySelector(".chain-lines-container svg") as? Element ?: return
    val lines = svgContainer.querySelectorAll(".board-chain-line").asList()

    if (linkIndex < lines.size) {
        val line = lines[linkIndex] as? Element
        if (highlight) {
            line?.classList?.add("svg-line-highlight")
        } else {
            line?.classList?.remove("svg-line-highlight")
        }
    }
}
