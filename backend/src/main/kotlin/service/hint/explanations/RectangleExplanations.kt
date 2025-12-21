package service.hint.explanations

import service.hint.helpers.*
import service.hint.helpers.LanguageKeyBuilder.hintKey
import service.hint.helpers.LanguageKeyBuilder.commonKey
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

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = hintKey("unique_rectangle", 1, "title"),
            description = hintKey("unique_rectangle", 1, "description",
                "cells" to urCellNames.joinToString(", "),
                "digits" to urDigitText
            ),
            highlightCells = urCells,
            colouredCells = urColouredCells,
            colouredCandidates = urCandidates,
            lines = rectangleLines
        ))
        
        val typeKey = if (type > 0) "unique_rectangle_type$type" else "unique_rectangle_generic"
        
        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = hintKey(typeKey, 1, "title",
                "type" to type.toString()
            ),
            description = hintKey(typeKey, 1, "description",
                "digits" to urDigitText,
                "extraCell" to (eliminationCells.firstOrNull()?.let { formatCellName(it) } ?: "one corner"),
                "extraDigit" to (eliminationDigits.firstOrNull()?.toString() ?: "")
            ),
            highlightCells = if (eliminationCells.isNotEmpty()) eliminationCells else urCells,
            colouredCells = urColouredCells,
            colouredCandidates = urCandidates + eliminationCandidates(eliminations),
            lines = rectangleLines
        ))
        
        // Step 3: Make the elimination
        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")
            val eliminatedDigits = eliminationDigits.joinToString(", ")
            
            steps.add(ExplanationStepDto(
                stepNumber = 3,
                title = hintKey("unique_rectangle", 3, "title"),
                description = hintKey("unique_rectangle_elim_type$type", 1, "description",
                    "digits" to eliminatedDigits,
                    "cells" to eliminationNames
                ),
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

        val boxName = if (boxIndex >= 0) "Box ${boxIndex + 1}" else "the highlighted box"
        val erCellNames = erCells.map { formatCellName(it) }.joinToString(", ")

        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = hintKey("empty_rectangle", 1, "title"),
            description = hintKey("empty_rectangle", 1, "description",
                "box" to boxName,
                "digit" to digit.toString(),
                "cells" to erCellNames
            ),
            highlightCells = erCells,
            regions = regions,
            colouredCells = erColouredCells,
            colouredCandidates = erCandidates,
            lines = erLines
        ))
        
        val conjugateNames = conjugateCells.map { formatCellName(it) }.joinToString(" and ")

        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = hintKey("empty_rectangle", 2, "title"),
            description = hintKey("empty_rectangle", 2, "description",
                "digit" to digit.toString(),
                "conjugateCells" to conjugateNames,
                "hasConjugate" to conjugateCells.isNotEmpty().toString()
            ),
            highlightCells = erCells + conjugateCells,
            regions = regions,
            colouredCells = erColouredCells + conjugateColouredCells,
            colouredCandidates = erCandidates + conjugateCandidates,
            lines = erLines
        ))
        
        // Step 3: Make the elimination
        if (eliminations.isNotEmpty()) {
            val eliminationNames = eliminationCells.map { formatCellName(it) }.joinToString(", ")

            steps.add(ExplanationStepDto(
                stepNumber = 3,
                title = hintKey("empty_rectangle", 3, "title"),
                description = hintKey("empty_rectangle", 3, "description",
                    "cells" to eliminationNames,
                    "digit" to digit.toString()
                ),
                highlightCells = eliminationCells,
                regions = regions,
                colouredCells = erColouredCells + conjugateColouredCells + eliminationCells.map { ColouredCellDto(it, "warning") },
                colouredCandidates = erCandidates + conjugateCandidates + eliminationCandidates(eliminations),
                lines = erLines
            ))
        }

        return steps
    }
