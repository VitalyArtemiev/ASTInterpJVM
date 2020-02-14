package interpreter

import util.Logger
import util.Stack
import kotlin.reflect.full.memberProperties

class OptimizerException(msg: String): Exception(msg)

class Optimizer (val r: Runner) {
    private val logger = Logger("Optimizer")

    lateinit var varTable: Array<Variable>
    lateinit var constTable: Array<ConstDecl>
    lateinit var functions: Array<FunDecl>

    private val blockStack = Stack<ASTNode>()
    private val callStack = Stack<Int>()
    private val assignStack = Stack<Int>()

    lateinit var root: ASTNode

    fun optimize(env: Environment): Environment {
        varTable = env.variables
        constTable = env.constants
        functions = env.functions
        root = env.root

        printVarTable()

        optimizeNode(root, constOnly = false)

        assert(blockStack.isEmpty())

        for (v in varTable) {
            v.value = null
        }

        logger.i("Optimization finished")

        return env
    }

    fun printVarTable() {
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

    fun evalExpr(node: Expr, constOnly: Boolean): Any {
        when (node) {
            is UnOp -> {
                val right = evalExpr(node.right, constOnly)
                node.right = ConstVal(right, getExprType(right), node.right.token)

                return when (node.op) {
                    TokenTypeEnum.unaryMinusOp -> {- right}
                    TokenTypeEnum.unaryPlusOp -> {right}
                    TokenTypeEnum.notOp -> {! right}
                    else -> {throw OptimizerException("Expression evaluation encountered unsupported operator: ${node.op}")}
                }
            }
            is BinOp -> {
                var left = evalExpr(node.left, constOnly)
                node.left = ConstVal(left, getExprType(left), node.left.token)

                var right = evalExpr(node.right, constOnly)
                node.right = ConstVal(right, getExprType(right), node.right.token)

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
                return executeCall(node, constOnly).value ?: throw OptimizerException("Call execution ($node) returned no value")
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
                blockStack.push(node)
                for (n in node.nodes) {
                    try {
                        optimizeNode(n, constOnly)
                    } catch (e: UninitializedPropertyAccessException) {
                        val varId = assignStack.pop()
                        varTable[varId].value = null
                        logger.i("Could not evaluate ${varTable[varId]} at compile-time")

                        logger.l(e.message!!)
                    }
                }
                blockStack.pop()

                return ExecutionResult(ValType.none, null)
            }
            is Block -> {
                if (node.nodes.size == 0) {
                    logger.w("Optimizer encountered empty block at $node; attempting to remove")
                    val parentBlock = blockStack.peek()

                    when (parentBlock) {
                        is Prog -> {
                            if (parentBlock.nodes.remove(node)) {
                                logger.i("Removal successful")
                            } else {
                                logger.e("Removal failed; node: $node, parent: $parentBlock")
                            }
                        }
                        is Block -> {
                            if (parentBlock.nodes.remove(node)) {
                                logger.i("Removal successful")
                            } else {
                                logger.e("Removal failed; node: $node, parent: $parentBlock")
                            }
                        }
                        else -> logger.i("Cannot remove empty block: likely belongs to function or statement")
                    }

                    return ExecutionResult(ValType.none, null)
                }

                blockStack.push(node)

                for (n in node.nodes) {
                    try {
                        if (n is Return) {
                            blockStack.pop()
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
                blockStack.pop()

                return ExecutionResult(ValType.none, null)
            }

            is Expr -> {
                val result = evalExpr(node, constOnly)
                val type = getExprType(result)
                return ExecutionResult(type, result)
            }
            is Call -> {
                return executeCall(node, constOnly)
            }
            is CallStmt -> {
                return executeCall(node.call, constOnly)
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
                node.expr = ConstVal(result.value!!, result.type, node.expr.token)

                varTable[node.identifier.refId].value = result.value
                assignStack.pop()
                return ExecutionResult(ValType.none, null)
            }
            is If -> {
                val result = optimizeNode(node.condition, constOnly)
                require(result.type == ValType.bool)//todo: are these checks needed
                {"Condition type error: expected bool, got ${result.type} "}

                node.condition = ConstVal(result.value!!, result.type, node.condition.token)

                return when (result.value as Boolean) {
                    true -> { optimizeNode(node.s, constOnly) }
                    false -> { ExecutionResult(ValType.none, null) }
                }
            }
            is While -> {
                try {
                    var result = optimizeNode(node.condition, true)
                    require(result.type == ValType.bool)
                    { "Condition type error: expected bool, got ${result.type} " }
                } catch (e: OptimizerException) {
                    logger.i("Encountered var in loop condition; leaving")
                }

                //node.condition = ConstVal(result.value!!, result.type, node.condition.token)

                try {
                    optimizeNode(node.s, constOnly = true)
                } catch (e: OptimizerException) {
                    blockStack.pop()
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
            else -> {
                throw OptimizerException("Execution encountered unsupported node: $node")
            }
        }
    }

    fun executeCall(node: Call, constOnly: Boolean): ExecutionResult {
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

                optimizeNode(node.callee.body, constOnly)
            } //execute function body block
            is PrecompiledBlock -> {
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