package interpreter

import util.RandomAccessIterator
import kotlin.collections.ArrayList
import interpreter.AST.*

class Parser() {
    lateinit var iter: RandomAccessIterator<Token>

    lateinit var tree: AST

    var import = ArrayList<ExportIdentifier>()

    fun parse(tokens: ArrayList<Token>): Environment {
        tree = AST(tokens, import.toTypedArray())
        val constants = tree.constants.toTypedArray()
        val variables = tree.extractVariables()
        val functions = tree.functions.toTypedArray()
        return Environment(tree.root, constants, variables, functions)
    }
}

class Environment(val root: ASTNode,
                  val constants: Array<ConstDecl>,
                  val variables: Array<Variable>,
                  val functions: Array<FunDecl>)

