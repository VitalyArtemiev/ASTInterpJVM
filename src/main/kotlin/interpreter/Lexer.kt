package interpreter

enum class TokenTypeEnum(pattern: String, val regex: Regex = Regex(pattern)) {
    TBD("(?!x)x"),
    startBlock("^\\{"), endBlock("^\\}"), openParenthesis("^\\("), closeParenthesis("^\\)"),
    colon("^:"), comma("^,"),
    intValue("^(\\d+)"), floatValue("^(\\d+)|(\\d+\\.\\d+((e|E)(-|\\+)?\\d+)?)"), boolValue("^(true|false)"),
    varDecl("^var"), funDecl("^fun"),
    type("^(int|float|bool)"), identifier("^\\w+"),
    ifStmt("^if"), whileStmt("^while"), forStmt("^for"),
    assignOP("^:="),
    plusOP("^\\+"), minusOP("^-"),
    multOp("^\\*"), divOp("^/"),
    equal("^="), less("^<"), greater("^>"),
    notEqual("^<>"), lequal("^<="), gequal("^>="),
    printVarTable("^\$PRINTVARTABLE"),
    EOF("(?!x)x")
}

public data class  Token (val line: Int, var text: String, var tokenType: TokenTypeEnum = TokenTypeEnum.TBD)

class Lexer {
    constructor (terminals: String) {

    }

    fun lex(source: String): ArrayList<Token> {
        val result = ArrayList<Token>()

        //for ((i: Int, line: String) in File(source).readLines().withIndex()) {
        for ((i: Int, line: String) in source.lines().withIndex()) {
            result.addAll(getTokens(line, i))
        }

        result.add(Token(0, "", TokenTypeEnum.EOF))
        return result
    }

    private fun getTokens(line: String, lineIndex: Int): Array<Token> {
        val list = ArrayList<Token>(8)

        val iterator = line.split(Regex("\\b")).iterator()

        while (iterator.hasNext()) {
            var str = iterator.next().trim()

            if (str == ".") {
                str = list.removeAt(list.lastIndex).text + '.' + iterator.next()
            }
            if (str == "") {continue}

            var matchFound: TokenTypeEnum  = match(str)

            var tokenText = str //needed for last add
            while (matchFound == TokenTypeEnum.TBD) {
                tokenText = tokenText.dropLast(1) //1st op before loop to optimise break
                matchFound = match(tokenText)

                forLoop@ for (i in 1 until str.length) {
                    if (matchFound != TokenTypeEnum.TBD) {
                        list.add(Token(lineIndex, tokenText, matchFound))
                        tokenText = str.drop(str.length - i)
                        matchFound = match(tokenText)

                        break@forLoop
                    }

                    tokenText = tokenText.dropLast(1)
                    matchFound = match(tokenText)
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