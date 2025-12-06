package service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TechniqueDescriptionTest {
    private val service = SudokuService()

    @Test
    fun `advanced techniques have descriptions`() {
        assertNotNull(service.describeTechnique("XYZ Wing"))
        assertNotNull(service.describeTechnique("Forcing Chains"))
    }

    @Test
    fun `description coverage matches priority table`() {
        assertEquals(
            emptyList(),
            service.missingDescriptionsForPriority(),
            "Every technique in the priority table should have a description entry"
        )
    }
}




