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
    val accentPrimaryText: Triple<Int, Int, Int>,

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
        bgPrimary = Triple(5, 5, 5),
        bgSecondary = Triple(26, 26, 26),
        bgTertiary = Triple(100, 100, 100),
        accentPrimary = Triple(100, 181, 246),
        accentSecondary = Triple(255, 82, 82),
        accentTertiary = Triple(255, 193, 7),
        accentPrimaryText = Triple(0,0,0),
        textPrimary = Triple(255, 255, 255),
        textSecondary = Triple(204, 204, 204),
        textTertiary = Triple(136, 136, 136),
        gridYes = Triple(96, 255, 96),
        gridNeutral = Triple(64, 64, 64),
        gridNo = Triple(255, 96, 96),
        desat = Triple(255, 255, 255),
        btnOpacity = 0.2,
        btnHoverOpacity = 0.35
    ),
    Theme.BLUE to ThemeColors(
        // Higher-contrast deep blue theme
        bgPrimary = Triple(7, 13, 20),   // Very dark navy for main background
        bgSecondary = Triple(15, 23, 42), // Slightly lighter for panels
        bgTertiary = Triple(45, 85, 150), // Noticeably lighter for grid cells
        accentPrimary = Triple(100, 181, 246),
        accentSecondary = Triple(255, 82, 82),
        accentTertiary = Triple(255, 193, 7),
        accentPrimaryText = Triple(0,0,0),
        textPrimary = Triple(255, 255, 255),
        textSecondary = Triple(204, 204, 204),
        textTertiary = Triple(136, 136, 136),
        gridYes = Triple(76, 175, 80),
        gridNeutral = Triple(64, 64, 75),
        gridNo = Triple(244, 67, 54),
        desat = Triple(255, 255, 255),
        btnOpacity = 0.15,
        btnHoverOpacity = 0.3
    ),
    Theme.LIGHT to ThemeColors(
        // High-contrast light theme tuned for grid clarity
        bgPrimary = Triple(238, 242, 248),   // Soft light slate background
        bgSecondary = Triple(190, 190, 190), // Pure white for cards/panels
        bgTertiary = Triple(130, 130, 150),  // Mid light grey for grid cells
        accentPrimary = Triple(37, 99, 235),   // Strong readable blue
        accentSecondary = Triple(220, 38, 38), // Deep red for mistakes
        accentTertiary = Triple(217, 119, 6),  // Dark amber for warnings/highlight-both
        accentPrimaryText = Triple(255,255,255),
        textPrimary = Triple(15, 23, 42),      // Near-black text
        textSecondary = Triple(55, 65, 81),
        textTertiary = Triple(107, 114, 128),
        gridYes = Triple(22, 163, 74),        // Saturated green
        gridNeutral = Triple(180, 180, 180),  // Slate neutral
        gridNo = Triple(220, 38, 38),         // Same deep red as accents
        desat = Triple(15, 23, 42),           // Dark neutral used for buttons
        btnOpacity = 0.10,
        btnHoverOpacity = 0.18
    ),
    Theme.EPAPER to ThemeColors(
        // Monochrome, ultra high-contrast theme for ePaper displays.
        // Keeps everything in greyscale to avoid artifacts on limited color panels
        // and biases toward very light backgrounds with pure black accents.
        bgPrimary = Triple(255, 255, 255),    
        bgSecondary = Triple(240, 240, 240),  // Slightly darker panels
        bgTertiary = Triple(220, 220, 220),   // Mid grey for grid cells
        accentPrimary = Triple(0, 0, 0),      // Black for primary highlights/lines
        accentSecondary = Triple(0, 0, 0),    // Also black – avoid color on ePaper
        accentTertiary = Triple(0, 0, 0),
        accentPrimaryText = Triple(255,255,255),
        textPrimary = Triple(0, 0, 0),
        textSecondary = Triple(60, 60, 60),
        textTertiary = Triple(120, 120, 120),
        gridYes = Triple(0, 64, 0),           // Strong black for "yes"
        gridNeutral = Triple(0, 0, 64), // Mid grey neutral
        gridNo = Triple(64, 0, 0),           // Strong black for "no"/errors
        desat = Triple(0, 0, 0),
        btnOpacity = 0.1,                  // Minimize mid-tone flashing
        btnHoverOpacity = 0.20
    )
)

fun rgbToString(rgb: Triple<Int, Int, Int>): String {
    return "${rgb.first}, ${rgb.second}, ${rgb.third}"
}

fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v
fun luminosity(rgb: Triple<Int, Int, Int>): Double =
    (0.299 * rgb.first + 0.587 * rgb.second + 0.114 * rgb.third) / 255.0

fun adjustTextLuminosity(rgb: Triple<Int, Int, Int>): Triple<Int, Int, Int> {
    val original = rgb
    val lum = luminosity(original)
    var dir = 1
    if (lum > 0.5) dir = -1
    val adjusted = Triple(clamp(original.first + (128 * dir)), clamp(original.second + (128 * dir)), clamp(original.third + (128 * dir)))
    return adjusted
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

    style.setProperty("--color-accent-primary-text", rgbToString(colors.accentPrimaryText))

    style.setProperty("--color-grid-yes", rgbToString(colors.gridYes))
    style.setProperty("--color-grid-neutral", rgbToString(colors.gridNeutral))
    style.setProperty("--color-grid-no", rgbToString(colors.gridNo))

    // Derived color variables (for compatibility with existing CSS)
    style.setProperty("--color-accent-success", rgbToString(colors.gridYes))
    style.setProperty("--color-accent-success-text", rgbToString(adjustTextLuminosity(colors.gridYes)))
    style.setProperty("--color-accent-info", rgbToString(colors.accentPrimary))
    style.setProperty("--color-accent-info-text", rgbToString(adjustTextLuminosity(colors.accentPrimary)))
    style.setProperty("--color-accent-warning", rgbToString(colors.accentTertiary))
    style.setProperty("--color-accent-warning-text", rgbToString(adjustTextLuminosity(colors.accentTertiary)))
    style.setProperty("--color-accent-error", rgbToString(colors.gridNo))
    style.setProperty("--color-accent-error-text", rgbToString(adjustTextLuminosity(colors.gridNo)))
    style.setProperty("--color-accent-desat", rgbToString(colors.desat))

    // Border and shadow (derived from backgrounds and black/white)
    style.setProperty("--color-border", rgbToString(colors.bgSecondary))
    style.setProperty("--color-shadow", "0, 0, 0")  // Black for shadows

    // Gradient background - need to wrap RGB values in rgb() for valid CSS
    style.setProperty("--gradient-bg", "linear-gradient(135deg, rgb(${rgbToString(colors.bgPrimary)}) 0%, rgb(${rgbToString(colors.bgSecondary)}) 100%")
}

