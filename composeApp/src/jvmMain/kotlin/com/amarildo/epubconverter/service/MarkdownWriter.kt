package com.amarildo.epubconverter.service

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor
import java.util.ArrayDeque

class MarkdownWriter {

    companion object {
        const val TXTZ_IMAGES_FOLDER: String = "images"
    }

    fun processXhtml(xhtml: String): String {
        val doc: Document = Jsoup.parse(xhtml)
        val body: Element = doc.body()
        val out = StringBuilder(4096)
        val listStack: ArrayDeque<String> = ArrayDeque()
        val blockquotes = IntArray(1)
        val inCode = BooleanArray(1)
        val inPre = BooleanArray(1)

        // stato e helper per le tabella
        val inTable = BooleanArray(1)
        val inThead = BooleanArray(1)
        val headerRowCount = IntArray(1)
        val tableRows = mutableListOf<MutableList<String>>()
        var currentRow: MutableList<String>? = null
        var currentCellBuf: StringBuilder? = null

        // stato e helper per <pre>
        var preBuf: StringBuilder? = null
        val preLang = arrayOf<String?>(null)

        fun appendActive(s: CharSequence) {
            if (inTable[0]) {
                if (currentCellBuf != null) {
                    currentCellBuf!!.append(s)
                }
                // altrimenti: siamo dentro <table> ma fuori da una cella -> ignora
            } else {
                out.append(s)
            }
        }

        /**
         * Flush del buffer <pre> come fenced code block Markdown
         */
        fun flushPre(out: StringBuilder, blockquoteLevel: Int) {
            val buf = preBuf ?: return
            val raw = buf.toString()
                .replace("\r\n", "\n")
                .replace("\r", "\n")
            val prefix = "> ".repeat(blockquoteLevel)
            val lang = preLang[0]?.trim().orEmpty()

            out.append("\n")
                .append(prefix).append("```").append(lang).append("\n")

            // mantieni le indentazioni/tabs così come sono
            val lines = raw.split('\n')
            for (line in lines) {
                if (line.isBlank()) continue
                out.append(prefix).append(line).append('\n')
            }

            out.append(prefix).append("```").append("\n")

            // reset stato
            preBuf = null
            preLang[0] = null
        }

        NodeTraversor.traverse(
            object : NodeVisitor {

                override fun head(node: Node, depth: Int) {
                    when (node) {
                        is Element -> {
                            when (val tag: String = node.tagName().lowercase()) {
                                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                                    val level = tag.substring(1).toInt()
                                    out.append("\n")
                                        .append("> ".repeat(blockquotes[0]))
                                        .append("#".repeat(level))
                                        .append(" ")
                                }

                                "p", "div" -> {
                                    if (inTable[0]) {
                                        // NON scrivere su out quando sei in tabella
                                        // se vuoi separare paragrafi nella cella: appendActive(" ")
                                    } else {
                                        out.append("\n")
                                            .append("> ".repeat(blockquotes[0]))
                                    }
                                }

                                "br" -> {
                                    if (inPre[0]) {
                                        preBuf?.append("\n")
                                    } else {
                                        appendActive("  \n")
                                    }
                                }

                                "blockquote" -> {
                                    blockquotes[0]++
                                    out.append("> ".repeat(blockquotes[0]))
                                }

                                "em", "i" -> out.append("*")

                                "strong", "b" -> out.append("**")

                                // [MODIFICA 2] — nel ramo Element di head(...): sostituisci la gestione di "code"
                                "code" -> {
                                    if (inPre[0]) {
                                        // Se è un <code class="w"/> vuoto dentro <pre>, trattalo come newline logico
                                        if (node.classNames().contains("w") && node.childNodeSize() == 0) {
                                            preBuf?.append('\n')
                                        }
                                        // altrimenti: i TextNode figli verranno raccolti dal ramo TextNode
                                    } else if (!inCode[0]) {
                                        out.append('`')
                                        inCode[0] = true
                                    }
                                }

                                "pre" -> {
                                    if (!inPre[0]) {
                                        // non scrivere nulla qui: inizializza buffer
                                        inPre[0] = true
                                        preBuf = StringBuilder(1024)
                                        // prendi la lingua, se disponibile
                                        val lang = node.attr("data-code-language")
                                        preLang[0] = lang.ifBlank { null }
                                    }
                                }

                                "hr" -> out.append("\n* * *")

                                "a" -> {
                                    val href: String = node.attr("href")
                                    if ("://" in href) {
                                        out.append('[')
                                    }
                                }

                                "img" -> {
                                    out.append('!')
                                    val alt: String = node.attr("alt")
                                    out.append('[')

                                    if (alt.isBlank()) {
                                        out.append(removeNewlines("image"))
                                    } else {
                                        out.append(removeNewlines(alt))
                                    }
                                    out.append(']')

                                    // percorso immagine
                                    val src: String = node.attr("src")
                                    val i: Int = src.lastIndexOf("/")
                                    val percorsoImmagine = "$TXTZ_IMAGES_FOLDER/${src.substring(i + 1)}"
                                    out.append('(').append(percorsoImmagine).append(')')
                                }

                                "ul", "ol" -> listStack.push(tag)

                                "li" -> {
                                    out.append("\n")
                                        .append("\t".repeat((listStack.size - 1).coerceAtLeast(0)))
                                        .append("> ".repeat(blockquotes[0]))
                                    if (listStack.isNotEmpty() && listStack.peek() == "ol") {
                                        // per semplicità: non numeriamo realmente; potresti tenere un contatore per livello
                                        out.append("1. ")
                                    } else {
                                        out.append("+ ")
                                    }
                                }

                                "table" -> {
                                    inTable[0] = true
                                    tableRows.clear()
                                    currentRow = null
                                    currentCellBuf = null
                                    inThead[0] = false
                                    headerRowCount[0] = 0
                                    out.append("\n") // separatore dal testo precedente
                                }

                                "thead" -> if (inTable[0]) inThead[0] = true
                                "tbody" -> if (inTable[0]) inThead[0] = false
                                "tr" -> if (inTable[0]) {
                                    currentRow = mutableListOf()
                                }

                                "th", "td" -> if (inTable[0]) {
                                    currentCellBuf = StringBuilder()
                                }
                            }
                        }

                        is TextNode -> {
                            val txtRaw: String = node.wholeText

                            // il controllo su txtRaw lo metto dopo la verifica se si trova dentro <pre> (codice) dato che dentro i blocchi
                            // di codice gli spazi li voglio
                            if (inPre[0]) {
                                // Dentro <pre>: accumula il testo così com'è (senza escape, senza indent aggiuntiva)
                                preBuf?.append(txtRaw)
                            }

                            if (txtRaw.isBlank()) return
                            if (inCode[0]) {
                                appendActive(removeNewlines(txtRaw))
                            } else {
                                appendActive(escapeMd(removeNewlines(txtRaw)))
                            }
                        }
                    }
                }

                override fun tail(node: Node, depth: Int) {
                    if (node is Element) {
                        val tag = node.tagName().lowercase()
                        when (tag) {
                            "em", "i" -> out.append("*")

                            "strong", "b" -> out.append("**")

                            "code" -> {
                                if (inCode[0]) {
                                    out.append('`')
                                    inCode[0] = false
                                }
                            }

                            "pre" -> {
                                if (inPre[0]) {
                                    // emetti il fenced code block
                                    flushPre(out, blockquotes[0])
                                    inPre[0] = false
                                }
                            }

                            "a" -> {
                                val href: String = node.attr("href")
                                if ("://" in href) {
                                    val title = if (node.hasAttr("title")) {
                                        " \"${removeNewlines(node.attr("title"))}\""
                                    } else {
                                        ""
                                    }
                                    out.append("](").append(href).append(title).append(')')
                                }
                            }

                            "blockquote" -> {
                                blockquotes[0] = (blockquotes[0] - 1).coerceAtLeast(0)
                            }

                            "ul", "ol" -> {
                                if (listStack.isNotEmpty()) listStack.pop()
                                out.append("\n")
                            }

                            // evita newline globali da <p>/<div> quando siamo in tabella
                            "p", "div", "h1", "h2", "h3", "h4", "h5", "h6" -> {
                                if (!inTable[0]) appendActive("\n")
                            }

                            "th", "td" -> {
                                if (inTable[0]) {
                                    currentRow?.add((currentCellBuf?.toString() ?: "").trim())
                                    currentCellBuf = null
                                }
                            }

                            "tr" -> {
                                if (inTable[0]) {
                                    currentRow?.let { tableRows.add(it) }
                                    if (inThead[0]) headerRowCount[0]++
                                    currentRow = null
                                }
                            }

                            "thead" -> if (inTable[0]) inThead[0] = false

                            "table" -> {
                                if (inTable[0]) {
                                    renderMarkdownTable(out, tableRows, headerRowCount[0], blockquotes[0])
                                    inTable[0] = false
                                    tableRows.clear()
                                    currentRow = null
                                    currentCellBuf = null
                                    out.append("\n")
                                }
                            }
                        }
                    }
                }

                // funzione supporto per tabella
                private fun renderMarkdownTable(
                    out: StringBuilder,
                    rows: List<List<String>>,
                    theadCount: Int,
                    blockquoteLevel: Int,
                ) {
                    // ripulisci righe completamente vuote (da whitespace DOM)
                    val cleanRows = rows
                        .map { r -> r.map { it.trim() } }
                        .filter { r -> r.any { it.isNotEmpty() } }

                    if (cleanRows.isEmpty()) return

                    val header = cleanRows.first()
                    val bodyStartIdx = 1

                    val prefix = "> ".repeat(blockquoteLevel)

                    // header
                    out.append(prefix)
                        .append("| ")
                        .append(header.joinToString(" | "))
                        .append(" |\n")
                    // separatore
                    out.append(prefix)
                        .append("| ")
                        .append(List(header.size) { "---" }.joinToString(" | "))
                        .append(" |\n")
                    // corpo
                    for (i in bodyStartIdx until cleanRows.size) {
                        val row = cleanRows[i]
                        out.append(prefix)
                            .append("| ")
                            .append(row.joinToString(" | "))
                            .append(" |\n")
                    }
                }
            },
            body,
        )

        return tidy(out.toString())
    }

    private fun removeNewlines(s: String): String {
        val r: String = s.replace("\r\n", " ")
            .replace("\r", " ")
            .replace("\n", " ")
        return Regex("[ ]{2,}").replace(r, " ")
    }

    private fun preFormat(s: String): String {
        val lines: List<String> = s.split(Regex("\\R"))
        val b = StringBuilder()
        for (l in lines) {
            b.append("    ")
                .append(l)
                .append("\n")
        }
        return b.toString()
    }

    private fun escapeMd(s: String): String = s
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("#", "\\#")
        .replace("+", "\\+")
        .replace("!", "\\!")
        .replace("|", "\\|") // necessario per le tabelle Markdown

    private fun tidy(s: String): String {
        val lines = s.split(Regex("\\R"))
        val b = StringBuilder()
        var inFence = false
        var currentFencePrefix = "" // supporta anche "> " ripetuti prima di ```

        for (raw in lines) {
            val l = raw

            // rileva l’inizio/fine di un fenced block con possibile prefix di blockquote
            // Esempi di match: "```", "> ```", ">> ```"
            val fenceRegex = Regex("""^(\s*(?:>\s)*)```""")
            val m = fenceRegex.find(l)
            if (m != null) {
                val prefix = m.groupValues[1]
                if (!inFence) {
                    inFence = true
                    currentFencePrefix = prefix
                } else {
                    // chiusura: solo se il prefix coincide (così non falsi positivi)
                    if (prefix == currentFencePrefix) {
                        inFence = false
                        currentFencePrefix = ""
                    }
                }
                b.append(l).append('\n')
                continue
            }

            if (inFence) {
                // Dentro fenced block: NON rimuovere tabs/leading spaces
                b.append(l).append('\n')
            } else {
                // Fuori: come prima, ma senza cancellare i \t indiscriminatamente
                // (rimuovere i \t poteva essere troppo aggressivo in certi casi)
                val trimmed = l.trim()
                if (trimmed.isEmpty()) {
                    b.append('\n')
                } else {
                    // rimuovi solo spazi leading 1..3 come prima
                    b.append(Regex("^ {1,3}").replace(l, "")).append('\n')
                }
            }
        }

        val r = Regex("\n{7,}").replace(b.toString(), "\n\n\n\n\n\n")
        return r.trim() + "\n\n"
    }
}
