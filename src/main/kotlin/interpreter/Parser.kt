package interpreter

import util.toRandomAccessIterator
import kotlin.random.Random

class Parser() {
    fun parse(tokens: ArrayList<Token>) {
        val iter = tokens.toRandomAccessIterator()
    }

    fun checkPoint(): Int {
        return 1
    }

    fun revert(a: Int) {}

    fun consume(token: tokenTypeEnum): Boolean {
        return true
    }

    fun tryOnce(l: () -> Boolean): Boolean {
        val a = checkPoint()
        val result = l()
        if (!result) {
            revert(a)
        }
        return result
    }

    fun trySeveral(l: () -> Boolean): Boolean {
        val a = checkPoint()
        var b: Int
        val result = l()
        do {
            b = checkPoint()
            val loopResult = l() //name shadowing intentional
        } while (loopResult)

        if (!result) {
            revert(a)
        } else {//at least 1 success
            revert(b)
        }
        return result
    }

    fun optional(l: () -> Unit): Boolean {
        l()
        return true
    }

    /*fun tryOnce(l: () -> Unit) {
    val a = checkPoint()
    try {
        l()
    }
    catch (e: Exception) {
        revert(a)
    }
}

fun trySeveral(l: () -> Unit) {
    var a = checkPoint()
    try {
        a = checkPoint()
        while (true) {
            l()
        }
    }
    catch (e: Exception) {
        revert(a)
    }
}*/

    fun block() {
        consume(tokenTypeEnum.startBlock)
        trySeveral { decl() }
        trySeveral { stmt() }
        consume(tokenTypeEnum.endBlock)
    }

    fun prog() {
        var finished = false
        do {
            trySeveral { decl() }
            trySeveral { stmt() }
        } while (!finished)
    }

    fun decl(): Boolean {
        return ident() &&
                typeDecl() &&
                optional {
                    ass_op()
                    expr()
                }
    }

    fun typeDecl(): Boolean {
        return true
    }

    fun ass_op(): Boolean {
        return true
    }

}

fun Random.nextBool() = (Random.nextBits(1) == 1)

fun stmt(): Boolean {return Random.nextBool()}
fun expr(): Boolean {return Random.nextBool()}
fun simpExpr(): Boolean {return Random.nextBool()}
fun ident(): Boolean {return Random.nextBool()}