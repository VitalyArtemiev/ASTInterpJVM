package interpreter

import util.RandomAccessIterator
import util.toRandomAccessIterator
import kotlin.random.Random

class Parser() {
    lateinit var iter: RandomAccessIterator<Token>

    lateinit var tree: AST

    fun parse(tokens: ArrayList<Token>): AST {
        iter = tokens.toRandomAccessIterator()
        tree = AST()

        prog()

        /*loop@ while (true ) {
            val token = iter.next()
            when (token.tokenType) {
                tokenTypeEnum.TBD -> TODO()
                tokenTypeEnum.startBlock -> TODO()
                tokenTypeEnum.endBlock -> TODO()
                tokenTypeEnum.openParenthesis -> TODO()
                tokenTypeEnum.closeParenthesis -> TODO()
                tokenTypeEnum.value -> TODO()
                tokenTypeEnum.variableDeclaration -> TODO()
                tokenTypeEnum.variableName -> TODO()
                tokenTypeEnum.ifStmt -> TODO()
                tokenTypeEnum.whileStmt -> TODO()
                tokenTypeEnum.forStmt -> TODO()
                tokenTypeEnum.assignOP -> TODO()
                tokenTypeEnum.plusOP -> TODO()
                tokenTypeEnum.minusOP -> TODO()
                tokenTypeEnum.printVarTable -> TODO()
                tokenTypeEnum.EOF -> break@loop
            }
        }*/ //possible optimization

        return tree
    }

    private fun consume(token: tokenTypeEnum): Boolean =  iter.next().tokenType == token

    fun tryOnce(l: () -> Boolean): Boolean {
        val a = iter.save()
        val result = l()
        if (!result) {
            iter.revert(a)
        }
        return result
    }

    fun trySeveral(l: () -> Boolean): Boolean {
        val a = iter.save()
        var b: Int
        val result = l()
        do {
            b = iter.save()
            val loopResult = l() //name shadowing intentional
        } while (loopResult)

        if (!result) {
            iter.revert(a)
        } else {//at least 1 success
            iter.revert(b)
        }
        return result
    }

    fun optional(l: () -> Boolean): Boolean {
        val a = iter.save()
        if (!l()) {
            iter.revert(a)
        }
        return true
    }

    fun prog(): Boolean {
        var finished = false
        do {
            trySeveral { decl() }
            trySeveral { stmt() }
        } while (!finished)

        return true
    }

    fun block() {
        consume(tokenTypeEnum.startBlock)
        trySeveral { decl() }
        trySeveral { stmt() }
        consume(tokenTypeEnum.endBlock)
    }

    fun decl(): Boolean {
        val token = iter.next()

        when (token.tokenType) {
            tokenTypeEnum.varDecl -> {}
            tokenTypeEnum.funDedcl -> {}
            else -> {return(false)}
        }

    }

    fun varDecl(): Boolean {
        return consume(tokenTypeEnum.varDecl) &&
                ident() &&
                typeDecl() &&
                optional {
                    consume(tokenTypeEnum.equal) &&
                    expr()
                }
    }

    fun typeDecl(): Boolean {
        consume(tokenTypeEnum.colon) &&
                type()
        return true
    }

    fun type(): Boolean {
        var token = iter.next()
        if (token.tokenType != tokenTypeEnum.type) {
            return false
        } else {
            token.text
        }
    }

    fun stmt(): Boolean {
        return tryOnce{simplStmt()} ||
                tryOnce{strStmt()}
    }

    private fun simplStmt(): Boolean {
        return tryOnce{assStmt()} ||
                tryOnce{funStmt()}
    }



    private fun assStmt(): Boolean { //final, no need for tryOnce TODO OR IS THERE??? because of expr
        tree.crawler.addNode(assStmt())
        emit
        val result = ident() &&
                consume(tokenTypeEnum.assignOP) &&
                expr()


        commit
        return result
    }

    private fun funStmt(): Boolean {
        return ident() &&
                consume(tokenTypeEnum.openParenthesis) &&
                tryOnce { expr() } &&
                trySeveral {
                    consume(tokenTypeEnum.comma) &&
                    expr()
                } &&
                consume(tokenTypeEnum.closeParenthesis)
    }

    private fun strStmt(): Boolean {

    }

    fun ass_op(): Boolean {
        return true
    }

    fun expr(): Boolean {
        return Random.nextBool()
    }
    fun simpExpr(): Boolean {return Random.nextBool()}

}

fun Random.nextBool() = (Random.nextBits(1) == 1)



fun ident(): Boolean {return Random.nextBool()}