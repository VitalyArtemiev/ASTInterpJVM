package interpreter

import util.Logger
import util.Stack
import kotlin.reflect.full.memberProperties

class ASTCrawler(val logger: Logger = Logger("Crawler")) {
    class History(var node: ASTNode) {
        var lastVisited: Int = -1
        override fun toString(): String {
            return node.toString()
        }
    }

    val stack = Stack<History>()
    val path = Stack<History>()

    var curNode: ASTNode
        get() = stack.peek().node
        set(value) {
            stack.push(History(value))
        }

    fun pop(): History {
        path.push(History(curNode))
        val result = stack.pop()
        return result
    }

    fun printStack() {
        logger.d("Current AST branch:")
        logger.l(stack.toString())
    }

    fun printPath() {
        logger.d("Path taken:")
        logger.l(path.toString())
    }

    fun visitChild(node: ASTNode) {
        for (p in curNode::class.memberProperties) {
            val v = p.getter.call(curNode)
            when (v) {
                is Prog -> {
                    for (n in v.nodes) {
                        visitChild(n)
                    }
                }
                is Block -> {
                    for (n in v.nodes) {
                        visitChild(n)
                    }
                }

                is ASTNode -> {
                    if (!checkLeaf(v)) {
                        visitChild(node)
                    } else {
                        //r.evalExpr(v)

                    }
                }
            }
        }

        //curNode = curNode.children[index]
    }

    fun checkLeaf(node: ASTNode): Boolean {
        return when (node) {
            is ConstVal, is ConstRef -> true
            is VarRef -> {
                TODO()
            }
            else -> false
        }
    }
}