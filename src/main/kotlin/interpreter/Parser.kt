package interpreter

import util.Logger
import util.RandomAccessIterator

class Parser {
    val logger = Logger("Parser")
    lateinit var iter: RandomAccessIterator<Token>

    lateinit var tree: AST

    var import = ArrayList<ExternIdentifier>()

    fun parse(tokens: ArrayList<Token>): Environment {
        try {
            tree = AST(tokens, import.toTypedArray())
        } catch (e: ASTException) {
            logger.e(e.message!!)
            logger.d(e.stackTrace.contentToString())
            throw Exception("Parser encountered a problem, halting")
        }

        val constants = tree.constants.toTypedArray()
        val variables = tree.extractVariables()
        val functions = tree.functions.toTypedArray()

        val topLevelIdentifiers = tree.exportIdentifiers() //for inter-module communication

        return Environment(tree.root, constants, variables, functions)
    }
}

class Environment(val root: ASTNode,
                  val constants: Array<ConstDecl>,
                  val variables: Array<Variable>,
                  val functions: Array<FunDecl>)

