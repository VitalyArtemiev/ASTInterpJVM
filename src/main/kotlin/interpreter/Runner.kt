package interpreter

class Runner {
    fun run(tree: AST) {


    }

    fun executeNode(node: ASTNode) {
        when (node) {
            is binOp -> {
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