package view

import SudokuApp
import adapter.ExplanationStepDto
import adapter.TechniqueMatchInfo
import htmlEscape
import kotlinx.html.*
import kotlinx.html.dom.append
import kotlinx.html.js.onClickFunction
import org.w3c.dom.HTMLElement
import i18n.HintStringInterpolation

internal fun TagConsumer<HTMLElement>.renderLandscapeHintSidebar(app: SudokuApp,
    hints: List<TechniqueMatchInfo>,
    selectedHint: TechniqueMatchInfo?
) {
    div("hint-sidebar") {
        // Show either the explanation view OR the list view (not both)
        if (app.showExplanation && selectedHint != null) {
            // Explanation view - replaces the entire list
            // Collapse any expanded hint when showing explanation
            app.expandedHintIndex = null
            renderExplanationView(app, selectedHint, hints.size)
        } else {
            // List view
            div("hint-sidebar-header") {
                h3 { +"Available Hints" }
                span("hint-count") { +"(${hints.size})" }
            }

            if (hints.isEmpty()) {
                div("hint-empty") {
                    if (app.isLoadingHints) {
                        p { +"🔄 Searching for hints..." }
                        p("hint-loading-subtitle") { +"Backend is processing..." }
                    } else {
                        p { +"No hints available" }
                        p("hint-loading-subtitle") { +"Try solving more cells first" }
                    }
                }
            } else {
                div("hint-list") {
                    hints.forEachIndexed { index, hint ->
                        val isSelected = index == app.selectedHintIndex
                        val isExpanded = app.expandedHintIndex == index
                        div("hint-item ${if (isSelected) "selected" else ""} ${if (isExpanded) "expanded" else ""}") {
                            // Header row (always visible) - title only
                            div("hint-item-header") {
                                onClickFunction = { e ->
                                    // Select this hint
                                    app.selectedHintIndex = index
                                    app.explanationStepIndex = 0
                                    // Toggle expansion: if clicking the same one, collapse it; otherwise expand this one
                                    app.expandedHintIndex = if (isExpanded) null else index
                                    app.render()
                                }
                                div("hint-item-content") {
                                    div("hint-technique") { +hint.techniqueName }
                                    // Description is hidden in landscape mode - only show when expanded
                                }
                            }
                            // Expanded content: eurekaNotation and Explain button
                            if (isExpanded) {
                                div("hint-item-expanded") {
                                    // Show eurekaNotation if available
                                    val eureka = hint.eurekaNotation
                                    if (eureka != null) {
                                        div("inline-eureka") {
                                            span("eureka-label") { +"Eureka: " }
                                            span("eureka-notation") { +eureka }
                                        }
                                    }
                                    // Explain button
                                    button(classes = "hint-explain-btn") {
                                        +"📖 Explain"
                                        onClickFunction = { e ->
                                            e.stopPropagation()
                                            app.showExplanation = true
                                            app.explanationStepIndex = 0
                                            app.render()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Close button
            button(classes = "hint-close-btn") {
                +"✕ Close"
                onClickFunction = {
                    app.showHints = false
                    app.expandedHintIndex = null
                    app.render()
                }
            }
        }
    }
}

internal fun TagConsumer<HTMLElement>.renderPortraitHintCard(app: SudokuApp,
    hints: List<TechniqueMatchInfo>,
    selectedHint: TechniqueMatchInfo?
) {
    div("hint-card") {
        // Compact header: [Prev] [Title or Position] [Next] [Collapse/Close]
        div("hint-card-header-compact") {
            button(classes = "hint-nav-btn-small ${if (app.selectedHintIndex <= 0) "disabled" else ""}") {
                +"◀ Prev"
                onClickFunction = {
                    if (app.selectedHintIndex > 0) {
                        app.selectedHintIndex--
                        app.explanationStepIndex = 0
                        app.render()
                    }
                }
            }

            // Title area: show technique name, or position if no hint selected
            div("hint-title-area") {
                if (selectedHint != null) {
                    span("hint-technique-compact") { +selectedHint.techniqueName }
                    span("hint-position-small") { +"${app.selectedHintIndex + 1}/${hints.size}" }
                } else {
                    span("hint-position") { +"${app.selectedHintIndex + 1} / ${hints.size}" }
                }
            }

            button(classes = "hint-nav-btn-small ${if (app.selectedHintIndex >= hints.size - 1) "disabled" else ""}") {
                +"Next ▶"
                onClickFunction = {
                    if (app.selectedHintIndex < hints.size - 1) {
                        app.selectedHintIndex++
                        app.explanationStepIndex = 0
                        app.render()
                    }
                }
            }

            // Close button

            button(classes = "hint-close-btn-small") {
                +"✕"
                onClickFunction = {
                    app.showHints = false
                    app.render()
                }
            }
        }

        if (selectedHint != null) {
            // Show explanation with step navigation
            renderInlineExplanationCompact(app, selectedHint)
        } else {
            div("hint-content hint-empty") {
                if (app.isLoadingHints) {
                    p { +"🔄 Searching for hints..." }
                    p("hint-loading-subtitle") { +"Backend is processing..." }
                } else {
                    p { +"No hints available" }
                    p("hint-loading-subtitle") { +"Try solving more cells first" }
                }
            }
        }
    }
}

/**
 * Render compact inline explanation (collapse button is in header)
 */
internal fun TagConsumer<HTMLElement>.renderInlineExplanationCompact(app: SudokuApp, hint: TechniqueMatchInfo) {
    val steps = if (hint.explanationSteps.isNotEmpty()) {
        hint.explanationSteps
    } else {
        app.generateFallbackExplanationSteps(hint)
    }
    val currentStep = steps.getOrNull(app.explanationStepIndex)

    div("inline-explanation-compact") {
        // Eureka notation if available (for chains)
        val eureka = hint.eurekaNotation
        if (eureka != null) {
            div("inline-eureka") {
                span("eureka-label") { +"Eureka: " }
                span("eureka-notation") { +eureka }
            }
        }

        // Step content
        if (currentStep != null) {
            div("inline-step") {
                div("step-header-compact") {
                    // Step navigation buttons (only show if more than one step)
                    if (steps.size > 1) {
                        button(classes = "step-nav-btn-small ${if (app.explanationStepIndex <= 0) "disabled" else ""}") {
                            +"◀"
                            onClickFunction = { e ->
                                e.stopPropagation()
                                if (app.explanationStepIndex > 0) {
                                    app.explanationStepIndex--
                                    app.render()
                                }
                            }
                        }
                    }

                    span("step-badge") { +"Step ${currentStep.stepNumber}" }
                    val interpolatedTitle = HintStringInterpolation.interpolate(currentStep.title)
                    span("step-title") { +interpolatedTitle }

                    // Step navigation buttons (only show if more than one step)
                    if (steps.size > 1) {
                        button(classes = "step-nav-btn-small ${if (app.explanationStepIndex >= steps.size - 1) "disabled" else ""}") {
                            +"▶"
                            onClickFunction = { e ->
                                e.stopPropagation()
                                if (app.explanationStepIndex < steps.size - 1) {
                                    app.explanationStepIndex++
                                    app.render()
                                }
                            }
                        }
                    }
                }
                div("step-description") {
                    val interpolatedDesc = HintStringInterpolation.interpolate(currentStep.description)
                    renderInteractiveDescription(app, interpolatedDesc, hint)
                }
            }
        } else {
            div("inline-step") {
                div("step-description") {
                    val interpolatedDesc = HintStringInterpolation.interpolate(hint.description)
                    renderInteractiveDescription(app, interpolatedDesc, hint)
                }
            }
        }
    }
}

/**
 * Render full explanation view (replaces hint list in landscape sidebar)
 */
internal fun TagConsumer<HTMLElement>.renderExplanationView(app: SudokuApp, hint: TechniqueMatchInfo, totalHints: Int) {
    // Use backend steps if available, otherwise generate fallback
    val steps = if (hint.explanationSteps.isNotEmpty()) {
        hint.explanationSteps
    } else {
        app.generateFallbackExplanationSteps(hint)
    }
    val currentStep = steps.getOrNull(app.explanationStepIndex)

    div("explanation-view") {
        // Header with back button
        div("explanation-view-header") {
            button(classes = "explanation-back-btn") {
                +"← Back to List"
                onClickFunction = {
                    app.showExplanation = false
                    app.explanationStepIndex = 0
                    app.render()
                }
            }
            span("hint-position-badge") { +"${app.selectedHintIndex + 1}/$totalHints" }
        }

        // Technique info
        div("explanation-technique-info") {
            div("explanation-technique-name") { +hint.techniqueName }
            val interpolatedDesc = HintStringInterpolation.interpolate(hint.description)
            div("explanation-technique-desc") { +interpolatedDesc }
        }

        // Eureka notation if available (for chains)
        val eureka = hint.eurekaNotation
        if (eureka != null) {
            div("explanation-eureka") {
                span("eureka-label") { +"Eureka: " }
                span("eureka-notation") { +eureka }
            }
        }

        // Step content
        div("explanation-step-content") {
            if (currentStep != null) {
                div("step-header") {
                    span("step-number") { +"Step ${currentStep.stepNumber}" }
                    val interpolatedTitle = HintStringInterpolation.interpolate(currentStep.title)
                    span("step-title") { +interpolatedTitle }
                }
                div("step-description") {
                    val interpolatedDesc = HintStringInterpolation.interpolate(currentStep.description)
                    renderInteractiveDescription(app, interpolatedDesc, hint)
                }
            } else {
                div("step-description") {
                    val interpolatedDesc = HintStringInterpolation.interpolate(hint.description)
                    renderInteractiveDescription(app, interpolatedDesc, hint)
                }
            }
        }

        // Navigation (only show if more than one step)
            if (steps.size > 1) {
            div("explanation-nav") {
                    button(classes = "explanation-nav-btn ${if (app.explanationStepIndex <= 0) "disabled" else ""}") {
                    +"◀ Prev"
                    onClickFunction = {
                            if (app.explanationStepIndex > 0) {
                                app.explanationStepIndex--
                                app.render()
                        }
                    }
                }
                    span("step-indicator") { +"Step ${app.explanationStepIndex + 1} / ${steps.size}" }
                    button(classes = "explanation-nav-btn ${if (app.explanationStepIndex >= steps.size - 1) "disabled" else ""}") {
                    +"Next ▶"
                    onClickFunction = {
                            if (app.explanationStepIndex < steps.size - 1) {
                                app.explanationStepIndex++
                                app.render()
                        }
                    }
                }
            }
        }

        // Close button at bottom
        button(classes = "hint-close-btn") {
            +"✕ Close Hints"
            onClickFunction = {
                    app.showHints = false
                    app.showExplanation = false
                    app.explanationStepIndex = 0
                    app.render()
            }
        }
    }
}

/**
 * Render inline explanation content (used in both landscape sidebar and portrait card)
 */
internal fun TagConsumer<HTMLElement>.renderInlineExplanation(app: SudokuApp, hint: TechniqueMatchInfo) {
    // Use backend steps if available, otherwise generate fallback
    val steps = if (hint.explanationSteps.isNotEmpty()) {
        hint.explanationSteps
    } else {
        app.generateFallbackExplanationSteps(hint)
    }
    val currentStep = steps.getOrNull(app.explanationStepIndex)

    div("inline-explanation") {
        // Collapse button
        div("explanation-collapse-row") {
            button(classes = "explanation-collapse-btn") {
                +"▲ Collapse"
                onClickFunction = { e ->
                    e.stopPropagation()
                    app.showExplanation = false
                    app.explanationStepIndex = 0
                    app.render()
                }
            }
        }

        // Eureka notation if available (for chains)
        val eureka = hint.eurekaNotation
        if (eureka != null) {
            div("inline-eureka") {
                span("eureka-label") { +"Eureka: " }
                span("eureka-notation") { +eureka }
            }
        }

        // Step content
        if (currentStep != null) {
            div("inline-step") {
                div("step-header") {
                    span("step-number") { +"Step ${currentStep.stepNumber}" }
                    val interpolatedTitle = HintStringInterpolation.interpolate(currentStep.title)
                    span("step-title") { +interpolatedTitle }
                }
                div("step-description") {
                    val interpolatedDesc = HintStringInterpolation.interpolate(currentStep.description)
                    renderInteractiveDescription(app, interpolatedDesc, hint)
                }
            }
        } else {
            // Fallback if no steps at all
            div("inline-step") {
                div("step-description") {
                    val interpolatedDesc = HintStringInterpolation.interpolate(hint.description)
                    renderInteractiveDescription(app, interpolatedDesc, hint)
                }
            }
        }

        // Navigation (only show if more than one step)
        if (steps.size > 1) {
            div("inline-nav") {
                button(classes = "inline-nav-btn ${if (app.explanationStepIndex <= 0) "disabled" else ""}") {
                    +"◀ Prev"
                    onClickFunction = { e ->
                        e.stopPropagation()
                        if (app.explanationStepIndex > 0) {
                            app.explanationStepIndex--
                            app.render()
                        }
                    }
                }
                span("step-indicator") { +"${app.explanationStepIndex + 1} / ${steps.size}" }
                button(classes = "inline-nav-btn ${if (app.explanationStepIndex >= steps.size - 1) "disabled" else ""}") {
                    +"Next ▶"
                    onClickFunction = { e ->
                        e.stopPropagation()
                        if (app.explanationStepIndex < steps.size - 1) {
                            app.explanationStepIndex++
                            app.render()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Generate fallback explanation steps if backend didn't provide any
 */
internal fun SudokuApp.generateFallbackExplanationSteps(hint: TechniqueMatchInfo): List<ExplanationStepDto> {
    val steps = mutableListOf<ExplanationStepDto>()

    // Step 1: Overview
    steps.add(ExplanationStepDto(
        stepNumber = 1,
        title = "{{hints.common.overview}}",
        description = hint.description,
        highlightCells = hint.highlightCells
    ))

    // Step 2: Eliminations (if any)
    if (hint.eliminations.isNotEmpty()) {
        val elimDesc = hint.eliminations.joinToString("; ") { elim ->
            val cells = elim.cells.map { "R${it/9 + 1}C${it%9 + 1}" }.joinToString(", ")
            "{{hints.common.remove|digit=${elim.digit}|cells=$cells}}"
        }
        steps.add(ExplanationStepDto(
            stepNumber = 2,
            title = "{{hints.common.eliminations}}",
            description = elimDesc,
            highlightCells = hint.eliminations.flatMap { it.cells }
        ))
    }

    // Step 3: Solutions (if any)
    if (hint.solvedCells.isNotEmpty()) {
        val solvedDesc = hint.solvedCells.joinToString("; ") { solved ->
            val cell = "R${solved.cell/9 + 1}C${solved.cell%9 + 1}"
            "{{hints.common.place|digit=${solved.digit}|cell=$cell}}"
        }
        steps.add(ExplanationStepDto(
            stepNumber = steps.size + 1,
            title = "{{hints.common.solution}}",
            description = solvedDesc,
            highlightCells = hint.solvedCells.map { it.cell }
        ))
    }

    return steps
}
