package service.hint.explanations

import service.hint.helpers.*
import dto.*
import sudoku.match.TechniqueMatch

    fun generateUniqueRectangleSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        
        // Extract UR data via reflection
        // Expected fields: uniqueRectangle (cells), uDigits (digits), type (int)
        var urCells = listOf<Int>()
        var urDigits = listOf<Int>()
        var type = 0
        
        try {
            val matchClass = match.javaClass
            // Fields might be named 'ur', 'uRect', 'cells', 'digits', 'uDigits', 'type'
            
            // Try extracting cells
            // Common implementations use 'ur' or 'cells' for the 4 cells
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
                     if (value is IntArray) { urDigits = value.toList().map { it + 1 }; break } // usually 0-8
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
        
        if (urCells.isEmpty()) urCells = eliminations.flatMap { it.cells }.distinct() // weak fallback

        // Step 1: The Deadly Pattern
        val urDigitText = urDigits.joinToString(" and ")
        val stepsDesc = if (urDigits.isNotEmpty()) {
            "The cells highlighted form a Unique Rectangle with candidates $urDigitText. " +
            "If these cells only contained these two digits in this pattern, the puzzle would have two valid solutions, which is not allowed."
        } else {
             "These cells form a potential 'Deadly Pattern' that would result in multiple solutions."
        }
        
        steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = "Identify the Deadly Pattern",
            description = stepsDesc,
            highlightCells = urCells,
            colouredCandidates = urCells.flatMap { cell ->
                val r = cell / 9
                val c = cell % 9
                urDigits.map { d -> ColouredCandidateDto(r, c, d, "target") }
            }
        ))
        
        // Step 2: The Break (Type specific logic)
        // For Type 1: One cell has an extra candidate.
        // For Type 2: Two cells have an extra candidate (same one).
        
        val breakDesc = when(type) {
            1 -> "Type 1: One of the corner cells (highlighted) has an extra candidate. It MUST be that candidate to avoid the deadly pattern."
            2 -> "Type 2: Two corner cells have an extra common candidate. One of them must be that candidate."
            3 -> "Type 3: Two corner cells have extra candidates that form a subset with neighbours."
            4 -> "Type 4: One digit is locked into two opposite corners, forcing the other digit out."
            else -> "We must eliminate candidates that would force this deadly pattern to exist."
        }
        
        // Identify the "break" cells (where eliminations happen or where the extra candidate is)
        val eliminationCells = eliminations.flatMap { it.cells }
        val breakCells = if (eliminationCells.isNotEmpty()) eliminationCells else urCells
        
        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = "Avoid the Pattern (Type $type)",
            description = breakDesc,
            highlightCells = breakCells,
            colouredCandidates = eliminationCandidates(eliminations) + urCells.flatMap { cell ->
                val r = cell / 9
                val c = cell % 9
                urDigits.map { d -> ColouredCandidateDto(r, c, d, "target") }
            }
        ))
        
        // Step 3: Eliminations
        if (eliminations.isNotEmpty()) {
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(ExplanationStepDto(
                stepNumber = 3,
                title = "Apply Eliminations",
                description = eliminationDesc ?: "Eliminate the candidates that would cause the deadly pattern.",
                highlightCells = eliminationCells,
                colouredCandidates = eliminationCandidates(eliminations)
            ))
        }

        return steps
    }

    fun generateEmptyRectangleSteps(
        techniqueName: String,
        match: TechniqueMatch,
        eliminations: List<EliminationDto>
    ): List<ExplanationStepDto> {
        val steps = mutableListOf<ExplanationStepDto>()
        
        // ER: Looking for a box with candidate restricted to 'L' shape (or just not full),
        // and a conjugate pair in a row/col that intersects it.
        
        var digits: List<Int> = emptyList()
        var cells: List<Int> = emptyList() // The ER cells
        
         try {
            val matchClass = match.javaClass
             // Try extracting "erBS" (Empty Rectangle BitSet) or similar
             for (fieldName in listOf("erBS", "startBS", "cells")) {
                 try {
                     val f = matchClass.getDeclaredField(fieldName)
                     f.isAccessible = true
                     val value = f.get(match)
                     if (value is java.util.BitSet) { cells = bitSetToList(value); break }
                 } catch (e: Exception) { }
             }
             // Try digit
             try {
                val f = matchClass.getDeclaredField("digit")
                f.isAccessible = true
                val d = f.get(match) as Int
                digits = listOf(d + 1)
             } catch (e: Exception) {
                 digits = eliminations.map { it.digit }.distinct()
             }
         } catch (e: Exception) {}
         
         val digit = digits.firstOrNull() ?: 0
         
         // Step 1: The Empty Rectangle
         steps.add(ExplanationStepDto(
            stepNumber = 1,
            title = "Identify the Empty Rectangle",
            description = "In the highlighted box, the candidate $digit is restricted to a specific pattern (usually forming an 'L' shape or missing from some rows/cols).",
            highlightCells = cells,
            colouredCandidates = cells.map { cell ->
                val r = cell / 9
                val c = cell % 9
                ColouredCandidateDto(r, c, digit, "target")
            }
         ))
         
         // Step 2: Connection
         steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = "Connect to Conjugate Pair",
            description = "A strong link (conjugate pair) in a row or column connects to this box. " +
                          "If the external candidate is true, it eliminates the intersection in the box, forcing $digit elsewhere in the box.",
            highlightCells = cells + eliminations.flatMap { it.cells },
             colouredCandidates = cells.map { cell ->
                val r = cell / 9
                val c = cell % 9
                ColouredCandidateDto(r, c, digit, "target")
            }
         ))
         
         // Step 3: Elimination
         if (eliminations.isNotEmpty()) {
            val eliminationDesc = summarizeEliminations(eliminations)
            steps.add(
                ExplanationStepDto(
                    stepNumber = 3,
                    title = "The Intersection Elimination",
                    description = eliminationDesc ?: "The intersection of the Empty Rectangle and the Conjugate Pair sees both possibilities, so it cannot be $digit.",
                    highlightCells = eliminations.flatMap { it.cells },
                    colouredCandidates = eliminationCandidates(eliminations)
                )
            )
        }

        return steps
    }
