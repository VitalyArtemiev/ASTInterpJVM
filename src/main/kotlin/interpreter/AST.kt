package interpreter

import java.util.*
import kotlin.collections.ArrayList
import kotlin.reflect.KProperty

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


class AST {
    lateinit var root: seqNode
    lateinit var crawler: TreeCrawler<ASTNode>
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