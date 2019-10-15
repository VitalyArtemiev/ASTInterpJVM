package interpreter

import java.util.regex.Matcher
import kotlin.test.Test

val source1 = "var a: int = 5\n" +
        "var b: int = 6\n" +
        "var c\n" +
        "c = a + b"

class LexerTest {
    @Test
    fun testLexer() {
        Matcher().
        var r= Regex("^var")
        r.
        var result = r.matchEntire("va")!!

        print(result.range)
        /*var l = Lexer("")

        var tokens = l.lex(source1)
        print(tokens.toString())*/
    }

}