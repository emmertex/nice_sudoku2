package service.hint.techniques
import sudoku.match.AICMatch
import service.hint.helpers.*
import sudoku.match.TechniqueMatch
import dto.*

    /**
     * Extract lines and groups from AIC (Alternating Inference Chain) matches
     */
    fun extractAICVisualData(match: AICMatch): Triple<List<LineDto>, List<GroupDto>, String?> {
        val chain = match.chain
        val nodes = chain.nodes
        val lines = mutableListOf<LineDto>()
        val groups = mutableListOf<GroupDto>()
        
        // Extract Eureka notation
        val eurekaBuilder = StringBuilder()
        chain.toEurekaString(eurekaBuilder)
        val eurekaNotation = eurekaBuilder.toString()
        
        // Extract groups for multi-cell nodes
        nodes.forEachIndexed { index, node ->
            val groupType = if (index % 2 == 0) "chain-off" else "chain-on"
            
            // Create group for the node's cells
            val candidates = mutableListOf<CandidateLocationDto>()
            var cell = node.cells().nextSetBit(0)
            while (cell >= 0) {
                val row = cell / 9
                val col = cell % 9
                candidates.add(CandidateLocationDto(row, col, node.digit() + 1))
                cell = node.cells().nextSetBit(cell + 1)
            }
            
            if (candidates.isNotEmpty()) {
                groups.add(GroupDto(
                    candidates = candidates,
                    groupType = groupType,
                    colourIndex = index % 2
                ))
            }
            
            // Handle ALS cells if present
            if (node.alsCells() != null) {
                val alsCandidates = mutableListOf<CandidateLocationDto>()
                var alsCell = node.alsCells().nextSetBit(0)
                while (alsCell >= 0) {
                    val row = alsCell / 9
                    val col = alsCell % 9
                    // For ALS, we add all digits in the ALS
                    if (node.alsDigits() != null) {
                        var digit = node.alsDigits().nextSetBit(0)
                        while (digit >= 0) {
                            alsCandidates.add(CandidateLocationDto(row, col, digit + 1))
                            digit = node.alsDigits().nextSetBit(digit + 1)
                        }
                    }
                    alsCell = node.alsCells().nextSetBit(alsCell + 1)
                }
                if (alsCandidates.isNotEmpty()) {
                    groups.add(GroupDto(
                        candidates = alsCandidates,
                        groupType = "als",
                        colourIndex = groups.size
                    ))
                }
            }
        }
        
        // Extract lines between consecutive nodes
        for (i in 1 until nodes.size) {
            val prevNode = nodes[i - 1]
            val currNode = nodes[i]
            
            // Find the closest pair of cells between nodes
            var minDist = Double.MAX_VALUE
            var fromCell = -1
            var toCell = -1
            
            var cellA = prevNode.cells().nextSetBit(0)
            while (cellA >= 0) {
                var cellB = currNode.cells().nextSetBit(0)
                while (cellB >= 0) {
                    val rowA = cellA / 9
                    val colA = cellA % 9
                    val rowB = cellB / 9
                    val colB = cellB % 9
                    val dist = Math.sqrt(((rowA - rowB) * (rowA - rowB) + (colA - colB) * (colA - colB)).toDouble())
                    if (dist < minDist) {
                        minDist = dist
                        fromCell = cellA
                        toCell = cellB
                    }
                    cellB = currNode.cells().nextSetBit(cellB + 1)
                }
                cellA = prevNode.cells().nextSetBit(cellA + 1)
            }
            
            if (fromCell >= 0 && toCell >= 0) {
                val isStrongLink = chain.isFirstLinkStrong xor (i % 2 == 0)
                val curveOffset = if (i % 2 == 0) 0.1 else -0.1
                
                lines.add(LineDto(
                    from = CandidateLocationDto(fromCell / 9, fromCell % 9, prevNode.digit() + 1),
                    to = CandidateLocationDto(toCell / 9, toCell % 9, currNode.digit() + 1),
                    curveX = curveOffset,
                    curveY = 0.5,
                    isStrongLink = isStrongLink
                ))
            }
        }
        
        return Triple(lines, groups, eurekaNotation)
    }
