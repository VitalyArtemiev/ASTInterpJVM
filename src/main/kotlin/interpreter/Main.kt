package interpreter

import util.Logger
import java.io.File

var terminalsPath: String = " "
var nonTerminalsPath: String = " "

fun main(args: Array<String>) {
    val logger = Logger("Main")

    var pathIndices: Int

    val terminalsText: String = try {
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

    var path = "langtest.tl"

    val l = Lexer(terminalsText)

    val tokens = l.lex(path)
    if (l.errors.isNotEmpty()) {
        logger.e("Lexical errors: \n" + l.errors.toString())
    } else {
        logger.i("Lexer finished with no errors")
    }

    val r = Runner()

    val runTimeIdentifiers: Array<ExternIdentifier> = arrayOf(
        ExternFunction("_PRINTVARTABLE", null, ValType.none, PrecompiledBlock(r::printVarTable,
            Token(-2, "Precompiled function _PRINTVARTABLE", TokenTypeEnum.identifier, 1))),
        ExternFunction("_PRINTLINEINFO", null, ValType.none, PrecompiledBlock(r::printLineInfo,
            Token(-2, "Precompiled function _PRINTLINEINFO", TokenTypeEnum.identifier, 1)))
    )

    val p = Parser(/*nonTerminalsText*/)
    p.import.addAll(runTimeIdentifiers)

    val env = p.parse(tokens)

    logger.i("Parser finished")

    //println(printTree(env.root))

    logger.d("Running unoptimized tree")
    r.run(env)

    val o = Optimizer()

    val optimizedEnv = o.optimize(env) //env is modified here

    //println(printTree(optimizedEnv.root))

    logger.d("Running optimized tree")
    r.run(optimizedEnv)
}

