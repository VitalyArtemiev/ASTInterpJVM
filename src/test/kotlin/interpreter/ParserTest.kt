package interpreter

import kotlin.test.Test

class ParserTest {
    @Test
    fun testParser() {
        val l = Lexer("")
        var tokens = l.lex("sourceExpr.tl")

        tokens.removeAt(tokens.lastIndex)
        println(tokens)

        val p = Parser()

        val rpn = AST.shuntYard(tokens)
        println(rpn)

        //var tree = p.parse(tokens)
        //println(tree)
    }
}