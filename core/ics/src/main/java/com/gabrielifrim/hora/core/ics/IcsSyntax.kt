package com.gabrielifrim.hora.core.ics

import java.io.ByteArrayOutputStream

/** Une ligne « NOM;PARAM=valeur:contenu » dépliée ; [value] est encore échappée. */
data class IcsProperty(val name: String, val params: Map<String, String>, val value: String) {
    /** Le contenu d'une propriété texte (SUMMARY, DESCRIPTION…), échappements résolus. */
    val text: String get() = IcsSyntax.unescape(value)
}

/** Un bloc BEGIN:…/END:… avec ses propriétés et ses sous-blocs. */
class IcsComponent(val name: String) {
    val properties = mutableListOf<IcsProperty>()
    val children = mutableListOf<IcsComponent>()

    fun first(name: String): IcsProperty? = properties.firstOrNull { it.name == name }
    fun all(name: String): List<IcsProperty> = properties.filter { it.name == name }
}

/** La syntaxe iCalendar seule : lignes pliées, paramètres, échappements. */
object IcsSyntax {

    /**
     * Lit un fichier entier. Tolérant : une ligne illisible est ignorée, un END
     * manquant ou en trop ne casse pas le reste.
     *
     * Le dépliage se fait sur les octets, avant le décodage UTF-8 : certains
     * logiciels plient au 75e octet en plein milieu d'un caractère accentué.
     */
    fun parse(bytes: ByteArray): List<IcsComponent> {
        val roots = mutableListOf<IcsComponent>()
        val stack = ArrayDeque<IcsComponent>()
        for (line in unfold(bytes)) {
            val property = parseLine(line) ?: continue
            when (property.name) {
                "BEGIN" -> IcsComponent(property.value.trim().uppercase()).also { c ->
                    (stack.lastOrNull()?.children ?: roots).add(c)
                    stack.addLast(c)
                }
                "END" -> {
                    val name = property.value.trim().uppercase()
                    if (stack.any { it.name == name }) while (stack.removeLast().name != name) Unit
                }
                else -> stack.lastOrNull()?.properties?.add(property)
            }
        }
        return roots
    }

    internal fun unfold(bytes: ByteArray): List<String> {
        val out = ByteArrayOutputStream(bytes.size)
        var i = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0
        while (i < bytes.size) {
            val b = bytes[i]
            val newline = when {
                b == '\r'.code.toByte() && i + 1 < bytes.size && bytes[i + 1] == '\n'.code.toByte() -> 2
                b == '\n'.code.toByte() || b == '\r'.code.toByte() -> 1
                else -> 0
            }
            if (newline > 0) {
                val next = bytes.getOrNull(i + newline)
                if (next == ' '.code.toByte() || next == '\t'.code.toByte()) {
                    i += newline + 1 // suite de la ligne précédente
                    continue
                }
                out.write('\n'.code)
                i += newline
            } else {
                out.write(b.toInt())
                i++
            }
        }
        return out.toString(Charsets.UTF_8).split('\n').filter { it.isNotBlank() }
    }

    /** NOM;P1=a;P2="b;c":valeur. Le premier « : » hors guillemets sépare la valeur. */
    internal fun parseLine(line: String): IcsProperty? {
        var quoted = false
        val colon = line.indices.firstOrNull { i ->
            if (line[i] == '"') quoted = !quoted
            !quoted && line[i] == ':'
        } ?: return null
        val head = splitOutsideQuotes(line.substring(0, colon), ';')
        val name = head.first().trim().uppercase().ifEmpty { return null }
        val params = head.drop(1).mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq).trim().uppercase() to p.substring(eq + 1).trim().removeSurrounding("\"")
        }.toMap()
        return IcsProperty(name, params, line.substring(colon + 1))
    }

    private fun splitOutsideQuotes(s: String, separator: Char): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var quoted = false
        for (c in s) {
            when {
                c == '"' -> { quoted = !quoted; current.append(c) }
                c == separator && !quoted -> { parts += current.toString(); current.clear() }
                else -> current.append(c)
            }
        }
        parts += current.toString()
        return parts
    }

    fun unescape(value: String): String {
        val sb = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '\\' && i + 1 < value.length) {
                when (val n = value[i + 1]) {
                    'n', 'N' -> sb.append('\n')
                    else -> sb.append(n) // \\ \; \, et tout échappement inconnu : le caractère tel quel
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    fun escape(text: String): String = text
        .replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\n")
        .replace("\n", "\\n")

    /** Plie une ligne à 75 octets au plus, sans couper un caractère UTF-8 ; fin CRLF. */
    fun fold(line: String): String {
        val sb = StringBuilder(line.length + 8)
        var bytes = 0
        var i = 0
        while (i < line.length) {
            val cp = line.codePointAt(i)
            val len = Character.charCount(cp)
            val size = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            // Chaque ligne de suite commence par une espace, qui compte dans ses 75 octets.
            if (bytes + size > 75) {
                sb.append("\r\n ")
                bytes = 1
            }
            sb.appendCodePoint(cp)
            bytes += size
            i += len
        }
        return sb.append("\r\n").toString()
    }
}
