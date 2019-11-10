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
    val iter = tokens.toRandomAccessIterator()
    var root = Prog()
    //lateinit var crawler: TreeCrawler
    var curScopeLevel = 0
    var curScopeIndex = 0

    open class ASTNode (val lineIndex: Int = 0, val text: String = "")

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
                    identifier, startBlock, ifStmt, whileStmt, retStmt ->  nodes[i++] = getStmt()
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
    enum class IdentifierType {Val, Var, Fun}
    val identifiers = ArrayList<Identifier>(8)
    inner class Identifier(val type: IdentifierType, val decl: Boolean = false,
                           val name: String = iter.next().text): ASTNode() { //name initialized here cuz i needed to pass it in manually in Call()
        val scopeLevel: Int = curScopeLevel
        val scopeIndex: Int = curScopeIndex
        init {
            if (decl) {
                if (identifiers.find {it.name == name && it.scopeIndex == scopeIndex} == null) {
                    identifiers.add(this)
                } else {
                    throw Exception("Identifier <$name> already exists")
                }
            } else {
                if (identifiers.find {it.name == name && (it.scopeIndex == scopeIndex || it.scopeLevel < scopeLevel)} == null) {
                    throw Exception("Identifier <$name> not found")
                }
            }
        }
    }
    enum class VarType {bool, int, float}

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
    open inner class Decl: ASTNode()
    inner class VarDecl(val identifier: Identifier, val varType: VarType, var expr: Expr): Decl()
    inner class FunDecl(val identifier: Identifier, val retType: VarType, var body: Block): Decl()

    fun getStmt(): Stmt {
        var token = iter.next()
        return when (token.tokenType) {
            ifStmt -> If(getExpr(), getStmt())
            whileStmt -> While(getExpr(), getStmt())
            retStmt -> Return(getExpr())
            startBlock -> Block()
            //callStmt ?????
            identifier -> Call()
            else -> throw Exception("This should not be possible")
        }
    }
    open inner class Stmt: ASTNode()
    inner class If(val condition: Expr, val s: Stmt): Stmt() {
        init {
            //lineIndex = 0//todo: add line information
            //text = ""
        }
    }
    inner class While(val e: Expr, val s: Stmt): Stmt() //s has to be block
    inner class Return(val e: Expr): Stmt()
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

    val boolOps = setOf (notOp, andOp, orOP, xorOp)
    val numOps = setOf (plusOP, minusOp, multOp, divOp)

    fun getExpr(): Expr {
        var save = iter.save()
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

        }

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
    inner class Factor()


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

