package interpreter

import interpreter.TokenTypeEnum.*
import interpreter.AST.*
import util.Logger
import kotlin.Exception

class Variable (val name: String, val type: ValType, var value: Any?) { //member of varTable
    override fun toString(): String {
        return "$name: $type = $value"
    }
}

class Runner(env: Environment) {
    val logger = Logger("Runner")

    val varTable: Array<Variable> = env.variables
    val constTable: Array<ConstDecl> = env.constants
    //val functions = env.functions

    val callStack = ArrayList<FunDecl>()

    val root = env.root

    fun run() {
        executeNode(root)
    }

    fun evalExpr(node: Expr): Any {
        when (node) {
            is UnOp -> {
                val right = evalExpr(node.right)
                val result = when (node.op) {
                    unaryMinusOp -> {- right}
                    notOp -> {! right}
                    else -> {throw Exception("Expression evaluation encountered unsupported operator: $node")}
                }

                return result
            }
            is BinOp -> {
                var left = evalExpr(node.left)
                var right = evalExpr(node.right)

                val result = when (node.op) {
                    plusOP -> {left + right}
                    minusOp -> {left - right}
                    divOp -> {left / right}
                    multOp -> {left * right}
                    powOp -> {left pow right}

                    andOp -> {left and right}
                    orOP -> {left or right}
                    xorOp -> {left xor right}

                    equal -> {left as Comparable<Any> == right} //todo: check if this comparable trick works
                    less -> {left as Comparable<Any> < right as Comparable<Any>}
                    greater -> {left as Comparable<Any> > right}
                    notEqual -> {left as Comparable<Any> != right}
                    lequal -> {left as Comparable<Any> <= right}
                    gequal -> {left as Comparable<Any> >= right}
                    else -> {throw Exception("Expression evaluation encountered unsupported operator: $node")}
                }

                return result
            }
            is VarRef -> {
                return varTable[node.varId].value ?: throw Exception("Referencing uninitialised variable")
            }
            is ConstRef -> {
                return constTable[node.constId].value
            }
            is ConstVal -> {
                return node.value
            }
            is Call -> {
                return executeCall(node).value ?: throw Exception("Call execution ($node) returned no value")
            }
            else -> {
                throw Exception("Expression evaluation encountered unsupported node: $node")
            }
        }
    }

    class ExecutionResult(val type: ValType, val value: Any?)

    fun executeNode(node: ASTNode): ExecutionResult {
        when (node) {
            is Prog -> {
                for (n in node.nodes) {
                    executeNode(n)
                }
                return ExecutionResult(ValType.none, null)
            }
            is Block -> {
                for (n in node.nodes) {
                    executeNode(n)
                }
                return ExecutionResult(ValType.none, null)
            }

            is Expr  -> {
                val result = evalExpr(node)
                val type = when (result) {
                    is Int -> ValType.int
                    is Float -> ValType.float
                    is Boolean -> ValType.bool
                    else -> {
                        logger.e("Execution expected Expr, received $result")
                        ValType.none
                    }
                }
                return ExecutionResult(type, result)
            }
            is Call  -> {
                return executeCall(node)
            }
            else -> {
                throw Exception("Execution encountered unsupported node: $node")
            }
        }


    }

    fun executeCall(node: Call): ExecutionResult {
        when (node.callee.body) {
            is Block -> {
                val signature = node.callee.params
                for ((i, p) in node.params.withIndex()) {
                    val result = executeNode(p) //get param values
                    if (signature[i].type != result.type) { //check param types
                        throw Exception("Function parameter type mismatch: expected ${signature[i].type}, " +
                                " got $result")
                    }

                    varTable[signature[i].identifier.refId].value = result.value //init param variables
                }

                return executeNode(node.callee.body)
            } //execute function body block
            is PrecompiledBlock -> {
                val signature = node.callee.params
                val parResults = Params(node.callee.params.size) {null}
                for ((i, p) in node.params.withIndex()) {
                    val result = executeNode(p) //get param values
                    if (signature[i].type != result.type) { //check param types
                        throw Exception("Precompiled function parameter type mismatch: expected ${signature[i].type}, " +
                                " got $result")
                    }

                    parResults[i] = result.value //init param variables
                }
                val result = (node.callee.body as PrecompiledBlock).f(parResults)
                val type: ValType = when(result) {
                    is Int -> {ValType.int}
                    is Float -> {ValType.float}
                    is Boolean -> {ValType.bool}
                    else -> {throw Exception("Unsupported return type from precompiled function: $result")}
                }

                return ExecutionResult(type, result)
            }
            else -> {
                throw Exception("WTF") // for some reason sealed class is incompatible with inner
            }
        }
    }

    fun printVarTable() {
        for (v in varTable) {
            println(v)
        }
    }
}