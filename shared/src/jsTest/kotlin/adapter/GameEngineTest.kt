package adapter

import kotlin.test.*
import kotlinx.browser.window
import kotlinx.coroutines.*
import kotlin.js.Promise

class GameEngineTest {
    @Test fun undoRestoresAutomaticCandidatesAndExactNotes() {
        val engine = GameEngine()
        engine.loadPuzzle("0".repeat(81))
        engine.setUserEliminations(0, setOf(2, 4))
        val before = engine.getCurrentGrid()
        engine.recordAction(engine.createPlacementAction(0, 1, before.getCell(0).displayCandidates))
        engine.setCellValue(0, 1)
        assertTrue(engine.undoLastAction())
        assertEquals(before, engine.getCurrentGrid())
    }

    @Test fun undoPlacementRestoresBlankManualNotes() {
        val engine = GameEngine()
        engine.loadPuzzle("0".repeat(81))
        engine.setUserEliminations(0, (1..9).toSet())
        engine.recordAction(engine.createPlacementAction(0, 3, emptySet()))
        engine.setCellValue(0, 3)
        engine.undoLastAction()
        assertTrue(engine.getCurrentGrid().getCell(0).displayCandidates.isEmpty())
        assertEquals((1..9).toSet(), engine.getCurrentGrid().getCell(1).candidates)
    }

    @Test fun localLoadAndRestorationNeverFetch() {
        val original = window.asDynamic().fetch
        var calls = 0
        window.asDynamic().fetch = { _: dynamic, _: dynamic -> calls++; Promise.reject(Throwable("unexpected")) }
        try {
            val engine = GameEngine()
            engine.loadPuzzle("0".repeat(81))
            engine.setCellValue(0, 1)
            engine.setCellValue(10, 2)
            assertEquals(0, calls)
        } finally { window.asDynamic().fetch = original }
    }

    @Test fun staleSolveCannotOverwriteAnotherPuzzle(): Promise<Unit> = MainScope().promise {
        val original = window.asDynamic().fetch
        var resolve: ((dynamic) -> Unit)? = null
        window.asDynamic().fetch = { _: dynamic, _: dynamic -> Promise<dynamic> { done, _ -> resolve = done } }
        try {
            val engine = GameEngine()
            engine.loadPuzzle("0".repeat(81))
            engine.solve()
            yield()
            engine.loadPuzzle("1" + "0".repeat(80))
            val response = js("({ok:true, status:200})")
            response.text = { Promise.resolve("{\"success\":true,\"hasSolution\":false}") }
            resolve!!(response)
            delay(30)
            assertEquals(1, engine.getCurrentGrid().getCell(0).value)
            assertEquals(1, engine.getCurrentGrid().cells.count { it.isSolved })
        } finally { window.asDynamic().fetch = original }
    }
}
