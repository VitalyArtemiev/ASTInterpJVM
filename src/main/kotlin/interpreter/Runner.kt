package interpreter

class Variable (val Name: String, var value: Int)


class Runner {
    lateinit var varTable: Array<Variable>

    fun run(tree: AST) {


    }

    fun executeNode(node: ASTNode) {
        when (node) {
            is binOp -> {
                var left = node.arg1
                if (left is )

                var right = node.arg2
                when (node.op) {
                    eBinOp.bMinus -> {
                        var result = node.arg1 - node.arg2}
                    eBinOp.bPlus -> TODO()
                    eBinOp.multiply -> TODO()
                    eBinOp.divide -> TODO()
                }
            }
        }
    }
}