package interpreter

import util.Logger
import util.Stack
import util.toRandomAccessIterator
import java.io.File

var terminalsPath: String = " "
var nonTerminalsPath: String = " "

fun main(args: Array<String>) {
    val logger = Logger("Main")
    var pathIndices: Int

    var terminalsText: String = try {
        val termIndex = args.indexOf("-t") + 1
        terminalsPath = args[termIndex]
        File(terminalsPath).readText()
    } catch (e: IndexOutOfBoundsException) {
        logger.e("terminalsPath arg not found")
        defaultTerminals
    } catch (e: NoSuchFileException) {
        defaultTerminals
    }

    var nonTerminalsText: String = try {
        val nonTermIndex = args.indexOf("-nt") + 1
        nonTerminalsPath = args[nonTermIndex]
        File(terminalsPath).readText()
    } catch (e: IndexOutOfBoundsException) {
        logger.e("nonTerminalsPath arg not found")
        defaultNonTerminals
    } catch (e: NoSuchFileException) {
        defaultNonTerminals
    }

    var path = "source.tl"

    val l = Lexer(terminalsText)

    var tokens = l.lex(path)
    if (l.errors.isNotEmpty()) {
        println("Lexical errors: \n" + l.errors.toString())
    } else {
        print("Lexer finished with no errors")
    }

    val p = Parser(/*nonTerminalsText*/)

    var env = p.parse(tokens)

    val r = Runner(env)

    val runTimeIdentifiers: Array<ExportIdentifier> = arrayOf(
        ExportFunction("_PRINTVARTABLE", null, AST.ValType.none, r::printVarTable)
    )

    r.run()

    /*val A = a(tokens)
    print(A.identifiers)*/
}
/*

class a(tokens: ArrayList<Token>) {
    var iter = tokens.toRandomAccessIterator()

    var root = Prog()

    val functions = ArrayList<FunDecl>() //to keep track of attached blocks
    val constants = ArrayList<ConstDecl>()
    val variables = extractVariables()

    //lateinit var crawler: TreeCrawler
    var curScopeLevel = 0
    var curScopeIndex = 0
    var maxScopeIndex = 0
    val scopes = Stack<Int>()

    open class ASTNode(val lineIndex: Int = 0, val text: String = "")

    fun expect(tokenType: TokenTypeEnum): Token {
        val actual = iter.next()
        if (actual.tokenType != tokenType) {
            throw Exception("$tokenType expected, but got $actual")
        }
        return actual
    }

    inner class Prog() : ASTNode() {
        val nodes = ArrayList<ASTNode>(16)

        init {
            var token = iter.peek()
            var i = 0
            while (token.tokenType != TokenTypeEnum.EOF) {
                when (token.tokenType) {
                    TokenTypeEnum.varDecl, TokenTypeEnum.funDecl -> nodes[i++] = getDecl() //todo: add line information
                    TokenTypeEnum.identifier, TokenTypeEnum.startBlock, TokenTypeEnum.ifStmt, TokenTypeEnum.whileStmt, TokenTypeEnum.retStmt -> nodes[i++] = getStmt()
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

    inner class ConstDecl(val identifier: Identifier, val varType: ValType, var value: Any): Decl() //TODO()

    inner class VarDecl(val identifier: Identifier, val varType: ValType, var expr: Expr?) : Decl()

    inner class FunDecl(val identifier: Identifier, val params: Array<VarDecl>, val retType: ValType,
                        var body: Block) : Decl() {
        init { //register function
            functions.add(this)
        }
    }

    fun getDecl(): Decl {
        val token = iter.next()
        return when (token.tokenType) {
            TokenTypeEnum.constDecl -> {
                val id = Identifier(IdentifierType.Const, decl = true)
                val type = getType()
                val expr = if (iter.peek().tokenType == TokenTypeEnum.equal) {
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
            TokenTypeEnum.varDecl -> {
                val id = Identifier(IdentifierType.Var, decl = true)
                val type = getType()
                val expr = if (iter.peek().tokenType == TokenTypeEnum.equal) {
                    getExpr()
                } else null
                VarDecl(id, type, expr)
            }
            TokenTypeEnum.funDecl -> {
                val id = Identifier(IdentifierType.Fun, decl = true)
                expect(TokenTypeEnum.openParenthesis)

                val params = ArrayList<VarDecl>()

                val blockScopeLevel = curScopeLevel + 1 //to fix parameter scope
                val blockScopeIndex = maxScopeIndex + 1

                loop@ while (true) {
                    params.add(getParDecl(blockScopeLevel, blockScopeIndex))
                    when (iter.peek().tokenType) {
                        TokenTypeEnum.comma -> {
                            iter.next()
                            continue@loop
                        }
                        TokenTypeEnum.closeParenthesis -> {
                            iter.next()
                            break@loop
                        }
                        else -> {
                            throw Exception("Error during parameter declaration: expected ',' or ')'. got ${iter.peek()}")
                        }
                    }
                }

                //closeParenthesis consumed

                val type = if (iter.peek().tokenType == TokenTypeEnum.colon) {
                    getType()
                } else ValType.none

                val block = Block()

                FunDecl(id, params.toTypedArray(), type, block)
            }
            else -> throw Exception("This should not be possible")
        }
    }

    fun getParDecl(scopeLevel: Int, scopeIndex: Int): VarDecl {
        val id = Identifier(IdentifierType.Var, decl = true, actualScopeLevel = scopeLevel, actualScopeIndex = scopeIndex)
        val type = getType()
        val expr = if (iter.peek().tokenType == TokenTypeEnum.equal) {
            getExpr()
        } else null

        return VarDecl(id, type, expr)
    }

    //data class Scope(val order: Int, Val index: Int = )
    enum class IdentifierType { Const, Var, Fun }

    val identifiers: ArrayList<Identifier> = ArrayList()
    var maxConstId = 0
    var maxVarId = 0
    var maxFunId = 0

    inner class Identifier(
        val type: IdentifierType, val decl: Boolean = false,
        val name: String = iter.next().text, //name initialized here cuz i needed to pass it in manually in Call()
        val actualScopeLevel: Int = -1, val actualScopeIndex: Int = -1
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
            // identifiers = ArrayList()
            println(identifiers)
            if (identifiers == null) {
                println("wat")
            }
            if (decl) {
                if (identifiers.find {
                        it.name == name && it.scopeIndex == scopeIndex
                    } == null) {
                    identifiers.add(this)
                } else {
                    throw Exception("Identifier <$name> already exists")
                }
            } else {
                if (identifiers.find { it.name == name && (it.scopeIndex == scopeIndex || it.scopeLevel < scopeLevel) } == null) {
                    throw Exception("Identifier <$name> not found")
                }
            }

            refId = when (type) {
                IdentifierType.Const -> maxConstId ++
                IdentifierType.Var -> maxVarId ++
                IdentifierType.Fun -> maxFunId ++
            }
        }
    }

    fun extractVariables(): Array<Variable> {
        return identifiers.filter { it.type == IdentifierType.Var }.map { Variable(it.name, interpreter.AST.ValType.bool, null) }.toTypedArray()
    }

    enum class ValType { none, bool, int, float }

    fun getType(): ValType {
        expect(TokenTypeEnum.colon)
        val token = expect(TokenTypeEnum.type)
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
            TokenTypeEnum.ifStmt -> If()
            TokenTypeEnum.whileStmt -> While()
            TokenTypeEnum.retStmt -> Return()
            TokenTypeEnum.startBlock -> Block()
            TokenTypeEnum.identifier -> {
                return when (getRelevantIdentifier(token.text).type) {
                    IdentifierType.Var -> Assign()
                    IdentifierType.Fun -> CallStmt()
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
            expect(TokenTypeEnum.ifStmt)
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
            expect(TokenTypeEnum.whileStmt)
            condition = getExpr()
            s = Block()
            //lineIndex = 0//todo: add line information
            //text = ""
        }
    }

    inner class Return(): Stmt() {
        val e: Expr
        init {
            expect(TokenTypeEnum.retStmt)
            e = getExpr()
        }
    }

    inner class Block(): Stmt() {
        val scopeIndex: Int
        val nodes = ArrayList<ASTNode>(16)
        init {
            expect(TokenTypeEnum.startBlock)

            scopes.push(curScopeIndex)

            curScopeLevel ++
            scopeIndex = maxScopeIndex ++
            curScopeIndex = scopeIndex

            var i = 0
            do {
                var token = iter.peek()

                when (token.tokenType) {
                    TokenTypeEnum.varDecl, TokenTypeEnum.funDecl -> nodes[i++] = getDecl() //todo: add line information
                    TokenTypeEnum.identifier, TokenTypeEnum.startBlock, TokenTypeEnum.ifStmt, TokenTypeEnum.whileStmt, TokenTypeEnum.retStmt ->  nodes[i++] = getStmt()
                    else -> { //EOF handled here
                        throw Exception("decl or stmt expected but $token found")
                        //iter.next()
                    }
                }

            } while (token.tokenType != TokenTypeEnum.endBlock)
            curScopeLevel --
            curScopeIndex = scopes.pop()
        }
    }

    inner class CallStmt(val call: Call = Call()): Stmt()
    inner class Call(): Expr(){
        val callee: FunDecl
        val params = ArrayList<Expr>(1)
        init {
            var name = iter.cur().text
            try {
                val id = Identifier(IdentifierType.Fun, name = name)
                callee = functions[id.refId]
            } catch (e: Exception) {
                throw Exception("Could not find callee <$name>;\n$e")
            }
            expect(TokenTypeEnum.openParenthesis)
            while (iter.peek().tokenType != TokenTypeEnum.closeParenthesis) {
                var node = getExpr()
                params.add(node)
            }
            iter.next()
        }
    }

    inner class Assign(val identifier: Identifier = Identifier(IdentifierType.Var),
                       val expr: Expr = getExpr()): Stmt()
    fun getRelevantIdentifier(name: String): Identifier {
        val candidates = identifiers.filter { it.name == name &&
                (it.scopeIndex == curScopeIndex || it.scopeLevel < curScopeLevel) }
        if (candidates.count() == 0) {
            throw Exception("Identifier <$name> not found")
        }

        return candidates.maxBy{ it.scopeLevel }!! //literally can't be null
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
        val unOp = if (iter.peek().tokenType == TokenTypeEnum.unaryMinusOp) {
            iter.next().tokenType
        } else {
            null
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
        return if (iter.peek().tokenType == TokenTypeEnum.powOp) {
            iter.next()
            var exp = getBase() //getExponent would be the same as getBAse
            BinOp(base, TokenTypeEnum.powOp, exp)
        } else {
            base
        }
    }

    fun getBase(): Expr {
        val token = iter.next()
        when(token.tokenType) {
            TokenTypeEnum.identifier -> {
                val id = getRelevantIdentifier(token.text)
                when (id.type) {
                    IdentifierType.Const -> {
                        return ConstRef(id.refId, id.valType)
                    }
                    IdentifierType.Var -> {
                        return VarRef(id.refId, id.valType)
                    }
                    IdentifierType.Fun -> {
                        return Call()
                    }
                }
            }
            TokenTypeEnum.openParenthesis -> {
                val e = getExpr()
                expect(TokenTypeEnum.closeParenthesis)
                return e
            }
            TokenTypeEnum.intValue -> {
                return ConstVal(token.text.toInt(), ValType.int)
            }
            TokenTypeEnum.floatValue -> {
                return ConstVal(token.text.toFloat(), ValType.float)
            }
            TokenTypeEnum.boolValue -> {
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

*/
