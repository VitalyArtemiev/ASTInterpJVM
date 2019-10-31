package interpreter

import interpreter.TokenTypeEnum.*
import util.toRandomAccessIterator
import java.util.*
import kotlin.collections.ArrayList

enum class eBinOp {bMinus, bPlus, multiply, divide}
enum class eUnOp {uMinus, uPlus}

/*class binOp: ASTNode(2) {

    lateinit var op: eBinOp
    var arg1: ASTNode
        get() = children[0]!!
        set(node: ASTNode) {
            children[0] = node
        }
    var arg2: ASTNode
        get() = children[1]!!
        set(node: ASTNode) {
            children[1] = node
        }
}

class unOp: ASTNode(1) {
    lateinit var op: eBinOp
    var arg: ASTNode
        get() = children[0]!!
        set(node: ASTNode) {
            children[0] = node
        }
}

class value(): ASTNode(0) {
    var value: Int = 0
}

class variable: ASTNode(0) {
    var index: Int = -1
}*/

class AST(tokens: ArrayList<Token>) {
    val iter = tokens.toRandomAccessIterator()
    var root = Prog()
    lateinit var crawler: TreeCrawler
    //lateinit var varList:

    open class ASTNode{
        var lineIndex: Int = 0
        var text: String = ""
    }

    inner class Prog() : ASTNode() {
        val nodes = ArrayList<ASTNode>(16)

        init {
            var token = iter.peek()
            var i = 0
            while (token.tokenType != EOF) {
                when (token.tokenType) {
                    varDecl, funDecl -> nodes[i++] = Decl() //todo: add line information
                    identifier, startBlock, ifStmt, whileStmt, retStmt ->  nodes[i++] = getStmt()
                    else -> {
                        println("decl or stmt expected but $token found")
                        iter.next()
                    }
                }
                token = iter.peek()
            }
        }
    }

    open inner class Decl: ASTNode()

    fun getStmt(): Stmt {
        var token = iter.next()
        return when (token.tokenType) {
            ifStmt -> If(getExpr(), getStmt())
            whileStmt -> While(getExpr(), getStmt())
            retStmt -> Return(getExpr())
            startBlock -> Block()
            else -> throw Exception("This should not be possible")
        }
    }
    open inner class Stmt: ASTNode()
    inner class If(val condition: Expr, val s: Stmt): Stmt() {
        init {
            lineIndex = 0//todo: add line information
            text = ""
        }
    }
    inner class While(val e: Expr, val s: Stmt): Stmt() //s has to be block
    inner class Return(val e: Expr): Stmt()
    inner class Block(): Stmt()

    fun getExpr(): Expr {
        var token = iter.next()
        when (token.tokenType) {
            ifStmt -> return()
        }
    }
    open inner class Expr: ASTNode()
    inner class Assign(val)


}

class TreeCrawler {
    class History(var node: AST.ASTNode) {
        var lastVisited: Int = -1
    }
    val stack = Stack<History>()
    
    lateinit var curNode: ASTNode
    
    fun visitChild(index: Int) {
        curNode = curNode.children[index]
    }
}

