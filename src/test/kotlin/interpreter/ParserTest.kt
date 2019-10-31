package interpreter

import kotlin.test.Test
import kotlin.test.*

class ParserTest {
    @Test
    fun testParser() {
        val l = Lexer("")
        val tokens = l.lex("source3.tl")
        val p = Parser()

        var tree = p.parse(tokens)
        println(tree)
    }
}