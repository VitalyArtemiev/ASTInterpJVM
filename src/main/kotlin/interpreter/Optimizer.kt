package interpreter

import util.Logger
import util.Stack

class OptimizerException(msg: String): Exception(msg)

class Optimizer: Runner() {
    private val logger = Logger("Optimizer")

    override val crawler = ASTCrawler(logger)

    private val emptyBlockStack = Stack<ASTNode>()
    private val callStack = Stack<Int>()
    private val assignStack = Stack<Int>()

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

    var canOmitCall = true //when function has  side effects, call can't be omitted

    fun tryConst(original: Expr, optimized: Expr ): Expr {
        val result = if (canOmitCall) {
            ConstVal(optimized, getExprType(optimized), original.token)
        } else {
            original
        }

        canOmitCall = true

        return result
    }

    fun optimizeExpr(node: Expr, constOnly: Boolean): Any {
        crawler.curNode = node

        val result = when (node) {
            is UnOp -> {
                val right = optimizeExpr(node.right, constOnly)
                //node.right = tryConst(node.right, right) //I was supposed to replace the following 2 lines with this

                if (canOmitCall) node.right = ConstVal(right, getExprType(right), node.right.token)
                canOmitCall = true

                when (node.op) {
                    TokenTypeEnum.unaryMinusOp -> {- right}
                    TokenTypeEnum.unaryPlusOp -> {right}
                    TokenTypeEnum.notOp -> {! right}
                    else -> {throw OptimizerException("Expression evaluation encountered unsupported operator: ${node.op}")}
                }
            }
            is BinOp -> {
                var left = optimizeExpr(node.left, constOnly)
                if (canOmitCall) node.left = ConstVal(left, getExprType(left), node.left.token)
                canOmitCall = true

                var right = optimizeExpr(node.right, constOnly)
                if (canOmitCall) node.right = ConstVal(right, getExprType(right), node.right.token)
                canOmitCall = true

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

                result
            }
            is VarRef -> {
                if (constOnly) {
                    throw OptimizerException("Trying to access variable in a const-only context")
                }
                varTable[node.varId].value ?:
                throw OptimizerException("Referencing uninitialised variable ${varTable[node.varId]} at $node")
            }
            is ConstRef -> {
                constTable[node.constId].value
            }
            is ConstVal -> {
                node.value
            }
            is Call -> {
                if (node.callee.body is Block && !hasSideEffects(node.callee.body as Block)) {
                    optimizeCall(node, constOnly).value ?: throw OptimizerException("Call execution ($node) returned no value")
                } else {
                    canOmitCall = false
                    executeCall(node).value ?: throw UninitializedPropertyAccessException("Cannot execute external function at compile-time")
                }
            }
        }

        crawler.pop()
        return result
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

    fun optimizeNode(node: ASTNode, constOnly: Boolean): ExecutionResult {
        crawler.curNode = node

        val result = when (node) {
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

                ExecutionResult(ValType.none, null)
            }
            is Block -> {
                var result = ExecutionResult(ValType.none, null)

                var breakIndex: Int = -1

                for ((i, n) in node.nodes.withIndex()) {
                    try {
                        if (n is Return) {
                            result = optimizeNode(n, constOnly)
                            breakIndex = i + 1
                            break
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
                //todo: this is actually wrong - what if returns are in different branches?
                if (breakIndex > -1) {//remove everything after return
                    node.nodes.dropLast(node.nodes.count() - breakIndex)
                }

                node.nodes.removeIf {it in emptyBlockStack}

                if (node.nodes.size == 0) {
                    logger.w("Optimizer encountered empty block at $node; attempting to remove")

                    emptyBlockStack.push(node)
                }

                result
            }
            is Expr -> {
                val result = optimizeExpr(node, constOnly)
                val type = getExprType(result)
                ExecutionResult(type, result)
            }
            is Call -> {
                if (node.callee.body is Block && !hasSideEffects(node.callee.body as Block)) {
                    optimizeCall(node, constOnly)
                } else {
                    executeCall(node)
                }
            }
            is CallStmt -> {
                optimizeCall(node.call, constOnly)
            }
            is VarDecl -> {
                if (node.expr != null) {
                    val result = optimizeNode(node.expr as Expr, constOnly)
                    require(result.type == varTable[node.identifier.refId].type)
                    {"Declaration assignment type error: expected ${varTable[node.identifier.refId].type}, got ${result.type}"}

                    if (canOmitCall) node.expr = ConstVal(result.value!!, result.type, node.expr!!.token)
                    canOmitCall = true

                    varTable[node.identifier.refId].value = result.value
                }
                ExecutionResult(ValType.none, null)
            }
            is ConstDecl, is FunDecl -> {
                ExecutionResult(ValType.none, null)
            }
            is Assign -> {
                assignStack.push(node.identifier.refId)
                val result = optimizeNode(node.expr, constOnly)
                require(result.type == varTable[node.identifier.refId].type)//todo: are these checks needed
                {"Assign type error: expected ${varTable[node.identifier.refId].type}, got ${result.type}"}
                if (canOmitCall) node.expr = ConstVal(result.value!!, result.type, node.expr.token)
                canOmitCall = true

                varTable[node.identifier.refId].value = result.value
                assignStack.pop()

                ExecutionResult(ValType.none, null)
            }
            is If -> {
                val result = optimizeNode(node.condition, constOnly)
                require(result.type == ValType.bool)//todo: are these checks needed
                {"Condition type error: expected bool, got ${result.type} "}

                if (canOmitCall) node.condition = ConstVal(result.value!!, result.type, node.condition.token)
                canOmitCall = true

                when (result.value as Boolean) {
                    true -> { optimizeNode(node.s, constOnly) }
                    false -> { ExecutionResult(ValType.none, null) }
                }
            }
            is While -> {
                try {
                    val result = optimizeNode(node.condition, constOnly = true)
                    require(result.type == ValType.bool)
                    { "Condition type error: expected bool, got ${result.type} " }
                } catch (e: OptimizerException) {
                    logger.i("Encountered var in loop condition; leaving")
                }

                //node.condition = ConstVal(result.value!!, result.type, node.condition.token) //todo: introduce break and adjust this

                try {
                    optimizeNode(node.s, constOnly = true)
                } catch (e: OptimizerException) {
                    logger.i("Encountered var in loop block; leaving")
                }

                ExecutionResult(ValType.none, null)
            }
            is Return -> {
                val result = optimizeNode(node.e, constOnly)
                require(result.type == functions[callStack.peek()].retType)
                {"Return type error: expected ${functions[callStack.peek()].retType}, got ${result.type}"}
                result
            }
            is Async -> {
                optimizeCall(node.c, constOnly)

                ExecutionResult(ValType.none, null)
            }
            is Await -> {
                ExecutionResult(ValType.none, null)
            }
            else -> {
                throw OptimizerException("Execution encountered unsupported node: $node")
            }
        }

        crawler.pop()
        return result
    }

    fun optimizeCall(node: Call, constOnly: Boolean): ExecutionResult {
        crawler.curNode = node

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
                    val result = optimizeNode(p, constOnly) //get param values

                    when (result.value) {
                        is Expr ->  node.params[i] = result.value
                        is Number -> node.params[i] = ConstVal(result.value, result.type, node.params[i].token)
                        else -> {}
                    }

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

        crawler.pop()

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
                else -> {}
            }
        }
        return false
    }
}