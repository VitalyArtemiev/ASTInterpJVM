package interpreter

import java.io.File

public enum class tokenTypeEnum {TBD,
    startBlock, endBlock, openParenthesis, closeParenthesis,
    colon, comma,
    value,
    varDecl, funDedcl,
    identifier, type,
    ifStmt, whileStmt, forStmt,
    assignOP, plusOP, minusOP,
    equal, less, greater, lequal, gequal, notEqual,
    printVarTable,
    EOF}

public data class  Token (val line: Int, var text: String, var tokenType: tokenTypeEnum = tokenTypeEnum.TBD){
    val tokenized: Boolean
        get() = tokenType == tokenTypeEnum.TBD
}

class Lexer(var terminals: String) {
    fun lex(source: String): ArrayList<Token> {
        var result = ArrayList<Token>()

        for ((i: Int, line: String) in File(source).readLines().withIndex()) {
            result.addAll(getTokens(line, i))
        }

        result.add(Token(0, "", tokenTypeEnum.EOF))
        return result
    }

    private fun getTokens(line: String, i: Int): Array<Token> {
        val tokens =  ArrayList<Token>()

        return tokens.toTypedArray()
    }
}