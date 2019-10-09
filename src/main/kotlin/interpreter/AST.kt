package interpreter

import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.KProperty

open class ASTNode(numChildren: Int) {
    var LineIndex: Int = 0
    var text: String = ""
    protected lateinit var children: ArrayList<ASTNode>
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

    private var curNodeIndex = 0

    val curNode: ASTNode? //todo: test, possible bug
        get() = nodes[curNodeIndex]

    fun advance() {
        curNodeIndex += 1
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


class AST {
    lateinit var root: seqNode
    lateinit var crawler: TreeCrawler<ASTNode> = TreeCrawler(seqNode)
}

class TreeCrawler<T> {
    class History<T>(var node: T) {
        var lastVisited: Int = -1
    }
    val stack = Stack<History<T>>()

}