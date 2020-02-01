package interpreter

import interpreter.AST.IdentifierType.*
import interpreter.TokenTypeEnum.*
import util.Stack
import util.toRandomAccessIterator

class AST(tokens: ArrayList<Token>, import: Array<ExportIdentifier>? = null) {
    var iter = tokens.toRandomAccessIterator()

    enum class IdentifierType { Const, Var, Fun }

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
        //getDefaultFunctions()
        //getDefaultConstants()
    }

    var root = Prog()

    open class ASTNode(val lineIndex: Int = 0, val text: String = "")

    fun expect(tokenType: TokenTypeEnum): Token {
        val actual = iter.next()
        if (actual.tokenType != tokenType) {
            throw Exception("$tokenType expected, but got $actual")
        }
        return actual
    }

    inner class Prog : ASTNode() {
        val nodes = ArrayList<ASTNode>(16)

        init {
            var token = iter.peek()
            while (token.tokenType != EOF) {
                when (token.tokenType) {
                    varDecl, funDecl -> nodes.add(getDecl()) //todo: add line information
                    identifier, startBlock, ifStmt, whileStmt, retStmt -> nodes.add(getStmt())
                    else -> {
                        println("decl or stmt expected but $token found")
                        iter.next()
                    }
                }
                token = iter.peek()
            }
        }
    }

    open inner class Decl : ASTNode()

    inner class ConstDecl(val identifier: Identifier, val type: ValType, var value: Any): Decl() {
        init { //register variable
            constants.add(this)
        }
    }

    inner class VarDecl(val identifier: Identifier, val type: ValType, var expr: Expr?) : Decl() {
        init { //register variable
            variables.add(this)
        }
    }

    inner class FunDecl(val identifier: Identifier, val params: Array<VarDecl>, val retType: ValType,
                        var body: BaseBlock) : Decl() {
        init { //register function
            functions.add(this)
        }
    }

    fun getDecl(): Decl {
        val token = iter.next()
        return when (token.tokenType) {
            constDecl -> {
                val id = Identifier(Const, mode = IdMode.Declare)
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

                ConstDecl(id, type, expr as Value)
            }
            varDecl -> {
                val id = Identifier(Var, mode = IdMode.Declare)
                val type = getType()
                val expr = if (iter.peek().tokenType == equal) {
                    iter.next()
                    getExpr()
                } else null
                VarDecl(id, type, expr)
            }
            funDecl -> {
                val id = Identifier(Fun, mode = IdMode.Declare)
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

                val block = Block()

                FunDecl(id, params.toTypedArray(), type, block)
            }
            else -> throw Exception("This should not be possible")
        }
    }

    fun getParDecl(scopeLevel: Int, scopeIndex: Int): VarDecl {
        val id = Identifier(Var, mode = IdMode.Declare, actualScopeLevel = scopeLevel, actualScopeIndex = scopeIndex)
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

    inner class Identifier(
        val type: IdentifierType, mode: IdMode = IdMode.Find,
        val name: String = iter.next().text, //name initialized here cuz i needed to pass it in manually in Call()
        actualScopeLevel: Int = -1, actualScopeIndex: Int = -1
    ) : ASTNode() {
        var scopeLevel: Int = when(actualScopeLevel){
            -1 -> curScopeLevel
            else -> actualScopeLevel
        }
        var scopeIndex: Int = when(actualScopeIndex){
            -1 -> curScopeIndex
            else -> actualScopeIndex
        }
        val refId: Int

        lateinit var valType: ValType

        init {
            when (mode) {
                IdMode.Declare -> {
                    if (identifiers.find { it.name == name && it.scopeIndex == scopeIndex } == null) {
                        identifiers.add(this)
                    } else {
                        throw Exception("Identifier <$name> already exists")
                    }

                    refId = when (type) {
                        Const -> maxConstId ++
                        Var -> maxVarId ++
                        Fun -> maxFunId ++
                    }
                }
                IdMode.Find -> {
                    val id= identifiers.find { it.name == name && (it.scopeIndex == scopeIndex || it.scopeLevel < scopeLevel) }
                        ?: throw Exception("Identifier <$name> not found")
                    refId = id.refId //todo: possible tomfoolery when finding declared variable
                }
                IdMode.Import -> {
                    scopeLevel = 0
                    scopeIndex = 0
                    if (identifiers.find { it.name == name && it.scopeIndex == scopeIndex } == null) {
                        identifiers.add(this)
                    } else {
                        throw Exception("Conflicting import identifiers: identifier <$name> already exists")
                    }

                    refId = when (type) {
                        Const -> maxConstId ++
                        Var -> maxVarId ++
                        Fun -> maxFunId ++
                    }
                }
            }
        }
        override fun toString(): String = "$type #$refId $name: valType si: $scopeIndex sl: $scopeLevel"
    }

    fun extractVariables(): Array<Variable> {
        return variables.map { Variable(it.identifier.name, it.type, null) }.toTypedArray()
    }

    enum class ValType { none, any, bool, int, float }

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
            ifStmt -> If()
            whileStmt -> While()
            retStmt -> Return()
            startBlock -> Block()
            identifier -> {
                return when (getRelevantIdentifier(token.text).type) {
                    Var -> Assign()
                    Fun -> CallStmt()
                    else -> throw Exception("Illegal operation with val $token")
                }
            }
            else -> throw Exception("This should not be possible")
        }
    }

    open inner class Stmt : ASTNode()
    inner class If() : Stmt() {
        val condition: Expr
        val s: Stmt

        init {
            expect(ifStmt)
            condition = getExpr()
            s = Block()
            //lineIndex = 0//todo: add line information
            //text = ""
        }
    }

    inner class While() : Stmt() {
        val condition: Expr
        val s: Stmt
        init {
            expect(whileStmt)
            condition = getExpr()
            s = Block()
            //lineIndex = 0//todo: add line information
            //text = ""
        }
    }

    inner class Return(): Stmt() {
        val e: Expr
        init {
            expect(retStmt)
            e = getExpr()
        }
    }

    open inner class BaseBlock(): Stmt()
    inner class PrecompiledBlock(f: (params: Params?) -> Any? = {}): BaseBlock() {
        lateinit var f: (params: Params?) -> Any?
    }

    inner class Block(): BaseBlock() {
        val scopeIndex: Int
        val nodes = ArrayList<ASTNode>(8)
        init {
            expect(startBlock)

            scopes.push(curScopeIndex)

            curScopeLevel ++ //one level down
            scopeIndex = ++ maxScopeIndex //get new index
            curScopeIndex = scopeIndex

            loop@ do {
                val token = iter.peek()

                when (token.tokenType) {
                    endBlock -> {iter.next(); break@loop}
                    varDecl, funDecl -> nodes.add(getDecl()) //todo: add line information
                    identifier, startBlock, ifStmt, whileStmt, retStmt ->  nodes.add(getStmt())
                    else -> { //EOF handled here
                        throw Exception("decl or stmt expected but $token found")
                        //iter.next()
                    }
                }

            } while (true)
            curScopeLevel --
            curScopeIndex = scopes.pop()
        }
    }

    inner class CallStmt(val call: Call = Call()): Stmt()
    inner class Call(): Expr(){
        val callee: FunDecl
        val params = ArrayList<Expr>(1)
        init {
            val name = iter.cur().text
            try {
                val id = Identifier(Fun, name = name)
                callee = functions[id.refId]
            } catch (e: Exception) {
                throw Exception("Could not find callee <$name>;\n$e")
            }
            expect(openParenthesis)
            while (iter.peek().tokenType != closeParenthesis) {
                val node = getExpr()
                params.add(node)
            }
            iter.next()
        }
    }

    inner class Assign(): Stmt() {
        val identifier: Identifier
        var expr: Expr

        init {
            identifier = Identifier(Var)
            expect(assignOp)
            expr =  getExpr()
        }
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
                        return Call()
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

    open inner class Expr: ASTNode()
    inner class UnOp(var op: TokenTypeEnum, var right: Expr): Expr()
    inner class BinOp(var left: Expr, var op: TokenTypeEnum, var right: Expr): Expr()

    open inner class Value(val type: ValType): Expr()
    inner class ConstVal(val value: Any, type: ValType): Value(type) //anonymous constant like a number
    inner class ConstRef(val constId: Int, type: ValType): Value(type) //reference to named constant
    inner class VarRef(val varId: Int, type: ValType): Value(type)
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

