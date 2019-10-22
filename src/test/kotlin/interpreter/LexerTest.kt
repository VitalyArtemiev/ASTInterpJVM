package interpreter

import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.test.Test

val source1 = "var a: int = 5\n" +
        "var b: int = 6\n" +
        "var c\n" +
        "c = a + b + 1.234"

val source2 = "{{{((({\n" +
        "    a:=-1\n" +
        "    b:= 2 + 3.1415\n" +
        "    c:= a - b\n" +
        "\n" +
        "    if a + b < 7 {\n" +
        "        b:= b + 1\n" +
        "    }\n" +
        "                        \n" +
        "    while c < b {\n" +
        "        c:= c + 1\n" +
        "        a:= a / 2\n" +
        "    }\n" +
        "                        \n" +
        "    b:= a * c\n" +
        "\n" +
        "    printVarTable\n" +
        "}"

class LexerTest {
    @Test
    fun testLexer() {
        /*var str = "var a: int = 5.182"

        var list = ArrayList<String>(8)
        str.split(Regex("\\b")).forEach {val temp = it.trim(); if (temp != "") {list.add(temp)} }

       // print(list)

        var p: Pattern = Pattern.compile("^var")

        var s = str[0].toString() + str[1].toString() + str[2].toString()
        var m: Matcher = p.matcher(s)
        //println(m.find())
        //println(m.group())

        for ((i, line) in source2.lines().withIndex()) {
            val list = ArrayList<String>(8)

            val iterator = line.split(Regex("\\b")).iterator()

            while (iterator.hasNext()) {
                var temp = iterator.next().trim()

                if (temp == ".") {
                    temp = list.removeAt(list.lastIndex) + '.' + iterator.next()
                }
                if (temp != "") {list.add(temp)}
            }

            println(list)
        }*/


        /*Matcher().
        var r= Regex("^var")
        r.
        var result = r.matchEntire("va")!!*/

        //print(result.range)
        var l = Lexer("")

        var tokens = l.lex(source2)
        print(tokens.toString())
    }

}