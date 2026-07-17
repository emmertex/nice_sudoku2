package view

import kotlinx.browser.document
import kotlin.js.asDynamic

enum class Theme {
    DARK,        // Pure dark theme
    BLUE,        // Blue gradient theme
    LIGHT,       // Light theme
    EPAPER       // High contrast ePaper theme
}

data class ThemeColours(
    // Core backgrounds
    val bgPrimary: Triple<Int, Int, Int>,
    val bgSecondary: Triple<Int, Int, Int>,
    val bgTertiary: Triple<Int, Int, Int>,
    val desat: Triple<Int, Int, Int>,

    // Core accents
    val accentPrimary: Triple<Int, Int, Int>,
    val accentSecondary: Triple<Int, Int, Int>,
    val accentTertiary: Triple<Int, Int, Int>,
    val accentPrimaryText: Triple<Int, Int, Int>,

    // Core text
    val textPrimary: Triple<Int, Int, Int>,
    val textSecondary: Triple<Int, Int, Int>,
    val textTertiary: Triple<Int, Int, Int>,

    // Grid status colours
    val gridYes: Triple<Int, Int, Int>,
    val gridNeutral: Triple<Int, Int, Int>,
    val gridNo: Triple<Int, Int, Int>,

    // Explicit on-* text tokens for accents (WCAG-oriented)
    val onSuccess: Triple<Int, Int, Int>,
    val onInfo: Triple<Int, Int, Int>,
    val onWarning: Triple<Int, Int, Int>,
    val onError: Triple<Int, Int, Int>,

    val btnOpacity: Double,
    val btnHoverOpacity: Double
)

private val WHITE = Triple(255, 255, 255)
private val BLACK = Triple(0, 0, 0)

val THEME_COLOURS = mapOf(
    Theme.DARK to ThemeColours(
        bgPrimary = Triple(5, 5, 5),
        bgSecondary = Triple(26, 26, 26),
        bgTertiary = Triple(100, 100, 100),
        accentPrimary = Triple(100, 181, 246),
        accentSecondary = Triple(255, 82, 82),
        accentTertiary = Triple(255, 193, 7),
        accentPrimaryText = BLACK,
        textPrimary = WHITE,
        textSecondary = Triple(204, 204, 204),
        textTertiary = Triple(136, 136, 136),
        gridYes = Triple(96, 255, 96),
        gridNeutral = Triple(64, 64, 64),
        gridNo = Triple(255, 96, 96),
        desat = WHITE,
        onSuccess = BLACK,
        onInfo = BLACK,
        onWarning = BLACK,
        onError = BLACK,
        btnOpacity = 0.2,
        btnHoverOpacity = 0.35
    ),
    Theme.BLUE to ThemeColours(
        bgPrimary = Triple(7, 13, 20),
        bgSecondary = Triple(15, 23, 42),
        bgTertiary = Triple(45, 85, 150),
        accentPrimary = Triple(100, 181, 246),
        accentSecondary = Triple(255, 82, 82),
        accentTertiary = Triple(255, 193, 7),
        accentPrimaryText = BLACK,
        textPrimary = WHITE,
        textSecondary = Triple(204, 204, 204),
        textTertiary = Triple(136, 136, 136),
        gridYes = Triple(76, 175, 80),
        gridNeutral = Triple(64, 64, 75),
        gridNo = Triple(244, 67, 54),
        desat = WHITE,
        onSuccess = WHITE,
        onInfo = BLACK,
        onWarning = BLACK,
        onError = WHITE,
        btnOpacity = 0.15,
        btnHoverOpacity = 0.3
    ),
    Theme.LIGHT to ThemeColours(
        // High-contrast light theme: white panels, light grid cells
        bgPrimary = Triple(238, 242, 248),
        bgSecondary = Triple(255, 255, 255),
        bgTertiary = Triple(220, 226, 236),
        accentPrimary = Triple(37, 99, 235),
        accentSecondary = Triple(220, 38, 38),
        accentTertiary = Triple(217, 119, 6),
        accentPrimaryText = WHITE,
        textPrimary = Triple(15, 23, 42),
        textSecondary = Triple(55, 65, 81),
        textTertiary = Triple(107, 114, 128),
        gridYes = Triple(22, 163, 74),
        gridNeutral = Triple(180, 180, 180),
        gridNo = Triple(220, 38, 38),
        desat = Triple(15, 23, 42),
        onSuccess = WHITE,
        onInfo = WHITE,
        onWarning = WHITE,
        onError = WHITE,
        btnOpacity = 0.10,
        btnHoverOpacity = 0.18
    ),
    Theme.EPAPER to ThemeColours(
        // True monochrome for ePaper displays
        bgPrimary = Triple(255, 255, 255),
        bgSecondary = Triple(240, 240, 240),
        bgTertiary = Triple(220, 220, 220),
        accentPrimary = Triple(0, 0, 0),
        accentSecondary = Triple(0, 0, 0),
        accentTertiary = Triple(0, 0, 0),
        accentPrimaryText = WHITE,
        textPrimary = Triple(0, 0, 0),
        textSecondary = Triple(60, 60, 60),
        textTertiary = Triple(120, 120, 120),
        gridYes = Triple(0, 0, 0),
        gridNeutral = Triple(128, 128, 128),
        gridNo = Triple(0, 0, 0),
        desat = Triple(0, 0, 0),
        onSuccess = WHITE,
        onInfo = WHITE,
        onWarning = WHITE,
        onError = WHITE,
        btnOpacity = 0.1,
        btnHoverOpacity = 0.20
    )
)

fun rgbToString(rgb: Triple<Int, Int, Int>): String {
    return "${rgb.first}, ${rgb.second}, ${rgb.third}"
}

fun rgbToHex(rgb: Triple<Int, Int, Int>): String {
    fun Int.toHexByte(): String = toString(16).padStart(2, '0')
    return "#${rgb.first.toHexByte()}${rgb.second.toHexByte()}${rgb.third.toHexByte()}"
}

fun applyTheme(theme: Theme) {
    val colours = THEME_COLOURS[theme] ?: THEME_COLOURS[Theme.DARK]!!
    val root = document.documentElement ?: return
    val style = root.asDynamic().style

    // Remove all theme classes
    root.classList.remove("theme-dark", "theme-blue", "theme-light", "theme-epaper")
    // Add current theme class
    root.classList.add("theme-${theme.name.lowercase()}")

    // Core colour variables
    style.setProperty("--colour-bg-primary", rgbToString(colours.bgPrimary))
    style.setProperty("--colour-bg-secondary", rgbToString(colours.bgSecondary))
    style.setProperty("--colour-bg-tertiary", rgbToString(colours.bgTertiary))

    style.setProperty("--colour-accent-primary", rgbToString(colours.accentPrimary))
    style.setProperty("--colour-accent-secondary", rgbToString(colours.accentSecondary))
    style.setProperty("--colour-accent-tertiary", rgbToString(colours.accentTertiary))
    // Alias for legacy CSS that referenced --colour-accent
    style.setProperty("--colour-accent", rgbToString(colours.accentPrimary))
    style.setProperty("--colour-btn-opacity", colours.btnOpacity.toString())
    style.setProperty("--colour-btn-hover-opacity", colours.btnHoverOpacity.toString())

    style.setProperty("--colour-text-primary", rgbToString(colours.textPrimary))
    style.setProperty("--colour-text-secondary", rgbToString(colours.textSecondary))
    style.setProperty("--colour-text-tertiary", rgbToString(colours.textTertiary))

    style.setProperty("--colour-accent-primary-text", rgbToString(colours.accentPrimaryText))

    style.setProperty("--colour-grid-yes", rgbToString(colours.gridYes))
    style.setProperty("--colour-grid-neutral", rgbToString(colours.gridNeutral))
    style.setProperty("--colour-grid-no", rgbToString(colours.gridNo))

    // Derived colour variables (for compatibility with existing CSS)
    style.setProperty("--colour-accent-success", rgbToString(colours.gridYes))
    style.setProperty("--colour-accent-success-text", rgbToString(colours.onSuccess))
    style.setProperty("--colour-accent-info", rgbToString(colours.accentPrimary))
    style.setProperty("--colour-accent-info-text", rgbToString(colours.onInfo))
    style.setProperty("--colour-accent-warning", rgbToString(colours.accentTertiary))
    style.setProperty("--colour-accent-warning-text", rgbToString(colours.onWarning))
    style.setProperty("--colour-accent-error", rgbToString(colours.gridNo))
    style.setProperty("--colour-accent-error-text", rgbToString(colours.onError))
    style.setProperty("--colour-accent-desat", rgbToString(colours.desat))
    // "Both" selection: tertiary amber/warning scale (replaces hard-coded purple)
    style.setProperty("--colour-accent-both", rgbToString(colours.accentTertiary))

    // Border colour: theme-specific for visibility
    val borderColour = when (theme) {
        Theme.DARK, Theme.BLUE -> colours.bgSecondary
        Theme.LIGHT -> Triple(200, 200, 210)
        Theme.EPAPER -> Triple(0, 0, 0)
    }
    style.setProperty("--colour-border", rgbToString(borderColour))

    val shadowColour = when (theme) {
        Theme.DARK, Theme.BLUE -> Triple(0, 0, 0)
        Theme.LIGHT -> Triple(0, 0, 0)
        Theme.EPAPER -> Triple(200, 200, 200)
    }
    style.setProperty("--colour-shadow", rgbToString(shadowColour))

    val shadowOpacity = when (theme) {
        Theme.DARK, Theme.BLUE -> 0.5
        Theme.LIGHT -> 0.15
        Theme.EPAPER -> 0.05
    }
    style.setProperty("--colour-shadow-opacity", shadowOpacity.toString())

    val useInvertedSelection = theme == Theme.EPAPER
    style.setProperty("--epaper-invert-selection", if (useInvertedSelection) "1" else "0")

    // Gradient background (must be valid CSS — closing paren required)
    style.setProperty(
        "--gradient-bg",
        "linear-gradient(135deg, rgb(${rgbToString(colours.bgPrimary)}) 0%, rgb(${rgbToString(colours.bgSecondary)}) 100%)"
    )

    // Keep PWA / browser chrome in sync with the active theme
    document.querySelector("meta[name=theme-color]")
        ?.setAttribute("content", rgbToHex(colours.bgPrimary))
}
