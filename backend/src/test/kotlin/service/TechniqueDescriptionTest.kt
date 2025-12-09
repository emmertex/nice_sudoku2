package service


import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import service.hint.metadata.describeTechnique
import service.hint.metadata.missingDescriptionsForPriority


class TechniqueDescriptionTest {

    @Test
    fun `advanced techniques have descriptions`() {
        assertNotNull(describeTechnique("XYZ Wing"))
        assertNotNull(describeTechnique("Forcing Chains"))
    }

    @Test
    fun `description coverage matches priority table`() {
        assertEquals(
            emptyList<String>(),
            missingDescriptionsForPriority()
        )
    }
}





