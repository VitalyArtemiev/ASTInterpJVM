package interpreter

import interpreter.TokenTypeEnum.*
import util.toRandomAccessIterator
import java.util.*
import kotlin.collections.ArrayList

open class ASTNode(numChildren: Int) {
    var LineIndex: Int = 0
    var text: String = ""
    var children: ArrayList<ASTNode> = ArrayList(numChildren)
}

class nodeList
class seqNode(numNodes: Int): ASTNode(numNodes)  {
    var nodes = nodeList() //such fucking jank
    operator fun nodeList.get(index: Int): ASTNode {
        return children[index]!!
    }
    operator fun nodeList.set(index: Int, value: ASTNode) {
        children[index] = value
    }

    fun addNode(node: ASTNode) {
        children.add(node)
    }
}

enum class eBinOp {bMinus, bPlus, multiply, divide}
enum class eUnOp {uMinus, uPlus}

class binOp: ASTNode(2) {

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
}

class AST(tokens: ArrayList<Token>) {
    val iter = tokens.toRandomAccessIterator()
    var root = Prog()
    lateinit var crawler: TreeCrawler
    //lateinit var varList:

    open class ASTNode(numChildren: Int) {
        var LineIndex: Int = 0
        var text: String = ""
        var children: ArrayList<ASTNode> = ArrayList(numChildren)
    }

    inner class Prog: ASTNode {
        constructor() : super(4) {
            var token = iter.peek()
            var i = 0
            while (token.tokenType != EOF) {
                when (token.tokenType) {
                    varDecl, funDecl -> children[i++] = Decl()
                    identifier, startBlock, ifStmt, whileStmt ->  children[i++] = Stmt()
                    else -> {
                        println("decl or stmt expected")
                        iter.next()
                    }
                }
            }
        }
    }

    open inner class Decl: ASTNode(3) {

    }

    open class Stmt: ASTNode(2) {

    }

    open class Expr: ASTNode(2) {

    }

    class If(val e: Expr, val s: Stmt): Stmt() {

    }
}

class TreeCrawler {
    class History(var node: ASTNode) {
        var lastVisited: Int = -1
    }
    val stack = Stack<History>()
    
    lateinit var curNode: ASTNode
    
    fun visitChild(index: Int) {
        curNode = curNode.children[index]
    }
}

