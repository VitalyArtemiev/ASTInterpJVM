package interpreter

import interpreter.IdentifierType.*
import interpreter.TokenTypeEnum.*
import util.Stack
import util.toRandomAccessIterator

open class ASTNode(val lineIndex: Int = 0, val text: String = "")

class Prog : ASTNode() {
    val nodes = ArrayList<ASTNode>(16)
}

enum class ValType { none, any, bool, int, float }

sealed class Decl : ASTNode()
class ConstDecl(val identifier: Identifier, val type: ValType, var value: Any): Decl()
class VarDecl(val identifier: Identifier, val type: ValType, var expr: Expr?) : Decl()
class FunDecl(val identifier: Identifier, val params: Array<VarDecl>, val retType: ValType,
                    var body: BaseBlock) : Decl()

sealed class Stmt : ASTNode()
class If(val condition: Expr, val s: Stmt) : Stmt()
class While(val condition: Expr, val s: Stmt) : Stmt()
class Return(val e: Expr): Stmt()

sealed class BaseBlock(): Stmt()
class PrecompiledBlock(var f: ((params: Params?) -> Any?)? = null): BaseBlock()
class Block(val scopeIndex: Int): BaseBlock() {
    val nodes = ArrayList<ASTNode>(8)
}

class CallStmt(val call: Call): Stmt()
class Call(val callee: FunDecl): Expr(){
    val params = ArrayList<Expr>(1)
}
class Assign(val identifier: Identifier, var expr: Expr): Stmt()

open class Expr: ASTNode()
class UnOp(var op: TokenTypeEnum, var right: Expr): Expr()
class BinOp(var left: Expr, var op: TokenTypeEnum, var right: Expr): Expr()

open class Value(val type: ValType): Expr()
class ConstVal(val value: Any, type: ValType): Value(type) //anonymous constant like a number
class ConstRef(val constId: Int, type: ValType): Value(type) //reference to named constant
class VarRef(val varId: Int, type: ValType): Value(type)

enum class IdentifierType { Const, Var, Fun }

class Identifier(
    val type: IdentifierType,
    val name: String, //name initialized here cuz i needed to pass it in manually in Call()
    val scopeLevel: Int, val scopeIndex: Int
) : ASTNode() {
    var refId: Int = -1
    lateinit var valType: ValType

    override fun toString(): String = "$type #$refId $name: valType si: $scopeIndex sl: $scopeLevel"
}

class AST(tokens: ArrayList<Token>, import: Array<ExportIdentifier>? = null) {
    var iter = tokens.toRandomAccessIterator()

    val identifiers: ArrayList<Identifier> = ArrayList()
    var maxConstId = 0
    var maxVarId = 0
    var maxFunId = 0

    val functions = ArrayList<FunDecl>() //to keep track of attached blocks
    val constants = ArrayList<ConstDecl>()
    val variables = ArrayList<VarDecl>()

    //lateinit var crawler: TreeCrawler
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
            throw Exception("$tokenType expected, but got $actual")
        }
        return actual
    }

    fun getProg(): Prog
    {
        val p = Prog()
        var token = iter.peek()
        while (token.tokenType != EOF) {
            when (token.tokenType) {
                varDecl, funDecl -> p.nodes.add(getDecl()) //todo: add line information
                identifier, startBlock, ifStmt, whileStmt, retStmt -> p.nodes.add(getStmt())
                else -> {
                    println("decl or stmt expected but $token found")
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
                val expr = if (iter.peek().tokenType == equal) {
                    getExpr()
                } else throw Exception("Expected value of constant, got ${iter.peek()}")
                //Optimizer.reduce(expr) for a more complex eval
                when (expr) {
                    is ConstVal -> {assert(type == expr.type)}
                    is ConstRef -> {assert(type == expr.type)}
                    else -> throw Exception("Expected constant expression, got $expr")
                }

                val c = ConstDecl(id, type, expr as Value)
                constants.add(c)
                c
            }
            varDecl -> {
                val id = getIdentifier(Var, IdMode.Declare)
                val type = getType()
                val expr = if (iter.peek().tokenType == equal) {
                    iter.next()
                    getExpr()
                } else null
                val v = VarDecl(id, type, expr)
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
                            throw Exception("Error during parameter declaration: expected ',' or ')'. got ${iter.peek()}")
                        }
                    }
                }

                //closeParenthesis consumed

                val type = if (iter.peek().tokenType == colon) {
                    getType()
                } else ValType.none

                val block = getBlock()

                var f = FunDecl(id, params.toTypedArray(), type, block)
                functions.add(f)
                f
            }
            else -> throw Exception("This should not be possible")
        }
    }

    fun getParDecl(scopeLevel: Int, scopeIndex: Int): VarDecl {
        val id = getIdentifier(
            type = Var,
            mode = IdMode.Declare,
            actualScopeLevel = scopeLevel,
            actualScopeIndex = scopeIndex
        )
        val type = getType()
        val expr = if (iter.peek().tokenType == equal) {
            getExpr()
        } else null

        return VarDecl(id, type, expr)
    }

    enum class IdMode {Declare, Import, Find}

    fun importIdentifiers(ids: Array<ExportIdentifier>) {
        for (ei in ids) {
            when (ei) {
                is ExportFunction -> {
                    val id = Identifier(Fun, IdMode.Import, ei.name)
                    val params = ArrayList<VarDecl>()

                    //create own block
                    if (ei.params != null) {
                        for ((name, type) in ei.params) {
                            val id = Identifier(Var, IdMode.Import, name, 1, ++maxScopeIndex) //own scope for imported fun params
                            params.add(VarDecl(id, type, null))
                        }
                    }

                    FunDecl(id, params.toTypedArray(), ei.valType, PrecompiledBlock(ei.body))
                }
                is ExportConstant -> {
                    val id = Identifier(Const, IdMode.Import, ei.name)
                    constants[id.refId].value = ei.value
                }
                is ExportVariable -> {
                    val id = Identifier(Var, IdMode.Import, ei.name)
                    variables[id.refId].expr = ConstVal(ei.defaultValue, ei.valType)
                }
            }
        }
    }

    fun exportIdentifiers(): Array<ExportIdentifier> {
        val export = ArrayList<ExportIdentifier>()
        identifiers.filter { it.scopeLevel == 0 }.forEach {
            when (it.type) {
                Const -> export.add(ExportConstant(it.name, it.valType, constants[it.refId].value))
                Var -> TODO()
                Fun -> export.add(ExportFunction(it.name,
                    (functions[it.refId].params.map { Pair(it.identifier.name, it.type) }.toTypedArray()),
                    functions[it.refId].retType, functions[it.refId].body))
            }
        }

    }

    fun getIdentifier(
        type: IdentifierType, mode: IdMode = IdMode.Find,
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
                val i = Identifier(type, name, scopeLevel, scopeIndex)

                if (identifiers.find { it.name == name && it.scopeIndex == scopeIndex } == null) {
                    identifiers.add(i)
                } else {
                    throw Exception("Identifier <$name> already exists")
                }

                i.refId = when (type) {
                    Const -> maxConstId ++
                    Var -> maxVarId ++
                    Fun -> maxFunId ++
                }

                return i
            }
            IdMode.Find -> {
                val id= identifiers.find { it.name == name && (it.scopeIndex == scopeIndex || it.scopeLevel < scopeLevel) }
                    ?: throw Exception("Identifier <$name> not found")
                //refId = id.refId //todo: possible tomfoolery when finding declared variable
                return id
            }
            IdMode.Import -> {
                scopeLevel = 0
                scopeIndex = 0
                val i = Identifier(type, name, scopeLevel, scopeIndex)

                if (identifiers.find { it.name == name && it.scopeIndex == scopeIndex } == null) {
                    identifiers.add(i)
                } else {
                    throw Exception("Conflicting import identifiers: identifier <$name> already exists")
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
            else -> throw Exception("Invalid type $token")
        }
    }

    fun getStmt(): Stmt {
        val token = iter.peek()
        return when (token.tokenType) {
            ifStmt -> {
                expect(ifStmt)
                If(getExpr(), getBlock())
            }
            whileStmt -> {
                expect(whileStmt)
                While(getExpr(), getBlock())
            }
            retStmt -> {
                expect(retStmt)
                Return(getExpr())
            }
            startBlock -> getBlock()
            identifier -> {
                return when (getRelevantIdentifier(token.text).type) {
                    Var -> getAssign()
                    Fun -> CallStmt(getCall())
                    else -> throw Exception("Illegal operation with val $token")
                }
            }
            else -> throw Exception("This should not be possible")
        }
    }


    fun getBlock(): Block {
        expect(startBlock)

        scopes.push(curScopeIndex)

        curScopeLevel ++ //one level down
        val b = Block(++ maxScopeIndex) //get new index
        curScopeIndex = b.scopeIndex

        loop@ do {
            val token = iter.peek()

            when (token.tokenType) {
                endBlock -> {iter.next(); break@loop}
                varDecl, funDecl -> b.nodes.add(getDecl()) //todo: add line information
                identifier, startBlock, ifStmt, whileStmt, retStmt ->  b.nodes.add(getStmt())
                else -> { //EOF handled here
                    throw Exception("decl or stmt expected but $token found")
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
            val id = getIdentifier(Fun, name = name)
            c = Call(functions[id.refId])
        } catch (e: Exception) {
            throw Exception("Could not find callee <$name>;\n$e")
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
        val i = getIdentifier(type = Var)
        expect(assignOp)
        val e = getExpr()
        return Assign(i, e)
    }

    fun getRelevantIdentifier(name: String): Identifier {
        val candidates = identifiers.filter {
            it.name == name && (it.scopeIndex == curScopeIndex || it.scopeLevel < curScopeLevel)
        }
        if (candidates.count() == 0) {
            throw Exception("Identifier <$name> not found")
        }
        return candidates.maxBy { it.scopeLevel }!! //literally can't be null
    }

    fun getExpr(): Expr {
        val s  = simpExpr()
        if (iter.peek().tokenType in relOps) {
            val op = iter.next().tokenType
            val s1 = simpExpr()
            return BinOp(s, op, s1)
        }
        return s
    }

    fun simpExpr(): Expr {
        val unOp = when (iter.peek().tokenType ) {
            plusOP -> {iter.next(); unaryPlusOp}
            minusOp -> {iter.next(); unaryPlusOp}
            else -> {null}
        }

        var left = getTerm()

        if (unOp != null) {
            left = UnOp(unOp , left)
        }

        while (iter.peek().tokenType in addOps) {
            val op = iter.next().tokenType
            val t = getTerm()
            left = BinOp(left, op, t)
        }
        return left
    }

    fun getTerm(): Expr {
        var left = getFactor()

        while (iter.peek().tokenType in multOps) {
            val op = iter.next().tokenType
            val t = getFactor()
            left = BinOp(left, op, t)
        }
        return left
    }

    fun getFactor(): Expr {
        var base = getBase()
        return if (iter.peek().tokenType == powOp) {
            iter.next()
            var exp = getBase() //getExponent would be the same as getBAse
            BinOp(base, powOp, exp)
        } else {
            base
        }
    }

    fun getBase(): Expr {
        val token = iter.next()
        when(token.tokenType) {
            identifier -> {
                val id = getRelevantIdentifier(token.text)
                when (id.type) {
                    Const -> {
                        return ConstRef(id.refId, constants[id.refId].type)
                    }
                    Var -> {
                        return VarRef(id.refId, variables[id.refId].type)
                    }
                    Fun -> {
                        return getCall()
                    }
                }
            }
            openParenthesis -> {
                val e = getExpr()
                expect(closeParenthesis)
                return e
            }
            intValue -> {
                return ConstVal(token.text.toInt(), ValType.int)
            }
            floatValue -> {
                return ConstVal(token.text.toFloat(), ValType.float)
            }
            boolValue -> {
                return ConstVal(token.text.toBoolean(), ValType.bool)
            }
            else -> {
                throw Exception("Impossible state: expected base, got $token")
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

