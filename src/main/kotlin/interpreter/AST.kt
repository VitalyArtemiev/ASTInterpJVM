package interpreter

import interpreter.TokenTypeEnum.*
import util.toRandomAccessIterator
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

enum class eBinOp {bMinus, bPlus, multiply, divide}
enum class eUnOp {uMinus, uPlus}

/*class binOp: ASTNode(2) {

    lateinit var op: eBinOp
    var arg1: ASTNode
        get() = children[0]!!
        set(node: ASTNode) {
            children[0] = node
        }
    var arg2: ASTNode
        get() = children[1]!!
        set(node: ASTNode) {
            children[1] = node
        }
}

class unOp: ASTNode(1) {
    lateinit var op: eBinOp
    var arg: ASTNode
        get() = children[0]!!
        set(node: ASTNode) {
            children[0] = node
        }
}

class value(): ASTNode(0) {
    var value: Int = 0
}

class variable: ASTNode(0) {
    var index: Int = -1
}*/


class AST(tokens: ArrayList<Token>) {
    var iter = tokens.toRandomAccessIterator()
    var root = Prog()
    //lateinit var crawler: TreeCrawler
    var curScopeLevel = 0
    var curScopeIndex = 0

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
            while (token.tokenType != EOF) {
                when (token.tokenType) {
                    varDecl, funDecl -> nodes[i++] = Decl() //todo: add line information
                    identifier, startBlock, ifStmt, whileStmt, retStmt -> nodes[i++] = getStmt()
                    else -> {
                        println("decl or stmt expected but $token found")
                        iter.next()
                    }
                }
                token = iter.peek()
            }
        }
    }

    fun getDecl(): Decl {
        var token = iter.next()
        return when (token.tokenType) {
            varDecl -> VarDecl(Identifier(IdentifierType.Var, decl = true), getType(), Expr())
            funDecl -> FunDecl(Identifier(IdentifierType.Fun, decl = true), getType(), Block())
            else -> throw Exception("This should not be possible")
        }
    }

    //data class Scope(val order: Int, Val index: Int = )
    enum class IdentifierType { Val, Var, Fun }

    val identifiers = ArrayList<Identifier>(8)

    inner class Identifier(
        val type: IdentifierType, val decl: Boolean = false,
        val name: String = iter.next().text
    ) : ASTNode() { //name initialized here cuz i needed to pass it in manually in Call()
        val scopeLevel: Int = curScopeLevel
        val scopeIndex: Int = curScopeIndex

        init {
            if (decl) {
                if (identifiers.find { it.name == name && it.scopeIndex == scopeIndex } == null) {
                    identifiers.add(this)
                } else {
                    throw Exception("Identifier <$name> already exists")
                }
            } else {
                if (identifiers.find { it.name == name && (it.scopeIndex == scopeIndex || it.scopeLevel < scopeLevel) } == null) {
                    throw Exception("Identifier <$name> not found")
                }
            }
        }
    }

    enum class VarType { bool, int, float }

    fun getType(): VarType {
        expect(colon)
        val token = expect(type)
        return when (token.text) {
            "bool" -> VarType.bool
            "int" -> VarType.int
            "float" -> VarType.float
            else -> throw Exception("Invalid type $token")
        }
    }

    /*inner class Type() {
        val type: VarType
        init {
            expect(colon)
            val token = expect(TokenTypeEnum.type)
            type = when (token.text) {
                "bool" -> VarType.bool
                "int" -> VarType.int
                "float" -> VarType.float
                else -> throw Exception("Invalid type $token")
            }
        }
    }*/
    open inner class Decl : ASTNode()

    inner class VarDecl(val identifier: Identifier, val varType: VarType, var expr: Expr) : Decl()
    inner class FunDecl(val identifier: Identifier, val retType: VarType, var body: Block) : Decl()

    fun getStmt(): Stmt {
        val token = iter.peek()
        return when (token.tokenType) {
            ifStmt -> If()
            whileStmt -> While()
            retStmt -> Return()
            startBlock -> Block()
            identifier -> {
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
    inner class Block(): Stmt() {
        val scopeIndex: Int
        val nodes = ArrayList<ASTNode>(16)
        init {
            expect(startBlock)
            curScopeLevel ++
            scopeIndex = curScopeIndex ++

            do {
                var token = iter.peek()
                var i = 0
                when (token.tokenType) {
                    varDecl, funDecl -> nodes[i++] = Decl() //todo: add line information
                    identifier, startBlock, ifStmt, whileStmt, retStmt ->  nodes[i++] = getStmt()
                    else -> { //EOF handled here
                        throw Exception("decl or stmt expected but $token found")
                        //iter.next()
                    }
                }

            } while (token.tokenType != endBlock)
            curScopeLevel --
        }
    }
    inner class CallStmt(val call: Call = Call()): Stmt()
    inner class Call(): Expr(){
        val callee: Identifier
        val params = ArrayList<Expr>(1)
        init {
            var name = iter.cur().text
            try {
                callee = Identifier(IdentifierType.Fun, name = name)
            } catch (e: Exception) {
                throw Exception("Could not find callee <$name>;\n$e")
            }
            expect(openParenthesis)
            while (iter.peek().tokenType != closeParenthesis) {
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

    val exprMembers = setOf (unaryMinusOp, plusOP, minusOp, divOp, multOp, powOp,
        notOp, andOp, orOP, xorOp,
        identifier, intValue, floatValue,
        openParenthesis,
        closeParenthesis)

    fun getExpr(): Expr {

        val expr = ArrayList<Token> (3)
        var token = iter.peek()
        var last = Token(0, "", TBD)
        var expectedParentheses = 0;
        loop@ while(token.tokenType in exprMembers) {
            when (token.tokenType) {
                openParenthesis -> expectedParentheses ++
                closeParenthesis -> expectedParentheses --
                identifier -> {
                    var ident = getRelevantIdentifier(token.text)
                    if (!(last.tokenType == TBD || last.tokenType in ops || last.tokenType == openParenthesis)) {
                        break@loop
                    }
                    when (ident.type) {
                        IdentifierType.Val -> {

                        }
                        IdentifierType.Var -> {

                        }
                        IdentifierType.Fun -> {
                            Call()

                        }
                    }
                }
                in ops -> {}
            }
            expr.add(iter.next())
            last = token
            token = iter.peek()
            when (last.tokenType) {
                TBD -> {}
                in ops -> {
                    if (token.tokenType != identifier) {
                        throw Exception("Identifier expected, but $token found")
                    }
                }
                identifier -> {

                }
            }
        }

        if (expectedParentheses > 0) {
            throw Exception("Closing parenthesis missing while parsing expression")
        } else
        if (expectedParentheses < 0) {
            throw Exception("Opening parenthesis missing while parsing expression")
        }

        val rpnExpr = toRPN(expr) //http://www.engr.mun.ca/~theo/Misc/exp_parsing.htm
        val mainIter = iter
        iter = rpnExpr.toRandomAccessIterator()

        /*var save = iter.save()
        var f1 = Factor()
        var op = BinOp()

        optional {
            trySeveral {
                binOp
                Factor
            }
        }


        var first = iter.next()
        var second = iter.peek()
        when () {

        }*/
        token = iter.peek()

        when (token.tokenType) {
            intValue, boolValue, floatValue -> return Value()
            identifier -> { //check call or var
                var ident = getRelevantIdentifier(token.text)
                when (ident.type) {
                    IdentifierType.Var -> {return Variable()}
                    IdentifierType.Fun -> {return Call()}
                }
            }
            plusOP, minusOp, multOp, divOp -> {}
            equal, less, greater, notEqual, lequal, gequal -> {}
            else -> throw Exception("Malformed expression: got $token")
        }
    }

    fun getBinOp(): Expr {
        var f1 = Factor()
        var token = iter.next()
        var f2 = Factor() // getExpr()
        when (token.tokenType) {
            plusOP -> {return Sum}
            minusOp -> {}
            multOp -> {}
            divOp-> {}
            else -> {throw Exception("Could not find binOp, found <$token>")}
        }

    }

    open inner class Expr: ASTNode()
    inner class Neg(val expr: Expr = getExpr()): Expr()
    inner class Sum(val left: Expr = getExpr(), val right: Expr = getExpr()): Expr()
    inner class Mult(val left: Expr = getExpr(), val right: Expr = getExpr()): Expr()
    inner class Div(val left: Expr = getExpr(), val right: Expr = getExpr()): Expr()
    inner class Comp(val left: Expr = getExpr(), val right: Expr = getExpr()): Expr()
    open inner class Value(): Expr()
    inner class Variable(): Value()
    inner class Constant(): Value()
    inner class Term(val left: Factor)

    inner class Factor(val left: Term = Term())

    var a = false

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

