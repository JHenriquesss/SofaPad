package br.com.sofapad

/**
 * Digitação em tempo real: compara o que já foi enviado ao PC com o novo conteúdo do campo
 * e decide quantos Backspace e qual texto mandar. Edições que não dá para espelhar com
 * segurança são recusadas (o campo volta ao que o PC tem), nunca adivinhadas.
 */
object LiveTyping {
    /** Teto por edição: apagar muito de uma vez pelo celular arrisca apagar outra coisa no PC. */
    const val MAX_BACKSPACES = 32

    sealed interface Plan {
        data class Send(val backspaces: Int, val insert: String, val enter: Boolean) : Plan
        data class Reject(val reason: String) : Plan
    }

    const val COMPLEX_DELETE = "Apagar emoji composto ou acento separado não é seguro em tempo real. Apague no PC."
    const val LARGE_DELETE = "Muito texto apagado de uma vez. Apague no PC (Ctrl+A e ⌫)."
    const val MIDDLE_NEWLINE = "Em tempo real, a quebra de linha só vale no fim do texto."
    const val TOO_LARGE = "Texto colado excede 4 KiB."

    fun plan(sent: String, next: String): Plan {
        var prefix = 0
        val limit = minOf(sent.length, next.length)
        while (prefix < limit && sent[prefix] == next[prefix]) prefix++
        // Não partir um emoji (par substituto) ao meio.
        if (prefix > 0 && sent[prefix - 1].isHighSurrogate()) prefix--
        val deleted = sent.substring(prefix)
        var inserted = next.substring(prefix)
        val newline = inserted.indexOfFirst { it == '\n' || it == '\r' }
        val enter = newline >= 0
        if (enter) {
            if (newline != inserted.length - 1) return Plan.Reject(MIDDLE_NEWLINE)
            inserted = inserted.dropLast(1)
        }
        if (deleted.isComplex()) return Plan.Reject(COMPLEX_DELETE)
        val backspaces = deleted.codePointCount(0, deleted.length)
        if (backspaces > MAX_BACKSPACES) return Plan.Reject(LARGE_DELETE)
        if (inserted.toByteArray(Charsets.UTF_8).size > Protocol.MAX_TEXT_BYTES) return Plan.Reject(TOO_LARGE)
        return Plan.Send(backspaces, inserted, enter)
    }

    /** Caracteres em que um Backspace do Windows pode apagar mais ou menos do que um código. */
    private fun String.isComplex(): Boolean {
        var index = 0
        while (index < length) {
            val code = codePointAt(index)
            val type = Character.getType(code)
            if (code == 0x200D || code in 0xFE00..0xFE0F || code in 0x1F3FB..0x1F3FF || code in 0x1F1E6..0x1F1FF ||
                code == 0x20E3 || code in 0xE0020..0xE007F || code == '\n'.code || code == '\r'.code ||
                type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
            ) return true
            index += Character.charCount(code)
        }
        return false
    }
}
