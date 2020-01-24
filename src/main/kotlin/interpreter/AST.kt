package interpreter

import interpreter.TokenTypeEnum.*
import util.Stack
import util.toRandomAccessIterator

class AST(tokens: ArrayList<Token>) {
    var iter = tokens.toRandomAccessIterator()
    //var root = Prog()
    //lateinit var crawler: TreeCrawler
    var curScopeLevel = 0
    var curScopeIndex = 0
    var maxScopeIndex = 0
    val scopes = Stack<Int>()

    val functions = ArrayList<FunDecl>() //to keep track of attached blocks

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
                    varDecl, funDecl -> nodes[i++] = getDecl() //todo: add line information
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

    open inner class Decl : ASTNode()

    inner class VarDecl(val identifier: Identifier, val varType: ValType, var expr: Expr?) : Decl()

    inner class FunDecl(val identifier: Identifier, params: Array<VarDecl>, retType: ValType, var body: Block) : Decl() { //parameter vars are declared in wrong scope
        init { //register function
            functions.add(this)
        }
    }
    inner class ConstDecl() //TODO()

    fun getDecl(): Decl {
        val token = iter.next()
        return when (token.tokenType) {
            varDecl -> {
                val id = Identifier(IdentifierType.Var, decl = true)
                val type = getType()
                val expr = if (iter.peek().tokenType == equal) {
                    getExpr()
                } else null
                VarDecl(id, type, expr)}
            funDecl -> {
                val id = Identifier(IdentifierType.Fun, decl = true)
                expect(openParenthesis)
                val params = ArrayList<VarDecl>()
                var token = iter.next()
                while (token.tokenType != closeParenthesis) {
                    params.add(getParDecl())
                    //TODO: CONSUME COMMA!!!!!!!!!!!!!
                    token = iter.next()
                }
                //closeParenthesis consumed

                val type = if (iter.peek().tokenType == colon) {
                    getType()
                } else ValType.none

                val block = Block()
                val blockScopeLevel = curScopeLevel + 1

                params.forEach {
                    it.identifier.scopeIndex = block.scopeIndex
                    it.identifier.scopeLevel = blockScopeLevel
                }

                FunDecl(id, params.toTypedArray(), type, block)
            }
            else -> throw Exception("This should not be possible")
        }
    }

    fun getParDecl(): VarDecl {
        val id = Identifier(IdentifierType.Var, decl = true)
        val type = getType()
        val expr = if (iter.peek().tokenType == equal) {
            getExpr()
        } else null

        return VarDecl(id, type, expr)
    }

    //data class Scope(val order: Int, Val index: Int = )
    enum class IdentifierType { Const, Var, Fun }

    val identifiers = ArrayList<Identifier>(8)

    inner class Identifier(
        val type: IdentifierType, val decl: Boolean = false,
        val name: String = iter.next().text //name initialized here cuz i needed to pass it in manually in Call()
    ) : ASTNode() {
        var scopeLevel: Int = curScopeLevel
        var scopeIndex: Int = curScopeIndex

        lateinit var valType: ValType

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

    enum class ValType { none, bool, int, float }

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

            scopes.push(curScopeIndex)

            curScopeLevel ++
            scopeIndex = maxScopeIndex ++
            curScopeIndex = scopeIndex

            var i = 0
            do {
                var token = iter.peek()

                when (token.tokenType) {
                    varDecl, funDecl -> nodes[i++] = getDecl() //todo: add line information
                    identifier, startBlock, ifStmt, whileStmt, retStmt ->  nodes[i++] = getStmt()
                    else -> { //EOF handled here
                        throw Exception("decl or stmt expected but $token found")
                        //iter.next()
                    }
                }

            } while (token.tokenType != endBlock)
            curScopeLevel --
            curScopeIndex = scopes.pop()
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
        identifier, intValue, floatValue, boolValue,
        openParenthesis,
        closeParenthesis)

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
        val unOp = if (iter.peek().tokenType == unaryMinusOp) {
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
                when(id.type) {
                    IdentifierType.Const -> {
                        Identifier()
                        return Constant(id.type)
                    }
                    IdentifierType.Var -> {
                        return VarRef()
                    }
                    IdentifierType.Fun -> {
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
                return Constant(ValType.int, token.text.toInt())
            }
            floatValue -> {
                return Constant(ValType.float, token.text.toFloat())
            }
            boolValue -> {
                return Constant(ValType.bool, token.text.toBoolean())
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
    inner class VarRef(type: ValType, id: Identifier): Value(type)
    inner class Constant(type: ValType, value: Any): Value(type)

    //inner class Term(val left: Factor)

    //inner class Factor(val left: Term = Term())

    fun shuntYard(tokens:  ArrayList<Token>) : ArrayList<Token> {
        var temp: Token
        var opStack = Stack<Token>()
        val outputStack = Stack<Token>()
        var nodeStack = Stack<ASTNode>()

        fun addNode(token: Token) {
            require(token.tokenType in ops)

            val arg1 = nodeStack.pop()
            val leaf1: ASTNode
            /*when (arg1.tokenType) {
                floatValue -> leaf1 = Constant(ValType.float, arg1.text.toFloat())
                intValue -> leaf1 = Constant(ValType.int, arg1.text.toInt())
                boolValue -> leaf1 = Constant(ValType.bool, arg1.text.toBoolean())
                in unaryOps -> leaf1 =
                in ops ->
                else -> leaf1 =
            }*/
            if (token.tokenType != TokenTypeEnum.unaryMinusOp) {
                val arg2 = nodeStack.pop()
            }
            else {

            }
        }

        //find unary minuses
        if (tokens[0].tokenType == minusOp){
            tokens[0].tokenType = unaryMinusOp
        }

        for (i in 1 until tokens.size) {
            if (((tokens[i - 1].tokenType in ops) or (tokens[i - 1].tokenType == openParenthesis))
                and (tokens[i].tokenType == minusOp)) {
                tokens[i].tokenType = unaryMinusOp
            }
        }


        for (token in tokens) {
            when (token.tokenType) {
                floatValue -> nodeStack.push(Constant(ValType.float, token.text.toFloat()))
                intValue -> nodeStack.push(Constant(ValType.int, token.text.toInt()))
                boolValue -> nodeStack.push(Constant(ValType.bool, token.text.toBoolean()))
                openParenthesis -> {opStack.push(token)}
                closeParenthesis -> {

                    temp = opStack.pop()
                    while (temp.tokenType != openParenthesis){

                        outputStack.push(temp)//#############
                        if (opStack.size != 0) {
                            temp = opStack.pop()
                        }
                        else {
                            print(outputStack)
                            throw Exception ("error with parentheses")
                        }
                    }
                }
                in ops -> {
                    if (opStack.size > 0) {
                        temp = opStack.peek()

                        while ((temp.tokenType in ops) &&
                            ((ops.indexOf(temp.tokenType) > ops.indexOf(token.tokenType)) ||
                                    (ops.indexOf(temp.tokenType) == ops.indexOf(token.tokenType) &&
                                            temp.tokenType in laOps)) && (opStack.size > 0)) {

                            outputStack.push(opStack.pop()) //#############
                            if (opStack.size == 0) {break} //jank, implement a proper stack
                            temp = opStack.peek()
                        }
                    }

                    opStack.push(token)
                }
                else -> {
                    throw Exception("error in symbols")
                }
            }
        }

        outputStack.addAll(opStack.reversed()) //#############

        return outputStack.toArrayList()
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

