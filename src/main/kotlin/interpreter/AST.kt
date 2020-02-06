package interpreter

import interpreter.IdentifierType.*
import interpreter.TokenTypeEnum.*
import util.Logger
import util.Stack
import util.toRandomAccessIterator

class ASTException(msg: String): Exception(msg)

sealed class ASTNode(val token: Token) {
    override fun toString(): String = "Pos ${token.line}:${token.numInLine} - ${token.tokenType}: ${token.text}"
}

class Prog(t: Token) : ASTNode(t) {
    val nodes = ArrayList<ASTNode>(16)
}

enum class ValType { none, any, bool, int, float }

sealed class Decl(t: Token) : ASTNode(t)
class ConstDecl(val identifier: Identifier, val type: ValType, var value: Any, t: Token): Decl(t)
class VarDecl(val identifier: Identifier, val type: ValType, var expr: Expr?, t: Token): Decl(t)
class FunDecl(val identifier: Identifier, val params: Array<VarDecl>, val retType: ValType, var body: BaseBlock,
              t: Token): Decl(t)

sealed class Stmt(t: Token) : ASTNode(t)
class If(val condition: Expr, val s: Stmt, t: Token) : Stmt(t)
class While(val condition: Expr, val s: Stmt, t: Token) : Stmt(t)
class Return(val e: Expr, t: Token): Stmt(t)

sealed class BaseBlock(t: Token): Stmt(t)
class PrecompiledBlock(var f: ((params: Params?) -> Any?), t: Token): BaseBlock(t)
class Block(val scopeIndex: Int, t: Token): BaseBlock(t) {
    val nodes = ArrayList<ASTNode>(8)
}

class CallStmt(val call: Call): Stmt(call.token)
class Call(val callee: FunDecl, t: Token): Expr(t){
    val params = ArrayList<Expr>(1)
}
class Assign(val identifier: Identifier, var expr: Expr, t: Token): Stmt(t)

sealed class Expr(t: Token): ASTNode(t)
class UnOp(var op: TokenTypeEnum, var right: Expr, t: Token): Expr(t)
class BinOp(var left: Expr, var op: TokenTypeEnum, var right: Expr, t: Token): Expr(t)

sealed class Value(val type: ValType, t: Token): Expr(t)
class ConstVal(val value: Any, type: ValType, t: Token): Value(type, t) //anonymous constant like a number
class ConstRef(val constId: Int, type: ValType, t: Token): Value(type, t) //reference to named constant
class VarRef(val varId: Int, type: ValType, t: Token): Value(type, t)

enum class IdentifierType { Const, Var, Fun }

class Identifier(
    val type: IdentifierType,
    val name: String, //name initialized here cuz i needed to pass it in manually in Call()
    val scopeLevel: Int, val scopeIndex: Int, t: Token
) : ASTNode(t) {
    var refId: Int = -1
    var valType: ValType = ValType.none

    override fun toString(): String = "$type #$refId $name: $valType si: $scopeIndex sl: $scopeLevel"
}

class AST(tokens: ArrayList<Token>, import: Array<ExportIdentifier>? = null) {
    val logger = Logger("AST Parser")

    var iter = tokens.toRandomAccessIterator()

    val identifiers: ArrayList<Identifier> = ArrayList()
    var maxConstId = 0
    var maxVarId = 0
    var maxFunId = 0

    val functions = ArrayList<FunDecl>() //to keep track of attached blocks
    val constants = ArrayList<ConstDecl>()
    val variables = ArrayList<VarDecl>()

    var curScopeLevel = 0
    var curScopeIndex = 0
    var maxScopeIndex = 0
    val scopes = Stack<Int>()

    init {
        importIdentifiers(defaultIdentifiers)
        if (import != null) {
            importIdentifiers(import)
        }
    }

    var root = getProg()

    fun expect(tokenType: TokenTypeEnum): Token {
        val actual = iter.next()
        if (actual.tokenType != tokenType) {
            throw ASTException("$tokenType expected, but got $actual")
        }
        return actual
    }

    fun getProg(): Prog
    {
        var token = iter.peek()
        val p = Prog(token)

        while (token.tokenType != EOF) {
            when (token.tokenType) {
                varDecl, funDecl -> p.nodes.add(getDecl())
                identifier, startBlock, ifStmt, whileStmt, retStmt -> p.nodes.add(getStmt())
                else -> {
                    logger.e("Decl or Stmt expected but $token found")
                    iter.next()
                }
            }
            token = iter.peek()
        }
        return p
    }

    fun getDecl(): Decl {
        val token = iter.next()
        return when (token.tokenType) {
            constDecl -> {
                val id = getIdentifier(Const, IdMode.Declare)
                val type = getType()
                id.valType = type

                val expr = if (iter.peek().tokenType == equal) {
                    getExpr()
                } else throw ASTException("Expected value of constant, got ${iter.peek()}")
                //Optimizer.reduce(expr) for a more complex eval
                when (expr) {
                    is ConstVal -> {assert(type == expr.type)}
                    is ConstRef -> {assert(type == expr.type)}
                    else -> throw ASTException("Expected constant expression, got $expr")
                }

                val c = ConstDecl(id, type, expr as Value, token)
                constants.add(c)
                c
            }
            varDecl -> {
                val id = getIdentifier(Var, IdMode.Declare)
                val type = getType()
                id.valType = type

                val expr = if (iter.peek().tokenType == equal) {
                    iter.next()
                    getExpr()
                } else null
                val v = VarDecl(id, type, expr, token)
                variables.add(v)
                v
            }
            funDecl -> {
                val id = getIdentifier(Fun, IdMode.Declare)
                expect(openParenthesis)

                val params = ArrayList<VarDecl>()

                val blockScopeLevel = curScopeLevel + 1 //to fix parameter scope
                val blockScopeIndex = maxScopeIndex + 1

                loop@ while (true) {
                    params.add(getParDecl(blockScopeLevel, blockScopeIndex))
                    when (iter.peek().tokenType) {
                        comma -> {
                            iter.next()
                            continue@loop
                        }
                        closeParenthesis -> {
                            iter.next()
                            break@loop
                        }
                        else -> {
                            throw ASTException("Error during parameter declaration: expected ',' or ')'. got ${iter.peek()}")
                        }
                    }
                }

                //closeParenthesis consumed

                val type = if (iter.peek().tokenType == colon) {
                    getType()
                } else ValType.none
                id.valType = type

                val block = getBlock()

                var f = FunDecl(id, params.toTypedArray(), type, block, token)
                functions.add(f)
                f
            }
            else -> throw ASTException("This should not be possible")
        }
    }

    fun getParDecl(scopeLevel: Int, scopeIndex: Int): VarDecl {
        val token = iter.cur()
        val id = getIdentifier(
            type = Var,
            mode = IdMode.Declare,
            actualScopeLevel = scopeLevel,
            actualScopeIndex = scopeIndex
        )
        val type = getType()
        id.valType = type

        val expr = if (iter.peek().tokenType == equal) {
            getExpr()
        } else null

        val v = VarDecl(id, type, expr, token)
        variables.add(v)
        return v
    }

    enum class IdMode {Declare, Import}

    fun importIdentifiers(ids: Array<ExportIdentifier>) {
        for (ei in ids) {
            when (ei) {
                is ExportFunction -> {
                    val id = getIdentifier(Fun, IdMode.Import, ei.name, 0, 0)
                    id.valType = ei.valType

                    val params = ArrayList<VarDecl>()

                    //create own block
                    if (ei.params != null) {
                        val scopeIndex = ++ maxScopeIndex //own scope for imported fun params
                        for ((name, type) in ei.params) {
                            val pId = getIdentifier(Var, IdMode.Import, name, 1, scopeIndex)
                            pId.valType = type

                            val v = VarDecl(pId, type, null, Token(-1, "External token $name", identifier, 0))
                            variables.add(v)
                            params.add(v)
                        }
                    }

                    functions.add(FunDecl(id, params.toTypedArray(), ei.valType, ei.body, Token(-1, "External token ${ei.name}", identifier, 0)))
                }
                is ExportConstant -> {
                    val id = getIdentifier(Const, IdMode.Import, ei.name, 0, 0)
                    id.valType = ei.valType

                    constants.add(ConstDecl(id, ei.valType, ei.value, Token(-1, "External token ${ei.name}", identifier, 0)))
                }
                is ExportVariable -> {
                    val id = getIdentifier(Var, IdMode.Import, ei.name, 0, 0)
                    id.valType = ei.valType

                    variables.add(VarDecl(id, ei.valType, ConstVal(ei.defaultValue, ei.valType,
                        Token(-1, "External token ${ei.name}", intValue, 0)),  //todo: assign appropriate type
                        Token(-1, "External token ${ei.name}", identifier, 0)))
                }
            }
        }
    }

    fun getIdentifier(
        type: IdentifierType, mode: IdMode,
        name: String = iter.next().text, //name initialized here cuz i needed to pass it in manually in Call()
        actualScopeLevel: Int = -1, actualScopeIndex: Int = -1
    ): Identifier {

        var scopeLevel: Int = when (actualScopeLevel) {
            -1 -> curScopeLevel
            else -> actualScopeLevel
        }
        var scopeIndex: Int = when (actualScopeIndex) {
            -1 -> curScopeIndex
            else -> actualScopeIndex
        }

        when (mode) {
            IdMode.Declare -> {
                val i = Identifier(type, name, scopeLevel, scopeIndex, iter.cur())

                if (identifiers.find { it.name == name && it.scopeIndex == scopeIndex } == null) {
                    identifiers.add(i)
                } else {
                    throw ASTException("Identifier <$name> already exists")
                }

                i.refId = when (type) {
                    Const -> maxConstId ++
                    Var -> maxVarId ++
                    Fun -> maxFunId ++
                }

                return i
            }
            IdMode.Import -> {
                val i = Identifier(type, name, scopeLevel, scopeIndex, iter.peek())

                if (identifiers.find { it.name == name && it.scopeIndex == scopeIndex } == null) {
                    identifiers.add(i)
                } else {
                    throw ASTException("Conflicting import identifiers: identifier <$name> already exists")
                }

                i.refId = when (type) {
                    Const -> maxConstId ++
                    Var -> maxVarId ++
                    Fun -> maxFunId ++
                }

                return i
            }
        }
    }

    fun findRelevantIdentifier(name: String): Identifier {
        val candidates = identifiers.filter {
            it.name == name && (it.scopeIndex == curScopeIndex || it.scopeIndex in scopes)
        }
        if (candidates.count() == 0) {
            throw ASTException("Identifier <$name> not found")
        }
        return candidates.maxBy { it.scopeLevel }!! //literally can't be null
    }

    fun exportIdentifiers(): Array<ExportIdentifier> {
        val export = ArrayList<ExportIdentifier>()
        identifiers.filter { it.scopeLevel == 0 }.forEach { id ->
            when (id.type) {
                Const -> export.add(ExportConstant(id.name, id.valType, constants[id.refId].value))
                //Var -> TODO()
                Fun -> export.add(ExportFunction(id.name,
                    (functions[id.refId].params.map { p -> Pair(p.identifier.name, p.type) }.toTypedArray()),
                    functions[id.refId].retType, functions[id.refId].body))
            }
        }
        return export.toTypedArray()
    }

    fun extractVariables(): Array<Variable> {
        return variables.map { Variable(it.identifier.name, it.type, null) }.toTypedArray()
    }

    fun getType(): ValType {
        expect(colon)
        val token = expect(type)
        return when (token.text) {
            "bool" -> ValType.bool
            "int" -> ValType.int
            "float" -> ValType.float
            else -> throw ASTException("Invalid type $token")
        }
    }

    fun getStmt(): Stmt {
        val token = iter.peek()
        return when (token.tokenType) {
            ifStmt -> {
                expect(ifStmt)
                If(getExpr(), getBlock(), token)
            }
            whileStmt -> {
                expect(whileStmt)
                While(getExpr(), getBlock(), token)
            }
            retStmt -> {
                expect(retStmt)
                Return(getExpr(), token)
            }
            startBlock -> getBlock()
            identifier -> {
                return when (findRelevantIdentifier(token.text).type) {
                    Var -> getAssign()
                    Fun ->{
                        iter.next()
                        CallStmt(getCall())
                    }
                    else -> throw ASTException("Illegal operation with val $token")
                }
            }
            else -> throw ASTException("This should not be possible")
        }
    }

    fun getBlock(): Block {
        expect(startBlock)

        scopes.push(curScopeIndex)

        curScopeLevel ++ //one level down
        val b = Block(++ maxScopeIndex, iter.cur()) //get new index
        curScopeIndex = b.scopeIndex

        loop@ do {
            val token = iter.peek()

            when (token.tokenType) {
                endBlock -> {iter.next(); break@loop}
                varDecl, funDecl -> b.nodes.add(getDecl())
                identifier, startBlock, ifStmt, whileStmt, retStmt ->  b.nodes.add(getStmt())
                else -> { //EOF handled here
                    throw ASTException("decl or stmt expected but $token found")
                    //iter.next()
                }
            }
        } while (true)
        curScopeLevel --
        curScopeIndex = scopes.pop()

        return b
    }

    fun getCall(): Call {
        val c: Call
        val name = iter.cur().text
        try {
            val id = findRelevantIdentifier(name)

            c = Call(functions[id.refId], iter.cur())
            require(id.valType == c.callee.retType)
        } catch (e: Exception) {
            throw ASTException("Could not find callee <$name>;\n$e")
        }
        expect(openParenthesis)
        while (iter.peek().tokenType != closeParenthesis) {
            val node = getExpr()
            c.params.add(node)
        }
        iter.next()

        return c
    }

    fun getAssign(): Assign {
        val token = iter.next()
        val id = findRelevantIdentifier(token.text)

        expect(assignOp)

        val e = getExpr()
        //todo: expect(i.valType = evalType(e))
        return Assign(id, e, token)
    }

    fun getExpr(): Expr {
        val s  = simpExpr()
        if (iter.peek().tokenType in relOps) {
            val token = iter.next()
            val op = token.tokenType
            val s1 = simpExpr()
            return BinOp(s, op, s1, token)
        }
        return s
    }

    fun simpExpr(): Expr {
        var token: Token? = null
        val unOp = when (iter.peek().tokenType ) {
            plusOP  -> {token = iter.next(); unaryPlusOp}
            minusOp -> {token = iter.next(); unaryPlusOp}
            else -> {null}
        }

        var left = getTerm()

        if (unOp != null) {
            left = UnOp(unOp , left, token!!)
        }

        while (iter.peek().tokenType in addOps) {
            token = iter.next()
            val op = token.tokenType
            val t = getTerm()
            left = BinOp(left, op, t, token)
        }
        return left
    }

    fun getTerm(): Expr {
        var left = getFactor()

        while (iter.peek().tokenType in multOps) {
            val token = iter.next()
            val op = token.tokenType
            val t = getFactor()
            left = BinOp(left, op, t, token)
        }
        return left
    }

    fun getFactor(): Expr {
        val base = getBase()
        return if (iter.peek().tokenType == powOp) {
            val token = iter.next()
            val exp = getBase() //getExponent would be the same as getBAse
            BinOp(base, powOp, exp, token)
        } else {
            base
        }
    }

    fun getBase(): Expr {
        val token = iter.next()
        when(token.tokenType) {
            identifier -> {
                val id = findRelevantIdentifier(token.text)
                return when (id.type) {
                    Const -> {
                        ConstRef(id.refId, constants[id.refId].type, token)
                    }
                    Var -> {
                        VarRef(id.refId, variables[id.refId].type, token)
                    }
                    Fun -> {
                        getCall()
                    }
                }
            }
            openParenthesis -> {
                val e = getExpr()
                expect(closeParenthesis)
                return e
            }
            intValue -> {
                return ConstVal(token.text.toInt(), ValType.int, token)
            }
            floatValue -> {
                return ConstVal(token.text.toFloat(), ValType.float, token)
            }
            boolValue -> {
                return ConstVal(token.text.toBoolean(), ValType.bool, token)
            }
            else -> {
                throw ASTException("Impossible state: expected base, got $token")
            }
        }
    }
}

/*class TreeCrawler {
    class History(var node: AST.ASTNode) {
        var lastVisited: Int = -1
    }
    val stack = Stack<History>()
    
    lateinit var curNode: ASTNode
    
    fun visitChild(index: Int) {
        curNode = curNode.children[index]
    }
}*/

