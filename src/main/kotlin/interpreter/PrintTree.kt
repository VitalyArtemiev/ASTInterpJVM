package interpreter

import kotlin.reflect.full.memberProperties


/*fun ASTNode.printTree(): String {
    return ""
}

fun Prog.printTree(): String {
    var s = ""
    for (n in nodes) {
        s += n.printTree()
    }
    return(" ".repeat(s.length / 2) + "Prog\n" + s)
}*/

fun printTree(node: ASTNode): String {
    var result = ""
    when (node) {
        is Prog -> {
            var s = ""
            for (n in node.nodes) {
                s += printTree(n)
            }
            result += (" ".repeat(s.length / 2) + "Prog\n" + s)
        }
        is Block -> {
            var s = ""
            for (n in node.nodes) {
                s += printTree(n)
            }
            result += (" ".repeat(s.length / 2) + "Block\n" + s)
        }
        is Call -> {}
        else -> {
            for (p in node::class.memberProperties) {
                val v = p.getter.call(node)
                var s = ""
                when (v) {
                    is ASTNode -> {
                        s +=  printTree(v) + " "
                    }
                }
                var name = node.toString()
                var pad = s.length  / 2 - name.length / 2
                if (pad < 0) {
                    pad = 0
                }
                if (s != "") {
                    result += (" ".repeat(pad) + name + "\n" + s)
                } else {
                    result += name + " "
                }
            }
        }
    }

    return result
}

class TreePrinter {



    fun checkLeaf(node: ASTNode): Boolean {
        return when (node) {
            is ConstVal, is ConstRef -> true
            is VarRef -> {
                TODO()}
            else -> false
        }
    }
}