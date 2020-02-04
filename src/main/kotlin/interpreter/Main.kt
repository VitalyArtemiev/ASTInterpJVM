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

    var path = "source.tl"

    val l = Lexer(terminalsText)

    val tokens = l.lex(path)
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
}

