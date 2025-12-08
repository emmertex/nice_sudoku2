package view

import kotlinx.browser.document
import kotlinx.browser.window
import kotlin.js.asDynamic

enum class Theme {
    DARK,        // Pure dark theme
    BLUE,        // Current blue gradient theme
    LIGHT,       // Light theme
    EPAPER       // High contrast ePaper theme
}

data class ThemeColors(
    // Core backgrounds
    val bgPrimary: Triple<Int, Int, Int>,
    val bgSecondary: Triple<Int, Int, Int>,
    val bgTertiary: Triple<Int, Int, Int>,
    val desat: Triple<Int, Int, Int>,

    // Core accents
    val accentPrimary: Triple<Int, Int, Int>,
    val accentSecondary: Triple<Int, Int, Int>,
    val accentTertiary: Triple<Int, Int, Int>,

    // Core text
    val textPrimary: Triple<Int, Int, Int>,
    val textSecondary: Triple<Int, Int, Int>,
    val textTertiary: Triple<Int, Int, Int>,

    // Grid status colors
    val gridYes: Triple<Int, Int, Int>,
    val gridNeutral: Triple<Int, Int, Int>,
    val gridNo: Triple<Int, Int, Int>,
    
    val btnOpacity: Double,
    val btnHoverOpacity: Double
)

val THEME_COLORS = mapOf(
    Theme.DARK to ThemeColors(
        bgPrimary = Triple(0, 0, 0),
        bgSecondary = Triple(26, 26, 26),
        bgTertiary = Triple(100, 100, 100),
        accentPrimary = Triple(100, 181, 246),
        accentSecondary = Triple(255, 82, 82),
        accentTertiary = Triple(255, 193, 7),
        textPrimary = Triple(255, 255, 255),
        textSecondary = Triple(204, 204, 204),
        textTertiary = Triple(136, 136, 136),
        gridYes = Triple(76, 175, 80),
        gridNeutral = Triple(158, 158, 158),
        gridNo = Triple(244, 67, 54),
        desat = Triple(100, 100, 100),
        btnOpacity = 0.2,
        btnHoverOpacity = 0.35
    ),
    Theme.BLUE to ThemeColors(
        bgPrimary = Triple(26, 26, 46),
        bgSecondary = Triple(22, 33, 62),
        bgTertiary = Triple(15, 52, 96),
        accentPrimary = Triple(100, 181, 246),
        accentSecondary = Triple(255, 82, 82),
        accentTertiary = Triple(255, 193, 7),
        textPrimary = Triple(255, 255, 255),
        textSecondary = Triple(204, 204, 204),
        textTertiary = Triple(136, 136, 136),
        gridYes = Triple(76, 175, 80),
        gridNeutral = Triple(158, 158, 158),
        gridNo = Triple(244, 67, 54),
        desat = Triple(255, 255, 255),
        btnOpacity = 0.15,
        btnHoverOpacity = 0.3
    ),
    Theme.LIGHT to ThemeColors(
        bgPrimary = Triple(245, 247, 250),
        bgSecondary = Triple(255, 255, 255),
        bgTertiary = Triple(230, 236, 245),
        accentPrimary = Triple(25, 118, 210),
        accentSecondary = Triple(239, 83, 80),
        accentTertiary = Triple(255, 160, 0),
        textPrimary = Triple(15, 23, 42),
        textSecondary = Triple(71, 85, 105),
        textTertiary = Triple(148, 163, 184),
        gridYes = Triple(46, 125, 50),
        gridNeutral = Triple(120, 144, 156),
        gridNo = Triple(229, 57, 53),
        desat = Triple(0, 0, 0),
        btnOpacity = 0.08,
        btnHoverOpacity = 0.16
    ),
    Theme.EPAPER to ThemeColors(
        bgPrimary = Triple(245, 245, 235),
        bgSecondary = Triple(230, 230, 220),
        bgTertiary = Triple(200, 200, 190),
        accentPrimary = Triple(0, 0, 0),
        accentSecondary = Triple(128, 0, 0),
        accentTertiary = Triple(80, 80, 80),
        textPrimary = Triple(0, 0, 0),
        textSecondary = Triple(40, 40, 40),
        textTertiary = Triple(80, 80, 80),
        gridYes = Triple(0, 102, 0),
        gridNeutral = Triple(90, 90, 90),
        gridNo = Triple(153, 0, 0),
        desat = Triple(0, 0, 0),
        btnOpacity = 0.1,
        btnHoverOpacity = 0.2
    )
)

fun rgbToString(rgb: Triple<Int, Int, Int>): String {
    return "${rgb.first}, ${rgb.second}, ${rgb.third}"
}

fun applyTheme(theme: Theme) {
    val colors = THEME_COLORS[theme] ?: THEME_COLORS[Theme.BLUE]!!
    val root = document.documentElement.asDynamic()
    val style = root.style

    // Core color variables
    style.setProperty("--color-bg-primary", rgbToString(colors.bgPrimary))
    style.setProperty("--color-bg-secondary", rgbToString(colors.bgSecondary))
    style.setProperty("--color-bg-tertiary", rgbToString(colors.bgTertiary))

    style.setProperty("--color-accent-primary", rgbToString(colors.accentPrimary))
    style.setProperty("--color-accent-secondary", rgbToString(colors.accentSecondary))
    style.setProperty("--color-accent-tertiary", rgbToString(colors.accentTertiary))
    style.setProperty("--color-btn-opacity", colors.btnOpacity.toString())
    style.setProperty("--color-btn-hover-opacity", colors.btnHoverOpacity.toString())

    style.setProperty("--color-text-primary", rgbToString(colors.textPrimary))
    style.setProperty("--color-text-secondary", rgbToString(colors.textSecondary))
    style.setProperty("--color-text-tertiary", rgbToString(colors.textTertiary))

    style.setProperty("--color-grid-yes", rgbToString(colors.gridYes))
    style.setProperty("--color-grid-neutral", rgbToString(colors.gridNeutral))
    style.setProperty("--color-grid-no", rgbToString(colors.gridNo))

    // Derived color variables (for compatibility with existing CSS)
    style.setProperty("--color-accent-success", rgbToString(colors.gridYes))
    style.setProperty("--color-accent-info", rgbToString(colors.accentPrimary))
    style.setProperty("--color-accent-warning", rgbToString(colors.accentTertiary))
    style.setProperty("--color-accent-error", rgbToString(colors.gridNo))
    style.setProperty("--color-accent-desat", rgbToString(colors.desat))

    // Border and shadow (derived from backgrounds and black/white)
    style.setProperty("--color-border", rgbToString(colors.bgSecondary))
    style.setProperty("--color-shadow", "0, 0, 0")  // Black for shadows

    // Gradient background - need to wrap RGB values in rgb() for valid CSS
    style.setProperty("--gradient-bg", "linear-gradient(135deg, rgb(${rgbToString(colors.bgPrimary)}) 0%, rgb(${rgbToString(colors.bgSecondary)}) 100%")
}

