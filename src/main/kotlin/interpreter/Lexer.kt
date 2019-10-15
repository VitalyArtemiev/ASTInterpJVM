package interpreter

import java.io.File

enum class TokenTypeEnum(pattern: String, val regex: Regex = Regex(pattern)) {
    TBD("TBD"),
    startBlock("^\\{"), endBlock("^\\}"), openParenthesis("^\\("), closeParenthesis("^\\)"),
    colon("^:"), comma("^,"),
    intValue("^(\\d+)"), floatValue("^(\\d+)|(\\d+\\.\\d+((e|E)(-|\\+)?\\d+)?)"), boolValue("^(true|false)"),
    varDecl("^var"), funDecl("^fun"),
    identifier("^\\w+"), type("^(int|float|bool)"),
    ifStmt("^if"), whileStmt("^while"), forStmt("^for"),
    assignOP("^ *:= *"), plusOP("^ *\\+ *"), minusOP("^ *- *"),
    equal("^ *= *"), less("^ *< *"), greater("^ *> *"), lequal("^ *<= *"),
    gequal("^ *>= *"), notEqual("^ *<> *"),
    printVarTable("^\$PRINTVARTABLE"),
    EOF("EOF")
}

public data class  Token (val line: Int, var text: String, var tokenType: TokenTypeEnum = TokenTypeEnum.TBD){
    val tokenized: Boolean
        get() = tokenType == TokenTypeEnum.TBD
}

class Lexer {
    constructor (terminals: String) {

    }

    fun lex(source: String): ArrayList<Token> {
        var result = ArrayList<Token>()

        //for ((i: Int, line: String) in File(source).readLines().withIndex()) {
        for ((i: Int, line: String) in source.lines().withIndex()) {
            result.addAll(getTokens(line, i))
        }

        result.add(Token(0, "", TokenTypeEnum.EOF))
        return result
    }

    private fun getTokens(line: String, lineIndex: Int): Array<Token> {
        val tokens =  ArrayList<Token>()

        val builder = StringBuilder(line.length)

        val matchList = TokenTypeEnum.values().toMutableSet()
        var i = 0
        do {

            while (i < line.length) {
                var curChars = builder.append(line[i])
                i++
                matchList.removeIf {//it.regex.
                    !it.regex.containsMatchIn(curChars) }

                if (matchList.size == 1) {
                    break
                }
            }

            assert(matchList.size == 1, {
                print("More than one match or none")
            })

            val match: TokenTypeEnum = matchList.elementAt(0)

            tokens.add(Token(lineIndex, builder.toString(), match))

            builder.clear()

        } while (i < line.length)


        var n: Int = 1
        var chars = line.take(n)



        return tokens.toTypedArray()
    }
}