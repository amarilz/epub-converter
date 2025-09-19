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

                                "br" -> appendActive("  \n") // importante: anche i <br> in cella vanno nel buffer

                                "blockquote" -> {
                                    blockquotes[0]++
                                    out.append("> ".repeat(blockquotes[0]))
                                }

                                "em", "i" -> out.append("*")

                                "strong", "b" -> out.append("**")

                                "code" -> {
                                    if (!inPre[0] && !inCode[0]) {
                                        out.append('`')
                                        inCode[0] = true
                                    }
                                }

                                "pre" -> {
                                    if (!inPre[0]) {
                                        out.append("\n")
                                        inPre[0] = true
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
                            val txt: String = node.wholeText
                            if (txt.isBlank()) return
                            if (inPre[0]) {
                                appendActive(preFormat(txt))
                            } else if (inCode[0]) {
                                appendActive(removeNewlines(txt))
                            } else {
                                appendActive(escapeMd(removeNewlines(txt)))
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
                                    out.append("\n")
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
        // rimuovi spazi in eccesso e linee vuote lunghe
        val lines: List<String> = s
            .replace("\t", "")
            .split(Regex("\\R"))
        val b = StringBuilder()
        for (l in lines) {
            if (l.trim().isEmpty()) {
                b.append("\n")
            } else {
                b.append(Regex("^ {1,3}").replace(l, ""))
                    .append("\n")
            }
        }
        val r = Regex("\n{7,}").replace(b.toString(), "\n\n\n\n\n\n")
        return r.trim() + "\n\n"
    }
}
