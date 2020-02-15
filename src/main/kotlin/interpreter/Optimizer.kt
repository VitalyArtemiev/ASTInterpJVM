package interpreter

import util.Logger
import util.Stack
import kotlin.math.E
import kotlin.reflect.full.memberProperties

class OptimizerException(msg: String): Exception(msg)

class Optimizer (val r: Runner) {
    private val logger = Logger("Optimizer")

    lateinit var varTable: Array<Variable>
    lateinit var constTable: Array<ConstDecl>
    lateinit var functions: Array<FunDecl>

    private val emptyBlockStack = Stack<ASTNode>()
    private val callStack = Stack<Int>()
    private val assignStack = Stack<Int>()

    lateinit var root: ASTNode

    fun optimize(env: Environment): Environment {
        varTable = env.variables
        constTable = env.constants
        functions = env.functions
        root = env.root

        optimizeNode(root, constOnly = false)

        assert(emptyBlockStack.isEmpty())

        for (v in varTable) {
            v.value = null
        }

        logger.i("Optimization finished")

        return env
    }

    /*fun getEnv(): Environment {
        return Environment(root, constTable.clone(), varTable, functions)
    }*/


    var canConst = true

    fun tryConst(node: ASTNode, const: ConstVal ): ASTNode {
        return if (canConst) {
            const
        } else {
            node
        }
    }

    fun optimizeExpr(node: Expr, constOnly: Boolean): Any {
        when (node) {
            is UnOp -> {
                val right = optimizeExpr(node.right, constOnly)
                if (canConst) node.right = ConstVal(right, getExprType(right), node.right.token)
                canConst = true

                return when (node.op) {
                    TokenTypeEnum.unaryMinusOp -> {- right}
                    TokenTypeEnum.unaryPlusOp -> {right}
                    TokenTypeEnum.notOp -> {! right}
                    else -> {throw OptimizerException("Expression evaluation encountered unsupported operator: ${node.op}")}
                }
            }
            is BinOp -> {
                var left = optimizeExpr(node.left, constOnly)
                if (canConst) node.left = ConstVal(left, getExprType(left), node.left.token)
                canConst = true

                var right = optimizeExpr(node.right, constOnly)
                if (canConst) node.right = ConstVal(right, getExprType(right), node.right.token)
                canConst = true

                if (node.op in relOps) {
                    if (left is Float && right is Int) {
                        right = right.toFloat()
                    } else if (left is Int && right is Float) {
                        left = left.toFloat()
                    }
                }

                val result = when (node.op) {
                    TokenTypeEnum.plusOP -> {left + right}
                    TokenTypeEnum.minusOp -> {left - right}
                    TokenTypeEnum.divOp -> {left / right}
                    TokenTypeEnum.multOp -> {left * right}
                    TokenTypeEnum.powOp -> {left pow right}

                    TokenTypeEnum.andOp -> {left and right}
                    TokenTypeEnum.orOP -> {left or right}
                    TokenTypeEnum.xorOp -> {left xor right}

                    TokenTypeEnum.equal -> {left as Comparable<Any> == right} //todo: check if this comparable trick works
                    TokenTypeEnum.less -> {left as Comparable<Any> < right}
                    TokenTypeEnum.greater -> {left as Comparable<Any> > right}
                    TokenTypeEnum.notEqual -> {left as Comparable<Any> != right}
                    TokenTypeEnum.lequal -> {left as Comparable<Any> <= right}
                    TokenTypeEnum.gequal -> {left as Comparable<Any> >= right}
                    else -> {throw OptimizerException("Expression evaluation encountered unsupported operator: $node")}
                }

                return result
            }
            is VarRef -> {
                if (constOnly) {
                    throw OptimizerException("Trying to access variable in a const-only context")
                }
                return varTable[node.varId].value ?:
                throw OptimizerException("Referencing uninitialised variable ${varTable[node.varId]} at $node")
            }
            is ConstRef -> {
                return constTable[node.constId].value
            }
            is ConstVal -> {
                return node.value
            }
            is Call -> {
                return if (node.callee.body is Block && !hasSideEffects(node.callee.body as Block)) {
                    optimizeCall(node, constOnly).value ?: throw OptimizerException("Call execution ($node) returned no value")
                } else {
                    canConst = false
                    executeCall(node).value ?: throw UninitializedPropertyAccessException("Cannot execute external function at compile-time")
                }
                //todo: need to consider side effects
            }
            else -> {
                throw OptimizerException("Expression evaluation encountered unsupported node: $node")
            }
        }
    }

    fun getExprType(result: Any): ValType {
        return when (result) {
            is Int -> ValType.int
            is Float, is Double -> ValType.float
            is Boolean -> ValType.bool
            else -> {
                logger.e("Execution expected Expr result, received $result")
                ValType.none
            }
        }
    }

    class ExecutionResult(val type: ValType, val value: Any?)

    fun optimizeNode(node: ASTNode, constOnly: Boolean): ExecutionResult {
        when (node) {
            is Prog -> {
                for (n in node.nodes) {
                    try {
                        optimizeNode(n, constOnly)
                    } catch (e: UninitializedPropertyAccessException) {
                        val varId = assignStack.pop()
                        varTable[varId].value = null
                        logger.i("Could not evaluate ${varTable[varId]} at compile-time, reason:\n ${e.message!!}")
                    }
                }
                node.nodes.removeIf {it in emptyBlockStack}

                return ExecutionResult(ValType.none, null)
            }
            is Block -> {
                for (n in node.nodes) {
                    try {
                        if (n is Return) {
                            return optimizeNode(n, constOnly)
                        } else {
                            optimizeNode(n, constOnly)
                        }
                    } catch (e: UninitializedPropertyAccessException) {
                        val varId = assignStack.pop()
                        varTable[varId].value = null
                        logger.i("Could not evaluate ${varTable[varId]} at compile-time")

                        logger.l(e.message!!)
                    }
                }
                node.nodes.removeIf {it in emptyBlockStack}

                if (node.nodes.size == 0) {
                    logger.w("Optimizer encountered empty block at $node; attempting to remove")

                    emptyBlockStack.push(node)
                }

                return ExecutionResult(ValType.none, null)
            }

            is Expr -> {
                val result = optimizeExpr(node, constOnly)
                val type = getExprType(result)
                return ExecutionResult(type, result)
            }
            is Call -> {
                return if (node.callee.body is Block && !hasSideEffects(node.callee.body as Block)) {
                    optimizeCall(node, constOnly)
                } else {
                    executeCall(node)
                }
            }
            is CallStmt -> {
                return optimizeCall(node.call, constOnly)
            }
            is VarDecl -> {
                if (node.expr != null) {
                    val result = optimizeNode(node.expr as Expr, constOnly)
                    require(result.type == varTable[node.identifier.refId].type)
                    {"Declaration assignment type error: expected ${varTable[node.identifier.refId].type}, got ${result.type}"}
                    varTable[node.identifier.refId].value = result.value
                }
                return ExecutionResult(ValType.none, null)
            }
            is ConstDecl, is FunDecl -> {
                //todo: hmm
                return ExecutionResult(ValType.none, null)
            }
            is Assign -> {
                assignStack.push(node.identifier.refId)
                val result = optimizeNode(node.expr, constOnly)
                require(result.type == varTable[node.identifier.refId].type)//todo: are these checks needed
                {"Assign type error: expected ${varTable[node.identifier.refId].type}, got ${result.type}"}
                if (canConst) node.expr = ConstVal(result.value!!, result.type, node.expr.token)
                canConst = true

                varTable[node.identifier.refId].value = result.value
                assignStack.pop()
                return ExecutionResult(ValType.none, null)
            }
            is If -> {
                val result = optimizeNode(node.condition, constOnly)
                require(result.type == ValType.bool)//todo: are these checks needed
                {"Condition type error: expected bool, got ${result.type} "}

                if (canConst) node.condition = ConstVal(result.value!!, result.type, node.condition.token)
                canConst = true

                return when (result.value as Boolean) {
                    true -> { optimizeNode(node.s, constOnly) }
                    false -> { ExecutionResult(ValType.none, null) }
                }
            }
            is While -> {
                try {
                    var result = optimizeNode(node.condition, constOnly = true)
                    require(result.type == ValType.bool)
                    { "Condition type error: expected bool, got ${result.type} " }
                } catch (e: OptimizerException) {
                    logger.i("Encountered var in loop condition; leaving")
                }

                //node.condition = ConstVal(result.value!!, result.type, node.condition.token)

                try {
                    optimizeNode(node.s, constOnly = true)
                } catch (e: OptimizerException) {
                    logger.i("Encountered var in loop block; leaving")
                }

                return ExecutionResult(ValType.none, null)
            }
            is Return -> {
                var result = optimizeNode(node.e, constOnly)
                require(result.type == functions[callStack.peek()].retType)
                {"Return type error: expected ${functions[callStack.peek()].retType}, got ${result.type}"}
                return result
            }
            is Async -> {
                optimizeCall(node.c, constOnly)
                return ExecutionResult(ValType.none, null)
            }
            is Await -> {
                return ExecutionResult(ValType.none, null)
            }
            else -> {
                throw OptimizerException("Execution encountered unsupported node: $node")
            }
        }
    }

    fun optimizeCall(node: Call, constOnly: Boolean): ExecutionResult {
        callStack.push(node.callee.identifier.refId)
        val result = when (node.callee.body) {
            is Block -> {
                val signature = node.callee.params
                for ((i, p) in node.params.withIndex()) {
                    val result = optimizeNode(p, constOnly) //get param values
                    if (signature[i].type != result.type) { //check param types
                        throw OptimizerException("Function parameter type mismatch: expected ${signature[i].type}, " +
                                " got $result")
                    }

                    varTable[signature[i].identifier.refId].value = result.value //init param variables
                }

                if (hasSideEffects(node.callee.body as Block)) {
                    try {
                        optimizeNode(node.callee.body, constOnly = true)
                    } catch (e: OptimizerException) {
                        //blockStack.pop()
                        logger.i("Encountered var in function with side effects; leaving")
                        executeNode(node.callee.body)
                    }
                } else {
                    optimizeNode(node.callee.body, constOnly)
                }
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

                ExecutionResult(node.callee.retType, null)
            }
        }
        callStack.pop()
        return result
    }

    fun hasSideEffects(node: Block): Boolean {
        for (n in node.nodes) {
            when (n) {
                is Assign -> {
                    if (n.identifier.scopeIndex != node.scopeIndex) {
                        return true
                    }
                }
                is Call -> {
                    return true //todo: implement recursive checks via passable stack
                }
            }
        }
        return false
    }

    fun evalExpr(node: Expr): Any {
        when (node) {
            is UnOp -> {
                val right = evalExpr(node.right)

                return when (node.op) {
                    TokenTypeEnum.unaryMinusOp -> {- right}
                    TokenTypeEnum.unaryPlusOp -> {right}
                    TokenTypeEnum.notOp -> {! right}
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
                    TokenTypeEnum.plusOP -> {left + right}
                    TokenTypeEnum.minusOp -> {left - right}
                    TokenTypeEnum.divOp -> {left / right}
                    TokenTypeEnum.multOp -> {left * right}
                    TokenTypeEnum.powOp -> {left pow right}

                    TokenTypeEnum.andOp -> {left and right}
                    TokenTypeEnum.orOP -> {left or right}
                    TokenTypeEnum.xorOp -> {left xor right}

                    TokenTypeEnum.equal -> {left as Comparable<Any> == right} //todo: check if this comparable trick works
                    TokenTypeEnum.less -> {left as Comparable<Any> < right}
                    TokenTypeEnum.greater -> {left as Comparable<Any> > right}
                    TokenTypeEnum.notEqual -> {left as Comparable<Any> != right}
                    TokenTypeEnum.lequal -> {left as Comparable<Any> <= right}
                    TokenTypeEnum.gequal -> {left as Comparable<Any> >= right}
                    else -> {throw RunnerException("Expression evaluation encountered unsupported operator: $node")}
                }

                return result
            }
            is VarRef -> {
                return varTable[node.varId].value ?:
                throw UninitializedPropertyAccessException("Referencing uninitialised variable ${varTable[node.varId]} at $node")
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
                    {"Declaration assignment type error: expected ${varTable[node.identifier.refId].type}, got ${result.type}"}
                    varTable[node.identifier.refId].value = result.value
                }
                return ExecutionResult(ValType.none, null)
            }
            is ConstDecl, is FunDecl -> {
                //todo: hmm
                return ExecutionResult(ValType.none, null)
            }
            is Assign -> {
                val result = executeNode(node.expr)
                require(result.type == varTable[node.identifier.refId].type)//todo: are these checks needed
                {"Assign type error: expected ${varTable[node.identifier.refId].type}, got ${result.type}"}

                varTable[node.identifier.refId].value = result.value
                return ExecutionResult(ValType.none, null)
            }
            is If -> {
                val result = executeNode(node.condition)
                require(result.type == ValType.bool)//todo: are these checks needed
                {"Condition type error: expected bool, got ${result.type} "}

                return when (result.value as Boolean) {
                    true -> { executeNode(node.s) }
                    false -> { ExecutionResult(ValType.none, null) }
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

                ExecutionResult(node.callee.retType, null)
            }
        }
        callStack.pop()
        return result
    }
}

class ASTCrawler (val r: Runner) {
    class History(var node: ASTNode) {
        var lastVisited: Int = -1
    }
    val stack = Stack<History>()

    var curNode: ASTNode
        get() = stack.peek().node
        set(value) { stack.push(History(value)) }


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
                TODO()}
            else -> false
        }
    }
}