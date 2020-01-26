package interpreter

//import kotlin.TEs
import kotlin.test.assertEquals

class LexerTest {
   // @Test
    fun testLexer() {
        var l = Lexer("")

        var tokens = l.lex("source.tl")
        println(tokens.toString())
        println("lexical errors: \n" + l.errors.toString())

        assertEquals(1, l.errors.size, "lexical errors: \n" + l.errors.toString())
    }
}