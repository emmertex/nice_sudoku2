package service.hint.explanations

import service.hint.helpers.*
import dto.*
import sudoku.match.TechniqueMatch

    /**
     * Build visual lines to show the rectangle outline
     */
    fun buildRectangleLines(urCells: List<Int>, urDigits: List<Int>): List<LineDto> {
        val lines = mutableListOf<LineDto>()
        
        if (urCells.size != 4 || urDigits.isEmpty()) return lines
        
        val digit = urDigits.first()
        
        // Sort cells to form a proper rectangle (by row then column)
        val sorted = urCells.sortedWith(compareBy({ it / 9 }, { it % 9 }))
        
        // Cells should be: top-left, top-right, bottom-left, bottom-right
        // After sorting: [0]=top-left, [1]=top-right, [2]=bottom-left, [3]=bottom-right
        val topLeft = sorted[0]
        val topRight = sorted[1]
        val bottomLeft = sorted[2]
        val bottomRight = sorted[3]
        
        // Draw the rectangle outline
        // Top edge
        lines.add(LineDto(
            from = CandidateLocationDto(topLeft / 9, topLeft % 9, digit),
            to = CandidateLocationDto(topRight / 9, topRight % 9, digit),
            isStrongLink = false,
            lineType = "ur-edge",
            description = "Rectangle edge"
        ))
        // Right edge
        lines.add(LineDto(
            from = CandidateLocationDto(topRight / 9, topRight % 9, digit),
            to = CandidateLocationDto(bottomRight / 9, bottomRight % 9, digit),
            isStrongLink = false,
            lineType = "ur-edge",
            description = "Rectangle edge"
        ))
        // Bottom edge
        lines.add(LineDto(
            from = CandidateLocationDto(bottomRight / 9, bottomRight % 9, digit),
            to = CandidateLocationDto(bottomLeft / 9, bottomLeft % 9, digit),
            isStrongLink = false,
            lineType = "ur-edge",
            description = "Rectangle edge"
        ))
        // Left edge
        lines.add(LineDto(
            from = CandidateLocationDto(bottomLeft / 9, bottomLeft % 9, digit),
            to = CandidateLocationDto(topLeft / 9, topLeft % 9, digit),
            isStrongLink = false,
            lineType = "ur-edge",
            description = "Rectangle edge"
        ))
        
        return lines
    }

    fun generateUniqueRectangleSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        
        // Extract UR data via reflection
        var urCells = listOf<Int>()
        var urDigits = listOf<Int>()
        var type = 0
        
        try {
            val matchClass = match.javaClass
            
            // Try extracting cells
            for (fieldName in listOf("ur", "uRect", "cells", "uniqueRectangle")) {
                 try {
                     val f = matchClass.getDeclaredField(fieldName)
                     f.isAccessible = true
                     val value = f.get(match)
                     if (value is IntArray) { urCells = value.toList(); break }
                     if (value is java.util.BitSet) { urCells = bitSetToList(value); break }
                 } catch (e: NoSuchFieldException) { continue }
            }
            
            // Try extracting digits
            for (fieldName in listOf("digits", "uDigits", "typeDigits")) {
                 try {
                     val f = matchClass.getDeclaredField(fieldName)
                     f.isAccessible = true
                     val value = f.get(match)
                     if (value is IntArray) { urDigits = value.toList().map { it + 1 }; break }
                     if (value is java.util.BitSet) { urDigits = bitSetToList(value).map { it + 1 }; break }
                 } catch (e: NoSuchFieldException) { continue }
            }
            
            // Try extracting type
             try {
                 val f = matchClass.getDeclaredField("type")
                 f.isAccessible = true
                 type = f.get(match) as Int
             } catch (e: Exception) {
                 // Try parsing from name "Unique Rectangle Type 1"
                 val typeRegex = "Type (\\d+)".toRegex()
                 val matchResult = typeRegex.find(techniqueName)
                 if (matchResult != null) {
                     type = matchResult.groupValues[1].toInt()
                 }
             }

        } catch (e: Exception) {
             // Fallback
        }
        
        if (urCells.isEmpty()) urCells = eliminations.flatMap { it.cells }.distinct()
        
        // Build visual elements
        val urCellNames = urCells.map { formatCellName(it) }
        val urDigitText = if (urDigits.size >= 2) "${urDigits[0]} and ${urDigits[1]}" else urDigits.joinToString(", ")
        val rectangleLines = buildRectangleLines(urCells, urDigits)
        
        // Identify cells with extra candidates (the "break" cells)
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()
        val eliminationDigits = eliminations.map { it.digit }.distinct()
        
        // Color the UR cells - floor cells (only UR digits) vs roof cells (extra candidates)
        val urColouredCells = urCells.map { cell ->
            if (cell in eliminationCells) {
                ColouredCellDto(cell, "warning")  // Yellow for cells with extra candidates
            } else {
                ColouredCellDto(cell, "target")   // Green for floor cells
            }
        }
        
        // Build candidate highlighting
        val urCandidates = urCells.flatMap { cell ->
            val r = cell / 9
            val c = cell % 9
            urDigits.map { d -> ColouredCandidateDto(r, c, d, "highlight") }
        }

        // Step 1: Explain the Deadly Pattern concept (ELI5)
        val step1Description = "Look at these four cells forming a rectangle: ${urCellNames.joinToString(", ")}. " +
            "They span exactly two rows, two columns, and two boxes. " +
            "Notice they all contain the candidates $urDigitText. " +
            "\n\nHere's the problem: if these four cells ONLY had $urDigitText as candidates, " +
            "you could swap the digits between the diagonal corners and still have a valid solution! " +
            "That means the puzzle would have TWO solutions - which is not allowed in proper Sudoku. " +
            "This forbidden pattern is called a 'Deadly Pattern' or 'Unique Rectangle'."

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = "Spot the Deadly Pattern",
            description = step1Description,
            highlightCells = urCells,
            colouredCells = urColouredCells,
            colouredCandidates = urCandidates,
            lines = rectangleLines
        ))
        
        // Step 2: Type-specific explanation (ELI5)
        val step2Description = when(type) {
            1 -> {
                val extraCell = eliminationCells.firstOrNull()
                val extraCellName = extraCell?.let { formatCellName(it) } ?: "one corner"
                "This is Type 1 - the simplest case. Three corners of the rectangle have only $urDigitText, " +
                "but $extraCellName has an extra candidate. " +
                "\n\nThink about it: if $extraCellName were just $urDigitText like the others, " +
                "we'd have the deadly pattern. So the extra candidate MUST be the true value of that cell! " +
                "We can safely remove $urDigitText from $extraCellName."
            }
            2 -> {
                val extraDigit = eliminationDigits.firstOrNull()
                "This is Type 2 - two corners share the same extra candidate ($extraDigit). " +
                "These two cells are on opposite corners of the rectangle. " +
                "\n\nSince the deadly pattern can't exist, at least one of these corners must be $extraDigit. " +
                "Any cell that can see BOTH of these corners cannot have $extraDigit."
            }
            3 -> {
                "This is Type 3 - two corners have extra candidates that form a 'naked subset' with nearby cells. " +
                "\n\nThe extra candidates in the UR corners, combined with other cells in the same house, " +
                "force certain eliminations. The deadly pattern is avoided because the subset logic " +
                "guarantees the UR won't complete."
            }
            4 -> {
                "This is Type 4 - one of the UR digits ($urDigitText) is 'locked' into two opposite corners. " +
                "\n\nBecause of a strong link, one digit must go in those two corners. " +
                "This means the OTHER digit cannot be in those corners (or we'd have the deadly pattern). " +
                "Remove the non-locked digit from the locked corners."
            }
            5 -> {
                "This is Type 5 - a diagonal strong link prevents the deadly pattern. " +
                "\n\nTwo opposite corners are strongly linked on one digit, meaning one of them must have it. " +
                "This breaks the symmetry that would allow the deadly pattern."
            }
            6 -> {
                "This is Type 6 - both diagonals have strong links on the same digit. " +
                "\n\nThis double-lock on one digit means it must appear in exactly two opposite corners, " +
                "forcing the other digit out of those corners."
            }
            else -> {
                "To avoid this deadly pattern, we need to ensure it can never complete. " +
                "\n\nSomething must 'break' the rectangle - either an extra candidate that must be true, " +
                "or a strong link that forces one digit into specific corners."
            }
        }
        
        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = if (type > 0) "Understand Type $type" else "Break the Pattern",
            description = step2Description,
            highlightCells = if (eliminationCells.isNotEmpty()) eliminationCells else urCells,
            colouredCells = urColouredCells,
            colouredCandidates = urCandidates + eliminationCandidates(eliminations),
            lines = rectangleLines
        ))
        
        // Step 3: Make the elimination
        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            val eliminatedDigits = eliminationDigits.joinToString(", ")
            
            val step3Description = when(type) {
                1 -> "Remove $eliminatedDigits from $eliminationNames. " +
                    "The extra candidate in this cell must be its true value to prevent the deadly pattern."
                2 -> "Remove $eliminatedDigits from $eliminationNames. " +
                    "These cells see both UR corners that share the extra candidate."
                else -> "Remove $eliminatedDigits from $eliminationNames to ensure the deadly pattern cannot form."
            }
            
            steps.add(ExplanationStepDto(
                stepNumber = 3,
                title = "Make the Elimination",
                description = step3Description,
                highlightCells = eliminationCells,
                colouredCells = urColouredCells + eliminationCells.map { ColouredCellDto(it, "warning") },
                colouredCandidates = urCandidates + eliminationCandidates(eliminations),
                lines = rectangleLines
            ))
        }

        return steps
    }

    /**
     * Build visual lines to show the Empty Rectangle L-shape and connections
     */
    fun buildEmptyRectangleLines(
        erCells: List<Int>,
        conjugateCells: List<Int>,
        digit: Int
    ): List<LineDto> {
        val lines = mutableListOf<LineDto>()
        
        // Draw connections between ER cells to show the L-shape
        if (erCells.size >= 2) {
            for (i in erCells.indices) {
                for (j in i + 1 until erCells.size) {
                    val c1 = erCells[i]
                    val c2 = erCells[j]
                    lines.add(LineDto(
                        from = CandidateLocationDto(c1 / 9, c1 % 9, digit),
                        to = CandidateLocationDto(c2 / 9, c2 % 9, digit),
                        isStrongLink = false,
                        lineType = "er-shape",
                        description = "Empty Rectangle pattern"
                    ))
                }
            }
        }
        
        // Draw strong link between conjugate pair
        if (conjugateCells.size >= 2) {
            val c1 = conjugateCells[0]
            val c2 = conjugateCells[1]
            lines.add(LineDto(
                from = CandidateLocationDto(c1 / 9, c1 % 9, digit),
                to = CandidateLocationDto(c2 / 9, c2 % 9, digit),
                isStrongLink = true,
                lineType = "er-conjugate",
                description = "Strong link (conjugate pair)"
            ))
        }
        
        return lines
    }

    fun generateEmptyRectangleSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        
        // Extract ER data
        var erCells: List<Int> = emptyList()
        var conjugateCells: List<Int> = emptyList()
        var digit = 0
        var boxIndex = -1
        
        try {
            val matchClass = match.javaClass
            
            // Try extracting ER cells
            for (fieldName in listOf("erBS", "startBS", "cells", "erCells")) {
                try {
                    val f = matchClass.getDeclaredField(fieldName)
                    f.isAccessible = true
                    val value = f.get(match)
                    if (value is java.util.BitSet) { erCells = bitSetToList(value); break }
                } catch (e: Exception) { }
            }
            
            // Try extracting conjugate pair
            for (fieldName in listOf("conjugate", "strongLink", "pair")) {
                try {
                    val f = matchClass.getDeclaredField(fieldName)
                    f.isAccessible = true
                    val value = f.get(match)
                    if (value is java.util.BitSet) { conjugateCells = bitSetToList(value); break }
                } catch (e: Exception) { }
            }
            
            // Try digit
            try {
                val f = matchClass.getDeclaredField("digit")
                f.isAccessible = true
                digit = (f.get(match) as Int) + 1
            } catch (e: Exception) {
                digit = eliminations.firstOrNull()?.digit ?: 0
            }
            
            // Try box index
            try {
                val f = matchClass.getDeclaredField("box")
                f.isAccessible = true
                boxIndex = f.get(match) as Int
            } catch (e: Exception) {
                // Infer from erCells
                if (erCells.isNotEmpty()) {
                    val cell = erCells.first()
                    boxIndex = (cell / 27) * 3 + ((cell % 9) / 3)
                }
            }
        } catch (e: Exception) {}
        
        if (digit == 0) digit = eliminations.firstOrNull()?.digit ?: 0
        
        // Build visual elements
        val erLines = buildEmptyRectangleLines(erCells, conjugateCells, digit)
        val eliminationCells = eliminations.flatMap { it.cells }.distinct()
        
        // Build regions
        val regions = if (boxIndex >= 0) {
            listOf(ColouredRegionDto("box", boxIndex, "primary"))
        } else {
            emptyList()
        }
        
        // Color cells
        val erColouredCells = erCells.map { ColouredCellDto(it, "target") }
        val conjugateColouredCells = conjugateCells.map { ColouredCellDto(it, "highlight") }
        
        // Build candidates
        val erCandidates = erCells.map { cell ->
            ColouredCandidateDto(cell / 9, cell % 9, digit, "target")
        }
        val conjugateCandidates = conjugateCells.map { cell ->
            ColouredCandidateDto(cell / 9, cell % 9, digit, "highlight")
        }

        // Step 1: Explain the Empty Rectangle concept (ELI5)
        val boxName = if (boxIndex >= 0) "Box ${boxIndex + 1}" else "the highlighted box"
        val erCellNames = erCells.map { formatCellName(it) }.joinToString(", ")
        
        val step1Description = "Look at $boxName. The candidate $digit only appears in an 'L-shape' or 'bent' pattern - " +
            "it's missing from at least one row AND one column within the box. " +
            "\n\nThis creates an 'Empty Rectangle' - the empty row and column form a cross inside the box, " +
            "and all the $digit candidates are in the corners of this cross. " +
            "The cells with $digit are: $erCellNames."

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = "Find the Empty Rectangle",
            description = step1Description,
            highlightCells = erCells,
            regions = regions,
            colouredCells = erColouredCells,
            colouredCandidates = erCandidates,
            lines = erLines
        ))
        
        // Step 2: Explain the connection to conjugate pair
        val conjugateNames = conjugateCells.map { formatCellName(it) }.joinToString(" and ")
        
        val step2Description = if (conjugateCells.isNotEmpty()) {
            "Now look outside the box. There's a 'strong link' (solid line) on $digit at $conjugateNames. " +
            "A strong link means: if one end is false, the other MUST be true. " +
            "\n\nOne end of this strong link connects to the Empty Rectangle through a shared row or column. " +
            "This creates a chain of logic: if the far end of the strong link is true, it eliminates $digit " +
            "from the intersection point in the box, which then forces $digit to a different position in the ER."
        } else {
            "A strong link outside the box connects to this Empty Rectangle. " +
            "If the external candidate is true, it forces $digit to shift within the box pattern."
        }

        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = "Connect to the Strong Link",
            description = step2Description,
            highlightCells = erCells + conjugateCells,
            regions = regions,
            colouredCells = erColouredCells + conjugateColouredCells,
            colouredCandidates = erCandidates + conjugateCandidates,
            lines = erLines
        ))
        
        // Step 3: Make the elimination
        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            
            val step3Description = "Here's the key insight: the elimination cell ($eliminationNames) can see " +
                "both ends of our chain - it sees one end of the strong link AND it sees where $digit " +
                "would be forced in the Empty Rectangle. " +
                "\n\nNo matter which way the logic goes, $digit will end up in a cell that sees $eliminationNames. " +
                "So $digit cannot be in $eliminationNames. Remove $digit from: $eliminationNames."

            steps.add(ExplanationStepDto(
                stepNumber = 3,
                title = "Make the Elimination",
                description = step3Description,
                highlightCells = eliminationCells,
                regions = regions,
                colouredCells = erColouredCells + conjugateColouredCells + eliminationCells.map { ColouredCellDto(it, "warning") },
                colouredCandidates = erCandidates + conjugateCandidates + eliminationCandidates(eliminations),
                lines = erLines
            ))
        }

        return steps
    }
