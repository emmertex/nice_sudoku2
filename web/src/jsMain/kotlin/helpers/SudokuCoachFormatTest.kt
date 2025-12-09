package helpers.importExport

import kotlinx.serialization.json.Json

/**
 * Test function for Sudoku Coach format import/export
 * This can be called from the browser console to verify the implementation
 */
fun testSudokuCoachImport() {
    console.log("=== Testing Sudoku Coach Import ===")
    
    val testInput = "SCv7_32_f2eaqkebdq1j047s2ufliqfso1feeinvk33ql1tk41i0i98i14ur9qlveu6q2a09nlqlehlrpim7dhnuabuphmn7ujcqigtcqjtnie2fsd2hqnmkrco7bqlla9uvlq2e9ukkulvplmg02m600c33ak5q6n6dkh9p7b062pgai33h002uu3d87oc4lub0rjdqb5avu75ic6t79or6s4v3c0qpkn5p53f8mfm0l9gvf0rt6uipm3jg1eulj9vr0s2br783mhqd6s6crsekkrr7hrfibq79sg4e94tke88od6h62ok3bhsp0ttps867gps84d9m1ds0uei3o55huk17n2gm08plic4lr7g761ei6cem9e7khskpftubcbie7i248eqli9vas2khd7dmpppapo2pumm583482c8u1ct488nt5irnpreknm6u81uvu0401afp6"
    
    try {
        console.log("Testing with custom Kotlin base32 decoder")
        console.log("Alphabet: 0123456789abcdefghijklmnopqrstuv")
        console.log("Input:", testInput.take(80) + "...")
        
        // Test the full import
        val result = SudokuCoachFormat.importFromSudokuCoach(testInput)
        if (result != null) {
            val (values, userEliminations, originalPuzzle) = result
            console.log("✓ Import successful!")
            console.log("Original puzzle:", originalPuzzle)
            console.log("Combined values:", values.joinToString(""))
            
            var totalEliminations = 0
            for (i in 0 until 81) {
                totalEliminations += userEliminations[i].size
            }
            console.log("Total user eliminations:", totalEliminations)
            
            // Verify expected values
            val expectedGiven = "010003006002050000030000000000200300000900017040006000021000070083000009560804200"
            val expectedUser = "000000000000000003000000000100040060256038400300010000000300000000000000000000031"
            
            console.log("\nVerification:")
            console.log("Expected given: ", expectedGiven)
            console.log("Actual puzzle:  ", originalPuzzle)
            console.log("Match: ", originalPuzzle == expectedGiven)
            
            // Build expected combined values
            val expectedValues = StringBuilder()
            for (i in 0 until 81) {
                val given = expectedGiven[i].digitToIntOrNull() ?: 0
                val user = expectedUser[i].digitToIntOrNull() ?: 0
                expectedValues.append(if (given != 0) given else user)
            }
            console.log("Expected values:", expectedValues.toString())
            console.log("Actual values:  ", values.joinToString(""))
            console.log("Values match:   ", values.joinToString("") == expectedValues.toString())
        } else {
            console.log("❌ Import returned null")
        }
        
    } catch (e: Exception) {
        console.log("❌ Error:", e.message)
        console.log("Stack trace:", e.stackTraceToString())
    }
}

/**
 * Make the test function available globally
 */
@JsExport
fun testSudokuCoachFormatGlobal() = testSudokuCoachImport()
