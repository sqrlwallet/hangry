package com.kevan.hangry

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A string read with format arguments goes through String.format, so a bare "%" in its text
 * ("70% of your reserve") throws as soon as the screen shows it. It has to be written "%%".
 */
class StringResourceFormatTest {

    // %1$d, %2$s, %1$.1f, %d... plus %% and %n; anything else after a % is a bare percent sign.
    private val formatSpec = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxXc]|%%|%n""")
    private val positionalArg = Regex("""%\d+\$""")

    @Test
    fun `formatted strings have no bare percent signs`() {
        val res = File("src/main/res")
        val files = res.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .flatMap { it.listFiles { f -> f.extension == "xml" }.orEmpty().toList() }
        assertTrue("No string resources found under ${res.absolutePath}", files.isNotEmpty())

        val broken = files.flatMap { file ->
            val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                .getElementsByTagName("string")
            (0 until strings.length).map { strings.item(it) as Element }
                .filter { it.getAttribute("formatted") != "false" }
                .filter { positionalArg.containsMatchIn(it.textContent) }
                .filter { formatSpec.replace(it.textContent, "").contains('%') }
                .map { "${file.name}: ${it.getAttribute("name")}" }
        }
        assertTrue("Bare % in formatted strings (write it as %%):\n" + broken.joinToString("\n"), broken.isEmpty())
    }
}
