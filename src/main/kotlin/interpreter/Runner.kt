package interpreter

import interpreter.TokenTypeEnum.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import util.Logger
import util.Stack
import kotlin.system.measureNanoTime

class RunnerException(msg: String) : Exception(msg)

class Variable(val name: String, val type: ValType, var value: Any?) { //member of varTable
    override fun toString(): String {
        return "$name: $type = $value"
    }
}

class ExecutionResult(val type: ValType, val value: Any?)

open class Runner {
    private val logger = Logger("Runner")

    lateinit var varTable: Array<Variable>
    lateinit var constTable: Array<ConstDecl>
    lateinit var functions: Array<FunDecl>

    private val asyncVars = ArrayList<Pair<Int, Job>>()

    private val callStack = Stack<Int>()

    lateinit var root: ASTNode

    fun run(env: Environment) {
        varTable = env.variables
        constTable = env.constants
        functions = env.functions
        root = env.root

        logger.i("Execution started")

        val timeTaken = measureNanoTime {
            executeNode(root)
        }

        if (!asyncVars.isEmpty()) {
            logger.w("Not all async threads were terminated")
        }
        logger.i("Execution ended")

        logger.i("Time taken: $timeTaken nanoseconds")
    }

    fun evalExpr(node: Expr): Any {
        when (node) {
            is UnOp -> {
                val right = evalExpr(node.right)

                return when (node.op) {
                    unaryMinusOp -> {- right}
                    unaryPlusOp -> {right}
                    notOp -> {! right}
                    else -> {throw RunnerException("Expression evaluation encountered unsupported operator: ${node.op}")}
                }
            }
            is BinOp -> {
                var left = evalExpr(node.left)
                var right = evalExpr(node.right)

                if (node.op in relOps) {
                    if (left is Float && right is Int) {
                        right = right.toFloat()
                    } else if (left is Int && right is Float) {
                        left = left.toFloat()
                    }
                }

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
                    less -> {left as Comparable<Any> < right}
                    greater -> {left as Comparable<Any> > right}
                    notEqual -> {left as Comparable<Any> != right}
                    lequal -> {left as Comparable<Any> <= right}
                    gequal -> {left as Comparable<Any> >= right}
                    else -> {throw RunnerException("Expression evaluation encountered unsupported operator: $node")}
                }

                return result
            }
            is VarRef -> {
                return varTable[node.varId].value ?:
                throw RunnerException("Referencing uninitialised variable ${varTable[node.varId]} at $node")
            }
            is ConstRef -> {
                return constTable[node.constId].value
            }
            is ConstVal -> {
                return node.value
            }
            is Call -> {
                return executeCall(node).value ?: throw RunnerException("Call execution ($node) returned no value")
            }
            else -> {
                throw RunnerException("Expression evaluation encountered unsupported node: $node")
            }
        }
    }

    fun executeNode(node: ASTNode): ExecutionResult {
        when (node) {
            is Prog -> {
                for (n in node.nodes) {
                    executeNode(n)
                }
                return ExecutionResult(ValType.none, null)
            }
            is Block -> {
                if (node.nodes.size == 0) {
                    logger.w("Runner encountered empty block at $node")
                }

                for (n in node.nodes) {
                    if (n is Return) {
                        return executeNode(n)
                    } else {
                        executeNode(n)
                    }
                }

                return ExecutionResult(ValType.none, null)
            }
            is Expr  -> {
                val result = evalExpr(node)
                val type = when (result) {
                    is Int -> ValType.int
                    is Float, is Double -> ValType.float
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
            is CallStmt -> {
                return executeCall(node.call)
            }
            is VarDecl -> {
                if (node.expr != null) {
                    val result = executeNode(node.expr as Expr)
                    require(result.type == varTable[node.identifier.refId].type)
                    { "Declaration assignment type error: expected ${varTable[node.identifier.refId].type}, got ${result.type}" }
                    varTable[node.identifier.refId].value = result.value
                }
                return ExecutionResult(ValType.none, null)
            }
            is ConstDecl -> {
                return ExecutionResult(ValType.none, null)
            }
            is FunDecl -> {
                //Todo: var alloc for recursive function calls
                return ExecutionResult(ValType.none, null)
            }
            is Assign -> {
                val result = executeNode(node.expr)
                require(result.type == varTable[node.identifier.refId].type)//todo: are these checks needed
                { "Assign type error: expected ${varTable[node.identifier.refId].type}, got ${result.type}" }

                varTable[node.identifier.refId].value = result.value
                return ExecutionResult(ValType.none, null)
            }
            is If -> {
                val result = executeNode(node.condition)
                require(result.type == ValType.bool)//todo: are these checks needed
                { "Condition type error: expected bool, got ${result.type} " }

                return when (result.value as Boolean) {
                    true -> {
                        executeNode(node.s)
                    }
                    false -> {
                        ExecutionResult(ValType.none, null)
                    }
                }
            }
            is While -> {
                var result = executeNode(node.condition)
                require(result.type == ValType.bool)
                    {"Condition type error: expected bool, got ${result.type} "}
                while (result.value as Boolean) {
                    executeNode(node.s)
                    result = executeNode(node.condition)
                }
                return ExecutionResult(ValType.none, null)
            }
            is Return -> {
                var result = executeNode(node.e)
                require(result.type == functions[callStack.peek()].retType)
                    {"Return type error: expected ${functions[callStack.peek()].retType}, got ${result.type}"}
                return result
            }
            is Async -> {
                val j = GlobalScope.launch {
                    val result = executeCall(node.c)
                    require(result.type == varTable[node.id.refId].type)//todo: are these checks needed
                    {"Async assign type error: expected ${varTable[node.id.refId].type}, got ${result.type}"}
                    varTable[node.id.refId].value = result.value
                }

                asyncVars.add(Pair(node.id.refId, j))
                return ExecutionResult(ValType.none, null)
            }
            is Await -> {
                val index = asyncVars.indexOfFirst { it.first == node.id.refId}
                if (index == -1) {
                    throw RunnerException("Awaiting nonexistant async identifier ${node.id}")
                }
                val (_, j) = asyncVars[index]
                while (!j.isCompleted) {}
                //println("awaait completed")
                asyncVars.removeAt(index)
                return ExecutionResult(ValType.none, null)
            }
            else -> {
                throw RunnerException("Execution encountered unsupported node: $node")
            }
        }
    }

    fun executeCall(node: Call): ExecutionResult {
        callStack.push(node.callee.identifier.refId)
        val result = when (node.callee.body) {
            is Block -> {
                val signature = node.callee.params
                for ((i, p) in node.params.withIndex()) {
                    val result = executeNode(p) //get param values
                    if (signature[i].type != result.type) { //check param types
                        throw RunnerException("Function parameter type mismatch: expected ${signature[i].type}, " +
                                " got $result")
                    }

                    varTable[signature[i].identifier.refId].value = result.value //init param variables
                }

                executeNode(node.callee.body)
            } //execute function body block
            is PrecompiledBlock -> {
                val signature = node.callee.params
                val parResults = Params(node.callee.params.size) {null}
                for ((i, p) in node.params.withIndex()) {
                    val result = executeNode(p) //get param values
                    if (signature[i].type != result.type && signature[i].type != ValType.any) { //check param types
                        throw RunnerException("Precompiled function parameter type mismatch: expected ${signature[i].type}," +
                                " got ${result.type}")
                    }

                    parResults[i] = result.value //init param variables
                }

                val result = (node.callee.body as PrecompiledBlock).f(parResults)
                val type: ValType = when(result) {
                    is Int -> {ValType.int}
                    is Float -> {ValType.float}
                    is Boolean -> {ValType.bool}
                    is Unit -> {return ExecutionResult(ValType.none, null)}
                    else -> {throw RunnerException("Unsupported return type from precompiled function: $result")}
                }

                ExecutionResult(type, result)
            }
        }
        callStack.pop()
        return result
    }

    fun printVarTable(params: Params?) {
        println("${util.PURPLE}Variables: ${util.CYAN}")
        for (v in varTable) {
            println(v)
        }
        println(util.RESET)

        println("${util.PURPLE}Constants: ${util.CYAN}")
        for (v in constTable) {
            println(v)
        }
        println(util.RESET)
    }
}
