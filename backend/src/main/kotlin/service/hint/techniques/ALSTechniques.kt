package service.hint.techniques
import sudoku.match.ALSMatch
import service.hint.helpers.*
import sudoku.match.TechniqueMatch
import dto.*
import dto.*

    /**
     * Extract lines and groups from ALS (Almost Locked Set) matches
     */
    fun extractALSVisualData(match: ALSMatch): Triple<List<LineDto>, List<GroupDto>, String?> {
        val chain = match.getChain()
        val nodes = chain.getNodes()
        val lines = mutableListOf<LineDto>()
        val groups = mutableListOf<GroupDto>()
        
        // Extract Eureka notation
        val eurekaBuilder = StringBuilder()
        chain.toEurekaALSString(eurekaBuilder)
        val eurekaNotation = eurekaBuilder.toString()
        
        // Process each collective (node) in the ALS chain
        var colourIndex = 0
        for (collective in nodes) {
            // Add ALS groups
            val alsList = collective.alsList()
            for (als in alsList) {
                val candidates = mutableListOf<CandidateLocationDto>()
                var cell = als.alsAllCells.nextSetBit(0)
                while (cell >= 0) {
                    val row = cell / 9
                    val col = cell % 9
                    // Add each digit in the ALS
                    var digit = als.alsDigits.nextSetBit(0)
                    while (digit >= 0) {
                        candidates.add(CandidateLocationDto(row, col, digit + 1))
                        digit = als.alsDigits.nextSetBit(digit + 1)
                    }
                    cell = als.alsAllCells.nextSetBit(cell + 1)
                }
                if (candidates.isNotEmpty()) {
                    groups.add(GroupDto(
                        candidates = candidates,
                        groupType = "als",
                        colourIndex = colourIndex
                    ))
                    colourIndex++
                }
            }
            
            // Add RCC (Restricted Common Candidate) links
            val startRCCs = collective.StartRCCnode()
            val linkRCCs = collective.LinkRCCnodes()
            
            // Process start RCCs
            for (rcc in startRCCs) {
                val rccCandidates = mutableListOf<CandidateLocationDto>()
                var cell = rcc.rccCells.nextSetBit(0)
                while (cell >= 0) {
                    val row = cell / 9
                    val col = cell % 9
                    rccCandidates.add(CandidateLocationDto(row, col, rcc.rccDigit + 1))
                    cell = rcc.rccCells.nextSetBit(cell + 1)
                }
                if (rccCandidates.size >= 2) {
                    // Draw lines between RCC cells
                    for (i in 0 until rccCandidates.size - 1) {
                        lines.add(LineDto(
                            from = rccCandidates[i],
                            to = rccCandidates[i + 1],
                            isStrongLink = true,
                            lineType = "rcc"
                        ))
                    }
                }
            }
            
            // Process link RCCs
            for (rcc in linkRCCs) {
                val rccCandidates = mutableListOf<CandidateLocationDto>()
                var cell = rcc.rccCells.nextSetBit(0)
                while (cell >= 0) {
                    val row = cell / 9
                    val col = cell % 9
                    rccCandidates.add(CandidateLocationDto(row, col, rcc.rccDigit + 1))
                    cell = rcc.rccCells.nextSetBit(cell + 1)
                }
                if (rccCandidates.size >= 2) {
                    // Draw lines between RCC cells
                    for (i in 0 until rccCandidates.size - 1) {
                        lines.add(LineDto(
                            from = rccCandidates[i],
                            to = rccCandidates[i + 1],
                            isStrongLink = false,
                            lineType = "rcc-link"
                        ))
                    }
                }
            }
        }
        
        return Triple(lines, groups, eurekaNotation)
    }
