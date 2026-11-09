package com.weenas.castbay.service

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every translation (values-*\/strings.xml, from Weblate or by hand) against the English one: no
 * string English no longer has, the same placeholders, and a folder for each language in Settings.
 * A string a translation leaves out shows in English, which is fine.
 */
class TranslationsTest {
    private val res = File("src/main/res")
    private val base = strings(File(res, "values/strings.xml"))
    private val translations = res.listFiles { dir -> dir.name.startsWith("values-") }.orEmpty()
        .map { File(it, "strings.xml") }.filter { it.exists() }

    @Test
    fun translationsHaveNoStringsEnglishDropped() {
        assertTrue("found no translations in ${res.absolutePath}", translations.isNotEmpty())
        for (file in translations) {
            val extra = strings(file).keys - base.keys
            assertTrue("${file.parentFile.name} has strings English no longer has: $extra", extra.isEmpty())
        }
    }

    @Test
    fun translationsKeepThePlaceholders() {
        for (file in translations) {
            for ((name, text) in strings(file)) {
                val english = base[name] ?: continue
                assertEquals("${file.parentFile.name}/$name placeholders", placeholders(english), placeholders(text))
            }
        }
    }

    @Test
    fun everyLanguageInSettingsHasItsStrings() {
        for (language in ReceiverSettings.LANGUAGES) {
            if (language == ReceiverSettings.LANGUAGE_SYSTEM || language == ReceiverSettings.LANGUAGE_EN) continue
            val folder = "values-" + language.replace("-", "-r")
            assertTrue("$language needs $folder/strings.xml", File(res, "$folder/strings.xml").exists())
        }
    }

    private fun strings(file: File): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) as Element }.associate { it.getAttribute("name") to it.textContent }
    }

    // %1$s, %2$d, %s, %d; %% is a percent sign, not a placeholder.
    private fun placeholders(text: String): List<String> =
        Regex("%(\\d+\\$)?[sdf]").findAll(text.replace("%%", "")).map { it.value }.sorted().toList()
}
