package interpreter

import interpreter.TokenTypeEnum.*
import interpreter.AST.*
import java.lang.Exception

class Variable (val Name: String, var value: Int)

unaryMinusOp,
notOp, andOp, orOP, xorOp,
identifier, intValue, floatValue, boolValue,

class Runner {
    lateinit var varTable: Array<Variable>

    val callStack = ArrayList<FunDecl>

    fun run(tree: AST) {


    }

    fun evalExpr(node: Expr): Any {
        when (node) {
            is UnOp -> {}
            is BinOp -> {
                var left = evalExpr(node.left)
                var right = evalExpr(node.right)

                var result = when (node.op) {
                    plusOP -> {left + right}
                    minusOp -> {left - right}
                    divOp -> {left / right}
                    multOp -> {left * right}
                    powOp -> {left pow right}
                    andOp -> {left and right}
                    orOP -> {left or right}
                    xorOp -> {left xor right}
                    else -> {throw Exception("Impossibru")}
                }
            }
            is VarRef -> {return varTable[node.]}
            is Constant -> {}
            is Call -> {}
        }
    }

    enum class erType {fail, int, float, bool, none}
    class ExecutionResult(val type: erType, val result: Any?)

    fun executeNode(node: ASTNode): ExecutionResult {
        when (node) {
            is Expr  -> { val result = evalExpr(node)
                when (result is )
            }
        }
    }

    fun evalExpr(node: ASTNode): Number {
        return 0;
    }
}