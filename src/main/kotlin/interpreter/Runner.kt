package interpreter

class Variable (val Name: String, var value: Int)


class Runner {
    lateinit var varTable: Array<Variable>

    fun run(tree: AST) {


    }

    fun executeNode(node: ASTNode) {
        when (node) {
            is binOp -> {
                var leftVal: Number
                var rightVal: Number

                var left = node.arg1
                if (left is value) {
                    leftVal = left.value
                }
                else {
                    leftVal = evalExpr(left) //obv wrong because may be variable
                }

                var right = node.arg2
                if (right is value) {
                    rightVal = right.value
                }
                else {
                    rightVal = evalExpr(right)
                }

                when (node.op) {
                    eBinOp.bMinus -> {
                        var result = leftVal as Int - rightVal as Int}
                    eBinOp.bPlus -> TODO()
                    eBinOp.multiply -> TODO()
                    eBinOp.divide -> TODO()
                }
            }
        }
    }

    fun evalExpr(node: ASTNode): Number {
        return 0;
    }
}