package interpreter

import interpreter.TokenTypeEnum.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import util.Logger
import util.Stack
import kotlin.reflect.full.memberProperties
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

    val crawler = ASTCrawler()

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
        crawler.curNode = node

        val result = when (node) {
            is UnOp -> {
                val right = evalExpr(node.right)

                when (node.op) {
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

                when (node.op) {
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
            }
            is VarRef -> {
                varTable[node.varId].value ?:
                throw RunnerException("Referencing uninitialised variable ${varTable[node.varId]} at $node")
            }
            is ConstRef -> {
                constTable[node.constId].value
            }
            is ConstVal -> {
                node.value
            }
            is Call -> {
                executeCall(node).value ?:
                throw RunnerException("Call execution ($node) returned no value")
            }
            else -> {
                throw RunnerException("Expression evaluation encountered unsupported node: $node")
            }
        }

        crawler.pop()
        return result
    }

    fun executeNode(node: ASTNode): ExecutionResult {
        crawler.curNode = node

        val result = when (node) {
            is Prog -> {
                for (n in node.nodes) {
                    executeNode(n)
                }

                ExecutionResult(ValType.none, null)
            }
            is Block -> {
                if (node.nodes.size == 0) {
                    logger.w("Runner encountered empty block at $node")
                }

                var result: ExecutionResult = ExecutionResult(ValType.none, null)

                for (n in node.nodes) {
                    if (n is Return) {
                        result = executeNode(n)
                        break
                    } else {
                        executeNode(n)
                    }
                }

                result
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

                ExecutionResult(type, result)
            }
            is Call  -> {
                executeCall(node)
            }
            is CallStmt -> {
                executeCall(node.call)
            }
            is VarDecl -> {
                if (node.expr != null) {
                    val result = executeNode(node.expr as Expr)
                    require(result.type == varTable[node.identifier.refId].type)
                    { "Declaration assignment type error: expected ${varTable[node.identifier.refId].type}, got ${result.type}" }
                    varTable[node.identifier.refId].value = result.value
                }
                ExecutionResult(ValType.none, null)
            }
            is ConstDecl -> {
                ExecutionResult(ValType.none, null)
            }
            is FunDecl -> {
                //Todo: var alloc for recursive function calls
                ExecutionResult(ValType.none, null)
            }
            is Assign -> {
                val result = executeNode(node.expr)
                require(result.type == varTable[node.identifier.refId].type)//todo: are these checks needed
                { "Assign type error: expected ${varTable[node.identifier.refId].type}, got ${result.type}" }

                varTable[node.identifier.refId].value = result.value
                ExecutionResult(ValType.none, null)
            }
            is If -> {
                val result = executeNode(node.condition)
                require(result.type == ValType.bool)//todo: are these checks needed
                { "Condition type error: expected bool, got ${result.type} " }

                when (result.value as Boolean) {
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

                ExecutionResult(ValType.none, null)
            }
            is Return -> {
                val result = executeNode(node.e)
                require(result.type == functions[callStack.peek()].retType)
                    {"Return type error: expected ${functions[callStack.peek()].retType}, got ${result.type}"}

                result
            }
            is Async -> {
                val j = GlobalScope.launch {
                    val result = executeCall(node.c)
                    require(result.type == varTable[node.id.refId].type)//todo: are these checks needed
                    {"Async assign type error: expected ${varTable[node.id.refId].type}, got ${result.type}"}
                    varTable[node.id.refId].value = result.value
                }

                asyncVars.add(Pair(node.id.refId, j))

                ExecutionResult(ValType.none, null)
            }
            is Await -> {
                val index = asyncVars.indexOfFirst { it.first == node.id.refId}
                if (index == -1) {
                    throw RunnerException("Awaiting nonexistant async identifier ${node.id}")
                }
                val (_, j) = asyncVars[index]
                while (!j.isCompleted) {}
                //println("await completed")
                asyncVars.removeAt(index)

                ExecutionResult(ValType.none, null)
            }
            else -> {
                throw RunnerException("Execution encountered unsupported node: $node")
            }
        }

        crawler.pop()
        return result
    }

    fun executeCall(node: Call): ExecutionResult {
        crawler.curNode = node

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
                    is Unit -> {ValType.none}
                    else -> {throw RunnerException("Unsupported return type from precompiled function: $result")}
                }

                ExecutionResult(type, result)
            }
        }
        callStack.pop()

        crawler.pop()
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

    fun printLineInfo(params: Params?) {
        print(util.PURPLE)
        println("Line ${crawler.curNode.token.line}, word ${crawler.curNode.token.numInLine}")
        print(util.RESET)

        crawler.printStack()
    }
}

class ASTCrawler {
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

