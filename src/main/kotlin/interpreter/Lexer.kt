package interpreter

import java.io.File

enum class TokenTypeEnum(pattern: String, val regex: Regex = Regex(pattern)) {
    TBD("(?!x)x"), //match none
    lineComment("^#"),
    startBlock("^\\{"), endBlock("^\\}"), openParenthesis("^\\("), closeParenthesis("^\\)"),
    colon("^:"), comma("^,"),
    intValue("^(\\d+)"), floatValue("^(\\d+)|(\\d+\\.\\d+((e|E)(-|\\+)?\\d+)?)"), boolValue("^(true|false)"),
    varDecl("^var"), funDecl("^fun"),
    ifStmt("^if"), whileStmt("^while"), forStmt("^for"), retStmt("^return"),
    printVarTable("^_PRINTVARTABLE"),
    type("^(int|float|bool)"), identifier("^\\w+"),
    assignOp("^:="), memberOp("^\\."),
    plusOP("^\\+"), minusOp("^-"),
    multOp("^\\*"), divOp("^/"),
    equal("^="), less("^<"), greater("^>"),
    notEqual("^<>"), lequal("^<="), gequal("^>="),
    EOF("(?!x)x") //match none
}

public data class  Token (val line: Int, var text: String, var tokenType: TokenTypeEnum = TokenTypeEnum.TBD) {
    val isTBD = {
        print("WTF TBD $this")
        tokenType == TokenTypeEnum.TBD
    }
}

class Lexer {
    constructor (terminals: String) {

    }

    val errors = ArrayList<Pair<Int, String>>()

    fun lex(source: String): ArrayList<Token> {
        val result = ArrayList<Token>()

        val lines = File(source).readLines()

        for ((i, line: String) in lines.withIndex()) {
            result.addAll(getTokens(line, i))
        }

        result.add(Token(lines.size, "End of file <$source>", TokenTypeEnum.EOF))
        return result
    }

    private fun getTokens(line: String, lineIndex: Int): Array<Token> {
        val list = ArrayList<Token>(8)

        val iterator = line.split(Regex("\\b")).iterator()

        while (iterator.hasNext()) {
            var str = iterator.next().trim()

            if (str == "") {continue}

            if (str == ".") {
                if ((list.last().tokenType == TokenTypeEnum.intValue)) {
                    str = list.removeAt(list.lastIndex).text + '.' + iterator.next()
                }
            }

            var matchFound: TokenTypeEnum  = match(str)

            var tokenText = str //needed for last add
            while (matchFound == TokenTypeEnum.TBD) {
                tokenText = tokenText.dropLast(1) //1st op before loop to optimise break

                matchFound = match(tokenText)

                forLoop@ for (i in 1 until str.length) {
                    if (matchFound != TokenTypeEnum.TBD) {
                        list.add(Token(lineIndex, tokenText, matchFound))

                        tokenText = str.drop(str.length - i).trimStart()
                        matchFound = match(tokenText)

                        break@forLoop
                    }

                    tokenText = tokenText.dropLast(1)
                    matchFound = match(tokenText)
                }

                if (tokenText == "") { //doesnt match anything
                    errors.add(Pair(lineIndex, str))
                    break
                }
            }

            list.add(Token(lineIndex, tokenText, matchFound))
        }

        return list.toTypedArray()
    }

    private fun match(s: String): TokenTypeEnum {
        var matchFound: TokenTypeEnum = TokenTypeEnum.TBD
        for (pattern in TokenTypeEnum.values()) {
            if (pattern.regex.matches(s)) {
                matchFound = pattern
                break
            }
        }
        return matchFound
    }
}