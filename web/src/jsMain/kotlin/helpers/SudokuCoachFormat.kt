package helpers.importExport

import domain.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Data structure matching Sudoku Coach format
 */
@Serializable
data class SudokuCoachData(
    val gridSize: Int = 9,
    val colours: List<String> = emptyList(),
    val givenDigits: String,
    val givenColours: String = "",
    val userDigits: String,
    val userColours: String = "",
    val userCellCandidates: String,
    val userCellCandidatesColours: String = ""
)

/**
 * External declarations for JavaScript compression/encoding libraries
 */
@JsModule("pako")
@JsNonModule
external object Pako {
    fun inflate(data: Uint8Array): Uint8Array
    fun deflate(data: Uint8Array): Uint8Array
}

external class Uint8Array(length: Int) {
    val length: Int
    operator fun get(index: Int): Byte
    operator fun set(index: Int, value: Byte)
    
    companion object {
        fun from(array: Array<Byte>): Uint8Array
    }
}

// Sudoku Coach uses a custom base32 alphabet (z-base-32 / human-oriented base-32)
// Alphabet: 0123456789abcdefghijklmnopqrstuv (32 characters)
private const val SUDOKU_COACH_BASE32_ALPHABET = "0123456789abcdefghijklmnopqrstuv"

/**
 * Custom base32 decoder for Sudoku Coach alphabet
 * Based on the alphabet: 0123456789abcdefghijklmnopqrstuv
 */
private fun base32Decode(input: String, variant: String): Uint8Array {
    val cleanInput = input.trimEnd('v').lowercase()
    val alphabet = SUDOKU_COACH_BASE32_ALPHABET
    
    // Build decode map
    val decodeMap = mutableMapOf<Char, Int>()
    for (i in alphabet.indices) {
        decodeMap[alphabet[i]] = i
    }
    
    // Decode base32
    val outputList = mutableListOf<Byte>()
    var bits = 0
    var value = 0
    
    for (char in cleanInput) {
        val digitValue = decodeMap[char] ?: continue
        value = (value shl 5) or digitValue
        bits += 5
        
        if (bits >= 8) {
            bits -= 8
            outputList.add(((value shr bits) and 0xFF).toByte())
            value = value and ((1 shl bits) - 1)
        }
    }
    
    // Convert to Uint8Array using dynamic access
    val result = Uint8Array(outputList.size)
    val resultDynamic = result.asDynamic()
    for (i in outputList.indices) {
        resultDynamic[i] = outputList[i]
    }
    return result
}

/**
 * Custom base32 encoder for Sudoku Coach alphabet
 * Based on the alphabet: 0123456789abcdefghijklmnopqrstuv
 */
private fun base32Encode(input: Uint8Array, variant: String): String {
    val alphabet = SUDOKU_COACH_BASE32_ALPHABET
    val result = StringBuilder()
    
    var bits = 0
    var value = 0
    
    // Use dynamic access for Uint8Array elements
    val inputDynamic = input.asDynamic()
    
    for (i in 0 until input.length) {
        val byte = (inputDynamic[i] as Number).toInt() and 0xFF
        value = (value shl 8) or byte
        bits += 8
        
        while (bits >= 5) {
            bits -= 5
            val index = (value shr bits) and 0x1F
            result.append(alphabet[index])
            value = value and ((1 shl bits) - 1)
        }
    }
    
    // Handle remaining bits
    if (bits > 0) {
        val index = (value shl (5 - bits)) and 0x1F
        result.append(alphabet[index])
    }
    
    return result.toString()
}

/**
 * Utilities for working with Sudoku Coach format
 */
object SudokuCoachFormat {
    
    private const val PREFIX = "SCv7_32_"
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Import from Sudoku Coach format
     * Returns Triple of (values, userEliminations, originalPuzzle) or null if invalid
     */
    fun importFromSudokuCoach(input: String): Triple<IntArray, Array<Set<Int>>, String>? {
        try {
            // Step 1: Remove prefix
            if (!input.startsWith(PREFIX)) return null
            var data = input.removePrefix(PREFIX)
            
            // Step 2: Append 'v' until length is divisible by 8
            while (data.length % 8 != 0) {
                data += 'v'
            }
            
            // Step 3: Base32 decode
            val decoded = base32Decode(data, "RFC4648")
            
            // Step 4: Zlib inflate
            val inflated = Pako.inflate(decoded)
            
            // Step 5: Convert to string
            val jsonString = inflated.toByteArray().decodeToString()
            
            // Step 6: Parse JSON
            val coachData = json.decodeFromString<SudokuCoachData>(jsonString)
            
            // Step 7: Convert to our format
            return convertFromCoachFormat(coachData)
        } catch (e: Exception) {
            console.log("Error importing Sudoku Coach format: ${e.message}")
            return null
        }
    }
    
    /**
     * Export to Sudoku Coach format
     */
    fun exportToSudokuCoach(grid: SudokuGrid, originalPuzzle: String): String? {
        try {
            // Step 1: Convert to Sudoku Coach data structure
            val coachData = convertToCoachFormat(grid, originalPuzzle)
            
            // Step 2: Convert to JSON
            val jsonString = json.encodeToString(coachData)
            console.log("Export JSON length:", jsonString.length)
            console.log("Export JSON (first 200):", jsonString.take(200))
            
            // Step 3: Convert to byte array using dynamic access
            val bytes = jsonString.encodeToByteArray()
            val uint8Array = Uint8Array(bytes.size)
            val uint8Dynamic = uint8Array.asDynamic()
            for (i in bytes.indices) {
                uint8Dynamic[i] = bytes[i]
            }
            console.log("Byte array length:", bytes.size)
            
            // Step 4: Zlib deflate
            val deflated = Pako.deflate(uint8Array)
            console.log("Deflated length:", deflated.length)
            
            // Step 5: Base32 encode
            val encoded = base32Encode(deflated, "RFC4648")
            console.log("Base32 encoded length:", encoded.length)
            console.log("Base32 encoded (first 100):", encoded.take(100))
            
            // Step 6: Add prefix (no trimming needed)
            val result = PREFIX + encoded
            console.log("Final result length:", result.length)
            console.log("Final result (first 100):", result.take(100))
            
            return result
        } catch (e: Exception) {
            console.log("Error exporting Sudoku Coach format: ${e.message}")
            console.log("Stack trace:", e.stackTraceToString())
            return null
        }
    }
    
    /**
     * Convert from Sudoku Coach format to our internal format
     * Returns Triple of (values, userEliminations, originalPuzzle)
     */
    private fun convertFromCoachFormat(data: SudokuCoachData): Triple<IntArray, Array<Set<Int>>, String>? {
        if (data.gridSize != 9) return null
        if (data.givenDigits.length != 81 || data.userDigits.length != 81) return null
        
        val values = IntArray(81)
        val userEliminations = Array<Set<Int>>(81) { emptySet() }
        
        // Parse values: combine given digits and user digits
        for (i in 0 until 81) {
            val given = data.givenDigits[i].digitToIntOrNull() ?: 0
            val user = data.userDigits[i].digitToIntOrNull() ?: 0
            values[i] = if (given != 0) given else user
        }
        
        // Parse user cell candidates (these are CANDIDATES, not eliminations)
        // Format: "digit1-digit2-digit3-..." for each cell
        val candidateParts = data.userCellCandidates.split("-")
        
        if (candidateParts.size == 81) {
            for (i in 0 until 81) {
                val candidateValue = candidateParts[i].toIntOrNull() ?: 0
                
                // Skip if cell has a value
                if (values[i] != 0) continue
                
                // Convert candidate bitfield to set
                // The format uses a bitfield where bit N represents digit N (1-9)
                // Bit 1 = candidate 1, Bit 2 = candidate 2, ..., Bit 9 = candidate 9
                val candidates = mutableSetOf<Int>()
                for (digit in 1..9) {
                    val bitMask = 1 shl digit
                    if (candidateValue and bitMask != 0) {
                        candidates.add(digit)
                    }
                }
                
                // Convert candidates to eliminations
                // eliminations = all digits NOT in candidates
                val eliminations = mutableSetOf<Int>()
                for (digit in 1..9) {
                    if (digit !in candidates) {
                        eliminations.add(digit)
                    }
                }
                
                userEliminations[i] = eliminations
            }
        }
        
        return Triple(values, userEliminations, data.givenDigits)
    }
    
    /**
     * Convert from our internal format to Sudoku Coach format
     */
    private fun convertToCoachFormat(grid: SudokuGrid, originalPuzzle: String): SudokuCoachData {
        val givenDigits = originalPuzzle
        val userDigitsBuilder = StringBuilder(81)
        val candidatesBuilder = mutableListOf<String>()
        
        for (i in 0 until 81) {
            val cell = grid.getCell(i)
            val givenValue = originalPuzzle[i].digitToIntOrNull() ?: 0
            
            // User digits: only non-given values
            if (givenValue == 0 && cell.value != null) {
                userDigitsBuilder.append(cell.value)
            } else {
                userDigitsBuilder.append('0')
            }
            
            // User cell candidates: compute from displayCandidates
            // displayCandidates = natural candidates AND NOT userEliminations
            // Bit N represents digit N (1-9): Bit 1 = digit 1, ..., Bit 9 = digit 9
            if (cell.value == null) {
                val displayCandidates = cell.displayCandidates
                var candidateBits = 0
                for (digit in displayCandidates) {
                    candidateBits = candidateBits or (1 shl digit)
                }
                candidatesBuilder.add(candidateBits.toString())
            } else {
                candidatesBuilder.add("0")
            }
        }
        
        return SudokuCoachData(
            gridSize = 9,
            colours = emptyList(),
            givenDigits = givenDigits,
            givenColours = ",".repeat(80), // 80 commas for 81 cells
            userDigits = userDigitsBuilder.toString(),
            userColours = ",".repeat(80),
            userCellCandidates = candidatesBuilder.joinToString("-"),
            userCellCandidatesColours = ""
        )
    }
}

/**
 * Extension function to convert Uint8Array to ByteArray
 */
private fun Uint8Array.toByteArray(): ByteArray {
    val result = ByteArray(this.length)
    val thisDynamic = this.asDynamic()
    for (i in 0 until this.length) {
        result[i] = (thisDynamic[i] as Number).toByte()
    }
    return result
}

