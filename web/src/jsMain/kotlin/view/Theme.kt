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
    
    val btnOpacity: Double,
    val btnHoverOpacity: Double
)

val THEME_COLOURS = mapOf(
    Theme.DARK to ThemeColours(
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
    Theme.BLUE to ThemeColours(
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
    Theme.LIGHT to ThemeColours(
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
    Theme.EPAPER to ThemeColours(
        // Monochrome, ultra high-contrast theme for ePaper displays.
        // Keeps everything in greyscale to avoid artifacts on limited colour panels
        // and biases toward very light backgrounds with pure black accents.
        bgPrimary = Triple(255, 255, 255),    
        bgSecondary = Triple(240, 240, 240),  // Slightly darker panels
        bgTertiary = Triple(220, 220, 220),   // Mid grey for grid cells
        accentPrimary = Triple(0, 0, 0),      // Black for primary highlights/lines
        accentSecondary = Triple(0, 0, 0),    // Also black
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
    val colours = THEME_COLOURS[theme] ?: THEME_COLOURS[Theme.BLUE]!!
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
    style.setProperty("--colour-accent-success-text", rgbToString(adjustTextLuminosity(colours.gridYes)))
    style.setProperty("--colour-accent-info", rgbToString(colours.accentPrimary))
    style.setProperty("--colour-accent-info-text", rgbToString(adjustTextLuminosity(colours.accentPrimary)))
    style.setProperty("--colour-accent-warning", rgbToString(colours.accentTertiary))
    style.setProperty("--colour-accent-warning-text", rgbToString(adjustTextLuminosity(colours.accentTertiary)))
    style.setProperty("--colour-accent-error", rgbToString(colours.gridNo))
    style.setProperty("--colour-accent-error-text", rgbToString(adjustTextLuminosity(colours.gridNo)))
    style.setProperty("--colour-accent-desat", rgbToString(colours.desat))

    // Border colour: theme-specific for visibility
    val borderColour = when (theme) {
        Theme.DARK, Theme.BLUE -> colours.bgSecondary  // Use bgSecondary for dark themes
        Theme.LIGHT -> Triple(200, 200, 210)  // Dark grey border for visibility on light background
        Theme.EPAPER -> Triple(0, 0, 0)  // Pure black border for maximum contrast
    }
    style.setProperty("--colour-border", rgbToString(borderColour))
    
    // Shadow colour: black for dark themes, dark grey for light themes, none for ePaper
    val shadowColour = when (theme) {
        Theme.DARK, Theme.BLUE -> Triple(0, 0, 0)  // Black shadows
        Theme.LIGHT -> Triple(0, 0, 0)  // Black shadows for depth
        Theme.EPAPER -> Triple(200, 200, 200)  // Very light grey to minimize ghosting
    }
    style.setProperty("--colour-shadow", rgbToString(shadowColour))
    
    // Shadow opacity: higher for dark themes, lower for light/ePaper
    val shadowOpacity = when (theme) {
        Theme.DARK, Theme.BLUE -> 0.5
        Theme.LIGHT -> 0.15  // Lighter shadows on light background
        Theme.EPAPER -> 0.05  // Minimal shadow to avoid ghosting
    }
    style.setProperty("--colour-shadow-opacity", shadowOpacity.toString())
    
    // ePaper inverted selection flag
    val useInvertedSelection = theme == Theme.EPAPER
    style.setProperty("--epaper-invert-selection", if (useInvertedSelection) "1" else "0")

    // Gradient background - need to wrap RGB values in rgb() for valid CSS
    style.setProperty("--gradient-bg", "linear-gradient(135deg, rgb(${rgbToString(colours.bgPrimary)}) 0%, rgb(${rgbToString(colours.bgSecondary)}) 100%")
}

