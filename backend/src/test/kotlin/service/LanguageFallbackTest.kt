package service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import i18n.LanguageConfig
import i18n.LanguageManager

/**
 * Regression tests for the English-fallback resolver in [LanguageManager.getString].
 *
 * `en.json` is the authoritative source for hint keys: every other locale resolves
 * a key against itself first, then falls back to English, then (only if the key is
 * missing from *both*) renders the bracketed `"[key]"` marker.
 *
 * Today every locale is a superset of `en.json`, so no existing key actually
 * triggers the fallback; it is future-proofing for the EN-only hint keys that the
 * later phases of the hint-techniques plan add. These tests pin the surrounding
 * contract: the active locale is consulted first, is never clobbered by the
 * fallback, and genuinely-missing keys still render `[key]`.
 */
class LanguageFallbackTest {

    @Test
    fun `resolving a key does not clobber the active locale`() {
        assertTrue(LanguageConfig.setLanguage("es"))
        assertEquals("es", LanguageManager.getCurrentLanguage())

        // `languageName` exists in every locale, so this resolves from the active
        // locale (not the EN fallback) and must not be the bracketed marker.
        val value = LanguageManager.getString("languageName")
        assertFalse(value.startsWith("["), "a known key must resolve, got: $value")

        // The active locale must be unchanged after resolution — the EN fallback
        // must load en.json into a side cache and never call loadLanguage("en").
        assertEquals("es", LanguageManager.getCurrentLanguage())
    }

    @Test
    fun `active locale value takes precedence over the english fallback`() {
        val enName = LanguageConfig.setLanguage("en").let { LanguageConfig.getCurrentLanguageName() }
        val esName = LanguageConfig.setLanguage("es").let { LanguageConfig.getCurrentLanguageName() }

        // If the resolver clobbered the active locale to English, both names would
        // be identical. They must differ, proving the active locale wins.
        assertTrue(enName != esName, "expected distinct native names: en=$enName es=$esName")
        assertEquals("es", LanguageManager.getCurrentLanguage())
    }

    @Test
    fun `a key missing from every locale still renders its bracketed marker`() {
        assertTrue(LanguageConfig.setLanguage("en"))
        val bogus = "hints.no_such_technique.step1.title"
        assertEquals("[$bogus]", LanguageManager.getString(bogus))
    }
}
