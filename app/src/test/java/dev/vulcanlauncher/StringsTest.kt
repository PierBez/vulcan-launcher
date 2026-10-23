package dev.vulcanlauncher

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Translations (res/values-xx/strings.xml) must match the English strings: same names, same format arguments. */
class StringsTest {
    /** Strings by name; a plural's quantities are joined, which keeps their format arguments together. */
    private fun strings(f: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f)
        return listOf("string", "plurals").flatMap { tag ->
            val nodes = doc.getElementsByTagName(tag)
            (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue to nodes.item(it).textContent }
        }.toMap()
    }

    /** The distinct format arguments, so a plural with more or fewer quantities than English still compares equal. */
    private fun args(s: String) = Regex("%(\\d+\\$)?[sd]").findAll(s).map { it.value }.toSortedSet().toList()

    @Test
    fun translationsMatchEnglish() {
        val res = File("src/main/res")
        val english = strings(File(res, "values/strings.xml"))
        assertTrue(english.size > 300)
        for (dir in res.listFiles { f -> f.name.startsWith("values-") }.orEmpty()) {
            val f = File(dir, "strings.xml").takeIf { it.exists() } ?: continue
            for ((name, text) in strings(f)) {
                val en = english[name]
                assertTrue("${dir.name}: $name isn't in values/strings.xml", en != null)
                assertEquals("${dir.name}: $name has other format arguments than English", args(en!!), args(text))
            }
        }
    }
}
